package com.rohit.labelbuilder.desktop.canvas;

/**
 * Design grid configuration: spacing in millimetres, whether the grid is drawn, and whether
 * coordinates snap to it. Pure data with a pure {@link #snap} helper so grid maths is testable
 * without the FX toolkit.
 */
public record GridSettings(double spacingMm, boolean showGrid, boolean snapToGrid) {

    public GridSettings {
        if (spacingMm <= 0 || !Double.isFinite(spacingMm)) {
            throw new IllegalArgumentException("Grid spacing must be positive: " + spacingMm);
        }
    }

    /** 5 mm grid, shown and snapping — a sensible default for label work. */
    public static GridSettings defaults() {
        return new GridSettings(5, true, true);
    }

    /** Rounds a millimetre value to the nearest grid line. */
    public double snap(double millimetres) {
        return Math.round(millimetres / spacingMm) * spacingMm;
    }

    public GridSettings withShowGrid(boolean value) {
        return new GridSettings(spacingMm, value, snapToGrid);
    }

    public GridSettings withSnapToGrid(boolean value) {
        return new GridSettings(spacingMm, showGrid, value);
    }
}
