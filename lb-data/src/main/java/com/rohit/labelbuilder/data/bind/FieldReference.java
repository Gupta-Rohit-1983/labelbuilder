package com.rohit.labelbuilder.data.bind;

import java.util.Objects;
import java.util.Optional;

/**
 * A reference to one field of one data source, as it appears in a property value:
 * {@code ${Product.Code}} (lbl-format.md §4.2).
 *
 * <p>The split is at the <b>first</b> dot, not the last, because a field name may itself contain dots.
 * A JSON or XML source flattens nested values with dotted names (Phase 11b), so
 * {@code ${Product.price.net}} is the field {@code price.net} of the source {@code Product} — not a
 * source called {@code Product.price}.
 *
 * @param sourceName the data source's name, as the document declares it
 * @param fieldName the field within that source, possibly dotted
 */
public record FieldReference(String sourceName, String fieldName) {

    public FieldReference {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(fieldName, "fieldName");
        if (sourceName.isBlank() || fieldName.isBlank()) {
            throw new IllegalArgumentException("a field reference needs both a source and a field name");
        }
    }

    /**
     * Parses {@code Source.Field}, or returns empty when the text is not a plain field reference.
     *
     * <p>Empty is the answer for anything the Phase 12 expression engine owns — a function call, an
     * operator, or a bare name with no dot such as {@code ${Serial}}, which is a variable rather than
     * a field. Those are left verbatim rather than guessed at, since inventing semantics for them now
     * would have to be unpicked when the engine arrives.
     */
    public static Optional<FieldReference> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            return Optional.empty();
        }
        String source = trimmed.substring(0, dot);
        String field = trimmed.substring(dot + 1);
        if (!isPlainName(source) || !isFieldName(field)) {
            return Optional.empty();
        }
        return Optional.of(new FieldReference(source, field));
    }

    /** The placeholder as it appears in a property value. */
    public String placeholder() {
        return "${" + qualifiedName() + "}";
    }

    public String qualifiedName() {
        return sourceName + "." + fieldName;
    }

    @Override
    public String toString() {
        return qualifiedName();
    }

    private static boolean isPlainName(String name) {
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** A field name is one or more plain names joined by dots, matching 11b's flattening. */
    private static boolean isFieldName(String name) {
        if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            return false;
        }
        for (String part : name.split("\\.")) {
            if (!isPlainName(part)) {
                return false;
            }
        }
        return true;
    }
}
