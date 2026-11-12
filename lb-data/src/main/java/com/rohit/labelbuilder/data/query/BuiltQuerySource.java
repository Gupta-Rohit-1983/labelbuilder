package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.RecordSet;
import com.rohit.labelbuilder.data.jdbc.ConnectionConfig;
import com.rohit.labelbuilder.data.jdbc.ConnectionManager;
import com.rohit.labelbuilder.data.jdbc.JdbcDataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link DataSource} over a visually built query (FR-DS-06).
 *
 * <p>Ties the three halves of the builder together: the {@link QuerySpec} is rendered for the
 * connection's own dialect, the literals the operator typed become bound values, and the parameters
 * they declared are converted to their declared types when the query runs.
 *
 * <p>The spec is kept, not just the SQL, so the query can be reopened in the builder — and re-rendered
 * if the connection later points at a different database. {@link #sql()} is what the editor shows and
 * exactly what will run.
 */
public final class BuiltQuerySource implements DataSource {

    private final QuerySpec spec;
    private final List<QueryParameter> parameters;
    private final RenderedQuery rendered;
    private final JdbcDataSource delegate;

    public BuiltQuerySource(String name, ConnectionManager connections, String configId, QuerySpec spec) {
        this(name, connections, configId, spec, List.of());
    }

    public BuiltQuerySource(
            String name,
            ConnectionManager connections,
            String configId,
            QuerySpec spec,
            List<QueryParameter> parameters) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        ConnectionConfig config = connections
                .config(configId)
                .orElseThrow(() -> new DataException("no connection is configured with id '" + configId
                        + "'; the query cannot be rendered without knowing its dialect"));
        this.rendered = SqlRenderer.render(spec, config.dialect());
        this.delegate = new JdbcDataSource(name, connections, configId, rendered.sql());
        requireDeclared();
    }

    /**
     * Refuses a query whose filters refer to a parameter nobody declared.
     *
     * <p>Caught here rather than at print time: an undeclared parameter means the prompt form will
     * never ask for it, so the run would fail at the printer with the operator standing next to it.
     */
    private void requireDeclared() {
        List<String> declared = parameters.stream().map(QueryParameter::name).toList();
        List<String> undeclared = rendered.declaredParameters().stream()
                .filter(name -> !declared.contains(name))
                .toList();
        if (!undeclared.isEmpty()) {
            throw new DataException("the query uses parameter(s) " + undeclared
                    + " that are not declared, so nothing would ever supply a value for them");
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }

    /** The structure the builder edits. */
    public QuerySpec spec() {
        return spec;
    }

    public List<QueryParameter> parameters() {
        return parameters;
    }

    /** The statement as rendered, with its {@code :name} placeholders — what the SQL editor shows. */
    public String sql() {
        return rendered.sql();
    }

    public RenderedQuery rendered() {
        return rendered;
    }

    @Override
    public List<FieldDefinition> fields() {
        return delegate.fields();
    }

    @Override
    public RecordSet open(Map<String, Object> suppliedValues) {
        Map<String, Object> declared = ParameterValues.resolve(parameters, suppliedValues);
        return delegate.open(rendered.bindings(declared));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
