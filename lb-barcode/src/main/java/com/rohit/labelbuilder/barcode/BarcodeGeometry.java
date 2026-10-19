package com.rohit.labelbuilder.barcode;

import java.util.List;

/**
 * An encoded barcode as <b>geometry in millimetres</b>: the bars as rectangles, plus any
 * human-readable captions, and the overall size including quiet zones.
 *
 * <p>Deliberately not a bitmap. Bars as millimetre rectangles feed the existing {@code RenderScene}
 * seam, so the design canvas and the Java2D print renderer draw the same description and stay crisp
 * at any DPI — and the thermal drivers in Phase 13e need module positions, which a rasterised image
 * would have thrown away.
 *
 * <p>Coordinates are relative to the barcode's own top-left, quiet zone included; the caller offsets
 * them to the element's position.
 */
public record BarcodeGeometry(double widthMm, double heightMm, List<Bar> bars, List<Caption> captions) {

    public BarcodeGeometry {
        bars = List.copyOf(bars);
        captions = List.copyOf(captions);
    }

    /** One bar (or, for 2D, one module): a filled rectangle. */
    public record Bar(double xMm, double yMm, double widthMm, double heightMm) {}

    /**
     * Human-readable text produced by the encoder, already positioned the way the symbology requires
     * — EAN and UPC, for instance, split their digits around the guard bars.
     */
    public record Caption(
            String text, double xMm, double endXMm, double baselineYMm, double fontSizeMm, CaptionAlign align) {}

    public enum CaptionAlign {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }

    /** Total ink area — useful for sanity checks and for scaling decisions. */
    public int barCount() {
        return bars.size();
    }
}
