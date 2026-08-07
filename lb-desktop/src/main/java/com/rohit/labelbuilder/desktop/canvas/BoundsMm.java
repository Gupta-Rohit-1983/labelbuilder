package com.rohit.labelbuilder.desktop.canvas;

import javafx.geometry.Point2D;

/**
 * An axis-aligned rectangle in model millimetres — the geometric core of selection and transform.
 * Pure and immutable, so all the hit-testing and resize maths is unit-testable without the FX
 * toolkit; element rotation is carried by the item, not by these bounds (handles are computed
 * unrotated, then rotated about the centre by the FX layer).
 */
public record BoundsMm(double x, double y, double w, double h) {

    public BoundsMm {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w) || !Double.isFinite(h)) {
            throw new IllegalArgumentException("Bounds must be finite");
        }
    }

    public double right() {
        return x + w;
    }

    public double bottom() {
        return y + h;
    }

    public Point2D center() {
        return new Point2D(x + w / 2, y + h / 2);
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    public boolean intersects(BoundsMm o) {
        return x < o.right() && right() > o.x && y < o.bottom() && bottom() > o.y;
    }

    public BoundsMm translated(double dx, double dy) {
        return new BoundsMm(x + dx, y + dy, w, h);
    }

    /**
     * The axis-aligned bounding box that encloses this rectangle after it is rotated {@code deg}
     * about its centre. Used for conservative visibility culling (Phase 6e): a rotated item is
     * "maybe visible" iff its rotated AABB intersects the viewport, so nothing on-screen is ever
     * wrongly skipped.
     */
    public BoundsMm rotatedAabb(double deg) {
        if (deg % 360 == 0) {
            return this;
        }
        double r = Math.toRadians(deg);
        double cos = Math.abs(Math.cos(r));
        double sin = Math.abs(Math.sin(r));
        double nw = w * cos + h * sin;
        double nh = w * sin + h * cos;
        Point2D c = center();
        return new BoundsMm(c.getX() - nw / 2, c.getY() - nh / 2, nw, nh);
    }

    /** World position (unrotated) of a resize handle. */
    public Point2D handlePoint(ResizeHandle handle) {
        double hx = handle.movesLeft() ? x : handle.movesRight() ? right() : x + w / 2;
        double hy = handle.movesTop() ? y : handle.movesBottom() ? bottom() : y + h / 2;
        return new Point2D(hx, hy);
    }

    /**
     * Moves the handle's edge(s) by {@code (dx, dy)} in local space, keeping the opposite edge
     * fixed, and clamps to {@code minSize} without letting the fixed edge move.
     */
    public BoundsMm resized(ResizeHandle handle, double dx, double dy, double minSize) {
        double nx = x;
        double nw = w;
        double ny = y;
        double nh = h;

        if (handle.movesLeft()) {
            nx = x + dx;
            nw = w - dx;
        } else if (handle.movesRight()) {
            nw = w + dx;
        }
        if (handle.movesTop()) {
            ny = y + dy;
            nh = h - dy;
        } else if (handle.movesBottom()) {
            nh = h + dy;
        }

        if (handle.movesLeft() && nw < minSize) {
            nx = right() - minSize; // right edge (x + w) stays fixed
            nw = minSize;
        } else if (handle.movesRight() && nw < minSize) {
            nw = minSize;
        }
        if (handle.movesTop() && nh < minSize) {
            ny = bottom() - minSize; // bottom edge stays fixed
            nh = minSize;
        } else if (handle.movesBottom() && nh < minSize) {
            nh = minSize;
        }
        return new BoundsMm(nx, ny, nw, nh);
    }
}
