package com.rohit.labelbuilder.model.document;

/** Design grid settings persisted with the document (lbl-format.md §3). */
public record GridSpec(boolean visible, double spacingMm, boolean snap) {

    public GridSpec {
        if (!(spacingMm > 0) || !Double.isFinite(spacingMm)) {
            throw new IllegalArgumentException("spacingMm must be positive finite, was " + spacingMm);
        }
    }

    /** A visible 1 mm grid with snapping on. */
    public static GridSpec defaults() {
        return new GridSpec(true, 1.0, true);
    }
}
