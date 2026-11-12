package com.rohit.labelbuilder.data;

import java.util.List;
import java.util.Optional;

/**
 * One row of data: field names to values.
 *
 * <p>Named {@code DataRecord} rather than {@code Record} (as architecture §5.3 originally sketched)
 * because a type called {@code Record} shadows {@link java.lang.Record} for every class in the same
 * package — a trap worth avoiding in the one module most likely to declare records of its own.
 *
 * <p>A record is a <b>snapshot</b>, valid after the cursor has moved on. Implementations must copy
 * whatever they need rather than reading through to a live cursor, so a caller can keep a row while
 * iterating.
 */
public interface DataRecord {

    /** The field names present, in source order. */
    List<String> fieldNames();

    /** True if this record carries that field at all — distinct from carrying a null value. */
    boolean has(String field);

    /**
     * The raw value, or empty when the field is absent <em>or</em> its value is null. Use
     * {@link #has(String)} to tell those apart.
     */
    Optional<Object> value(String field);

    /**
     * The value rendered as text for merging into a label. A missing or null field yields an empty
     * string: a blank on a label is almost always better than "null" printed on a carton.
     */
    default String text(String field) {
        return value(field).map(String::valueOf).orElse("");
    }
}
