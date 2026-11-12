package com.rohit.labelbuilder.data.jdbc;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;

/**
 * The Connection Manager: named connection configs, test-connection, and the schema browser
 * (FR-DS-06).
 *
 * <p>It is the one place a password is handled. A caller asks for a connection by config id; this
 * class resolves the secret from the {@link CredentialStore}, hands it to the driver, and wipes its
 * copy. Nothing above it ever sees a credential, which is what lets the rest of lb-data — and every
 * {@code .lbl} — be written without one (risk R-08).
 *
 * <p>Connections are not pooled. A merge opens a cursor, streams it and closes it, so a pool's
 * benefit is small, while its cost is a set of live connections outliving the job that needed them,
 * each holding a credential that was resolved minutes ago. Pooling belongs on the server, where
 * request rates justify it, and {@link #connect} is the seam to put it behind if that changes.
 */
public final class ConnectionManager {

    /** Long enough for a WAN round trip, short enough that a wrong host does not look like a hang. */
    private static final int TEST_TIMEOUT_SECONDS = 10;

    private final Map<String, ConnectionConfig> configs = new LinkedHashMap<>();
    private final CredentialStore credentials;

    public ConnectionManager(CredentialStore credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    /** A manager for connections that need no password, such as a local SQLite file. */
    public static ConnectionManager withoutCredentials() {
        return new ConnectionManager(CredentialStore.none());
    }

    public ConnectionManager register(ConnectionConfig config) {
        configs.put(config.id(), config);
        return this;
    }

    public void remove(String configId) {
        configs.remove(configId);
    }

    public Optional<ConnectionConfig> config(String configId) {
        return Optional.ofNullable(configs.get(configId));
    }

    public List<ConnectionConfig> configs() {
        return List.copyOf(configs.values());
    }

    /**
     * Opens a connection, resolving the secret now rather than earlier.
     *
     * <p>The caller owns the connection and must close it. {@link JdbcDataSource} ties one to the
     * lifetime of a cursor so that closing the record set closes the connection too.
     */
    public Connection connect(String configId) {
        ConnectionConfig config = require(configId);
        requireDriver(config);
        char[] password = credentials.password(configId).orElse(null);
        try {
            Properties properties = new Properties();
            config.properties().forEach(properties::setProperty);
            if (config.username() != null && !config.username().isBlank()) {
                properties.setProperty("user", config.username());
            }
            if (password != null) {
                // Properties forces a String here, so the secret does briefly exist as one. The array
                // is still wiped below, which bounds how long the char data itself is readable.
                properties.setProperty("password", new String(password));
            }
            Connection connection = DriverManager.getConnection(config.url(), properties);
            markReadOnly(connection);
            return connection;
        } catch (SQLException e) {
            // The driver's message can echo the URL; it never echoes the password, and the config
            // cannot hold one anyway.
            throw new DataException(
                    "cannot connect to '" + config.displayName() + "' (" + config.url() + "): " + e.getMessage(), e);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Tries the connection and reports what answered, for the wizard's Test button.
     *
     * <p>Never throws: a failed test is an answer, not an error, and the operator needs the driver's
     * own words to fix a typo in a host name.
     */
    public ConnectionTest test(String configId) {
        ConnectionConfig config = configs.get(configId);
        if (config == null) {
            return ConnectionTest.failed("there is no connection configured with id '" + configId + "'");
        }
        if (!config.dialect().isDriverAvailable()) {
            return ConnectionTest.failed(config.dialect().displayName()
                    + " needs its JDBC driver on the classpath; add the driver JAR and try again");
        }
        try (Connection connection = connect(configId)) {
            if (!connection.isValid(TEST_TIMEOUT_SECONDS)) {
                return ConnectionTest.failed("the connection opened but is not usable");
            }
            // Reading the metadata proves a real round trip, which merely opening a socket does not.
            DatabaseMetaData metaData = connection.getMetaData();
            return new ConnectionTest(
                    true,
                    "connected to " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion(),
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion());
        } catch (DataException e) {
            return ConnectionTest.failed(e.getMessage());
        } catch (SQLException e) {
            return ConnectionTest.failed(e.getMessage());
        }
    }

    /** The tables and views the login can see, for the schema browser (FR-DS-06). */
    public List<TableRef> tables(String configId) {
        try (Connection connection = connect(configId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<TableRef> tables = new ArrayList<>();
            // "BASE TABLE" as well as "TABLE": the SQL standard name, which some drivers report instead.
            try (ResultSet rows = metaData.getTables(null, null, "%", new String[] {"TABLE", "BASE TABLE", "VIEW"})) {
                while (rows.next()) {
                    tables.add(TableRef.of(
                            rows.getString("TABLE_CAT"),
                            rows.getString("TABLE_SCHEM"),
                            rows.getString("TABLE_NAME"),
                            rows.getString("TABLE_TYPE")));
                }
            }
            return tables;
        } catch (SQLException e) {
            throw new DataException("cannot list the tables of '" + configId + "': " + e.getMessage(), e);
        }
    }

    /**
     * The columns of one table, so a field tree can be shown before any query exists.
     *
     * <p>Read from {@link DatabaseMetaData} rather than by selecting from the table: the operator may
     * be browsing a table of ten million rows, and looking at its shape should not read any of them.
     */
    public List<FieldDefinition> columns(String configId, TableRef table) {
        try (Connection connection = connect(configId)) {
            List<FieldDefinition> fields = new ArrayList<>();
            try (ResultSet rows = connection
                    .getMetaData()
                    .getColumns(table.catalog().orElse(null), table.schema().orElse(null), table.name(), "%")) {
                while (rows.next()) {
                    fields.add(column(rows));
                }
            }
            if (fields.isEmpty()) {
                throw new DataException("no columns found for " + table.qualifiedName()
                        + "; the table may not exist or may not be visible to this login");
            }
            return fields;
        } catch (SQLException e) {
            throw new DataException("cannot read the columns of " + table.qualifiedName() + ": " + e.getMessage(), e);
        }
    }

    private static FieldDefinition column(ResultSet rows) throws SQLException {
        FieldType type = SqlTypes.of(rows.getInt("DATA_TYPE"));
        int size = rows.getInt("COLUMN_SIZE");
        int decimals = rows.getInt("DECIMAL_DIGITS");
        boolean nullable = rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
        return new FieldDefinition(
                rows.getString("COLUMN_NAME"),
                type,
                type == FieldType.STRING && size > 0 ? OptionalInt.of(size) : OptionalInt.empty(),
                type == FieldType.DECIMAL && decimals > 0 ? OptionalInt.of(decimals) : OptionalInt.empty(),
                nullable);
    }

    /**
     * Asks the driver to treat the connection as read-only.
     *
     * <p>A data source only ever reads, and saying so lets a server route the session to a replica and
     * refuses an accidental write. It is advisory — the JDBC spec allows a driver to ignore it, and
     * some reject it outright — so a refusal is not a reason to fail a connection that works.
     */
    private static void markReadOnly(Connection connection) {
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // advisory only; the connection is still perfectly usable for reading
        }
    }

    ConnectionConfig require(String configId) {
        ConnectionConfig config = configs.get(configId);
        if (config == null) {
            throw new DataException("no connection is configured with id '" + configId
                    + "'; a template referring to it needs that connection set up on this machine");
        }
        return config;
    }

    private static void requireDriver(ConnectionConfig config) {
        if (!config.dialect().isDriverAvailable()) {
            throw new DataException(config.dialect().displayName() + " needs its JDBC driver ("
                    + config.dialect().driverClassName()
                    + ") on the classpath; SQL Server and MySQL ship with LabelBuilder, others need the"
                    + " driver JAR adding (FR-DS-05)");
        }
    }

    /** The outcome of a test-connection attempt. */
    public record ConnectionTest(boolean successful, String message, String productName, String productVersion) {

        static ConnectionTest failed(String message) {
            return new ConnectionTest(false, message, "", "");
        }
    }
}
