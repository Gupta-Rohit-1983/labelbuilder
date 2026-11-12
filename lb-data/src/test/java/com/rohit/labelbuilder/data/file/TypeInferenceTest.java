package com.rohit.labelbuilder.data.file;

import static com.rohit.labelbuilder.data.FieldType.BOOLEAN;
import static com.rohit.labelbuilder.data.FieldType.DATE;
import static com.rohit.labelbuilder.data.FieldType.DECIMAL;
import static com.rohit.labelbuilder.data.FieldType.INTEGER;
import static com.rohit.labelbuilder.data.FieldType.STRING;
import static com.rohit.labelbuilder.data.FieldType.TIME;
import static com.rohit.labelbuilder.data.FieldType.TIMESTAMP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeInferenceTest {

    @Test
    void wholeNumbersAreIntegers() {
        assertThat(TypeInference.inferType(List.of("1", "42", "-7", "0"))).isEqualTo(INTEGER);
    }

    @Test
    void aLeadingZeroKeepsTheColumnTextual() {
        // 0012 is an article number. Typing it INTEGER invites the next hop to print "12", and a
        // barcode built from a truncated code scans to the wrong product.
        assertThat(TypeInference.inferType(List.of("0012", "0013"))).isEqualTo(STRING);
    }

    @Test
    void aLoneZeroIsStillANumber() {
        assertThat(TypeInference.inferType(List.of("0", "5", "0"))).isEqualTo(INTEGER);
        assertThat(TypeInference.inferType(List.of("0.5", "1.25"))).isEqualTo(DECIMAL);
    }

    @Test
    void oneAndZeroAreQuantitiesNotFlags() {
        assertThat(TypeInference.inferType(List.of("1", "0", "1"))).isEqualTo(INTEGER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "FALSE", "Yes", "no", "Y", "n"})
    void booleanWordsAreRecognisedWhateverTheirCase(String word) {
        assertThat(TypeInference.inferType(List.of(word))).isEqualTo(BOOLEAN);
    }

    @Test
    void mixingIntegersAndDecimalsGivesDecimal() {
        assertThat(TypeInference.inferType(List.of("1", "2.50", "3"))).isEqualTo(DECIMAL);
    }

    @Test
    void isoTemporalsAreTyped() {
        assertThat(TypeInference.inferType(List.of("2026-10-26", "2026-01-01"))).isEqualTo(DATE);
        assertThat(TypeInference.inferType(List.of("08:30", "17:45:10"))).isEqualTo(TIME);
        assertThat(TypeInference.inferType(List.of("2026-10-26T08:30:00"))).isEqualTo(TIMESTAMP);
        assertThat(TypeInference.inferType(List.of("2026-10-26T08:30:00Z"))).isEqualTo(TIMESTAMP);
    }

    @Test
    void localeFormattedDatesStayTextBecauseTheyAreAmbiguous() {
        // 26/10/2026 and 10/26/2026 are the same day written by different countries; guessing which
        // convention a file follows is how a batch ends up dated five months out.
        assertThat(TypeInference.inferType(List.of("26/10/2026", "01/02/2026"))).isEqualTo(STRING);
    }

    @Test
    void thousandsSeparatorsStayText() {
        assertThat(TypeInference.inferType(List.of("1,234.50"))).isEqualTo(STRING);
    }

    @Test
    void aDigitStringTooLongForALongStaysText() {
        // An 18-digit SSCC or a 19-digit EPC is an identifier, not a measurement. Typing it numeric
        // invites a consumer to hold it in a double, and a shipping label carrying an SSCC rounded in
        // its last digits is scanned at the wrong destination.
        assertThat(TypeInference.inferType(List.of("1234567890123456789"))).isEqualTo(STRING);
        assertThat(TypeInference.inferType(List.of("123456789012345678"))).isEqualTo(INTEGER);
    }

    @Test
    void anOverlongNumberWithARealDecimalPointIsStillADecimal() {
        // The cap is about digit strings being identifiers; a value written with a fractional part is
        // plainly a measurement whatever its magnitude.
        assertThat(TypeInference.inferType(List.of("12345678901234567890.5"))).isEqualTo(DECIMAL);
    }

    @Test
    void blankSamplesAreIgnoredRatherThanForcingText() {
        assertThat(TypeInference.inferType(Arrays.asList("", "5", null, "  ", "6")))
                .isEqualTo(INTEGER);
    }

    @Test
    void aColumnWithNoValuesAtAllIsText() {
        assertThat(TypeInference.inferType(List.of())).isEqualTo(STRING);
        assertThat(TypeInference.inferType(List.of("", "   "))).isEqualTo(STRING);
    }

    @Test
    void oneBadValueDropsTheWholeColumnToText() {
        assertThat(TypeInference.inferType(List.of("1", "2", "n/a"))).isEqualTo(STRING);
    }

    @Test
    void temporalTypeNamesTheKindOfTemporalAValueIs() {
        assertThat(TypeInference.temporalType("2026-10-26")).contains(DATE);
        assertThat(TypeInference.temporalType("08:30")).contains(TIME);
        assertThat(TypeInference.temporalType("2026-10-26T08:30")).contains(TIMESTAMP);
        assertThat(TypeInference.temporalType("hello")).isEmpty();
        assertThat(TypeInference.temporalType("")).isEmpty();
    }

    // ---- reconcile: for sources that know each value's type rather than guessing ----

    @Test
    void reconcileKeepsASingleAgreedType() {
        assertThat(TypeInference.reconcile(List.of(INTEGER, INTEGER))).isEqualTo(INTEGER);
    }

    @Test
    void reconcileWidensRatherThanFallingBackWhereItCan() {
        assertThat(TypeInference.reconcile(List.of(INTEGER, DECIMAL))).isEqualTo(DECIMAL);
        assertThat(TypeInference.reconcile(List.of(DATE, TIMESTAMP))).isEqualTo(TIMESTAMP);
    }

    @Test
    void reconcileFallsBackToTextForGenuinelyMixedColumns() {
        assertThat(TypeInference.reconcile(List.of(INTEGER, STRING))).isEqualTo(STRING);
        assertThat(TypeInference.reconcile(List.of(BOOLEAN, DATE))).isEqualTo(STRING);
    }

    @Test
    void reconcileTreatsNoInformationAsText() {
        assertThat(TypeInference.reconcile(List.of())).isEqualTo(STRING);
        assertThat(TypeInference.reconcile(Arrays.asList(null, null))).isEqualTo(STRING);
    }

    @Test
    void reconcileIgnoresUnknownsAmongKnowns() {
        // A null is "this value said nothing", e.g. a JSON null or an Excel error cell.
        assertThat(TypeInference.reconcile(Arrays.asList(INTEGER, null, INTEGER)))
                .isEqualTo(INTEGER);
    }
}
