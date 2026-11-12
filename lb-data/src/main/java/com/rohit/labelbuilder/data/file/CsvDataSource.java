package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.AbstractRecordSet;
import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * A delimited-text {@link DataSource} (FR-DS-03).
 *
 * <p>Each {@link #open} streams the file from the start; nothing is buffered beyond the row in hand,
 * so a million-row export merges inside a fixed heap (NFR-05, risk R-06).
 *
 * <p>Values are handed over exactly as the file holds them — see {@link TypeInference} for why a label
 * must print the text the operator saw rather than a reformatted parse of it.
 */
public final class CsvDataSource implements DataSource {

    private final String name;
    private final Path file;
    private final CsvOptions options;

    /**
     * Memoised because the schema costs a read of the file, and the designer asks for it on every
     * repaint of the data panel. Volatile rather than synchronized: computing it twice is harmless,
     * publishing it half-built would not be.
     */
    private volatile List<FieldDefinition> fields;

    public CsvDataSource(String name, Path file) {
        this(name, file, CsvOptions.DEFAULT);
    }

    public CsvDataSource(String name, Path file, CsvOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.file = Objects.requireNonNull(file, "file");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** A source whose delimiter, quote and encoding are read off the file by {@link CsvSniffer}. */
    public static CsvDataSource sniffing(String name, Path file) {
        return new CsvDataSource(name, file, CsvSniffer.sniff(file));
    }

    @Override
    public String name() {
        return name;
    }

    public Path file() {
        return file;
    }

    public CsvOptions options() {
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
        return new CsvCursor(fields(), file, options);
    }

    /** Reads the header and the first {@code sampleRows} rows to work out the columns and their types. */
    private List<FieldDefinition> scanSchema() {
        List<String> headerCells = List.of();
        List<List<String>> sample = new ArrayList<>();
        try (Reader reader = TextFiles.reader(file, options.charset());
                CSVParser parser = format(options).parse(reader)) {
            Iterator<CSVRecord> rows = parser.iterator();
            if (options.headerRow() && rows.hasNext()) {
                headerCells = rows.next().toList();
            }
            while (rows.hasNext() && sample.size() < options.sampleRows()) {
                sample.add(rows.next().toList());
            }
        } catch (IOException e) {
            throw new DataException("cannot read " + file, e);
        } catch (UncheckedIOException e) {
            throw new DataException("cannot read " + file, e.getCause());
        }

        int width = headerCells.size();
        for (List<String> row : sample) {
            width = Math.max(width, row.size());
        }
        if (width == 0) {
            // An empty file is not an error: a nightly export can legitimately have no rows today, and
            // the template must still open.
            return List.of();
        }

        List<String> padded = new ArrayList<>(headerCells);
        while (padded.size() < width) {
            padded.add(null); // headerNames fills a blank or missing header cell positionally
        }
        List<String> names = options.headerRow() ? FieldNames.headerNames(padded) : FieldNames.positionalNames(width);

        List<ColumnScan> columns = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            columns.add(new ColumnScan());
        }
        for (List<String> row : sample) {
            for (int i = 0; i < width; i++) {
                if (i < row.size()) {
                    columns.get(i).add(row.get(i), null);
                } else {
                    columns.get(i).addMissing();
                }
            }
        }

        List<FieldDefinition> definitions = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            definitions.add(columns.get(i).toField(names.get(i)));
        }
        return definitions;
    }

    static CSVFormat format(CsvOptions options) {
        return CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(options.delimiter())
                .setQuote(options.quote())
                .setIgnoreEmptyLines(true)
                // Surrounding spaces are kept: a column padded to a fixed width by the exporting system
                // may be padded because the receiving label expects it.
                .setIgnoreSurroundingSpaces(false)
                .get();
    }

    /** Streams one CSV row at a time; the header, if any, is consumed on construction. */
    private static final class CsvCursor extends AbstractRecordSet {

        private final Path file;
        private final List<String> names;
        private final Reader reader;
        private final CSVParser parser;
        private final Iterator<CSVRecord> rows;

        CsvCursor(List<FieldDefinition> fields, Path file, CsvOptions options) {
            super(fields);
            this.file = file;
            this.names = fields.stream().map(FieldDefinition::name).toList();
            this.reader = TextFiles.reader(file, options.charset());
            try {
                this.parser = format(options).parse(reader);
                this.rows = parser.iterator();
                if (options.headerRow() && rows.hasNext()) {
                    rows.next(); // the header is schema, not data
                }
            } catch (IOException e) {
                closeQuietly(reader);
                throw new DataException("cannot read " + file, e);
            } catch (RuntimeException e) {
                closeQuietly(reader);
                throw e;
            }
        }

        @Override
        protected DataRecord advance() {
            try {
                return rows.hasNext() ? toRecord(rows.next()) : null;
            } catch (UncheckedIOException e) {
                throw new DataException("cannot read " + file, e.getCause());
            }
        }

        private DataRecord toRecord(CSVRecord row) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) {
                // A short row leaves the column present but empty, which is what the row means: the
                // exporter stopped writing cells, it did not delete the column.
                values.put(names.get(i), i < row.size() ? row.get(i) : null);
            }
            for (int i = names.size(); i < row.size(); i++) {
                // A row wider than the schema is a malformed export, but dropping the extra cells would
                // hide it. They are carried under positional names so the data is at least visible.
                values.put(FieldNames.unique(values, FieldNames.column(i)), row.get(i));
            }
            return new MapRecord(values);
        }

        @Override
        protected void release() {
            try {
                parser.close();
            } catch (IOException e) {
                throw new DataException("cannot close " + file, e);
            } finally {
                closeQuietly(reader);
            }
        }

        private static void closeQuietly(Reader reader) {
            try {
                reader.close();
            } catch (IOException suppressed) {
                // nothing useful to do while unwinding
            }
        }
    }
}
