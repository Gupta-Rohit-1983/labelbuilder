package com.rohit.labelbuilder.data;

import java.util.List;
import java.util.OptionalLong;

/**
 * The bookkeeping every streaming {@link RecordSet} needs, so each format only has to say how to read
 * one more row.
 *
 * <p>A subclass implements {@link #advance()} — "the next row, or null when there are no more" — and
 * optionally {@link #release()}. The cursor state machine that the contract tests pin down (current
 * row invalid before the first {@code next()} and after exhaustion, exhausted stays exhausted, a
 * closed cursor refuses rather than returning stale rows, closing twice is harmless) lives here once
 * rather than being re-derived, and subtly differently, in CSV, Excel, JSON, XML and JDBC.
 *
 * <p>{@link #size()} defaults to empty because that is the honest answer for a file or socket: the row
 * count is not known until the last row has been read. A subclass that genuinely knows overrides it.
 */
public abstract class AbstractRecordSet implements RecordSet {

    private final List<FieldDefinition> fields;
    private DataRecord current;
    private boolean closed;
    private boolean exhausted;

    protected AbstractRecordSet(List<FieldDefinition> fields) {
        this.fields = List.copyOf(fields);
    }

    @Override
    public final List<FieldDefinition> fields() {
        return fields;
    }

    @Override
    public final boolean next() {
        requireOpen();
        if (exhausted) {
            return false;
        }
        DataRecord row = advance();
        if (row == null) {
            // Latched, so a source whose advance() would misbehave on a second call is never asked.
            exhausted = true;
            current = null;
            return false;
        }
        current = row;
        return true;
    }

    @Override
    public final DataRecord current() {
        requireOpen();
        if (current == null) {
            throw new IllegalStateException("no current record — call next() first, and check it returned true");
        }
        return current;
    }

    @Override
    public OptionalLong size() {
        return OptionalLong.empty();
    }

    @Override
    public final void close() {
        if (closed) {
            return;
        }
        // Set before releasing: if release() fails the cursor is still unusable, and a second close()
        // does not try again.
        closed = true;
        current = null;
        release();
    }

    /** Reads one more row, or returns null when the source is exhausted. */
    protected abstract DataRecord advance();

    /** Releases the file handle, connection or stream. Called at most once. */
    protected void release() {
        // most cursors hold nothing that needs releasing
    }

    protected final boolean isClosed() {
        return closed;
    }

    protected final void requireOpen() {
        if (closed) {
            throw new IllegalStateException("record set is closed");
        }
    }
}
