package com.rohit.labelbuilder.data;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One column of a data source's schema (lbl-format.md §5).
 *
 * <p>The schema is cached in the template so the designer can bind fields and lay a label out while
 * the source is unreachable — a template opened on a machine with no database must still be
 * editable, it just cannot merge.
 *
 * @param length maximum character length where the source declares one
 * @param scale decimal places where the source declares them
 */
public record FieldDefinition(String name, FieldType type, OptionalInt length, OptionalInt scale, boolean nullable) {

    public FieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(scale, "scale");
        if (name.isBlank()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
    }

    /** A nullable field with no declared length or scale. */
    public static FieldDefinition of(String name, FieldType type) {
        return new FieldDefinition(name, type, OptionalInt.empty(), OptionalInt.empty(), true);
    }

    /** A text field with a declared maximum length. */
    public static FieldDefinition text(String name, int length) {
        return new FieldDefinition(name, FieldType.STRING, OptionalInt.of(length), OptionalInt.empty(), true);
    }

    /** A decimal field with declared scale. */
    public static FieldDefinition decimal(String name, int scale) {
        return new FieldDefinition(name, FieldType.DECIMAL, OptionalInt.empty(), OptionalInt.of(scale), true);
    }
}
