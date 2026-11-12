package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.jdbc.JdbcDialect;
import com.rohit.labelbuilder.data.jdbc.TableRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Renders a {@link QuerySpec} as SQL for one dialect (FR-DS-06).
 *
 * <p><b>No value is ever written into the SQL text.</b> A literal the operator typed into the builder
 * becomes a generated {@code :p1} placeholder and its value goes into
 * {@link RenderedQuery#literalValues()}. There is no code path here that concatenates a value, so the
 * builder cannot produce an injectable statement whatever is typed into it — the same stance risk
 * R-04 takes on expressions, and the reason this renderer has no "quote a value" helper to misuse.
 *
 * <p>Identifiers <em>are</em> written into the text, because they have to be, and are quoted per
 * dialect by {@link JdbcDialect#quote}. They come from the schema browser rather than from free text,
 * and a name containing the dialect's own quote character is escaped rather than passed through.
 *
 * <p>Pure and headless: the builder UI in the desktop module renders a preview of the SQL by calling
 * this, so what the operator reads is exactly what will run.
 */
public final class SqlRenderer {

    /** Generated placeholders are {@code p1}, {@code p2}, … — short, and unlikely to collide. */
    private static final String GENERATED_PREFIX = "p";

    private SqlRenderer() {}

    public static RenderedQuery render(QuerySpec spec, JdbcDialect dialect) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(dialect, "dialect");

        Map<String, Object> literals = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (spec.distinct()) {
            sql.append("DISTINCT ");
        }
        if (dialect.limitStyle() == JdbcDialect.LimitStyle.TOP
                && spec.rowLimit().isPresent()) {
            sql.append("TOP (").append(spec.rowLimit().getAsInt()).append(") ");
        }
        sql.append(columns(spec, dialect));
        sql.append(" FROM ").append(table(spec.from(), dialect));
        for (JoinSpec join : spec.joins()) {
            sql.append(' ').append(join(join, dialect));
        }
        appendWhere(sql, spec, dialect, literals);
        appendOrderBy(sql, spec, dialect);
        appendLimit(sql, spec, dialect);
        return new RenderedQuery(sql.toString(), literals, spec.parameterNames());
    }

    private static String columns(QuerySpec spec, JdbcDialect dialect) {
        if (spec.selectsEverything()) {
            return "*";
        }
        StringJoiner selected = new StringJoiner(", ");
        for (QueryColumn column : spec.columns()) {
            String rendered = column(column, dialect);
            selected.add(column.alias()
                    .map(alias -> rendered + " AS " + dialect.quote(alias))
                    .orElse(rendered));
        }
        return selected.toString();
    }

    private static String column(QueryColumn column, JdbcDialect dialect) {
        return column.table().map(table -> dialect.quote(table) + ".").orElse("") + dialect.quote(column.name());
    }

    private static String table(TableRef table, JdbcDialect dialect) {
        // The catalog is left out deliberately: a three-part name ties the query to one database name,
        // and a template promoted from test to production would then point at the wrong one.
        return table.schema().map(schema -> dialect.quote(schema) + ".").orElse("") + dialect.quote(table.name());
    }

    private static String join(JoinSpec join, JdbcDialect dialect) {
        StringBuilder rendered =
                new StringBuilder(join.type().sql()).append(' ').append(table(join.table(), dialect));
        join.alias().ifPresent(alias -> rendered.append(" AS ").append(dialect.quote(alias)));
        return rendered.append(" ON ")
                .append(column(join.left(), dialect))
                .append(" = ")
                .append(column(join.right(), dialect))
                .toString();
    }

    private static void appendWhere(
            StringBuilder sql, QuerySpec spec, JdbcDialect dialect, Map<String, Object> literals) {
        if (spec.filters().isEmpty()) {
            return;
        }
        StringJoiner conditions = new StringJoiner(" " + spec.combinator() + " ");
        for (QueryFilter filter : spec.filters()) {
            conditions.add(condition(filter, dialect, literals));
        }
        sql.append(" WHERE ").append(conditions);
    }

    private static String condition(QueryFilter filter, JdbcDialect dialect, Map<String, Object> literals) {
        String column = column(filter.column(), dialect);
        ComparisonOperator operator = filter.operator();
        List<String> placeholders = new ArrayList<>(filter.values().size());
        for (FilterValue value : filter.values()) {
            placeholders.add(placeholder(value, literals));
        }
        return switch (operator) {
            case IS_NULL, IS_NOT_NULL -> column + " " + operator.sql();
            case IN, NOT_IN -> column + " " + operator.sql() + " (" + String.join(", ", placeholders) + ")";
            case BETWEEN -> column + " BETWEEN " + placeholders.get(0) + " AND " + placeholders.get(1);
            default -> column + " " + operator.sql() + " " + placeholders.get(0);
        };
    }

    /**
     * A placeholder for one operand — generated for a literal, the declared name for a parameter.
     *
     * <p>Generated names are numbered by how many literals have already been collected, so rendering
     * the same spec twice produces identical SQL. That matters for the preview: an SQL string that
     * changed on every repaint would make the editor look like it was fighting the operator, and would
     * defeat any caching keyed on the query text.
     */
    private static String placeholder(FilterValue value, Map<String, Object> literals) {
        return switch (value) {
            case FilterValue.Parameter parameter -> ":" + parameter.name();
            case FilterValue.Literal literal -> {
                String name = GENERATED_PREFIX + (literals.size() + 1);
                literals.put(name, literal.value());
                yield ":" + name;
            }
        };
    }

    private static void appendOrderBy(StringBuilder sql, QuerySpec spec, JdbcDialect dialect) {
        if (spec.sort().isEmpty()) {
            return;
        }
        StringJoiner terms = new StringJoiner(", ");
        for (SortColumn sort : spec.sort()) {
            terms.add(column(sort.column(), dialect) + (sort.descending() ? " DESC" : " ASC"));
        }
        sql.append(" ORDER BY ").append(terms);
    }

    private static void appendLimit(StringBuilder sql, QuerySpec spec, JdbcDialect dialect) {
        if (spec.rowLimit().isEmpty()) {
            return;
        }
        int limit = spec.rowLimit().getAsInt();
        switch (dialect.limitStyle()) {
            // The limit is a number the builder owns, never operator input, so it is written inline.
            // Several databases will not accept a placeholder in a LIMIT clause anyway.
            case LIMIT -> sql.append(" LIMIT ").append(limit);
            case FETCH_FIRST -> sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
            case TOP -> {
                // already emitted before the column list
            }
        }
    }
}
