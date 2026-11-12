package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Accumulates one column's sample values while a source reads its first rows, then turns them into a
 * {@link FieldDefinition}.
 *
 * <p>Two kinds of source feed this. CSV and XML hand over text and nothing else, so the type is
 * guessed by {@link TypeInference#inferType}. Excel and JSON know each value's type from the cell or
 * the JSON token, so they declare it per value and the column type is
 * {@link TypeInference#reconcile}d from those. Declarations win when present: a source that knows
 * should not be second-guessed by a heuristic.
 *
 * <p>Neither {@code length} nor {@code scale} is reported. {@link FieldDefinition} documents them as
 * what the source <em>declares</em>, and a file declares neither — the widest value in a 50-row sample
 * is an observation, and writing it into the template's cached schema would turn a sampling artefact
 * into something later phases treat as a constraint.
 */
final class ColumnScan {

    private final List<String> samples = new ArrayList<>();
    private final List<FieldType> declared = new ArrayList<>();
    private boolean nullable;

    /**
     * Records one value.
     *
     * @param text the value as the file holds it, or null when the row has no value for this column
     * @param declaredType the type the source knows this value to have, or null when it only has text
     */
    void add(String text, FieldType declaredType) {
        if (text == null || text.isBlank()) {
            nullable = true;
        }
        if (text != null) {
            samples.add(text);
        }
        if (declaredType != null) {
            declared.add(declaredType);
        }
    }

    /** Records that a row did not contain this column at all — ragged CSV, or an absent JSON field. */
    void addMissing() {
        nullable = true;
    }

    FieldDefinition toField(String name) {
        FieldType type = declared.isEmpty() ? TypeInference.inferType(samples) : TypeInference.reconcile(declared);
        // No values seen at all: nothing proves the column is ever populated, so assume it can be empty.
        boolean canBeNull = nullable || samples.isEmpty();
        return new FieldDefinition(name, type, OptionalInt.empty(), OptionalInt.empty(), canBeNull);
    }
}
