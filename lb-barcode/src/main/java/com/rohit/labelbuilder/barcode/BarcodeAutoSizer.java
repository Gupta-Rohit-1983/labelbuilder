package com.rohit.labelbuilder.barcode;

/**
 * Fits a barcode to a target box (Phase 10c) by solving for the X-dimension or module size that makes
 * the symbol occupy the space available — the element's bounds, once the canvas wires this up.
 *
 * <p>A barcode cannot simply be stretched: its width is quantised to whole modules, and the quiet
 * zone must survive the resize or the result stops scanning. So rather than scaling a picture, this
 * measures the symbol once and solves for the module size that fits.
 */
public final class BarcodeAutoSizer {

    /** A convenient reference size for the measuring pass; any positive value gives the same answer. */
    private static final double PROBE_MODULE_MM = 1.0;

    /** The matrix fit converges in two or three passes; this is a safety stop, not a budget. */
    private static final int MAX_FIT_PASSES = 8;

    private static final double CONVERGED_MM = 1e-9;

    private BarcodeAutoSizer() {}

    /**
     * A copy of {@code request} sized to fit {@code widthMm} × {@code heightMm}.
     *
     * <p>For a linear symbology the X-dimension is solved exactly: the quiet zone is a fixed
     * millimetre margin, so only the bars scale, and the bar height is set from the box. For a matrix
     * symbology the module size is chosen so the symbol plus its quiet zone fits the smaller axis,
     * keeping it square.
     *
     * @throws BarcodeException if the value will not encode, or the box is too small to hold the
     *     quiet zone alone
     */
    public static BarcodeRequest fitTo(BarcodeRequest request, double widthMm, double heightMm) {
        if (!(widthMm > 0) || !(heightMm > 0)) {
            throw new BarcodeException("target size must be positive, was %s x %s".formatted(widthMm, heightMm));
        }
        return request.symbology().isTwoDimensional()
                ? fitMatrix(request, widthMm, heightMm)
                : fitLinear(request, widthMm, heightMm);
    }

    /**
     * Width is {@code 2 × quietZone + modules × X}; the quiet zone is in absolute millimetres and does
     * not scale, so the module count can be recovered from one probe and the X-dimension solved
     * directly.
     */
    private static BarcodeRequest fitLinear(BarcodeRequest request, double widthMm, double heightMm) {
        double usableWidth = widthMm - 2 * request.quietZoneMm();
        if (usableWidth <= 0) {
            throw new BarcodeException("%s mm is too narrow for a %s mm quiet zone on each side"
                    .formatted(trim(widthMm), trim(request.quietZoneMm())));
        }
        BarcodeGeometry probe = Barcode1DEncoder.encode(request.withModuleWidthMm(PROBE_MODULE_MM));
        double modules = (probe.widthMm() - 2 * request.quietZoneMm()) / PROBE_MODULE_MM;
        if (modules <= 0) {
            throw new BarcodeException("could not measure " + request.symbology());
        }

        double barHeight = heightMm - humanReadableHeight(request, probe);
        if (barHeight <= 0) {
            throw new BarcodeException(
                    "%s mm is too short once the human-readable text is allowed for".formatted(trim(heightMm)));
        }
        return request.withModuleWidthMm(usableWidth / modules).withBarHeightMm(barHeight);
    }

    /** How much of the probe's height went to the caption rather than the bars. */
    private static double humanReadableHeight(BarcodeRequest request, BarcodeGeometry probe) {
        return Math.max(0, probe.heightMm() - request.barHeightMm());
    }

    /**
     * A matrix symbol is square (or fixed-ratio) and quantised in both axes, so it is fitted to the
     * smaller of the two dimensions; the leftover space is the caller's to distribute.
     */
    private static BarcodeRequest fitMatrix(BarcodeRequest request, double widthMm, double heightMm) {
        // Measure the bare symbol: the quiet zone is re-derived from the final module size, so
        // including it in the probe would make this circular.
        BarcodeGeometry probe =
                Barcode2DEncoder.encode(request.withQuietZoneMm(0).withModuleWidthMm(PROBE_MODULE_MM));
        double modulesAcross = probe.widthMm() / PROBE_MODULE_MM;
        double modulesDown = probe.heightMm() / PROBE_MODULE_MM;

        if (modulesAcross <= 0 || modulesDown <= 0) {
            throw new BarcodeException("could not measure " + request.symbology());
        }

        // The encoder expresses the quiet zone in *whole modules*, so its millimetre width depends on
        // the module size still being solved for. Treating it as a fixed margin overflows the box: a
        // 1 mm zone at a 1.095 mm module really costs 1.095 mm a side. Iterate to a fixed point —
        // each pass re-rounds the zone and shrinks the module to suit, converging quickly because the
        // module only ever decreases.
        double module = Math.min(widthMm / modulesAcross, heightMm / modulesDown);
        for (int pass = 0; pass < MAX_FIT_PASSES; pass++) {
            double quietModules = quietZoneModules(request.quietZoneMm(), module);
            double next =
                    Math.min(widthMm / (modulesAcross + 2 * quietModules), heightMm / (modulesDown + 2 * quietModules));
            boolean converged = Math.abs(next - module) < CONVERGED_MM;
            module = next;
            if (converged) {
                break;
            }
        }
        if (!(module > 0) || !Double.isFinite(module)) {
            throw new BarcodeException("%s x %s mm is too small for a %s mm quiet zone"
                    .formatted(trim(widthMm), trim(heightMm), trim(request.quietZoneMm())));
        }
        return request.withModuleWidthMm(module);
    }

    /** Mirrors the encoder's conversion exactly; any divergence would reintroduce the overflow. */
    private static int quietZoneModules(double quietZoneMm, double moduleMm) {
        return (int) Math.round(quietZoneMm / moduleMm);
    }

    private static String trim(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
