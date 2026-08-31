package com.rohit.labelbuilder.model.geom;

/**
 * An axis-aligned rectangle in <b>model millimetres</b>, origin at the label's top-left, y growing
 * downward (NFR-07). This is the model's own geometry type — deliberately independent of the
 * desktop's {@code BoundsMm} (which imports JavaFX) so lb-model stays framework-free.
 *
 * <p>Immutable; every mutator returns a new instance. Width and height are non-negative (a
 * degenerate zero-extent box is allowed — a {@code line} uses its bounds' opposite corners as
 * endpoints and may be zero in one axis).
 */
public record Bounds(double xMm, double yMm, double widthMm, double heightMm) {

    public Bounds {
        requireFinite(xMm, "xMm");
        requireFinite(yMm, "yMm");
        requireFinite(widthMm, "widthMm");
        requireFinite(heightMm, "heightMm");
        if (widthMm < 0 || heightMm < 0) {
            throw new IllegalArgumentException("size must be non-negative: " + widthMm + " x " + heightMm);
        }
    }

    /** Bounds from a position and size. */
    public static Bounds of(double xMm, double yMm, double widthMm, double heightMm) {
        return new Bounds(xMm, yMm, widthMm, heightMm);
    }

    public double rightMm() {
        return xMm + widthMm;
    }

    public double bottomMm() {
        return yMm + heightMm;
    }

    public double centerXMm() {
        return xMm + widthMm / 2.0;
    }

    public double centerYMm() {
        return yMm + heightMm / 2.0;
    }

    /** Translate by a delta, keeping the same size. */
    public Bounds translated(double dxMm, double dyMm) {
        return new Bounds(xMm + dxMm, yMm + dyMm, widthMm, heightMm);
    }

    /** Move the top-left corner to a new position, keeping the same size. */
    public Bounds withPosition(double newXMm, double newYMm) {
        return new Bounds(newXMm, newYMm, widthMm, heightMm);
    }

    /** Resize about the top-left corner, keeping the same position. */
    public Bounds withSize(double newWidthMm, double newHeightMm) {
        return new Bounds(xMm, yMm, newWidthMm, newHeightMm);
    }

    /** True if the point lies within this box, inclusive of the top-left edges. */
    public boolean contains(double px, double py) {
        return px >= xMm && px <= rightMm() && py >= yMm && py <= bottomMm();
    }

    /** True if this box overlaps {@code other} on a positive area (edge-touch is not overlap). */
    public boolean intersects(Bounds other) {
        return xMm < other.rightMm() && rightMm() > other.xMm && yMm < other.bottomMm() && bottomMm() > other.yMm;
    }

    private static void requireFinite(double v, String field) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(field + " must be finite, was " + v);
        }
    }
}
