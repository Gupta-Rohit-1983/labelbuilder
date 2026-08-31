package com.rohit.labelbuilder.model.document;

import com.rohit.labelbuilder.model.style.RgbaColor;
import java.util.Objects;

/**
 * The physical label medium (lbl-format.md §3): its size in millimetres, orientation, non-printable
 * margins, background colour, and an optional stock-preset id ({@code null} for custom stock).
 */
public record Stock(
        double widthMm,
        double heightMm,
        Orientation orientation,
        Margins marginsMm,
        RgbaColor backgroundColor,
        String presetId) {

    public Stock {
        if (!(widthMm > 0) || !Double.isFinite(widthMm)) {
            throw new IllegalArgumentException("widthMm must be positive finite, was " + widthMm);
        }
        if (!(heightMm > 0) || !Double.isFinite(heightMm)) {
            throw new IllegalArgumentException("heightMm must be positive finite, was " + heightMm);
        }
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(marginsMm, "marginsMm");
        Objects.requireNonNull(backgroundColor, "backgroundColor");
        // presetId may be null (custom stock)
    }

    /** Custom white stock of the given size, landscape if wider than tall, no margins. */
    public static Stock of(double widthMm, double heightMm) {
        Orientation orientation = widthMm >= heightMm ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
        return new Stock(widthMm, heightMm, orientation, Margins.none(), RgbaColor.WHITE, null);
    }

    public Stock withSize(double newWidthMm, double newHeightMm) {
        return new Stock(newWidthMm, newHeightMm, orientation, marginsMm, backgroundColor, presetId);
    }
}
