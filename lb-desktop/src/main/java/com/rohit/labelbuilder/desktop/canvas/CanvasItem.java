package com.rohit.labelbuilder.desktop.canvas;

import javafx.geometry.Point2D;

/**
 * A placeholder canvas object for Phase 6 — a labelled, rotatable rectangle in model millimetres.
 * It exists so selection, handles and transforms (6c) have something to act on before the real
 * label object model arrives (Phase 7a), which will supply proper {@code LabelElement}s in its
 * place; the selection/transform machinery is written against the geometry, not this class, so it
 * carries over unchanged.
 */
public final class CanvasItem {

    private final String label;
    private BoundsMm bounds;
    private double rotationDeg;

    public CanvasItem(String label, BoundsMm bounds) {
        this.label = label;
        this.bounds = bounds;
    }

    public String label() {
        return label;
    }

    public BoundsMm bounds() {
        return bounds;
    }

    public void setBounds(BoundsMm bounds) {
        this.bounds = bounds;
    }

    public double rotationDeg() {
        return rotationDeg;
    }

    public void setRotationDeg(double rotationDeg) {
        this.rotationDeg = rotationDeg;
    }

    public Point2D center() {
        return bounds.center();
    }
}
