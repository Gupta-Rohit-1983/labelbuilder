package com.rohit.labelbuilder.data;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * A {@link DataSource} over rows already in memory — the {@code manual} source of lbl-format.md §5
 * (prompt-at-print values), and the reference implementation the cursor contract is tested against.
 *
 * <p>Holding a list here is legitimate precisely because the caller supplied one: the rule R-06
 * guards is that the <em>interface</em> must not force a source to materialise a table it is
 * streaming. Nothing stops a source whose data is genuinely small from being backed by a list.
 */
public final class InMemoryDataSource implements DataSource {

    private final String name;
    private final List<FieldDefinition> fields;
    private final List<DataRecord> rows;

    public InMemoryDataSource(String name, List<FieldDefinition> fields, List<DataRecord> rows) {
        this.name = Objects.requireNonNull(name, "name");
        this.fields = List.copyOf(fields);
        this.rows = List.copyOf(rows);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<FieldDefinition> fields() {
        return fields;
    }

    @Override
    public RecordSet open(Map<String, Object> parameters) {
        return new ListCursor(fields, rows);
    }

    /** A forward-only cursor over a list; each {@link #open} call gets a fresh one. */
    private static final class ListCursor implements RecordSet {

        private final List<FieldDefinition> fields;
        private final List<DataRecord> rows;
        private final Iterator<DataRecord> iterator;
        private final long total;
        private DataRecord current;
        private boolean closed;

        ListCursor(List<FieldDefinition> fields, List<DataRecord> rows) {
            this.fields = fields;
            this.rows = rows;
            this.iterator = rows.iterator();
            this.total = rows.size();
        }

        @Override
        public List<FieldDefinition> fields() {
            return fields;
        }

        @Override
        public boolean next() {
            requireOpen();
            if (iterator.hasNext()) {
                current = iterator.next();
                return true;
            }
            current = null;
            return false;
        }

        @Override
        public DataRecord current() {
            requireOpen();
            if (current == null) {
                throw new IllegalStateException("no current record — call next() first, and check it returned true");
            }
            return current;
        }

        @Override
        public OptionalLong size() {
            // This source genuinely knows; a streaming one would return empty here.
            return OptionalLong.of(total);
        }

        @Override
        public void close() {
            closed = true;
            current = null;
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("record set is closed");
            }
        }

        @Override
        public String toString() {
            return "ListCursor(" + rows.size() + " rows)";
        }
    }
}
