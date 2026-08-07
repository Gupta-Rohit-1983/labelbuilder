package com.rohit.labelbuilder.desktop.canvas;

import javafx.geometry.Orientation;

/**
 * A user alignment guide: an infinite line at a fixed model position. A {@link
 * Orientation#VERTICAL} guide is a vertical line at model x = {@code positionMm}; a {@link
 * Orientation#HORIZONTAL} guide is a horizontal line at model y = {@code positionMm}.
 */
public record Guide(Orientation orientation, double positionMm) {

    public static Guide vertical(double xMm) {
        return new Guide(Orientation.VERTICAL, xMm);
    }

    public static Guide horizontal(double yMm) {
        return new Guide(Orientation.HORIZONTAL, yMm);
    }

    public Guide movedTo(double newPositionMm) {
        return new Guide(orientation, newPositionMm);
    }
}
