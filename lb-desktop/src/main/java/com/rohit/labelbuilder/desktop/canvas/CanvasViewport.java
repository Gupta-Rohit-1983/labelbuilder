package com.rohit.labelbuilder.desktop.canvas;

/**
 * The canvas coordinate transform: maps between <em>model millimetres</em> (architecture NFR-07 —
 * the model is in mm, pixels are a rendering concern) and <em>device pixels</em>, given a zoom
 * factor and a pan offset.
 *
 * <p>Immutable with pure transforms, so all coordinate maths is unit-testable headlessly; {@link
 * DesignCanvas} holds one of these in a property and repaints when it changes.
 *
 * <p>{@code pixelsPerMm} is the device scale at 100% zoom; the default assumes the CSS-reference
 * 96 dpi, so "100%" is roughly physical size on a typical monitor. {@code offsetX/Y} is the device
 * pixel at which model origin (0,0 mm) lands. Effective scale is {@code pixelsPerMm * zoom}.
 */
public record CanvasViewport(double pixelsPerMm, double zoom, double offsetX, double offsetY) {

    /** Device pixels per millimetre at 100% zoom, assuming 96 dpi (1 in = 25.4 mm). */
    public static final double DEFAULT_PIXELS_PER_MM = 96.0 / 25.4;

    public static final double MIN_ZOOM = 0.05; // 5%
    public static final double MAX_ZOOM = 64.0; // 6400%

    public CanvasViewport {
        if (pixelsPerMm <= 0 || !Double.isFinite(pixelsPerMm)) {
            throw new IllegalArgumentException("pixelsPerMm must be positive and finite: " + pixelsPerMm);
        }
        zoom = clampZoom(zoom);
    }

    /** Identity viewport at 100% with the model origin at the top-left device pixel. */
    public static CanvasViewport initial() {
        return new CanvasViewport(DEFAULT_PIXELS_PER_MM, 1.0, 0, 0);
    }

    /** Device pixels per model millimetre at the current zoom. */
    public double scale() {
        return pixelsPerMm * zoom;
    }

    public double toDeviceX(double millimetresX) {
        return offsetX + millimetresX * scale();
    }

    public double toDeviceY(double millimetresY) {
        return offsetY + millimetresY * scale();
    }

    public double toModelX(double deviceX) {
        return (deviceX - offsetX) / scale();
    }

    public double toModelY(double deviceY) {
        return (deviceY - offsetY) / scale();
    }

    /** Pans by a device-pixel delta. */
    public CanvasViewport pannedBy(double deviceDx, double deviceDy) {
        return new CanvasViewport(pixelsPerMm, zoom, offsetX + deviceDx, offsetY + deviceDy);
    }

    /**
     * Sets an absolute zoom while keeping the model point currently under {@code (pivotX, pivotY)}
     * device pixels fixed under that same pixel — i.e. zoom toward the cursor.
     */
    public CanvasViewport withZoomAt(double newZoom, double pivotX, double pivotY) {
        double clamped = clampZoom(newZoom);
        double modelX = toModelX(pivotX);
        double modelY = toModelY(pivotY);
        double newScale = pixelsPerMm * clamped;
        return new CanvasViewport(pixelsPerMm, clamped, pivotX - modelX * newScale, pivotY - modelY * newScale);
    }

    /** Multiplies the zoom by {@code factor}, keeping the given device pivot fixed. */
    public CanvasViewport zoomedByAt(double factor, double pivotX, double pivotY) {
        return withZoomAt(zoom * factor, pivotX, pivotY);
    }

    /**
     * Fits a surface of {@code surfaceW × surfaceH} mm inside a {@code viewW × viewH}-pixel
     * viewport with {@code paddingPx} of margin, centred. Returns {@code this} unchanged if any
     * dimension is non-positive (nothing sensible to fit yet — e.g. before first layout).
     */
    public CanvasViewport fit(double surfaceW, double surfaceH, double viewW, double viewH, double paddingPx) {
        if (surfaceW <= 0 || surfaceH <= 0 || viewW <= 0 || viewH <= 0) {
            return this;
        }
        double sx = (viewW - 2 * paddingPx) / (surfaceW * pixelsPerMm);
        double sy = (viewH - 2 * paddingPx) / (surfaceH * pixelsPerMm);
        double newZoom = clampZoom(Math.min(sx, sy));
        double newScale = pixelsPerMm * newZoom;
        double offX = (viewW - surfaceW * newScale) / 2;
        double offY = (viewH - surfaceH * newScale) / 2;
        return new CanvasViewport(pixelsPerMm, newZoom, offX, offY);
    }

    /**
     * The model-millimetre rectangle currently visible in a {@code viewWpx × viewHpx} viewport.
     * Used to cull off-screen content (Phase 6e) — anything not intersecting this need not be drawn.
     */
    public BoundsMm visibleModelBounds(double viewWpx, double viewHpx) {
        double x0 = toModelX(0);
        double y0 = toModelY(0);
        double x1 = toModelX(viewWpx);
        double y1 = toModelY(viewHpx);
        return new BoundsMm(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    private static double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }
}
