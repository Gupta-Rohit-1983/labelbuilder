package com.rohit.labelbuilder.data.file;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns whatever a file calls its columns into names a template can bind to.
 *
 * <p>Field names reach the template as {@code ${Product.Code}}, so they have to be present and
 * distinct whatever the file does. Real exports routinely break both rules: an ERP dump with a blank
 * trailing header, a report with two columns both called {@code Description}, an XML row with the
 * same child element repeated. Dropping or overwriting the duplicate would lose data silently, which
 * on a label means a wrong print nobody traces back to the import — so a collision is renamed, never
 * discarded.
 */
final class FieldNames {

    private FieldNames() {}

    /** The fallback name for column {@code index} (0-based), matching the spreadsheet habit of 1-based. */
    static String column(int index) {
        return "Column" + (index + 1);
    }

    /** Names a header row's cells, filling blanks positionally and renaming repeats. */
    static List<String> headerNames(List<String> raw) {
        Set<String> taken = new LinkedHashSet<>();
        List<String> names = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String cell = raw.get(i);
            String base = cell == null || cell.isBlank() ? column(i) : cell.trim();
            names.add(unique(taken, base));
        }
        return names;
    }

    /** Positional names for a file read without a header row. */
    static List<String> positionalNames(int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(column(i));
        }
        return names;
    }

    /** {@code name}, or {@code name_2}, {@code name_3}… if already taken. Adds the result to {@code taken}. */
    static String unique(Set<String> taken, String candidate) {
        String name = candidate;
        int suffix = 2;
        while (!taken.add(name)) {
            name = candidate + "_" + suffix++;
        }
        return name;
    }

    /** As {@link #unique(Set, String)} but against the keys of a record being built. */
    static String unique(Map<String, ?> taken, String candidate) {
        String name = candidate;
        int suffix = 2;
        while (taken.containsKey(name)) {
            name = candidate + "_" + suffix++;
        }
        return name;
    }
}
