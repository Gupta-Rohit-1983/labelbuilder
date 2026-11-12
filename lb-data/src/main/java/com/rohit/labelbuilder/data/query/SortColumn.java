package com.rohit.labelbuilder.data.query;

import java.util.Objects;

/**
 * One term of a built query's {@code ORDER BY}.
 *
 * <p>Sort order is not cosmetic on a label run: it decides the sequence labels come out of the
 * printer, which is the sequence somebody applies them to cartons in. An unordered query is free to
 * return rows differently on each run, so a merge that must match a pick list needs this.
 */
public record SortColumn(QueryColumn column, boolean descending) {

    public SortColumn {
        Objects.requireNonNull(column, "column");
    }

    public static SortColumn ascending(QueryColumn column) {
        return new SortColumn(column, false);
    }

    public static SortColumn descending(QueryColumn column) {
        return new SortColumn(column, true);
    }
}
