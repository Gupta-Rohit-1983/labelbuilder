package com.rohit.labelbuilder.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.ErrorCorrection;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Symbology;
import org.junit.jupiter.api.Test;

/** Check digits, validation and auto-sizing (Phase 10c). */
class BarcodeValidationTest {

    /** A healthy Code 128: generous X-dimension, quiet zone and bar height. */
    private static BarcodeRequest healthy() {
        return BarcodeRequest.oneD(
                Symbology.CODE_128,
                "12345678",
                0.33,
                12,
                3.3,
                CheckDigitMode.AUTO,
                HriPosition.BELOW,
                2.8,
                "Helvetica");
    }

    // ---- check digits ----

    @Test
    void modulo10MatchesTheKnownEanCheckDigit() {
        // 123456789012 -> 8 is the textbook EAN-13 example.
        assertThat(CheckDigits.modulo10("123456789012")).isEqualTo(8);
    }

    @Test
    void modulo10ValidatesAFullValue() {
        assertThat(CheckDigits.hasValidModulo10("1234567890128")).isTrue();
        assertThat(CheckDigits.hasValidModulo10("1234567890129")).isFalse();
    }

    @Test
    void modulo10RejectsNonNumericInput() {
        assertThatThrownBy(() -> CheckDigits.modulo10("12A456"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("digits only");
    }

    @Test
    void modulo43ComputesTheCode39CheckCharacter() {
        // Alphabet indices summed, mod 43, indexed back into the alphabet
        // ("0..9" = 0..9, "A..Z" = 10..35).
        assertThat(CheckDigits.modulo43("0")).isEqualTo('0'); // 0 -> '0'
        assertThat(CheckDigits.modulo43("A")).isEqualTo('A'); // 10 -> 'A'
        assertThat(CheckDigits.modulo43("Z")).isEqualTo('Z'); // 35 -> 'Z'
        assertThat(CheckDigits.modulo43("AA")).isEqualTo('K'); // 20 -> 'K'
        assertThat(CheckDigits.modulo43("ZZ")).isEqualTo('R'); // 35+35 = 70, 70 % 43 = 27 -> 'R'
    }

    @Test
    void modulo43RejectsCharactersCode39CannotEncode() {
        assertThatThrownBy(() -> CheckDigits.modulo43("lower"))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("Code 39");
    }

    // ---- validation ----

    @Test
    void aWellFormedBarcodeReportsNothing() {
        assertThat(BarcodeValidator.validate(healthy()).isClean()).isTrue();
    }

    @Test
    void anEmptyValueIsAnErrorAndStopsFurtherAdvice() {
        ValidationReport report = BarcodeValidator.validate(healthy().withModuleWidthMm(0.33));
        assertThat(report.isClean()).isTrue();

        ValidationReport empty = BarcodeValidator.validate(BarcodeRequest.oneD(
                Symbology.CODE_128, "", 0.33, 12, 3.3, CheckDigitMode.AUTO, HriPosition.BELOW, 2.8, "Helvetica"));

        assertThat(empty.hasErrors()).isTrue();
        assertThat(empty.errors()).hasSize(1).first().asString().contains("empty");
    }

    @Test
    void anUnencodableValueIsAnErrorNotAWarning() {
        ValidationReport report = BarcodeValidator.validate(BarcodeRequest.oneD(
                Symbology.EAN_13,
                "NOTDIGITS",
                0.33,
                12,
                3.3,
                CheckDigitMode.AUTO,
                HriPosition.BELOW,
                2.8,
                "Helvetica"));

        assertThat(report.hasErrors()).isTrue();
    }

    @Test
    void tooNarrowAnXDimensionIsAWarningBecauseItStillEncodes() {
        ValidationReport report = BarcodeValidator.validate(healthy().withModuleWidthMm(0.1));

        assertThat(report.hasErrors()).isFalse();
        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("X-dimension"));
    }

    @Test
    void tooTightAQuietZoneIsWarnedAboutWithTheRequirementNamed() {
        ValidationReport report = BarcodeValidator.validate(healthy().withQuietZoneMm(0.2));

        assertThat(report.warnings())
                .anySatisfy(w -> assertThat(w).contains("quiet zone").contains("10"));
    }

    @Test
    void theQuietZoneRequirementScalesWithTheModuleSize() {
        // 2 mm is ample at a 0.1 mm module (needs 1 mm) but far too tight at 0.5 mm (needs 5 mm).
        assertThat(BarcodeValidator.validate(healthy().withModuleWidthMm(0.2).withQuietZoneMm(2.0))
                        .warnings())
                .noneSatisfy(w -> assertThat(w).contains("quiet zone"));
        assertThat(BarcodeValidator.validate(healthy().withModuleWidthMm(0.5).withQuietZoneMm(2.0))
                        .warnings())
                .anySatisfy(w -> assertThat(w).contains("quiet zone"));
    }

    @Test
    void aShortSymbolIsWarnedAbout() {
        ValidationReport report = BarcodeValidator.validate(healthy().withBarHeightMm(2));

        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("bar height"));
    }

