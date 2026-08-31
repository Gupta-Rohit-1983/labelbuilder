package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.Objects;

/**
 * A barcode/2D-code <b>placeholder</b> (lbl-format.md §4.3–4.4). Phase 7a models the element and its
 * geometry only; the real encoders (Barcode4J for 1D, ZXing for 2D) and validation land in Phase 10.
 * {@code value} is the raw template string (literal + {@code ${field}}), resolved by the Phase 12
 * engine, not here.
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
        int rotationQuadrant)
        implements LabelElement {

    public BarcodeElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(symbology, "symbology");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(checkDigit, "checkDigit");
        Objects.requireNonNull(hri, "hri");
        requireNonNegativeFinite(xDimensionMm, "xDimensionMm");
        requireNonNegativeFinite(barHeightMm, "barHeightMm");
        requireNonNegativeFinite(quietZoneMm, "quietZoneMm");
        if (rotationQuadrant != 0 && rotationQuadrant != 90 && rotationQuadrant != 180 && rotationQuadrant != 270) {
            throw new IllegalArgumentException("rotationQuadrant must be 0/90/180/270, was " + rotationQuadrant);
        }
    }

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
                rotationQuadrant);
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
                rotationQuadrant);
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
                rotationQuadrant);
    }

    private static void requireNonNegativeFinite(double v, String field) {
        if (v < 0 || !Double.isFinite(v)) {
            throw new IllegalArgumentException(field + " must be finite and non-negative, was " + v);
        }
    }
}
