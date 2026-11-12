package com.rohit.labelbuilder.data.query;

import java.util.Objects;
import java.util.Optional;

/**
 * One column in a built query, optionally qualified by a table and renamed by an alias.
 *
 * <p>The alias matters more than it looks: it becomes the field name a template binds to, so
 * {@code ${Product.Price}} works whether the underlying column is {@code PRICE}, {@code unit_price} or
 * {@code p.PRC}. Renaming in the builder is how a label survives a column being renamed in the
 * database.
 *
 * @param table the table or alias to qualify with, empty for an unqualified column
 * @param name the column's name in the database
 * @param alias the name to expose it as, empty to use {@code name}
 */
public record QueryColumn(Optional<String> table, String name, Optional<String> alias) {

    public QueryColumn {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(alias, "alias");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a column needs a name");
        }
    }

    public static QueryColumn of(String name) {
        return new QueryColumn(Optional.empty(), name, Optional.empty());
    }

    public static QueryColumn of(String table, String name) {
        return new QueryColumn(Optional.of(table), name, Optional.empty());
    }

    public QueryColumn as(String alias) {
        return new QueryColumn(table, name, Optional.of(alias));
    }

    public QueryColumn in(String table) {
        return new QueryColumn(Optional.of(table), name, alias);
    }

    /** The name a bound field will have. */
    public String fieldName() {
        return alias.orElse(name);
    }
}
