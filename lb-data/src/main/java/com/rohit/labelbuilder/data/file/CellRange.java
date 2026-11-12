package com.rohit.labelbuilder.data.file;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A rectangular region of a worksheet in A1 notation (FR-DS-04).
 *
 * <p>Rows and columns are 0-based here, as in the spreadsheet libraries that consume them, while
 * {@link #parse} and {@link #toA1()} speak the 1-based notation the operator types. Keeping the
 * conversion in one place is the point: an off-by-one between the two conventions shifts every label
 * in a run by a row, which looks like a data problem rather than a parsing one.
 *
 * <p>The end of the range is optional, because the useful case is open-ended. "My data starts at B3"
 * means every row from there to wherever it stops — a range pinned to today's row count would silently
 * truncate tomorrow's longer export.
 */
public record CellRange(int firstRow, int firstColumn, OptionalInt lastRow, OptionalInt lastColumn) {

    /** The whole used area of the sheet. */
    public static final CellRange ALL = new CellRange(0, 0, OptionalInt.empty(), OptionalInt.empty());

    public CellRange {
        Objects.requireNonNull(lastRow, "lastRow");
        Objects.requireNonNull(lastColumn, "lastColumn");
        if (firstRow < 0 || firstColumn < 0) {
            throw new IllegalArgumentException("range starts before A1: row " + firstRow + ", column " + firstColumn);
        }
        if (lastRow.isPresent() && lastRow.getAsInt() < firstRow) {
            throw new IllegalArgumentException("lastRow " + lastRow.getAsInt() + " is before firstRow " + firstRow);
        }
        if (lastColumn.isPresent() && lastColumn.getAsInt() < firstColumn) {
            throw new IllegalArgumentException(
                    "lastColumn " + lastColumn.getAsInt() + " is before firstColumn " + firstColumn);
        }
    }

    /** An open-ended range starting at one cell. */
    public static CellRange from(int firstRow, int firstColumn) {
        return new CellRange(firstRow, firstColumn, OptionalInt.empty(), OptionalInt.empty());
    }

    /**
     * Parses A1 notation.
     *
     * <p>Four shapes are accepted: {@code B2:D10} (a closed box), {@code B2} (open-ended from a cell),
     * {@code B:D} (whole columns) and {@code 2:10} (whole rows). A reversed range such as
     * {@code D10:B2} denotes the same box and is normalised rather than rejected — spreadsheet
     * selections come out either way round depending on which corner the operator dragged from.
     */
    public static CellRange parse(String a1) {
        Objects.requireNonNull(a1, "a1");
        String text = a1.trim().replace("$", "");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("cell range must not be blank");
        }
        String[] parts = text.split(":", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("not a cell range: " + a1);
        }
        Corner start = Corner.parse(parts[0], a1);
        if (parts.length == 1) {
            return new CellRange(
                    start.row().orElse(0), start.column().orElse(0), OptionalInt.empty(), OptionalInt.empty());
        }
        Corner end = Corner.parse(parts[1], a1);
        if (start.row().isPresent() != end.row().isPresent()
                || start.column().isPresent() != end.column().isPresent()) {
            throw new IllegalArgumentException("the two ends of " + a1 + " are not the same kind of reference");
        }
        return new CellRange(
                min(start.row(), end.row()),
                min(start.column(), end.column()),
                max(start.row(), end.row()),
                max(start.column(), end.column()));
    }

    public boolean containsRow(int row) {
        return row >= firstRow && (lastRow.isEmpty() || row <= lastRow.getAsInt());
    }

    public boolean containsColumn(int column) {
        return column >= firstColumn && (lastColumn.isEmpty() || column <= lastColumn.getAsInt());
    }

    /** The last row, or {@code sheetLastRow} when the range is open-ended. */
    public int effectiveLastRow(int sheetLastRow) {
        return lastRow.isPresent() ? Math.min(lastRow.getAsInt(), sheetLastRow) : sheetLastRow;
    }

    public String toA1() {
        String start = columnLetters(firstColumn) + (firstRow + 1);
        if (lastRow.isEmpty() && lastColumn.isEmpty()) {
            return start;
        }
        String end = (lastColumn.isPresent() ? columnLetters(lastColumn.getAsInt()) : "")
                + (lastRow.isPresent() ? String.valueOf(lastRow.getAsInt() + 1) : "");
        return start + ":" + end;
    }

    /** {@code A} → 0, {@code Z} → 25, {@code AA} → 26. */
    public static int columnIndex(String letters) {
        int index = 0;
        for (int i = 0; i < letters.length(); i++) {
            char c = Character.toUpperCase(letters.charAt(i));
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException("not a column reference: " + letters);
            }
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    /** The inverse of {@link #columnIndex}. */
    public static String columnLetters(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("column index must not be negative, was " + index);
        }
        StringBuilder letters = new StringBuilder();
        for (int remaining = index; ; remaining = remaining / 26 - 1) {
            letters.insert(0, (char) ('A' + remaining % 26));
            if (remaining < 26) {
                return letters.toString();
            }
        }
    }

    /** Both ends are known to agree on presence by the time this is called. */
    private static int min(OptionalInt a, OptionalInt b) {
        return a.isPresent() ? Math.min(a.getAsInt(), b.getAsInt()) : 0;
    }

    private static OptionalInt max(OptionalInt a, OptionalInt b) {
        if (a.isEmpty() || b.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(a.getAsInt(), b.getAsInt()));
    }

    /** One end of a range: a cell, a bare column letter, or a bare row number. */
    private record Corner(OptionalInt row, OptionalInt column) {

        static Corner parse(String text, String whole) {
            String reference = text.trim();
            int split = 0;
            while (split < reference.length() && Character.isLetter(reference.charAt(split))) {
                split++;
            }
            String letters = reference.substring(0, split);
            String digits = reference.substring(split);
            if (reference.isEmpty() || (letters.isEmpty() && digits.isEmpty())) {
                throw new IllegalArgumentException("not a cell range: " + whole);
            }
            for (int i = 0; i < digits.length(); i++) {
                if (!Character.isDigit(digits.charAt(i))) {
                    throw new IllegalArgumentException("not a cell range: " + whole);
                }
            }
            OptionalInt column = letters.isEmpty() ? OptionalInt.empty() : OptionalInt.of(columnIndex(letters));
            if (digits.isEmpty()) {
                return new Corner(OptionalInt.empty(), column);
            }
            int row = Integer.parseInt(digits);
            if (row < 1) {
                throw new IllegalArgumentException("rows are numbered from 1 in " + whole);
            }
            return new Corner(OptionalInt.of(row - 1), column);
        }
    }
}
