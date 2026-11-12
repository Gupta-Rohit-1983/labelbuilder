package com.rohit.labelbuilder.data.jdbc;

import java.util.Objects;
import java.util.Optional;

/**
 * One table or view, as the schema browser lists it (FR-DS-06).
 *
 * @param type {@code TABLE}, {@code VIEW}, or whatever else the driver reports
 */
public record TableRef(Optional<String> catalog, Optional<String> schema, String name, String type) {

    public TableRef {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    public static TableRef of(String catalog, String schema, String name, String type) {
        return new TableRef(
                Optional.ofNullable(blankToNull(catalog)), Optional.ofNullable(blankToNull(schema)), name, type);
    }

    /**
     * The name to put in a {@code FROM} clause, qualified by whichever parts the driver gave.
     *
     * <p>Unquoted: quoting correctly is per-dialect, and the query builder in Phase 11d is where that
     * belongs. This is for display and for composing a starting query the operator can edit.
     */
    public String qualifiedName() {
        StringBuilder qualified = new StringBuilder();
        schema.ifPresent(s -> qualified.append(s).append('.'));
        return qualified.append(name).toString();
    }

    public boolean isView() {
        return type.toUpperCase(java.util.Locale.ROOT).contains("VIEW");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public String toString() {
        return qualifiedName() + " (" + type + ")";
    }
}
