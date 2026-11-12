package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.FieldType;
import java.util.Objects;
import java.util.Optional;

/**
 * A declared query parameter: its name, what type of value it takes, and where that value comes from
 * (lbl-format.md §5).
 *
 * <p>The declared {@link #type()} is what makes a parameter usable from a form. A prompt field hands
 * over text, and binding the text {@code "5"} to {@code WHERE qty >= ?} either fails or — worse —
 * compares as a string, so {@code "10" < "5"} and the run silently selects the wrong products.
 * {@link ParameterValues} uses the type to convert; without it, the conversion would have to be
 * guessed at every call site.
 *
 * @param boundTo the expression supplying the value, e.g. {@code ${Prompt.SKU}}, kept opaque until the
 *     expression engine arrives in Phase 12; empty means the value is supplied directly
 * @param defaultValue used when nothing is supplied; its absence is what makes a parameter required
 */
public record QueryParameter(String name, FieldType type, Optional<Object> defaultValue, Optional<String> boundTo) {

    public QueryParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(boundTo, "boundTo");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a parameter needs a name");
        }
    }

    /** A required parameter: no default, so a value has to be supplied. */
    public static QueryParameter required(String name, FieldType type) {
        return new QueryParameter(name, type, Optional.empty(), Optional.empty());
    }

    /** An optional parameter, falling back to {@code defaultValue}. */
    public static QueryParameter withDefault(String name, FieldType type, Object defaultValue) {
        return new QueryParameter(name, type, Optional.of(defaultValue), Optional.empty());
    }

    public QueryParameter boundTo(String expression) {
        return new QueryParameter(name, type, defaultValue, Optional.of(expression));
    }

    /** A parameter with no default must be given a value before the query can run. */
    public boolean isRequired() {
        return defaultValue.isEmpty();
    }
}
