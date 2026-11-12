package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.jdbc.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A query described structurally rather than as text — what the visual query builder edits
 * (FR-DS-06).
 *
 * <p>Keeping the structure, not just the generated SQL, is what makes the builder reversible: a query
 * stored this way can be reopened in the builder next month, have a column added, and be re-rendered
 * for whatever dialect the connection now points at. A template that stored only a SQL string would
 * be editable in the SQL editor alone, and would be wrong if the database moved from SQL Server to
 * PostgreSQL.
 *
 * <p>Immutable, with withers, for the same reason the document model is: the builder's undo stack is
 * then a list of these and nothing has to be un-done by hand.
 *
 * @param columns the columns to select, empty for all of them
 * @param combinator how the filters combine; a mixture needs the SQL editor
 * @param rowLimit a cap on rows, which the preview uses and a test print wants
 */
public record QuerySpec(
        TableRef from,
        List<QueryColumn> columns,
        List<JoinSpec> joins,
        List<QueryFilter> filters,
        FilterCombinator combinator,
        List<SortColumn> sort,
        OptionalInt rowLimit,
        boolean distinct) {

    public QuerySpec {
        Objects.requireNonNull(from, "from");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        joins = List.copyOf(Objects.requireNonNull(joins, "joins"));
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        Objects.requireNonNull(combinator, "combinator");
        sort = List.copyOf(Objects.requireNonNull(sort, "sort"));
        Objects.requireNonNull(rowLimit, "rowLimit");
        if (rowLimit.isPresent() && rowLimit.getAsInt() < 1) {
            throw new IllegalArgumentException("a row limit of " + rowLimit.getAsInt() + " would select nothing");
        }
    }

    /** Every column of one table: what the builder starts from once a table has been picked. */
    public static QuerySpec from(TableRef table) {
        return new QuerySpec(
                table, List.of(), List.of(), List.of(), FilterCombinator.AND, List.of(), OptionalInt.empty(), false);
    }

    public QuerySpec select(QueryColumn... columns) {
        return withColumns(List.of(columns));
    }

    public QuerySpec withColumns(List<QueryColumn> columns) {
        return new QuerySpec(from, columns, joins, filters, combinator, sort, rowLimit, distinct);
    }

    public QuerySpec plusColumn(QueryColumn column) {
        List<QueryColumn> extended = new ArrayList<>(columns);
        extended.add(column);
        return withColumns(extended);
    }

    public QuerySpec join(JoinSpec join) {
        List<JoinSpec> extended = new ArrayList<>(joins);
        extended.add(join);
        return new QuerySpec(from, columns, extended, filters, combinator, sort, rowLimit, distinct);
    }

    public QuerySpec where(QueryFilter filter) {
        List<QueryFilter> extended = new ArrayList<>(filters);
        extended.add(filter);
        return new QuerySpec(from, columns, joins, extended, combinator, sort, rowLimit, distinct);
    }

    public QuerySpec withCombinator(FilterCombinator combinator) {
        return new QuerySpec(from, columns, joins, filters, combinator, sort, rowLimit, distinct);
    }

    public QuerySpec orderBy(SortColumn... sort) {
        return new QuerySpec(from, columns, joins, filters, combinator, List.of(sort), rowLimit, distinct);
    }

    public QuerySpec withRowLimit(int rowLimit) {
        return new QuerySpec(from, columns, joins, filters, combinator, sort, OptionalInt.of(rowLimit), distinct);
    }

    public QuerySpec withoutRowLimit() {
        return new QuerySpec(from, columns, joins, filters, combinator, sort, OptionalInt.empty(), distinct);
    }

    public QuerySpec withDistinct(boolean distinct) {
        return new QuerySpec(from, columns, joins, filters, combinator, sort, rowLimit, distinct);
    }

    /** Whether the query selects every column, because none were named. */
    public boolean selectsEverything() {
        return columns.isEmpty();
    }

    /** Every parameter name the filters refer to, in first-seen order and without repeats. */
    public List<String> parameterNames() {
        return filters.stream()
                .flatMap(filter -> filter.parameterNames().stream())
                .distinct()
                .toList();
    }

    /** How a query's filters are combined. */
    public enum FilterCombinator {
        AND,
        OR
    }
}
