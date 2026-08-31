package com.rohit.labelbuilder.model.style;

import java.util.Objects;

/** An outline: colour, width in millimetres, and dash pattern (lbl-format.md §4.6). */
public record Stroke(RgbaColor color, double widthMm, DashStyle dash) {

    public Stroke {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(dash, "dash");
        if (widthMm < 0 || !Double.isFinite(widthMm)) {
            throw new IllegalArgumentException("widthMm must be finite and non-negative, was " + widthMm);
        }
    }

    /** A solid black hairline of the given width. */
    public static Stroke solid(double widthMm) {
        return new Stroke(RgbaColor.BLACK, widthMm, DashStyle.SOLID);
    }

    /** A zero-width stroke standing for "no outline". */
    public static Stroke none() {
        return new Stroke(RgbaColor.TRANSPARENT, 0, DashStyle.SOLID);
    }
}
