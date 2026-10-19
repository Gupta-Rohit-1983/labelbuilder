package com.rohit.labelbuilder.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.style.ErrorCorrection;
import com.rohit.labelbuilder.model.style.Symbology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 2D encoding through ZXing — native modules scaled to millimetres, never rasterised. */
class Barcode2DEncoderTest {

    private static final double MODULE_MM = 0.5;

    private static BarcodeGeometry encode(Symbology symbology, String value) {
        return Barcode2DEncoder.encode(BarcodeRequest.twoD(symbology, value, MODULE_MM, 0, ErrorCorrection.M));
    }

    @ParameterizedTest
    @EnumSource(
            value = Symbology.class,
            names = {"QR", "GS1_QR", "DATA_MATRIX", "GS1_DATA_MATRIX", "PDF417", "AZTEC"})
    void everyTwoDimensionalSymbologyEncodes(Symbology symbology) {
        BarcodeGeometry geometry = encode(symbology, "LABELBUILDER-12345");

        assertThat(geometry.barCount()).as("%s produced no modules", symbology).isPositive();
        assertThat(geometry.widthMm()).isPositive();
        assertThat(geometry.heightMm()).isPositive();
        assertThat(geometry.captions())
                .as("2D symbols carry no HRI of their own")
                .isEmpty();
    }

    @Test
    void aQrSymbolIsSquareAndSizedInWholeModules() {
        BarcodeGeometry geometry = encode(Symbology.QR, "hello");

        assertThat(geometry.widthMm()).isEqualTo(geometry.heightMm());
        // Native modules: the width must divide evenly by the module size.
        assertThat(geometry.widthMm() / MODULE_MM)
                .isCloseTo(Math.rint(geometry.widthMm() / MODULE_MM), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void theSmallestQrSymbolIsTwentyOneModulesAcross() {
        // Version 1 is 21x21. Getting anything else would mean ZXing had scaled the matrix for us,
        // which is exactly what asking for the native size is meant to prevent.
        BarcodeGeometry geometry = encode(Symbology.QR, "A");

        assertThat(geometry.widthMm() / MODULE_MM).isEqualTo(21.0);
    }

    @Test
    void moduleSizeScalesTheSymbolLinearly() {
        double small = encode(Symbology.QR, "hello").widthMm();
        double large = Barcode2DEncoder.encode(
                        BarcodeRequest.twoD(Symbology.QR, "hello", MODULE_MM * 4, 0, ErrorCorrection.M))
                .widthMm();

        assertThat(large).isCloseTo(small * 4, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void theQuietZoneIsConvertedFromMillimetresToModules() {
        double bare = encode(Symbology.QR, "hello").widthMm();
        // 2 mm at 0.5 mm per module = 4 modules on each side.
        double padded = Barcode2DEncoder.encode(
                        BarcodeRequest.twoD(Symbology.QR, "hello", MODULE_MM, 2.0, ErrorCorrection.M))
                .widthMm();

        assertThat(padded).isCloseTo(bare + 8 * MODULE_MM, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void strongerErrorCorrectionProducesALargerOrEqualSymbol() {
        double light = Barcode2DEncoder.encode(
                        BarcodeRequest.twoD(Symbology.QR, "LABELBUILDER-12345", MODULE_MM, 0, ErrorCorrection.L))
                .widthMm();
        double heavy = Barcode2DEncoder.encode(
                        BarcodeRequest.twoD(Symbology.QR, "LABELBUILDER-12345", MODULE_MM, 0, ErrorCorrection.H))
                .widthMm();

        assertThat(heavy).isGreaterThan(light);
    }

    @Test
    void adjacentModulesAreMergedIntoRuns() {
        BarcodeGeometry geometry = encode(Symbology.QR, "LABELBUILDER-12345");

        long darkModules = Math.round(geometry.bars().stream()
                .mapToDouble(bar -> bar.widthMm() / MODULE_MM)
                .sum());

        // Every dark module is still covered, but by far fewer rectangles than modules — the whole
        // point of merging runs before handing this to the renderer.
        assertThat(geometry.barCount()).isLessThan((int) darkModules);
        assertThat(darkModules).isPositive();
    }

    @Test
    void everyModuleRunStaysInsideTheSymbol() {
        BarcodeGeometry geometry = encode(Symbology.DATA_MATRIX, "LABELBUILDER");

        assertThat(geometry.bars()).allSatisfy(bar -> {
            assertThat(bar.xMm()).isGreaterThanOrEqualTo(0);
            assertThat(bar.xMm() + bar.widthMm()).isLessThanOrEqualTo(geometry.widthMm() + 1e-9);
            assertThat(bar.yMm() + bar.heightMm()).isLessThanOrEqualTo(geometry.heightMm() + 1e-9);
            assertThat(bar.heightMm()).isEqualTo(MODULE_MM); // one module tall
        });
    }

    @Test
    void anEmptyValueIsRejectedWithAClearMessage() {
        assertThatThrownBy(() -> encode(Symbology.QR, ""))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void aValueTooLargeForTheSymbologyIsReported() {
        String huge = "X".repeat(5000);

        assertThatThrownBy(() -> encode(Symbology.QR, huge))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("QR");
    }

    @Test
    void oneDimensionalSymbologiesArePointedAtTheOtherEncoder() {
        assertThatThrownBy(() -> encode(Symbology.CODE_128, "12345"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("1D");
        assertThat(Barcode2DEncoder.supports(Symbology.CODE_128)).isFalse();
        assertThat(Barcode2DEncoder.supports(Symbology.QR)).isTrue();
    }
}