    @Test
    void matrixSymbologiesAreJudgedOnModuleSizeNotBarHeight() {
        ValidationReport report =
                BarcodeValidator.validate(BarcodeRequest.twoD(Symbology.QR, "hello", 0.1, 2.0, ErrorCorrection.M));

        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("module size"));
        assertThat(report.warnings()).noneSatisfy(w -> assertThat(w).contains("bar height"));
    }

    @Test
    void aztecNeedsNoQuietZone() {
        ValidationReport report =
                BarcodeValidator.validate(BarcodeRequest.twoD(Symbology.AZTEC, "hello", 0.5, 0, ErrorCorrection.M));

        assertThat(report.warnings()).noneSatisfy(w -> assertThat(w).contains("quiet zone"));
    }

    // ---- auto-sizing ----

    @Test
    void aLinearBarcodeIsSizedToFillTheBoxExactly() {
        BarcodeRequest fitted = BarcodeAutoSizer.fitTo(healthy(), 60, 20);
        BarcodeGeometry geometry = Barcode1DEncoder.encode(fitted);

        assertThat(geometry.widthMm()).isCloseTo(60, org.assertj.core.data.Offset.offset(0.01));
        assertThat(geometry.heightMm()).isCloseTo(20, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void autoSizingPreservesTheQuietZoneRatherThanScalingIt() {
        BarcodeRequest fitted = BarcodeAutoSizer.fitTo(healthy(), 60, 20);

        assertThat(fitted.quietZoneMm()).isEqualTo(healthy().quietZoneMm());
    }

    @Test
    void aMatrixSymbolIsFittedToTheSmallerAxisAndStaysWithinTheBox() {
        BarcodeRequest request = BarcodeRequest.twoD(Symbology.QR, "LABELBUILDER", 0.5, 1.0, ErrorCorrection.M);

        BarcodeRequest fitted = BarcodeAutoSizer.fitTo(request, 40, 25);
        BarcodeGeometry geometry = Barcode2DEncoder.encode(fitted);

        assertThat(geometry.widthMm()).isLessThanOrEqualTo(40.0);
        assertThat(geometry.heightMm()).isLessThanOrEqualTo(25.0);
        // Fitted to the tighter axis, so it should nearly fill it.
        assertThat(geometry.heightMm()).isGreaterThan(20.0);
    }

    @Test
    void aBoxTooSmallForTheQuietZoneIsRejectedClearly() {
        assertThatThrownBy(() -> BarcodeAutoSizer.fitTo(healthy(), 5, 20))
                .isInstanceOf(BarcodeException.class)
                .hasMessageContaining("quiet zone");
    }

    @Test
    void aNonPositiveTargetIsRejected() {
        assertThatThrownBy(() -> BarcodeAutoSizer.fitTo(healthy(), 0, 20)).isInstanceOf(BarcodeException.class);
    }
}
