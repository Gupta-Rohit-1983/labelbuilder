package com.rohit.labelbuilder.data;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;

/**
 * A forward-only cursor over rows (architecture §5.3, risk R-06).
 *
 * <p><b>Never a {@code List<DataRecord>}.</b> NFR-05 requires a 10 000-record batch inside a 1 GB
 * heap, and real merges run far larger; materialising a table before printing it is the single
 * decision that would make batch printing impossible to fix later. So the contract is a cursor from
 * the start, and every implementation — CSV, Excel, JDBC, REST — streams.
 *
 * <p>Usage is {@code while (records.next()) { ... records.current() ... }}, or a for-each loop, from
 * inside try-with-resources. A record set is <b>single-pass</b>: it cannot be rewound, and
 * {@link #iterator()} may be taken only once.
 *
 * <p>{@link #close()} is not optional. A JDBC cursor holds a connection and an Excel sheet holds a
 * file handle; both must be released even when a print job fails halfway.
 */
public interface RecordSet extends AutoCloseable, Iterable<DataRecord> {

    /** The schema of the rows this cursor yields. */
    List<FieldDefinition> fields();

    /**
     * Advance to the next row.
     *
     * @return false when the data is exhausted, after which {@link #current()} is invalid
     */
    boolean next();

    /**
     * The row the cursor is on.
     *
     * @throws IllegalStateException before the first {@link #next()}, or after it returned false
     */
    DataRecord current();

    /**
     * How many rows there are, when the source can say so without reading them.
     *
     * <p>Empty is the honest answer for a streaming source: a forward-only JDBC cursor or a REST feed
     * genuinely does not know, and counting would mean fetching everything — the very thing this
     * interface exists to avoid. Callers that need a total for a progress bar must either accept its
     * absence or count as they go.
     */
    OptionalLong size();

    /** Releases the underlying resource. Declared without a checked exception so callers stay clean. */
    @Override
    void close();

    /**
     * A single-pass iterator over the <em>remaining</em> rows, so a record set can be used in a
     * for-each loop.
     *
     * <p>Every iterator reads the same underlying cursor: taking a second one resumes where the first
     * stopped rather than starting again, and iterating a set that has already been consumed yields
     * nothing. That follows from the cursor being forward-only — it is not a defect to work around,
     * but it does mean a record set should be looped exactly once.
     */
    @Override
    default Iterator<DataRecord> iterator() {
        return new CursorIterator(this);
    }

    /** Bridges the cursor to {@link Iterator}'s look-ahead contract. */
    final class CursorIterator implements Iterator<DataRecord> {

        private final RecordSet records;
        private boolean advanced;
        private boolean hasMore;

        CursorIterator(RecordSet records) {
            this.records = records;
        }

        @Override
        public boolean hasNext() {
            if (!advanced) {
                hasMore = records.next();
                advanced = true;
            }
            return hasMore;
        }

        @Override
        public DataRecord next() {
            if (!hasNext()) {
                throw new NoSuchElementException("no more records");
            }
            advanced = false;
            return records.current();
        }
    }
}
