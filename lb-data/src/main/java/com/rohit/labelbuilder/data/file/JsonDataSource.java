package com.rohit.labelbuilder.data.file;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.rohit.labelbuilder.data.AbstractRecordSet;
import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A JSON {@link DataSource} over an array of objects found at a {@link RecordPath}.
 *
 * <p>Read with Jackson's streaming parser, one token at a time: the document is never built as a
 * tree, so a 200 MB export costs one row of memory (risk R-06).
 *
 * <p>Nested objects are flattened with dotted names, so {@code {"price":{"net":1.50}}} binds as
 * {@code ${Product.price.net}}. Each value keeps the literal text the document held — {@code 1.50}
 * stays {@code "1.50"} rather than becoming the double 1.5 — and its type comes from the JSON token,
 * so a quoted {@code "0012"} is STRING while a bare {@code 12} is INTEGER. JSON draws that
 * distinction deliberately and a label is exactly where it matters.
 *
 * <p><b>Arrays nested inside a row are skipped.</b> A row with a list of components is two tables, not
 * one, and flattening it would either invent columns per index or silently keep only the first.
 * Repeating sub-records belong to the {@code table} element and its {@code rowSource} (lbl-format.md
 * §4.7), which reads them as a source of its own.
 */
public final class JsonDataSource implements DataSource {

    private static final JsonFactory FACTORY = new JsonFactory();

    private final String name;
    private final Path file;
    private final JsonOptions options;
    private volatile List<FieldDefinition> fields;

    public JsonDataSource(String name, Path file) {
        this(name, file, JsonOptions.DEFAULT);
    }

