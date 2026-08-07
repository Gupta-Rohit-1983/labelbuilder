package com.rohit.labelbuilder.render.scene;

import java.util.List;

/**
 * A single drawable in a {@link RenderScene}, positioned in model millimetres. Sealed so every
 * renderer (Java2D reference, JavaFX canvas) switches over the same closed set — adding a shape
 * won't compile until each renderer handles it.
 *
 * <p>{@code fill}/{@code stroke} may be {@code null} to mean "no fill" / "no outline".
 * {@code rotationDeg} rotates the shape clockwise about its own centre.
 */
public sealed interface RenderPrimitive {

    /** Axis-aligned (then optionally rotated) rectangle. */
    record Rect(
            double xMm,
            double yMm,
            double wMm,
            double hMm,
            double rotationDeg,
            RenderColor fill,
            RenderColor stroke,
            double strokeWidthMm)
            implements RenderPrimitive {}

    /** Ellipse inscribed in the given box, optionally rotated about its centre. */
    record Ellipse(
            double xMm,
            double yMm,
            double wMm,
            double hMm,
            double rotationDeg,
            RenderColor fill,
            RenderColor stroke,
            double strokeWidthMm)
            implements RenderPrimitive {}

    /** Straight line segment. */
    record Line(double x1Mm, double y1Mm, double x2Mm, double y2Mm, RenderColor stroke, double strokeWidthMm)
            implements RenderPrimitive {}

    /**
     * Text with its baseline anchored at {@code (xMm, yMm)}. Font-dependent, so text scenes are
     * excluded from pixel-baseline regression (fonts differ across platforms); geometry scenes
     * carry the parity guarantee.
     */
    record Text(
            double xMm, double yMm, String text, double fontSizeMm, RenderColor color, double rotationDeg, boolean bold)
            implements RenderPrimitive {}

    /** Convenience: a filled+stroked upright rectangle. */
    static Rect rect(
            double xMm,
            double yMm,
            double wMm,
            double hMm,
            RenderColor fill,
            RenderColor stroke,
            double strokeWidthMm) {
        return new Rect(xMm, yMm, wMm, hMm, 0, fill, stroke, strokeWidthMm);
    }

    static List<RenderPrimitive> none() {
        return List.of();
    }
}
