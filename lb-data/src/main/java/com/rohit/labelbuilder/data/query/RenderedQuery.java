package com.rohit.labelbuilder.data.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link QuerySpec} turned into SQL, together with the values its generated placeholders need.
 *
 * <p>Two kinds of placeholder come out of rendering. A literal the operator typed into the builder
 * becomes a generated parameter — {@code :p1}, {@code :p2} — whose value is in {@link #literalValues},
 * and a declared parameter keeps its own name and is left for the caller to supply. Splitting them is
 * what lets a built query be previewed immediately (the literals are already known) while still
 * prompting for the rest.
 *
 * @param sql the statement, with {@code :name} placeholders for {@link com.rohit.labelbuilder.data.jdbc.NamedQuery} to position
 * @param literalValues values for the placeholders the renderer generated, in the order they appear
 *     in the SQL, so a UI can list them beside the statement
 * @param declaredParameters the names the caller still has to supply, in first-seen order
 */
public record RenderedQuery(String sql, Map<String, Object> literalValues, List<String> declaredParameters) {

    public RenderedQuery {
        Objects.requireNonNull(sql, "sql");
        // LinkedHashMap, not Map.copyOf: an immutable Map's iteration order is scrambled by a salt
        // randomised once per JVM, so copyOf would make the order of the bound values differ between
        // runs of the same build — unstable in a UI listing them, and in anything that compares them.
        literalValues = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(literalValues, "literalValues")));
        declaredParameters = List.copyOf(Objects.requireNonNull(declaredParameters, "declaredParameters"));
    }

    /**
     * The full parameter map to open the source with: the renderer's literals plus what the caller
     * supplied for the declared parameters.
     *
     * <p>Supplied values win on a name clash, which can only happen if a declared parameter is called
     * {@code p1}; the alternative — the renderer's value silently overriding the operator's — would be
     * the wrong way round.
     */
    public Map<String, Object> bindings(Map<String, Object> suppliedValues) {
        Map<String, Object> merged = new LinkedHashMap<>(literalValues);
        merged.putAll(suppliedValues);
        return merged;
    }

    /** Bindings for a query whose only values are the built-in literals. */
    public Map<String, Object> bindings() {
        return literalValues;
    }

    public boolean needsSuppliedValues() {
        return !declaredParameters.isEmpty();
    }
}
