package com.rohit.labelbuilder.barcode;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.ErrorCorrection;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.Objects;

/**
 * Everything needed to encode one barcode, in millimetres.
 *
 * <p>Separate from {@link BarcodeElement} so the encoder can be driven from a test, a print job or an
 * automation run without constructing a document — and so the point-to-millimetre conversion for the
 * HRI font happens in exactly one place.
 *
 * @param moduleWidthMm the X-dimension: the width of the narrowest bar, which is what actually
 *     determines whether a scanner can read the result
 */
public record BarcodeRequest(
        Symbology symbology,
        String value,
        double moduleWidthMm,
        double barHeightMm,
        double quietZoneMm,
        CheckDigitMode checkDigit,
        HriPosition hriPosition,
        double hriFontSizeMm,
        String hriFontFamily,
        ErrorCorrection errorCorrection) {

    private static final double PT_TO_MM = 25.4 / 72.0;

    public BarcodeRequest {
        Objects.requireNonNull(symbology, "symbology");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(checkDigit, "checkDigit");
        Objects.requireNonNull(hriPosition, "hriPosition");
        Objects.requireNonNull(hriFontFamily, "hriFontFamily");
        Objects.requireNonNull(errorCorrection, "errorCorrection");
        if (!(moduleWidthMm > 0) || !Double.isFinite(moduleWidthMm)) {
            throw new IllegalArgumentException("moduleWidthMm must be positive, was " + moduleWidthMm);
        }
        requireNonNegative(barHeightMm, "barHeightMm");
        requireNonNegative(quietZoneMm, "quietZoneMm");
        requireNonNegative(hriFontSizeMm, "hriFontSizeMm");
    }

    /** Build a request from a document element, converting the HRI font from points to millimetres. */
    public static BarcodeRequest from(BarcodeElement element) {
        return new BarcodeRequest(
                element.symbology(),
                element.value(),
                element.xDimensionMm(),
                element.barHeightMm(),
                element.quietZoneMm(),
                element.checkDigit(),
                element.hri().position(),
                element.hri().font().sizePt() * PT_TO_MM,
                element.hri().font().family(),
                element.errorCorrection());
    }

    /** A request for a 1D symbology, where error correction does not apply. */
    public static BarcodeRequest oneD(
            Symbology symbology,
            String value,
            double moduleWidthMm,
            double barHeightMm,
            double quietZoneMm,
            CheckDigitMode checkDigit,
            HriPosition hriPosition,
            double hriFontSizeMm,
            String hriFontFamily) {
        return new BarcodeRequest(
                symbology,
                value,
                moduleWidthMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hriPosition,
                hriFontSizeMm,
                hriFontFamily,
                ErrorCorrection.M);
    }

    /** A request for a 2D symbology, where bar height, check digits and HRI do not apply. */
    public static BarcodeRequest twoD(
            Symbology symbology,
            String value,
            double moduleSizeMm,
            double quietZoneMm,
            ErrorCorrection errorCorrection) {
        return new BarcodeRequest(
                symbology,
                value,
                moduleSizeMm,
                0,
                quietZoneMm,
                CheckDigitMode.AUTO,
                HriPosition.NONE,
                0,
                "Helvetica",
                errorCorrection);
    }

    /** A copy with a different X-dimension / module size. */
    public BarcodeRequest withModuleWidthMm(double newModuleWidthMm) {
        return new BarcodeRequest(
                symbology,
                value,
                newModuleWidthMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hriPosition,
                hriFontSizeMm,
                hriFontFamily,
                errorCorrection);
    }

    /** A copy with a different bar height (1D only). */
    public BarcodeRequest withBarHeightMm(double newBarHeightMm) {
        return new BarcodeRequest(
                symbology,
                value,
                moduleWidthMm,
                newBarHeightMm,
                quietZoneMm,
                checkDigit,
                hriPosition,
                hriFontSizeMm,
                hriFontFamily,
                errorCorrection);
    }

    /** A copy with a different quiet zone. */
    public BarcodeRequest withQuietZoneMm(double newQuietZoneMm) {
        return new BarcodeRequest(
                symbology,
                value,
                moduleWidthMm,
                barHeightMm,
                newQuietZoneMm,
                checkDigit,
                hriPosition,
                hriFontSizeMm,
                hriFontFamily,
                errorCorrection);
    }

    /** The text the HRI should show: the custom override when set, otherwise the encoded value. */
    public static String captionTextFor(BarcodeElement element) {
        String custom = element.hri().customText();
        return custom == null || custom.isBlank() ? element.value() : custom;
    }

    private static void requireNonNegative(double value, String field) {
        if (value < 0 || !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and non-negative, was " + value);
        }
    }
}
