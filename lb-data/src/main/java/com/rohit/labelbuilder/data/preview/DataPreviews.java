package com.rohit.labelbuilder.data.preview;

import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.RecordSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a bounded sample of any {@link DataSource} for the preview pane.
 *
 * <p>One implementation serves CSV, Excel, JSON, XML and JDBC, because they are all the same interface
 * — the dividend from making {@code DataSource} a factory for cursors in 11a rather than five separate
 * reader APIs.
 *
 * <p>Three properties the designer depends on:
 *
 * <ul>
 *   <li><b>Bounded.</b> One row past the limit is read to detect truncation, and then the cursor is
 *       closed. Previewing {@code SELECT * FROM order_lines} on a production database must not read
 *       forty million rows, and must not hold the connection while the operator reads the result.
 *   <li><b>Never throws.</b> A broken query, a missing file and an unreachable server are all answers
 *       the pane has to display. The designer is where queries get fixed, so a stack trace instead of
 *       a message would be the worst possible response.
 *   <li><b>Schema even when there are no rows.</b> An empty result still carries its columns, so a
 *       label can be laid out against a query that matches nothing today.
 * </ul>
 */
public final class DataPreviews {

    /** Enough to see the shape of the data; small enough that the pane fills instantly. */
    public static final int DEFAULT_MAX_ROWS = 100;

    private DataPreviews() {}

    public static DataPreview of(DataSource source) {
        return of(source, Map.of(), DEFAULT_MAX_ROWS);
    }

    public static DataPreview of(DataSource source, Map<String, Object> parameters) {
        return of(source, parameters, DEFAULT_MAX_ROWS);
    }

    public static DataPreview of(DataSource source, Map<String, Object> parameters, int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("a preview of " + maxRows + " rows would show nothing");
        }
        long startedAt = System.nanoTime();
        List<FieldDefinition> fields = List.of();
        try {
            // Read first, so a source whose schema needs the file or the server still reports its
            // columns alongside any rows — and so a failure here is reported the same way as any other.
            fields = source.fields();
            List<DataRecord> rows = new ArrayList<>();
            boolean truncated;
            try (RecordSet records = source.open(parameters)) {
                while (rows.size() < maxRows && records.next()) {
                    rows.add(records.current());
                }
                // One more, only to find out whether to say "first 100 rows".
                truncated = records.next();
            }
            return new DataPreview(fields, rows, truncated, Optional.empty(), elapsedSince(startedAt));
        } catch (RuntimeException e) {
            return new DataPreview(fields, List.of(), false, Optional.of(message(e)), elapsedSince(startedAt));
        }
    }

    /**
     * The message to show, falling back to the exception's type when it has none.
     *
     * <p>A blank pane captioned with nothing would look like a hang; the class name at least tells
     * somebody what to search for.
     */
    private static String message(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static Duration elapsedSince(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