    public JsonDataSource(String name, Path file, JsonOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.file = Objects.requireNonNull(file, "file");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public String name() {
        return name;
    }

    public Path file() {
        return file;
    }

    public JsonOptions options() {
        return options;
    }

    @Override
    public List<FieldDefinition> fields() {
        List<FieldDefinition> cached = fields;
        if (cached == null) {
            cached = scanSchema();
            fields = cached;
        }
        return cached;
    }

    @Override
    public RecordSet open(Map<String, Object> parameters) {
        return new JsonCursor(fields(), file, options);
    }

    /**
     * Reads the first rows and unions their fields.
     *
     * <p>A union rather than the first row's keys: JSON omits a field instead of writing null, so
     * taking row one as the schema would hide every optional field. A field missing from any sampled
     * row is nullable.
     */
    private List<FieldDefinition> scanSchema() {
        Map<String, ColumnScan> columns = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        int rows = 0;
        try (JsonRowReader reader = new JsonRowReader(file, options.recordPath())) {
            Map<String, JsonValue> row;
            while (rows < options.sampleRows() && (row = reader.nextRow()) != null) {
                rows++;
                for (Map.Entry<String, JsonValue> entry : row.entrySet()) {
                    columns.computeIfAbsent(entry.getKey(), f -> new ColumnScan())
                            .add(entry.getValue().text(), entry.getValue().declaredType());
                    occurrences.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        // Counted afterwards rather than per row: a field first seen in row five was absent from rows
        // one to four, and a field absent from any sampled row is one a template must survive without.
        int sampled = rows;
        columns.forEach((field, scan) -> {
            if (occurrences.getOrDefault(field, 0) < sampled) {
                scan.addMissing();
            }
        });
        List<FieldDefinition> definitions = new ArrayList<>(columns.size());
        columns.forEach((field, scan) -> definitions.add(scan.toField(field)));
        return definitions;
    }

    /** One JSON scalar: the literal text, and the type its token declares. */
    private record JsonValue(String text, FieldType declaredType) {}

    /**
     * Pulls one row at a time out of the array at a record path.
     *
     * <p>Separate from the cursor because the schema scan needs the same walk, and a second
     * implementation of "descend the path, then read objects" would be a second set of bugs.
     */
    private static final class JsonRowReader implements AutoCloseable {

        private final Path file;
        private final JsonParser parser;
        private final boolean singleObject;
        private boolean done;

        JsonRowReader(Path file, RecordPath path) {
            this.file = file;
            // Opened into a local first: a path that is not there, or a document that is not rows,
            // throws from inside the constructor, and there would then be no reader for anyone to
            // close. On Windows that leaves the file locked against the next attempt to read it.
            JsonParser opened = null;
            try {
                opened = FACTORY.createParser(file.toFile());
                JsonToken token = descend(opened, path, file);
                if (token != JsonToken.START_ARRAY && token != JsonToken.START_OBJECT) {
                    throw new DataException(
                            "expected an array or object of records at " + path + " in " + file + ", found " + token);
                }
                // A feed that returns one record returns an object, not an array of one.
                this.singleObject = token == JsonToken.START_OBJECT;
                this.parser = opened;
            } catch (IOException e) {
                closeQuietly(opened);
                throw new DataException("cannot read " + file, e);
            } catch (RuntimeException e) {
                closeQuietly(opened);
                throw e;
            }
        }

        private static void closeQuietly(JsonParser parser) {
            if (parser == null) {
                return;
            }
            try {
                parser.close();
            } catch (IOException suppressed) {
                // the original failure is what the caller needs to see
            }
        }

        /** Walks the path, leaving the parser on the token the path names. */
        private static JsonToken descend(JsonParser parser, RecordPath path, Path file) throws IOException {
            JsonToken token = parser.nextToken();
            for (String segment : path.segments()) {
                if (token != JsonToken.START_OBJECT) {
                    throw new DataException("cannot follow '" + segment + "' in " + file + ": not an object");
                }
                boolean found = false;
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    boolean wanted = segment.equals(parser.currentName());
                    token = parser.nextToken();
                    if (wanted) {
                        found = true;
                        break;
                    }
                    parser.skipChildren();
                }
                if (!found) {
                    throw new DataException("no '" + segment + "' field in " + file + " at path " + path);
                }
            }
            return token;
        }

        /** The next row, or null at the end. */
        Map<String, JsonValue> nextRow() {
            if (done) {
                return null;
            }
            try {
                if (singleObject) {
                    done = true;
                    return readObject(parser, "");
                }
                JsonToken token = parser.nextToken();
                while (token == JsonToken.START_ARRAY) {
                    // An array of arrays has no field names to bind to; skip it rather than invent them.
                    parser.skipChildren();
                    token = parser.nextToken();
                }
                if (token == null || token == JsonToken.END_ARRAY) {
                    done = true;
                    return null;
                }
                if (token == JsonToken.START_OBJECT) {
                    return readObject(parser, "");
                }
                // An array of scalars is a one-column table; "value" is the only name available.
                Map<String, JsonValue> single = new LinkedHashMap<>();
                single.put("value", scalar(parser, token));
                return single;
            } catch (IOException e) {
                throw new DataException("cannot read " + file, e);
            }
        }

        private static Map<String, JsonValue> readObject(JsonParser parser, String prefix) throws IOException {
            Map<String, JsonValue> values = new LinkedHashMap<>();
            collect(parser, prefix, values);
            return values;
        }

        private static void collect(JsonParser parser, String prefix, Map<String, JsonValue> values)
                throws IOException {
            JsonToken fieldToken = parser.nextToken();
            while (fieldToken != null && fieldToken != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                String name = prefix.isEmpty() ? field : prefix + "." + field;
                JsonToken value = parser.nextToken();
                if (value == null) {
                    break; // truncated document: stop where it stops rather than throwing on null
                }
                switch (value) {
                    case START_OBJECT -> collect(parser, name, values);
                    case START_ARRAY -> parser.skipChildren();
                    default -> values.put(FieldNames.unique(values, name), scalar(parser, value));
                }
                fieldToken = parser.nextToken();
            }
        }

        private static JsonValue scalar(JsonParser parser, JsonToken token) throws IOException {
            return switch (token) {
                case VALUE_NULL -> new JsonValue(null, null);
                case VALUE_TRUE, VALUE_FALSE -> new JsonValue(parser.getText(), FieldType.BOOLEAN);
                case VALUE_NUMBER_INT -> new JsonValue(parser.getText(), FieldType.INTEGER);
                case VALUE_NUMBER_FLOAT -> new JsonValue(parser.getText(), FieldType.DECIMAL);
                case VALUE_STRING -> {
                    String text = parser.getText();
                    yield new JsonValue(text, TypeInference.temporalType(text).orElse(FieldType.STRING));
                }
                default -> new JsonValue(parser.getText(), FieldType.STRING);
            };
        }

        @Override
        public void close() {
            try {
                parser.close();
            } catch (IOException e) {
                throw new DataException("cannot close " + file, e);
            }
        }
    }

    /** Streams rows, padding each one out to the schema the source published. */
    private static final class JsonCursor extends AbstractRecordSet {

        private final List<String> names;
        private final JsonRowReader reader;

        JsonCursor(List<FieldDefinition> fields, Path file, JsonOptions options) {
            super(fields);
            this.names = fields.stream().map(FieldDefinition::name).toList();
            this.reader = new JsonRowReader(file, options.recordPath());
        }

        @Override
        protected DataRecord advance() {
            Map<String, JsonValue> row = reader.nextRow();
            if (row == null) {
                return null;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String field : names) {
                // Every schema field is present on every record, so a template bound to an optional
                // field renders blank rather than failing on the row that omits it.
                values.put(field, row.containsKey(field) ? row.get(field).text() : null);
            }
            row.forEach((field, value) -> {
                if (!values.containsKey(field)) {
                    // A field that appeared after the sample ended: carried, not dropped.
                    values.put(field, value.text());
                }
            });
            return new MapRecord(values);
        }

        @Override
        protected void release() {
            reader.close();
        }
    }
}
