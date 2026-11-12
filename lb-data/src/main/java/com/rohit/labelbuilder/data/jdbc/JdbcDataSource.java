package com.rohit.labelbuilder.data.jdbc;

import com.rohit.labelbuilder.data.AbstractRecordSet;
import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link DataSource} over a SQL query (FR-DS-05, FR-DS-06).
 *
 * <p>It holds a <b>config id</b>, not a connection — the indirection that keeps credentials out of
 * the {@code .lbl} (risk R-08). Everything about reaching the database is the
 * {@link ConnectionManager}'s business; this class knows a query and a schema.
 *
 * <p><b>The schema can be cached.</b> {@link FieldDefinition} promises a designer can lay a label out
 * while the source is unreachable, and this is where that is honoured: a source constructed with
 * fields uses them and never touches the database until {@link #open} is called. A template opened on
 * a laptop with no VPN is fully editable; it just cannot merge.
 *
 * <p>Where there is no cached schema, the columns are read from
 * {@link PreparedStatement#getMetaData()} — the driver describes the result without running the
 * query, so designing against a report that takes a minute to execute costs nothing. Drivers that
 * return nothing there fall back to executing with a one-row limit.
 */
public final class JdbcDataSource implements DataSource {

    private final String name;
    private final ConnectionManager connections;
    private final String configId;
    private final NamedQuery query;
    private volatile List<FieldDefinition> fields;

    public JdbcDataSource(String name, ConnectionManager connections, String configId, String sql) {
        this(name, connections, configId, sql, null);
    }

    /**
     * @param cachedFields the schema stored with the template, or null to read it from the database
     */
    public JdbcDataSource(
            String name,
            ConnectionManager connections,
            String configId,
            String sql,
            List<FieldDefinition> cachedFields) {
        this.name = Objects.requireNonNull(name, "name");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.configId = Objects.requireNonNull(configId, "configId");
        this.query = NamedQuery.parse(Objects.requireNonNull(sql, "sql"));
        this.fields = cachedFields == null ? null : List.copyOf(cachedFields);
    }

    @Override
    public String name() {
        return name;
    }

    public String configId() {
        return configId;
    }

    /** The statement as written, with its {@code :name} placeholders intact. */
    public NamedQuery query() {
        return query;
    }

    /** The parameters this query needs values for, in first-seen order. */
    public List<String> parameterNames() {
        return query.distinctParameterNames();
    }

    @Override
    public List<FieldDefinition> fields() {
        List<FieldDefinition> cached = fields;
        if (cached == null) {
            cached = describe();
            fields = cached;
        }
        return cached;
    }

    /** Re-reads the schema from the database, for when the query has been edited. */
    public List<FieldDefinition> refreshFields() {
        List<FieldDefinition> fresh = describe();
        fields = fresh;
        return fresh;
    }

    @Override
    public RecordSet open(Map<String, Object> parameters) {
        return new JdbcCursor(fields(), connections, configId, query, parameters);
    }

    /**
     * Asks the driver to describe the result set.
     *
     * <p>Parameters are left unbound. {@code getMetaData()} describes the <em>shape</em> of the result,
     * which does not depend on the values a {@code WHERE} clause will be given, and a driver that
     * insists otherwise is caught by the fallback below.
     */
    private List<FieldDefinition> describe() {
        try (Connection connection = connections.connect(configId);
                PreparedStatement statement = connection.prepareStatement(query.sql())) {
            ResultSetMetaData metaData = statement.getMetaData();
            if (metaData != null) {
                return read(metaData);
            }
            return describeByExecuting(statement);
        } catch (SQLException e) {
            throw new DataException(failureMessage(e), e);
        }
    }

    /** For drivers whose {@code getMetaData()} returns null until the statement has run. */
    private List<FieldDefinition> describeByExecuting(PreparedStatement statement) throws SQLException {
        bindNulls(statement);
        statement.setMaxRows(1);
        try (ResultSet rows = statement.executeQuery()) {
            return read(rows.getMetaData());
        }
    }

    private static List<FieldDefinition> read(ResultSetMetaData metaData) throws SQLException {
        List<FieldDefinition> fields = new ArrayList<>(metaData.getColumnCount());
        for (int column = 1; column <= metaData.getColumnCount(); column++) {
            fields.add(SqlTypes.field(metaData, column));
        }
        return fields;
    }

    /**
     * Binds every parameter to NULL, purely so a describing execution is legal.
     *
     * <p>Safe because only the column list is read from the result, and a {@code WHERE} clause matching
     * nothing is the cheapest possible way to get it.
     */
    private void bindNulls(PreparedStatement statement) throws SQLException {
        for (int i = 1; i <= query.parameterNames().size(); i++) {
            statement.setObject(i, null);
        }
    }

    private String failureMessage(SQLException e) {
        return "the query for data source '" + name + "' failed: " + e.getMessage();
    }

    /**
     * Streams a result set, owning the connection and statement that produced it.
     *
     * <p>All three are released together when the cursor closes, which is why
     * {@code try (RecordSet …)} is enough to guarantee a print job cannot leak a connection even when
     * it fails halfway.
     */
    private static final class JdbcCursor extends AbstractRecordSet {

        private final List<String> names;
        private final Connection connection;
        private final PreparedStatement statement;
        private final ResultSet rows;

        JdbcCursor(
                List<FieldDefinition> fields,
                ConnectionManager connections,
                String configId,
                NamedQuery query,
                Map<String, Object> parameters) {
            super(fields);
            this.names = fields.stream().map(FieldDefinition::name).toList();
            Connection openedConnection = null;
            PreparedStatement preparedStatement = null;
            try {
                openedConnection = connections.connect(configId);
                preparedStatement = openedConnection.prepareStatement(
                        query.sql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                bind(preparedStatement, query, parameters);
                // Per-vendor: on MySQL anything but Integer.MIN_VALUE buffers the whole result set in
                // the driver, which would make the cursor contract a fiction (risk R-06).
                preparedStatement.setFetchSize(
                        connections.require(configId).dialect().streamingFetchSize());
                preparedStatement.setFetchDirection(ResultSet.FETCH_FORWARD);
                this.connection = openedConnection;
                this.statement = preparedStatement;
                this.rows = preparedStatement.executeQuery();
            } catch (SQLException e) {
                closeQuietly(preparedStatement, openedConnection);
                throw new DataException("the query failed: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                closeQuietly(preparedStatement, openedConnection);
                throw e;
            }
        }

        /**
         * Binds each named parameter by position.
         *
         * <p>A missing value is refused rather than bound to NULL. NULL would make the query succeed
         * and return the wrong rows — or none — and a print run that quietly produces no labels is
         * harder to diagnose than one that says which parameter it was not given.
         */
        private static void bind(PreparedStatement statement, NamedQuery query, Map<String, Object> parameters)
                throws SQLException {
            List<String> ordered = query.parameterNames();
            for (int i = 0; i < ordered.size(); i++) {
                String parameter = ordered.get(i);
                if (!parameters.containsKey(parameter)) {
                    throw new DataException("the query needs a value for ':" + parameter + "'; supplied: "
                            + (parameters.isEmpty() ? "nothing" : parameters.keySet()));
                }
                statement.setObject(i + 1, parameters.get(parameter));
            }
        }

        @Override
        protected DataRecord advance() {
            try {
                if (!rows.next()) {
                    return null;
                }
                Map<String, Object> values = new LinkedHashMap<>();
                for (int i = 0; i < names.size(); i++) {
                    values.put(names.get(i), SqlTypes.value(rows.getObject(i + 1)));
                }
                return new MapRecord(values);
            } catch (SQLException e) {
                throw new DataException("reading the next row failed: " + e.getMessage(), e);
            }
        }

        @Override
        protected void release() {
            closeQuietly(statement, connection);
        }

        private static void closeQuietly(PreparedStatement statement, Connection connection) {
            // Both are attempted whatever the other does: a statement that will not close must not be
            // the reason a connection stays open.
            try {
                if (statement != null) {
                    statement.close();
                }
            } catch (SQLException ignored) {
                // nothing useful to do while unwinding
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ignored) {
                // nothing useful to do while unwinding
            }
        }
    }
}
