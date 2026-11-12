package com.rohit.labelbuilder.data.file;

import java.util.Objects;
import java.util.Optional;

/**
 * Which part of a workbook to read (FR-DS-04).
 *
 * <p>The sheet is identified by name where one is given, falling back to {@code sheetIndex}. Name is
 * the better anchor for a stored template — inserting a sheet in front shifts every index — but an
 * index is what an import wizard has before the workbook has been opened.
 *
 * @param range the region to read, open-ended by default so the next export may be longer
 * @param headerRow whether the first row of the range names the columns
 * @param sampleRows how many data rows to read when guessing column types
 */
public record ExcelOptions(
        Optional<String> sheetName, int sheetIndex, CellRange range, boolean headerRow, int sampleRows) {

    public static final ExcelOptions DEFAULT =
            new ExcelOptions(Optional.empty(), 0, CellRange.ALL, true, TypeInference.DEFAULT_SAMPLE_ROWS);

    public ExcelOptions {
        Objects.requireNonNull(sheetName, "sheetName");
        Objects.requireNonNull(range, "range");
        if (sheetIndex < 0) {
            throw new IllegalArgumentException("sheetIndex must not be negative, was " + sheetIndex);
        }
        if (sampleRows < 1) {
            throw new IllegalArgumentException("sampleRows must be at least 1, was " + sampleRows);
        }
    }

    public ExcelOptions withSheetName(String sheetName) {
        return new ExcelOptions(Optional.of(sheetName), sheetIndex, range, headerRow, sampleRows);
    }

    public ExcelOptions withSheetIndex(int sheetIndex) {
        return new ExcelOptions(sheetName, sheetIndex, range, headerRow, sampleRows);
    }

    public ExcelOptions withRange(CellRange range) {
        return new ExcelOptions(sheetName, sheetIndex, range, headerRow, sampleRows);
    }

    public ExcelOptions withRange(String a1) {
        return withRange(CellRange.parse(a1));
    }

    public ExcelOptions withHeaderRow(boolean headerRow) {
        return new ExcelOptions(sheetName, sheetIndex, range, headerRow, sampleRows);
    }

    public ExcelOptions withSampleRows(int sampleRows) {
        return new ExcelOptions(sheetName, sheetIndex, range, headerRow, sampleRows);
    }
}
