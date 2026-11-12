package com.rohit.labelbuilder.data.query;

import java.util.Objects;

/**
 * One operand of a filter: either a value the operator typed, or a named parameter supplied at print
 * time.
 *
 * <p>The distinction is <em>when</em> the value is known, not whether it is safe. Both end up as a
 * bound {@code ?} — a {@link Literal} gets a generated parameter name — so the builder has no way to
 * put a value into the SQL text even if it wanted to. That is deliberate: a query builder that
 * concatenated literals would be an injection hole reachable from a scanner on a warehouse floor, and
 * making it impossible is cheaper than reviewing every path that reaches it.
 */
public sealed interface FilterValue {

    /** A value fixed when the query was built, such as "only active products". */
    record Literal(Object value) implements FilterValue {}

    /**
     * A value supplied when the query runs, declared by {@link QueryParameter} and bound by name.
     *
     * @param name the parameter name, without the leading colon
     */
    record Parameter(String name) implements FilterValue {

        public Parameter {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("a parameter needs a name");
            }
        }
    }

    static FilterValue of(Object value) {
        return new Literal(value);
    }

    static FilterValue parameter(String name) {
        return new Parameter(name);
    }
}
