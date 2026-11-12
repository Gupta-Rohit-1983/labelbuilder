package com.rohit.labelbuilder.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable {@link DataRecord} backed by a copied map.
 *
 * <p>The copy is what makes a record a safe snapshot: a cursor may reuse its own buffers between
 * rows, so a record that read through to live state would change under a caller holding it.
 *
 * <p>Null values are kept rather than dropped, so {@link #has(String)} can distinguish "this row has
 * no such column" from "this column is null here" — a distinction that matters when a print
 * condition asks whether a field is set.
 */
public final class MapRecord implements DataRecord {

    private final Map<String, Object> values;
    private final List<String> fieldNames;

    public MapRecord(Map<String, Object> values) {
        // LinkedHashMap, not Map.copyOf: field order is part of the schema, and Map.of rejects nulls.
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.fieldNames = List.copyOf(this.values.keySet());
    }

    /** A record from alternating name/value pairs — convenient for tests and prompts. */
    public static MapRecord of(Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "expected name/value pairs, got " + nameValuePairs.length + " arguments");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            values.put(String.valueOf(nameValuePairs[i]), nameValuePairs[i + 1]);
        }
        return new MapRecord(values);
    }

    @Override
    public List<String> fieldNames() {
        return fieldNames;
    }

    @Override
    public boolean has(String field) {
        return values.containsKey(field);
    }

    @Override
    public Optional<Object> value(String field) {
        return Optional.ofNullable(values.get(field));
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        values.forEach((name, value) -> parts.add(name + "=" + value));
        return "MapRecord" + parts;
    }
}
