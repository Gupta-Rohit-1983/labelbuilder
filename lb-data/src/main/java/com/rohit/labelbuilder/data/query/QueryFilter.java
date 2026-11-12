package com.rohit.labelbuilder.data.query;

import java.util.List;
import java.util.Objects;

/**
 * One condition in a built query's {@code WHERE} clause.
 *
 * <p>Validated on construction: the operand count has to match the operator, so a half-filled filter
 * row in the UI is refused where the operator can still be told about it, rather than becoming SQL
 * that fails at print time.
 */
public record QueryFilter(QueryColumn column, ComparisonOperator operator, List<FilterValue> values) {

    public QueryFilter {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(operator, "operator");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (!operator.accepts(values.size())) {
            throw new IllegalArgumentException(operator + " takes "
                    + (operator.isVariadic() ? "at least one value" : operator.operandCount() + " value(s)")
                    + ", got " + values.size());
        }
    }

    /** A comparison against a fixed value. */
    public static QueryFilter where(QueryColumn column, ComparisonOperator operator, Object value) {
        return new QueryFilter(column, operator, List.of(FilterValue.of(value)));
    }

    /** A comparison against a value supplied when the query runs. */
    public static QueryFilter whereParameter(QueryColumn column, ComparisonOperator operator, String parameter) {
        return new QueryFilter(column, operator, List.of(FilterValue.parameter(parameter)));
    }

    /** {@code IS NULL} / {@code IS NOT NULL}, which take no operands. */
    public static QueryFilter whereNull(QueryColumn column, boolean isNull) {
        return new QueryFilter(column, isNull ? ComparisonOperator.IS_NULL : ComparisonOperator.IS_NOT_NULL, List.of());
    }

    public static QueryFilter whereIn(QueryColumn column, Object... values) {
        return new QueryFilter(
                column,
                ComparisonOperator.IN,
                java.util.Arrays.stream(values).map(FilterValue::of).toList());
    }

    public static QueryFilter whereBetween(QueryColumn column, Object low, Object high) {
        return new QueryFilter(column, ComparisonOperator.BETWEEN, List.of(FilterValue.of(low), FilterValue.of(high)));
    }

    /** The names of the parameters this filter needs values for. */
    public List<String> parameterNames() {
        return values.stream()
                .filter(FilterValue.Parameter.class::isInstance)
                .map(value -> ((FilterValue.Parameter) value).name())
                .toList();
    }
}
