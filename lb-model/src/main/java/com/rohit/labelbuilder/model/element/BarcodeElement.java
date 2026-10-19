package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.ErrorCorrection;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.Objects;

/**
 * A barcode or 2D code (lbl-format.md §4.3–4.4). One element covers both families, discriminated by
 * its {@link Symbology}; the encoders live in lb-barcode (Barcode4J for 1D, ZXing for 2D).
 *
 * <p>{@code value} is the raw template string (literal + {@code ${field}}), resolved by the Phase 12
 * engine, not here.
 *
 * <p>Field applicability depends on the symbology: {@code xDimensionMm} is the narrow-bar width for
 * 1D and the module size for 2D; {@code barHeightMm} and {@code checkDigit} are 1D concerns;
 * {@code errorCorrection} is a 2D one. They are all carried regardless so switching symbology does
 * not silently discard settings the user may switch back to.
 *
 * <p>{@code rotationQuadrant} is 0/90/180/270 — thermal firmware only rotates in quadrants, distinct
 * from the free {@code rotationDeg} on the common properties, which applies to screen rendering.
 */
public record BarcodeElement(
        ElementProperties properties,
        Symbology symbology,
        String value,
        double xDimensionMm,
        double barHeightMm,
        double quietZoneMm,
        CheckDigitMode checkDigit,
        Hri hri,
        int rotationQuadrant,
        ErrorCorrection errorCorrection)
        implements LabelElement {

    public BarcodeElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(symbology, "symbology");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(checkDigit, "checkDigit");
        Objects.requireNonNull(hri, "hri");
        Objects.requireNonNull(errorCorrection, "errorCorrection");
        requireNonNegativeFinite(xDimensionMm, "xDimensionMm");
        requireNonNegativeFinite(barHeightMm, "barHeightMm");
        requireNonNegativeFinite(quietZoneMm, "quietZoneMm");
        if (rotationQuadrant != 0 && rotationQuadrant != 90 && rotationQuadrant != 180 && rotationQuadrant != 270) {
            throw new IllegalArgumentException("rotationQuadrant must be 0/90/180/270, was " + rotationQuadrant);
        }
    }

    /**
     * A barcode with the common defaults: medium error correction and no quadrant rotation.
     */
    public static BarcodeElement of(
            ElementProperties properties,
            Symbology symbology,
            String value,
            double xDimensionMm,
            double barHeightMm,
            double quietZoneMm,
            CheckDigitMode checkDigit,
            Hri hri) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                0,
                ErrorCorrection.M);
    }

    // --- copies: every reconstruction of this record lives here, so adding a component later
    // --- touches this file alone rather than every caller that wanted to change one field.

    public BarcodeElement withValue(String newValue) {
        return new BarcodeElement(
                properties,
                symbology,
                newValue,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withSymbology(Symbology newSymbology) {
        return new BarcodeElement(
                properties,
                newSymbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withXDimensionMm(double newXDimensionMm) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                newXDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withBarHeightMm(double newBarHeightMm) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                newBarHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withQuietZoneMm(double newQuietZoneMm) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                newQuietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withCheckDigit(CheckDigitMode newCheckDigit) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                newCheckDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withHri(Hri newHri) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                newHri,
                rotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withRotationQuadrant(int newRotationQuadrant) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                newRotationQuadrant,
                errorCorrection);
    }

    public BarcodeElement withErrorCorrection(ErrorCorrection newErrorCorrection) {
        return new BarcodeElement(
                properties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                newErrorCorrection);
    }

    @Override
    public BarcodeElement withProperties(ElementProperties newProperties) {
        return new BarcodeElement(
                newProperties,
                symbology,
                value,
                xDimensionMm,
                barHeightMm,
                quietZoneMm,
                checkDigit,
                hri,
                rotationQuadrant,
                errorCorrection);
    }

    private static void requireNonNegativeFinite(double v, String field) {
        if (v < 0 || !Double.isFinite(v)) {
            throw new IllegalArgumentException(field + " must be finite and non-negative, was " + v);
        }
    }
}
