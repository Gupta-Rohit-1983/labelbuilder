package com.rohit.labelbuilder.desktop.canvas;

/**
 * The physical label area the canvas draws, in millimetres. A minimal stand-in for the real label
 * model (Phase 7a) — just enough for the viewport to have something to frame and render.
 */
public record LabelSurface(double widthMm, double heightMm) {

    public LabelSurface {
        if (widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("Label dimensions must be positive: " + widthMm + " x " + heightMm);
        }
    }

    /** A common default label size until documents carry their own (Phase 7). */
    public static LabelSurface defaultSize() {
        return new LabelSurface(100, 60);
    }
}
