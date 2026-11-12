package com.rohit.labelbuilder.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The cursor state machine every file and JDBC source inherits (Phase 11b). */
class AbstractRecordSetTest {

    private static final List<FieldDefinition> FIELDS = List.of(FieldDefinition.text("Code", 10));

    /** A cursor over a queue, counting how often the subclass hooks are called. */
    private static final class QueueCursor extends AbstractRecordSet {

        private final Deque<DataRecord> rows = new ArrayDeque<>();
        private int advances;
        private int releases;

        QueueCursor(String... codes) {
            super(FIELDS);
            for (String code : codes) {
                rows.addLast(MapRecord.of("Code", code));
            }
        }

        @Override
        protected DataRecord advance() {
            advances++;
            return rows.pollFirst();
        }

        @Override
        protected void release() {
            releases++;
        }
    }

    @Test
    void theCursorWalksEveryRowOnce() {
        try (QueueCursor cursor = new QueueCursor("A", "B")) {
            assertThat(cursor.next()).isTrue();
            assertThat(cursor.current().text("Code")).isEqualTo("A");
            assertThat(cursor.next()).isTrue();
            assertThat(cursor.current().text("Code")).isEqualTo("B");
            assertThat(cursor.next()).isFalse();
        }
    }

    @Test
    void thereIsNoCurrentRowBeforeTheFirstNext() {
        try (QueueCursor cursor = new QueueCursor("A")) {
            assertThatThrownBy(cursor::current)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("next()");
        }
    }

    @Test
    void thereIsNoCurrentRowAfterExhaustion() {
        try (QueueCursor cursor = new QueueCursor("A")) {
            while (cursor.next()) {
                // drain
            }
            assertThatThrownBy(cursor::current).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void anExhaustedCursorIsNotAskedForMoreRows() {
        // A JDBC result set asked to advance past its end can throw rather than politely say no, so
        // the latch here is what keeps the subclass from having to defend itself.
        try (QueueCursor cursor = new QueueCursor("A")) {
            while (cursor.next()) {
                // drain
            }
            int advancesWhenExhausted = cursor.advances;

            assertThat(cursor.next()).isFalse();
            assertThat(cursor.next()).isFalse();
            assertThat(cursor.advances).isEqualTo(advancesWhenExhausted);
        }
    }

    @Test
    void aClosedCursorRefusesRatherThanReturningStaleRows() {
        QueueCursor cursor = new QueueCursor("A", "B");
        cursor.next();
        cursor.close();

        assertThatThrownBy(cursor::next).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(cursor::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theResourceIsReleasedExactlyOnceHoweverOftenCloseIsCalled() {
        QueueCursor cursor = new QueueCursor("A");

        cursor.close();
        cursor.close();
        cursor.close();

        assertThat(cursor.releases).isEqualTo(1);
    }

    @Test
    void aFailingReleaseStillLeavesTheCursorClosed() {
        AbstractRecordSet cursor = new AbstractRecordSet(FIELDS) {
            @Override
            protected DataRecord advance() {
                return null;
            }

            @Override
            protected void release() {
                throw new DataException("the socket was already gone");
            }
        };

        assertThatThrownBy(cursor::close).isInstanceOf(DataException.class);
        // Closing again must not retry a release that already failed.
        cursor.close();
        assertThatThrownBy(cursor::next).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSchemaIsImmutableHoweverTheSubclassWasConstructed() {
        try (QueueCursor cursor = new QueueCursor("A")) {
            assertThatThrownBy(() -> cursor.fields().add(FieldDefinition.text("Sneaky", 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void sizeIsUnknownUnlessTheSubclassSaysOtherwise() {
        try (QueueCursor cursor = new QueueCursor("A", "B")) {
            assertThat(cursor.size()).isEmpty();
        }
    }

    @Test
    void theForEachLoopFromTheRecordSetContractStillWorks() {
        try (QueueCursor cursor = new QueueCursor("A", "B")) {
            List<String> seen = new java.util.ArrayList<>();
            for (DataRecord record : cursor) {
                seen.add(record.text("Code"));
            }
            assertThat(seen).containsExactly("A", "B");
        }
    }
}
