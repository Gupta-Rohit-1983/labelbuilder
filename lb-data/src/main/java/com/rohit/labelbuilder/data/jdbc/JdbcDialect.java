package com.rohit.labelbuilder.data.jdbc;

import java.util.Objects;

/**
 * The databases the connection wizard knows how to build a URL for (FR-DS-05).
 *
 * <p>SQL Server and MySQL ship with the product; the rest are recognised here but need the user to
 * supply the driver JAR, which is why {@link #isDriverAvailable()} exists and why a missing driver has
 * to produce a message naming the dialect rather than a {@code ClassNotFoundException}.
 *
 * <p>{@link #streamingFetchSize()} is the load-bearing member. Risk R-06 is a promise that a merge
 * streams, and on MySQL that promise is false by default: the Connector/J driver reads the entire
 * result set into the client before returning the first row unless the fetch size is exactly
 * {@link Integer#MIN_VALUE}. A cursor interface alone cannot deliver streaming — the driver has to be
 * told, per vendor, and this is where that knowledge lives.
 */
public enum JdbcDialect {
    SQL_SERVER(
            "Microsoft SQL Server",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://{host}:{port};databaseName={database};encrypt=true;trustServerCertificate=true",
            1433,
            Fetch.DEFAULT),
    MYSQL(
            "MySQL / MariaDB",
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://{host}:{port}/{database}",
            3306,
            // See the class comment: anything else buffers the whole result set client-side.
            Integer.MIN_VALUE),
    POSTGRESQL(
            "PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://{host}:{port}/{database}", 5432, Fetch.DEFAULT),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//{host}:{port}/{database}", 1521, Fetch.DEFAULT),
    SQLITE("SQLite", "org.sqlite.JDBC", "jdbc:sqlite:{database}", 0, Fetch.DEFAULT),
    H2("H2", "org.h2.Driver", "jdbc:h2:{database}", 0, Fetch.DEFAULT),
    /** A URL typed by hand, for a database not listed above. */
    GENERIC("Other (JDBC URL)", null, "{database}", 0, Fetch.DEFAULT);

    /**
     * Held in a nested class because an enum constant's arguments may not reference a static field of
     * the enum itself.
     */
    private static final class Fetch {
        /** Rows per round trip: large enough to amortise latency, small enough to stay bounded. */
        static final int DEFAULT = 500;
    }

    private final String displayName;
    private final String driverClassName;
    private final String urlTemplate;
    private final int defaultPort;
    private final int streamingFetchSize;

    JdbcDialect(String displayName, String driverClassName, String urlTemplate, int defaultPort, int fetchSize) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.urlTemplate = urlTemplate;
        this.defaultPort = defaultPort;
        this.streamingFetchSize = fetchSize;
    }

    public String displayName() {
        return displayName;
    }

    /** The driver class, or null for {@link #GENERIC}, where the URL decides. */
    public String driverClassName() {
        return driverClassName;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** Whether this dialect's URL needs a host and port, as opposed to naming a file. */
    public boolean isNetworked() {
        return defaultPort > 0;
    }

    public int streamingFetchSize() {
        return streamingFetchSize;
    }

    /** Whether the driver is on the classpath, so the wizard can say so before a connection is tried. */
    public boolean isDriverAvailable() {
        if (driverClassName == null) {
            return true;
        }
        try {
            Class.forName(driverClassName);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Builds a JDBC URL.
     *
     * @param host ignored where {@link #isNetworked()} is false
     * @param port the port, or 0 for this dialect's default
     * @param database the database name, or the file path for a file-based dialect
     */
    public String url(String host, int port, String database) {
        Objects.requireNonNull(database, "database");
        return urlTemplate
                .replace("{host}", host == null ? "" : host)
                .replace("{port}", String.valueOf(port > 0 ? port : defaultPort))
                .replace("{database}", database);
    }

    /**
     * Quotes an identifier so a column called {@code Order} or {@code Total Qty} is still valid SQL.
     *
     * <p>Quoting is per-vendor and unavoidable for generated SQL: a reporting view's columns are named
     * by whoever built the view, and reserved words and spaces are both common. The quote character
     * inside an identifier is escaped by doubling it, which is what every one of these dialects
     * expects.
     */
    public String quote(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("an identifier must not be blank");
        }
        return switch (this) {
            case SQL_SERVER -> "[" + identifier.replace("]", "]]") + "]";
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            default -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }

    /** Where a row limit goes in a {@code SELECT}, which the SQL standard left to the vendors. */
    public LimitStyle limitStyle() {
        return switch (this) {
            case SQL_SERVER -> LimitStyle.TOP;
            // Oracle only grew LIMIT's equivalent in 12c; FETCH FIRST is the standard spelling.
            case ORACLE -> LimitStyle.FETCH_FIRST;
            default -> LimitStyle.LIMIT;
        };
    }

    /** The three spellings of "give me at most n rows". */
    public enum LimitStyle {
        /** {@code SELECT TOP (n) …} — before the column list. */
        TOP,
        /** {@code … LIMIT n} — after everything. */
        LIMIT,
        /** {@code … FETCH FIRST n ROWS ONLY} — after everything. */
        FETCH_FIRST
    }

    /** The dialect a JDBC URL belongs to, for recognising a URL somebody pasted in. */
    public static JdbcDialect ofUrl(String url) {
        if (url != null) {
            for (JdbcDialect dialect : values()) {
                if (dialect != GENERIC && url.startsWith(prefixOf(dialect))) {
                    return dialect;
                }
            }
        }
        return GENERIC;
    }

    private static String prefixOf(JdbcDialect dialect) {
        // "jdbc:sqlserver://{host}…" → "jdbc:sqlserver:"
        String template = dialect.urlTemplate;
        int firstPlaceholder = template.indexOf('{');
        String literal = firstPlaceholder < 0 ? template : template.substring(0, firstPlaceholder);
        int lastColon = literal.lastIndexOf(':');
        return lastColon < 0 ? literal : literal.substring(0, lastColon + 1);
    }
}
