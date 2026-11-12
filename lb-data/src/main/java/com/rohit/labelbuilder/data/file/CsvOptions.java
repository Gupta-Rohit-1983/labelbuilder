package com.rohit.labelbuilder.data.file;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * How to read a delimited text file (FR-DS-03).
 *
 * <p>Every setting is explicit so a template can store it and get the same columns next month.
 * {@link CsvSniffer} produces a filled-in instance from a sample of the file, which is a starting
 * point for the import wizard — not a substitute for recording the answer.
 *
 * @param headerRow whether the first row names the columns rather than carrying data
 * @param sampleRows how many data rows to read when guessing column types
 */
public record CsvOptions(char delimiter, char quote, Charset charset, boolean headerRow, int sampleRows) {

    public static final CsvOptions DEFAULT =
            new CsvOptions(',', '"', StandardCharsets.UTF_8, true, TypeInference.DEFAULT_SAMPLE_ROWS);

    public CsvOptions {
        Objects.requireNonNull(charset, "charset");
        if (delimiter == quote) {
            throw new IllegalArgumentException("delimiter and quote must differ, both were '" + delimiter + "'");
        }
        if (sampleRows < 1) {
            throw new IllegalArgumentException("sampleRows must be at least 1, was " + sampleRows);
        }
    }

    public CsvOptions withDelimiter(char delimiter) {
        return new CsvOptions(delimiter, quote, charset, headerRow, sampleRows);
    }

    public CsvOptions withQuote(char quote) {
        return new CsvOptions(delimiter, quote, charset, headerRow, sampleRows);
    }

    public CsvOptions withCharset(Charset charset) {
        return new CsvOptions(delimiter, quote, charset, headerRow, sampleRows);
    }

    public CsvOptions withHeaderRow(boolean headerRow) {
        return new CsvOptions(delimiter, quote, charset, headerRow, sampleRows);
    }

    public CsvOptions withSampleRows(int sampleRows) {
        return new CsvOptions(delimiter, quote, charset, headerRow, sampleRows);
    }
}
