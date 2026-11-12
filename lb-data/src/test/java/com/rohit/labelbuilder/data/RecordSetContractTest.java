package com.rohit.labelbuilder.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** The cursor contract every data source must honour (Phase 11a, risk R-06). */
class RecordSetContractTest {

    private static final List<FieldDefinition> FIELDS =
            List.of(FieldDefinition.text("Code", 20), FieldDefinition.decimal("Price", 2));

    private static InMemoryDataSource source(DataRecord... rows) {
        return new InMemoryDataSource("Product", FIELDS, List.of(rows));
    }

    private static DataRecord row(String code, Object price) {
        return MapRecord.of("Code", code, "Price", price);
    }

    // ---- cursor semantics ----

    @Test
    void theCursorWalksEveryRowOnceAndThenStops() {
        try (RecordSet records = source(row("A", 1), row("B", 2)).open()) {
            List<String> seen = new ArrayList<>();
            while (records.next()) {
                seen.add(records.current().text("Code"));
            }

            assertThat(seen).containsExactly("A", "B");
            assertThat(records.next()).isFalse(); // exhausted stays exhausted
        }
    }

    @Test
    void currentBeforeTheFirstNextIsRejected() {
        try (RecordSet records = source(row("A", 1)).open()) {
            assertThatThrownBy(records::current)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("next()");
        }
    }

    @Test
    void currentAfterExhaustionIsRejected() {
        try (RecordSet records = source(row("A", 1)).open()) {
            while (records.next()) {
                // drain
            }
            assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void anEmptySourceSimplyYieldsNothing() {
        try (RecordSet records = source().open()) {
            assertThat(records.next()).isFalse();
            assertThat(records.size()).hasValue(0);
        }
    }

    @Test
    void usingAClosedCursorIsRejectedRatherThanReturningStaleRows() {
        RecordSet records = source(row("A", 1)).open();
        records.close();

        assertThatThrownBy(records::next).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closingTwiceIsHarmless() {
        RecordSet records = source(row("A", 1)).open();
        records.close();

        assertThat(records).isNotNull();
        records.close();
    }

    // ---- for-each support ----

    @Test
    void aRecordSetWorksInAForEachLoop() {
        List<String> seen = new ArrayList<>();
        try (RecordSet records = source(row("A", 1), row("B", 2)).open()) {
            for (DataRecord record : records) {
                seen.add(record.text("Code"));
            }
        }
        assertThat(seen).containsExactly("A", "B");
    }

    @Test
    void iterationResumesRatherThanRestartingBecauseTheCursorIsForwardOnly() {
        try (RecordSet records = source(row("A", 1), row("B", 2)).open()) {
            records.iterator().next(); // consumes "A"

            List<String> rest = new ArrayList<>();
            records.forEach(record -> rest.add(record.text("Code")));

            assertThat(rest).containsExactly("B");
        }
    }

    @Test
    void anExhaustedIteratorThrowsRatherThanReturningNull() {
        try (RecordSet records = source().open()) {
            assertThatThrownBy(() -> records.iterator().next()).isInstanceOf(NoSuchElementException.class);
        }
    }

    // ---- size is honest about what it cannot know ----

    @Test
    void aSourceThatKnowsItsSizeReportsIt() {
        try (RecordSet records = source(row("A", 1), row("B", 2), row("C", 3)).open()) {
            assertThat(records.size()).hasValue(3);
        }
    }

    @Test
    void aStreamingSourceMayReportNoSizeAndCallersMustCope() {
        RecordSet streaming = new RecordSet() {
            private boolean served;

            @Override
            public List<FieldDefinition> fields() {
                return FIELDS;
            }

            @Override
            public boolean next() {
                boolean more = !served;
                served = true;
                return more;
            }

            @Override
            public DataRecord current() {
                return row("A", 1);
            }

            @Override
            public OptionalLong size() {
                return OptionalLong.empty(); // counting would mean fetching everything
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        try (streaming) {
            assertThat(streaming.size()).isEmpty();
            assertThat(streaming.next()).isTrue();
        }
    }

    // ---- opening twice ----

    @Test
    void eachOpenYieldsAFreshIndependentCursor() {
        InMemoryDataSource source = source(row("A", 1), row("B", 2));

        try (RecordSet first = source.open();
                RecordSet second = source.open()) {
            first.next();

            // The second cursor starts at the beginning: a print run re-reads rather than sharing.
            assertThat(second.next()).isTrue();
            assertThat(second.current().text("Code")).isEqualTo("A");
            assertThat(first.current().text("Code")).isEqualTo("A");
        }
    }

    @Test
    void theSchemaIsAvailableWithoutOpeningTheSource() {
        assertThat(source().fields()).extracting(FieldDefinition::name).containsExactly("Code", "Price");
    }
}
