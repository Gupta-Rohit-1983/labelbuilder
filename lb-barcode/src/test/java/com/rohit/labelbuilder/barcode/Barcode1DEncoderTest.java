package com.rohit.labelbuilder.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Symbology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 1D encoding through Barcode4J â€” headless, no toolkit, no rasterisation. */
class Barcode1DEncoderTest {

    private static BarcodeRequest request(Symbology symbology, String value) {
        return BarcodeRequest.oneD(
                symbology, value, 0.25, 12, 2.5, CheckDigitMode.AUTO, HriPosition.BELOW, 2.8, "Helvetica");
    }

    private static BarcodeGeometry encode(Symbology symbology, String value) {
        return Barcode1DEncoder.encode(request(symbology, value));
    }

    // ---- the symbologies the roadmap names for 10a ----

    @ParameterizedTest
    @CsvSource({
        "CODE_128, ABC-12345",
        "GS1_128, 0101234567890128",
        "CODE_39, ABC123",
        "EAN_13, 123456789012",
        "EAN_8, 1234567",
        "UPC_A, 12345678901",
        "UPC_E, 0123456",
        "ITF_14, 1234567890123",
        "INTERLEAVED_2_OF_5, 1234567890",
        // Barcode4J wants Codabar's start/stop characters in lowercase.
        "CODABAR, a12345b"
    })
    void everyRoadmapSymbologyEncodesToBars(Symbology symbology, String value) {
        BarcodeGeometry geometry = encode(symbology, value);

        assertThat(geometry.barCount()).as("%s produced no bars", symbology).isPositive();
        assertThat(geometry.widthMm()).isPositive();
        assertThat(geometry.heightMm()).isPositive();
    }

    @Test
    void barsStayInsideTheReportedFootprint() {
        BarcodeGeometry geometry = encode(Symbology.CODE_128, "LABELBUILDER");

        assertThat(geometry.bars()).allSatisfy(bar -> {
            assertThat(bar.xMm()).isGreaterThanOrEqualTo(0);
            assertThat(bar.xMm() + bar.widthMm()).isLessThanOrEqualTo(geometry.widthMm() + 1e-6);
            assertThat(bar.yMm() + bar.heightMm()).isLessThanOrEqualTo(geometry.heightMm() + 1e-6);
        });
    }

    // ---- the X-dimension actually drives the geometry ----

    @Test
    void aWiderModuleProducesAProportionallyWiderBarcode() {
        double narrow = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                        Symbology.CODE_128,
                        "12345678",
                        0.25,
                        12,
                        0,
                        CheckDigitMode.AUTO,
                        HriPosition.NONE,
                        0,
                        "Helvetica"))
                .widthMm();
        double wide = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                        Symbology.CODE_128,
                        "12345678",
                        0.50,
                        12,
                        0,
                        CheckDigitMode.AUTO,
                        HriPosition.NONE,
                        0,
                        "Helvetica"))
                .widthMm();

        assertThat(wide).isCloseTo(narrow * 2, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void theQuietZoneIsIncludedInTheFootprint() {
        double without = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                        Symbology.CODE_128,
                        "12345678",
                        0.25,
                        12,
                        0,
                        CheckDigitMode.AUTO,
                        HriPosition.NONE,
                        0,
                        "Helvetica"))
                .widthMm();
        double with = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                        Symbology.CODE_128,
                        "12345678",
                        0.25,
                        12,
                        5,
                        CheckDigitMode.AUTO,
                        HriPosition.NONE,
                        0,
                        "Helvetica"))
                .widthMm();

        // A quiet zone on both sides: the code is unscannable without it, so it is part of the size.
        assertThat(with).isCloseTo(without + 10, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void barHeightIsHonoured() {
        BarcodeGeometry tall = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                Symbology.CODE_128, "12345678", 0.25, 30, 0, CheckDigitMode.AUTO, HriPosition.NONE, 0, "Helvetica"));

        assertThat(tall.heightMm()).isCloseTo(30, org.assertj.core.data.Offset.offset(0.01));
    }

    // ---- human-readable text ----

    @Test
    void hriBelowProducesACaptionAndNoneProducesNothing() {
        assertThat(encode(Symbology.CODE_128, "12345678").captions()).isNotEmpty();

        BarcodeGeometry bare = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                Symbology.CODE_128, "12345678", 0.25, 12, 2.5, CheckDigitMode.AUTO, HriPosition.NONE, 0, "Helvetica"));
        assertThat(bare.captions()).isEmpty();
    }

    @Test
    void hriAddsHeightBeyondTheBars() {
        BarcodeGeometry withText = encode(Symbology.CODE_128, "12345678");
        BarcodeGeometry withoutText = Barcode1DEncoder.encode(BarcodeRequest.oneD(
                Symbology.CODE_128, "12345678", 0.25, 12, 2.5, CheckDigitMode.AUTO, HriPosition.NONE, 0, "Helvetica"));

        assertThat(withText.heightMm()).isGreaterThan(withoutText.heightMm());
    }

    // ---- validation ----

    @Test
    void lettersInANumericSymbologyAreRejectedWithTheValueAndSymbologyNamed() {
        assertThatThrownBy(() -> encode(Symbology.EAN_13, "ABCDEFGHIJKL"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("EAN_13")
                .hasMessageContaining("ABCDEFGHIJKL");
    }

    @Test
    void aWrongLengthValueIsRejected() {
        assertThatThrownBy(() -> encode(Symbology.EAN_13, "123"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("EAN_13");
    }

    @Test
    void aBadCheckDigitIsRejectedWhenTheValueClaimsToCarryOne() {
        // 123456789012_8_ is the correct check digit; 9 is not.
        BarcodeRequest claimed = BarcodeRequest.oneD(
                Symbology.EAN_13,
                "1234567890129",
                0.25,
                12,
                2.5,
                CheckDigitMode.PROVIDED,
                HriPosition.BELOW,
                2.8,
                "Helvetica");

        assertThatThrownBy(() -> Barcode1DEncoder.encode(claimed)).isInstanceOf(BarcodeException.class);
    }

    @Test
    void aCorrectCheckDigitIsAcceptedWhenProvided() {
        BarcodeRequest claimed = BarcodeRequest.oneD(
                Symbology.EAN_13,
                "1234567890128",
                0.25,
                12,
                2.5,
                CheckDigitMode.PROVIDED,
                HriPosition.BELOW,
                2.8,
                "Helvetica");

        assertThatCode(() -> Barcode1DEncoder.encode(claimed)).doesNotThrowAnyException();
    }

    // ---- what this build cannot do, stated plainly ----

    @Test
    void msiReportsThatThisBuildHasNoEncoderForIt() {
        assertThatThrownBy(() -> encode(Symbology.MSI))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("MSI");
        assertThat(Barcode1DEncoder.supports(Symbology.MSI)).isFalse();
    }

    @Test
    void twoDimensionalSymbologiesArePointedAtTheOtherEncoder() {
        assertThatThrownBy(() -> encode(Symbology.QR, "hello"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("2D");
        assertThat(Barcode1DEncoder.supports(Symbology.DATA_MATRIX)).isFalse();
    }

    @Test
    void supportsReportsTrueForTheSymbologiesWeEncode() {
        for (Symbology symbology : new Symbology[] {
            Symbology.CODE_128, Symbology.CODE_39, Symbology.EAN_13, Symbology.UPC_A, Symbology.CODABAR
        }) {
            assertThat(Barcode1DEncoder.supports(symbology)).as("%s", symbology).isTrue();
        }
    }

    private static BarcodeGeometry encode(Symbology symbology) {
        return encode(symbology, "12345678");
    }
}
