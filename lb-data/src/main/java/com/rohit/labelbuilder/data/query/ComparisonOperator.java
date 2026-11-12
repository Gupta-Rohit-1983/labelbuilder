package com.rohit.labelbuilder.data.query;

/**
 * The comparisons a built filter can make.
 *
 * <p>{@link #operandCount()} is what keeps the builder honest: {@code BETWEEN} needs two operands and
 * {@code IS NULL} needs none, so a filter that cannot be rendered is rejected when it is constructed
 * rather than producing broken SQL later. {@code IN} is the exception — it takes any number, but at
 * least one, since {@code IN ()} is a syntax error in every dialect.
 */
public enum ComparisonOperator {
    EQUALS("=", 1),
    NOT_EQUALS("<>", 1),
    GREATER_THAN(">", 1),
    GREATER_OR_EQUAL(">=", 1),
    LESS_THAN("<", 1),
    LESS_OR_EQUAL("<=", 1),
    /** Wildcards are the operator's business: the value is bound, so {@code %} comes from the value. */
    LIKE("LIKE", 1),
    NOT_LIKE("NOT LIKE", 1),
    IN("IN", Arity.VARIADIC),
    NOT_IN("NOT IN", Arity.VARIADIC),
    BETWEEN("BETWEEN", 2),
    IS_NULL("IS NULL", 0),
    IS_NOT_NULL("IS NOT NULL", 0);

    /** Any number of operands, but at least one. */
    public static final int VARIADIC = Arity.VARIADIC;

    /** Nested because an enum constant's arguments may not reference a static field of the enum. */
    private static final class Arity {
        static final int VARIADIC = -1;
    }

    private final String sql;
    private final int operandCount;

    ComparisonOperator(String sql, int operandCount) {
        this.sql = sql;
        this.operandCount = operandCount;
    }

    public String sql() {
        return sql;
    }

    /** How many operands this operator takes, or {@link #VARIADIC}. */
    public int operandCount() {
        return operandCount;
    }

    public boolean isVariadic() {
        return operandCount == VARIADIC;
    }

    /** Whether {@code count} operands is a legal number for this operator. */
    public boolean accepts(int count) {
        return isVariadic() ? count >= 1 : count == operandCount;
    }
}
