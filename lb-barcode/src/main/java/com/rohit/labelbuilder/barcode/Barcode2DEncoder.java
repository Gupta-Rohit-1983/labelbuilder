package com.rohit.labelbuilder.barcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.rohit.labelbuilder.model.style.ErrorCorrection;
import com.rohit.labelbuilder.model.style.Symbology;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes two-dimensional symbologies with ZXing (Phase 10b): QR, Data Matrix, PDF417 and Aztec.
 *
 * <p>ZXing is asked for the symbol at its <b>native module size</b> (one matrix cell per module) and
 * the modules are then scaled into millimetres here. Letting ZXing rasterise to a pixel size would
 * tie the result to a resolution and round module edges unevenly — fatal for a label that must print
 * at 203 or 600 dpi and still scan.
 *
 * <p>Runs of adjacent dark modules are merged into single rectangles. A dense QR symbol holds tens of
 * thousands of modules; emitting one rectangle each would swamp the renderer for no visual gain.
 */
public final class Barcode2DEncoder {

    /** Ask for the native matrix: ZXing then uses a scale of one cell per module. */
    private static final int NATIVE_SIZE = 0;

    private Barcode2DEncoder() {}

    /**
     * Encode a value.
     *
     * @throws BarcodeException if the symbology is not 2D, or the value will not fit or encode
     */
    public static BarcodeGeometry encode(BarcodeRequest request) {
        if (!request.symbology().isTwoDimensional()) {
            throw new BarcodeException(request.symbology() + " is a 1D symbology — use the 1D encoder, not the 2D one");
        }
        if (request.value().isEmpty()) {
            // ZXing's failure here is an opaque IllegalArgumentException; say what is actually wrong.
            throw new BarcodeException("cannot encode an empty value as " + request.symbology());
        }

        BitMatrix matrix;
        try {
            matrix = writerFor(request.symbology())
                    .encode(request.value(), formatFor(request.symbology()), NATIVE_SIZE, NATIVE_SIZE, hints(request));
        } catch (WriterException | RuntimeException e) {
            throw new BarcodeException(
                    "cannot encode \"%s\" as %s: %s".formatted(request.value(), request.symbology(), e.getMessage()),
                    e);
        }
        return toGeometry(matrix, request.moduleWidthMm());
    }

    /** True when {@link #encode} can handle this symbology. */
    public static boolean supports(Symbology symbology) {
        return symbology.isTwoDimensional();
    }

    /**
     * Turn the module matrix into millimetre rectangles, merging horizontal runs.
     *
     * <p>The matrix already includes the quiet zone requested via {@link EncodeHintType#MARGIN}, so
     * its full extent is the symbol's footprint.
     */
    private static BarcodeGeometry toGeometry(BitMatrix matrix, double moduleMm) {
        List<BarcodeGeometry.Bar> bars = new ArrayList<>();
        for (int y = 0; y < matrix.getHeight(); y++) {
            int runStart = -1;
            for (int x = 0; x <= matrix.getWidth(); x++) {
                boolean dark = x < matrix.getWidth() && matrix.get(x, y);
                if (dark && runStart < 0) {
                    runStart = x;
                } else if (!dark && runStart >= 0) {
                    bars.add(new BarcodeGeometry.Bar(
                            runStart * moduleMm, y * moduleMm, (x - runStart) * moduleMm, moduleMm));
                    runStart = -1;
                }
            }
        }
        return new BarcodeGeometry(matrix.getWidth() * moduleMm, matrix.getHeight() * moduleMm, bars, List.of());
    }

    private static Writer writerFor(Symbology symbology) {
        return switch (symbology) {
            case QR, GS1_QR -> new QRCodeWriter();
            case DATA_MATRIX, GS1_DATA_MATRIX -> new DataMatrixWriter();
            case PDF417 -> new PDF417Writer();
            case AZTEC -> new AztecWriter();
            default -> throw new BarcodeException("not a 2D symbology: " + symbology);
        };
    }

    private static BarcodeFormat formatFor(Symbology symbology) {
        return switch (symbology) {
            case QR, GS1_QR -> BarcodeFormat.QR_CODE;
            case DATA_MATRIX, GS1_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX;
            case PDF417 -> BarcodeFormat.PDF_417;
            case AZTEC -> BarcodeFormat.AZTEC;
            default -> throw new BarcodeException("not a 2D symbology: " + symbology);
        };
    }

    private static Map<EncodeHintType, Object> hints(BarcodeRequest request) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        // The quiet zone is expressed in modules, so convert from the millimetres the model holds.
        hints.put(EncodeHintType.MARGIN, quietZoneModules(request));
        switch (request.symbology()) {
            case QR, GS1_QR -> hints.put(EncodeHintType.ERROR_CORRECTION, qrLevel(request.errorCorrection()));
            case AZTEC, PDF417 -> hints.put(EncodeHintType.ERROR_CORRECTION, numericLevel(request.errorCorrection()));
            // Data Matrix is ECC200 only — there is nothing to choose, so no hint is set.
            default -> {}
        }
        return hints;
    }

    private static int quietZoneModules(BarcodeRequest request) {
        return (int) Math.round(request.quietZoneMm() / request.moduleWidthMm());
    }

    private static ErrorCorrectionLevel qrLevel(ErrorCorrection level) {
        return switch (level) {
            case L -> ErrorCorrectionLevel.L;
            case M -> ErrorCorrectionLevel.M;
            case Q -> ErrorCorrectionLevel.Q;
            case H -> ErrorCorrectionLevel.H;
        };
    }

    /**
     * Aztec and PDF417 take a numeric strength rather than QR's four letters. These map the four
     * levels onto comparable amounts of redundancy; they are approximations, not equivalences.
     */
    private static int numericLevel(ErrorCorrection level) {
        return switch (level) {
            case L -> 1;
            case M -> 3;
            case Q -> 5;
            case H -> 7;
        };
    }
}
