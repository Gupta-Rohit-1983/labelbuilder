package com.rohit.labelbuilder.data.file;

import java.util.Objects;

/**
 * How to read rows out of a JSON document.
 *
 * @param recordPath where the array of rows sits, {@link RecordPath#ROOT} for a top-level array
 * @param sampleRows how many rows to read when working out the field list
 */
public record JsonOptions(RecordPath recordPath, int sampleRows) {

    public static final JsonOptions DEFAULT = new JsonOptions(RecordPath.ROOT, TypeInference.DEFAULT_SAMPLE_ROWS);

    public JsonOptions {
        Objects.requireNonNull(recordPath, "recordPath");
        if (sampleRows < 1) {
            throw new IllegalArgumentException("sampleRows must be at least 1, was " + sampleRows);
        }
    }

    public JsonOptions withRecordPath(RecordPath recordPath) {
        return new JsonOptions(recordPath, sampleRows);
    }

    public JsonOptions withRecordPath(String dottedPath) {
        return withRecordPath(RecordPath.parse(dottedPath));
    }

    public JsonOptions withSampleRows(int sampleRows) {
        return new JsonOptions(recordPath, sampleRows);
    }
}
