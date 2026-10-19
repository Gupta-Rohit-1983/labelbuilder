package com.rohit.labelbuilder.barcode;

import com.rohit.labelbuilder.model.style.Symbology;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a barcode before it is printed (Phase 10c): whether it encodes at all, and whether its
 * dimensions give a scanner a fair chance.
 *
 * <p>Never throws — it reports. The inspector shows this live while the user types, and the print
 * pipeline consults it before committing a job to media; both need the full picture rather than the
 * first failure.
 *
 * <p>The dimensional thresholds follow general GS1 guidance. They are advisory minimums for
 * <em>general</em> use; a specific trading partner or scanner may demand more, which is why they
 * surface as warnings a user can knowingly accept rather than hard limits.
 */
public final class BarcodeValidator {

    /** Below roughly 7.5 mil, ordinary scanners start to struggle with a linear barcode. */
    private static final double MIN_LINEAR_X_DIMENSION_MM = 0.19;

    /** 2D symbols need a little more per module than a linear bar. */
    private static final double MIN_MATRIX_MODULE_MM = 0.25;

    /** GS1 recommends at least 0.25 in of bar height for a retail linear symbol. */
    private static final double MIN_LINEAR_BAR_HEIGHT_MM = 6.35;

    private BarcodeValidator() {}

    /** Validate a request, collecting every problem found. */
    public static ValidationReport validate(BarcodeRequest request) {
        List<ValidationReport.Issue> issues = new ArrayList<>();

        if (request.value().isEmpty()) {
            add(issues, ValidationReport.Severity.ERROR, "the value is empty");
            return new ValidationReport(issues); // nothing further is meaningful
        }

        // Does it encode? This is the authoritative check — the encoders know the real rules.
        try {
            if (request.symbology().isTwoDimensional()) {
                Barcode2DEncoder.encode(request);
            } else {
                Barcode1DEncoder.encode(request);
            }
        } catch (BarcodeException e) {
            add(issues, ValidationReport.Severity.ERROR, e.getMessage());
            // Dimensional advice below still applies and may help explain the failure.
        }

        checkModuleSize(request, issues);
        checkQuietZone(request, issues);
        checkBarHeight(request, issues);
        return new ValidationReport(issues);
    }

    private static void checkModuleSize(BarcodeRequest request, List<ValidationReport.Issue> issues) {
        boolean matrix = request.symbology().isTwoDimensional();
        double minimum = matrix ? MIN_MATRIX_MODULE_MM : MIN_LINEAR_X_DIMENSION_MM;
        if (request.moduleWidthMm() < minimum) {
            add(
                    issues,
                    ValidationReport.Severity.WARNING,
                    "%s of %s mm is below the %s mm usually needed to scan reliably"
                            .formatted(
                                    matrix ? "module size" : "X-dimension",
                                    trim(request.moduleWidthMm()),
                                    trim(minimum)));
        }
    }

    /**
     * The quiet zone is the blank margin a scanner needs to find the symbol's edges. It is specified
     * as a multiple of the module, so the requirement grows with the barcode.
     */
    private static void checkQuietZone(BarcodeRequest request, List<ValidationReport.Issue> issues) {
        int requiredModules = minimumQuietZoneModules(request.symbology());
        double requiredMm = requiredModules * request.moduleWidthMm();
        if (request.quietZoneMm() + 1e-9 < requiredMm) {
            add(
                    issues,
                    ValidationReport.Severity.WARNING,
                    "%s needs a quiet zone of at least %s modules (%s mm here); %s mm is set"
                            .formatted(
                                    request.symbology(),
                                    requiredModules,
                                    trim(requiredMm),
                                    trim(request.quietZoneMm())));
        }
    }

    private static void checkBarHeight(BarcodeRequest request, List<ValidationReport.Issue> issues) {
        if (request.symbology().isTwoDimensional()) {
            return; // a matrix symbol's height is determined by its module count
        }
        if (request.barHeightMm() + 1e-9 < MIN_LINEAR_BAR_HEIGHT_MM) {
            add(
                    issues,
                    ValidationReport.Severity.WARNING,
                    "bar height of %s mm is below the %s mm usually recommended; short bars make the "
                                    .formatted(trim(request.barHeightMm()), trim(MIN_LINEAR_BAR_HEIGHT_MM))
                            + "symbol hard to sweep with a scanner");
        }
    }

    /** Minimum quiet zone, in modules, per symbology. */
    private static int minimumQuietZoneModules(Symbology symbology) {
        return switch (symbology) {
            case EAN_13, EAN_8, UPC_A, UPC_E -> 9;
            case QR, GS1_QR -> 4;
            case PDF417 -> 2;
            case DATA_MATRIX, GS1_DATA_MATRIX -> 1;
            case AZTEC -> 0; // Aztec is designed to need none
            default -> 10; // Code 128/39, ITF, Codabar, MSI
        };
    }

    private static void add(List<ValidationReport.Issue> issues, ValidationReport.Severity severity, String message) {
        issues.add(new ValidationReport.Issue(severity, message));
    }

    private static String trim(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
