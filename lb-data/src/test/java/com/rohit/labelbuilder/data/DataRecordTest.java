package com.rohit.labelbuilder.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataRecordTest {

    @Test
    void fieldsAreReadableByName() {
        DataRecord record = MapRecord.of("Code", "ABC", "Qty", 7);

        assertThat(record.value("Code")).contains("ABC");
        assertThat(record.text("Qty")).isEqualTo("7");
    }

    @Test
    void fieldOrderIsPreservedBecauseItIsPartOfTheSchema() {
        DataRecord record = MapRecord.of("Zeta", 1, "Alpha", 2, "Mid", 3);

        assertThat(record.fieldNames()).containsExactly("Zeta", "Alpha", "Mid");
    }

    @Test
    void aMissingFieldRendersAsBlankRatherThanTheWordNull() {
        DataRecord record = MapRecord.of("Code", "ABC");

        // Printing "null" on a carton is worse than printing nothing.
        assertThat(record.text("Nope")).isEmpty();
        assertThat(record.value("Nope")).isEmpty();
    }

    @Test
    void aNullValueIsDistinguishableFromAMissingField() {
        Map<String, Object> values = new HashMap<>();
        values.put("Code", null);
        DataRecord record = new MapRecord(values);

        assertThat(record.has("Code")).isTrue(); // the column exists
        assertThat(record.value("Code")).isEmpty(); // but holds no value
        assertThat(record.has("Absent")).isFalse();
    }

    @Test
    void aRecordIsASnapshotAndDoesNotFollowItsSourceMap() {
        Map<String, Object> values = new HashMap<>();
        values.put("Code", "ORIGINAL");
        DataRecord record = new MapRecord(values);

        values.put("Code", "CHANGED"); // a cursor reusing its buffer would do exactly this

        assertThat(record.text("Code")).isEqualTo("ORIGINAL");
    }

    @Test
    void theBackingMapCannotBeMutatedThroughTheRecord() {
        DataRecord record = MapRecord.of("Code", "ABC");

        assertThatThrownBy(() -> record.fieldNames().add("Sneaky")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unpairedArgumentsAreRejected() {
        assertThatThrownBy(() -> MapRecord.of("Code", "ABC", "Dangling")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldDefinitionsCarryTheDeclaredLengthAndScale() {
        assertThat(FieldDefinition.text("Code", 20).length()).hasValue(20);
        assertThat(FieldDefinition.decimal("Price", 2).scale()).hasValue(2);
        assertThat(FieldDefinition.of("Flag", FieldType.BOOLEAN).length()).isEmpty();
    }

    @Test
    void aBlankFieldNameIsRejected() {
        assertThatThrownBy(() -> FieldDefinition.of("  ", FieldType.STRING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
