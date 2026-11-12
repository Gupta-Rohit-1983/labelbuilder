package com.rohit.labelbuilder.data.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where the rows live inside a JSON document (FR-DS-07).
 *
 * <p>Almost no real feed puts its rows at the top level. An API answers
 * {@code {"status":"ok","data":{"items":[…]}}}, so the source needs to be told that the rows are at
 * {@code data.items}. The path is a sequence of object field names — deliberately not a general
 * JSONPath, since the streaming reader walks it in one pass and filters or wildcards would require
 * holding the document.
 */
public record RecordPath(List<String> segments) {

    /** The document itself: for a file that is already an array of rows. */
    public static final RecordPath ROOT = new RecordPath(List.of());

    public RecordPath {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("path segments must not be blank: " + segments);
        }
    }

    /** Parses a dotted path. A leading {@code $} is accepted, so a JSONPath-shaped answer works. */
    public static RecordPath parse(String path) {
        if (path == null) {
            return ROOT;
        }
        String text = path.trim();
        if (text.startsWith("$")) {
            text = text.substring(1);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : text.split("\\.")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }
        return segments.isEmpty() ? ROOT : new RecordPath(segments);
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    @Override
    public String toString() {
        return isRoot() ? "$" : String.join(".", segments);
    }
}
