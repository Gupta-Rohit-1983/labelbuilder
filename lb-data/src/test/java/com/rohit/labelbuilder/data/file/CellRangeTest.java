package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CellRangeTest {

    @Test
    void parsesAClosedBoxIntoZeroBasedIndices() {
        CellRange range = CellRange.parse("B2:D10");

        assertThat(range.firstRow()).isEqualTo(1);
        assertThat(range.firstColumn()).isEqualTo(1);
        assertThat(range.lastRow()).hasValue(9);
        assertThat(range.lastColumn()).hasValue(3);
    }

    @Test
    void oneCellMeansOpenEnded() {
        // "My data starts at B3" must not pin the range to today's row count.
        CellRange range = CellRange.parse("B3");

        assertThat(range.firstRow()).isEqualTo(2);
        assertThat(range.firstColumn()).isEqualTo(1);
        assertThat(range.lastRow()).isEmpty();
        assertThat(range.lastColumn()).isEmpty();
    }

    @Test
    void wholeColumnsLeaveTheRowsOpen() {
        CellRange range = CellRange.parse("B:D");

        assertThat(range.firstRow()).isZero();
        assertThat(range.firstColumn()).isEqualTo(1);
        assertThat(range.lastRow()).isEmpty();
        assertThat(range.lastColumn()).hasValue(3);
    }

    @Test
    void wholeRowsLeaveTheColumnsOpen() {
        CellRange range = CellRange.parse("2:10");

        assertThat(range.firstRow()).isEqualTo(1);
        assertThat(range.firstColumn()).isZero();
        assertThat(range.lastRow()).hasValue(9);
        assertThat(range.lastColumn()).isEmpty();
    }

    @Test
    void aReversedRangeIsTheSameBox() {
        // A spreadsheet selection comes out either way round depending on the drag direction.
        assertThat(CellRange.parse("D10:B2")).isEqualTo(CellRange.parse("B2:D10"));
    }

    @Test
    void absoluteReferencesAreAccepted() {
        assertThat(CellRange.parse("$B$2:$D$10")).isEqualTo(CellRange.parse("B2:D10"));
    }

    @Test
    void lowerCaseReferencesAreAccepted() {
        assertThat(CellRange.parse("b2:d10")).isEqualTo(CellRange.parse("B2:D10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2B", "B2:C3:D4", "   ", "B2:", "AB!2", "B0"})
    void nonsenseIsRejectedRatherThanGuessedAt(String text) {
        assertThatThrownBy(() -> CellRange.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTwoEndsMustBeTheSameKindOfReference() {
        assertThatThrownBy(() -> CellRange.parse("B2:D")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containmentRespectsAnOpenEnd() {
        CellRange range = CellRange.parse("B2");

        assertThat(range.containsRow(1)).isTrue();
        assertThat(range.containsRow(0)).isFalse();
        assertThat(range.containsRow(999_999)).isTrue();
        assertThat(range.containsColumn(1)).isTrue();
        assertThat(range.containsColumn(0)).isFalse();
    }

    @Test
    void anOpenEndedRangeTakesTheSheetsLastRow() {
        assertThat(CellRange.parse("A1").effectiveLastRow(42)).isEqualTo(42);
    }

    @Test
    void aClosedRangeIsStillClampedToTheSheet() {
        // A range of A1:A1000 over a 50-row sheet must not produce 950 blank labels.
        assertThat(CellRange.parse("A1:A1000").effectiveLastRow(49)).isEqualTo(49);
        assertThat(CellRange.parse("A1:A10").effectiveLastRow(49)).isEqualTo(9);
    }

    @Test
    void columnLettersAndIndicesAreInverses() {
        assertThat(CellRange.columnIndex("A")).isZero();
        assertThat(CellRange.columnIndex("Z")).isEqualTo(25);
        assertThat(CellRange.columnIndex("AA")).isEqualTo(26);
        assertThat(CellRange.columnIndex("AZ")).isEqualTo(51);
        assertThat(CellRange.columnIndex("BA")).isEqualTo(52);

        for (int index = 0; index < 1000; index++) {
            assertThat(CellRange.columnIndex(CellRange.columnLetters(index)))
                    .as("round trip of column %d", index)
                    .isEqualTo(index);
        }
    }

    @Test
    void a1NotationRoundTrips() {
        assertThat(CellRange.parse("B2:D10").toA1()).isEqualTo("B2:D10");
        assertThat(CellRange.parse("B3").toA1()).isEqualTo("B3");
        assertThat(CellRange.ALL.toA1()).isEqualTo("A1");
    }

    @Test
    void anEndBeforeTheStartIsRejectedWhenConstructedDirectly() {
        assertThatThrownBy(() -> new CellRange(5, 0, java.util.OptionalInt.of(2), java.util.OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
