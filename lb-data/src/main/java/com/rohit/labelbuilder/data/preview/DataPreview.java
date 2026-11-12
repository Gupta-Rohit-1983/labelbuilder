package com.rohit.labelbuilder.data.preview;

import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.FieldDefinition;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The first few rows of a data source, for the preview pane beside the query editor (FR-DS-06).
 *
 * <p><b>This is the one place rows are deliberately materialised</b>, and the exception is bounded on
 * purpose: at most {@link DataPreviews#DEFAULT_MAX_ROWS} of them, read and then closed. Risk R-06
 * forbids a table being held to print it, not a hundred rows being held to show them — and a preview
 * that streamed would have nothing to display. {@link #truncated()} says there was more, so the pane
 * can show "first 100 rows" rather than implying that is all the data.
 *
 * @param error the failure message instead of rows; a preview reports a broken query rather than
 *     throwing, because the pane has to show the message
 * @param elapsed how long the read took, so the UI can warn before the same query is used for a run
 */
public record DataPreview(
        List<FieldDefinition> fields,
        List<DataRecord> rows,
        boolean truncated,
        Optional<String> error,
        Duration elapsed) {

    public DataPreview {
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(elapsed, "elapsed");
    }

    public boolean isSuccessful() {
        return error.isEmpty();
    }

    /** A query that ran but matched nothing — worth distinguishing from one that failed. */
    public boolean isEmpty() {
        return isSuccessful() && rows.isEmpty();
    }

    /** One row's value for a field, as text, for a table cell. */
    public String text(int row, String field) {
        return rows.get(row).text(field);
    }

    /** What the pane's caption should say. */
    public String summary() {
        if (error.isPresent()) {
            return error.get();
        }
        if (rows.isEmpty()) {
            return "no rows matched";
        }
        return (truncated ? "first " + rows.size() + " rows" : rows.size() + (rows.size() == 1 ? " row" : " rows"))
                + " in " + elapsed.toMillis() + " ms";
    }
}
