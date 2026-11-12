package com.rohit.labelbuilder.data;

/**
 * The logical type of a data-source field (lbl-format.md §5), independent of any particular source.
 *
 * <p>Deliberately small. A label merges values into text, barcodes and conditions; it does not need
 * the full type system of every database behind it. Anything exotic arrives as {@link #STRING}, which
 * is lossless for printing purposes.
 */
public enum FieldType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIME,
    TIMESTAMP,
    /** Images and other binary payloads, resolved by the element that references them. */
    BINARY
}
