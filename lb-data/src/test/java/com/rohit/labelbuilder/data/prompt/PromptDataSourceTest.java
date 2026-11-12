package com.rohit.labelbuilder.data.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.RecordSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptDataSourceTest {

    private static DataRecord answer(PromptDataSource source, Map<String, Object> answers) {
        try (RecordSet records = source.open(answers)) {
            assertThat(records.next()).isTrue();
            DataRecord record = records.current();
            assertThat(records.next()).as("a form yields exactly one record").isFalse();
            return record;
        }
    }

    @Test
    void theFormYieldsExactlyOneRecord() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("SKU", FieldType.STRING));

        assertThat(answer(source, Map.of("SKU", "A1")).text("SKU")).isEqualTo("A1");
    }

    @Test
    void itIsAnOrdinaryDataSourceSoALabelBindsToItTheSameWay() {
        // ${Prompt.SKU} must work exactly like ${Product.Code}.
        PromptDataSource source = new PromptDataSource(
                "Prompt",
                PromptField.required("SKU", FieldType.STRING),
                PromptField.optional("Qty", FieldType.INTEGER));

        assertThat(source.name()).isEqualTo("Prompt");
        assertThat(source.fields())
                .extracting(FieldDefinition::name, FieldDefinition::type)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("SKU", FieldType.STRING),
                        org.assertj.core.api.Assertions.tuple("Qty", FieldType.INTEGER));
    }

    @Test
    void aRequiredFieldIsNullableOnlyWhenItIsOptional() {
        assertThat(PromptField.required("SKU", FieldType.STRING)
                        .toFieldDefinition()
                        .nullable())
                .isFalse();
        assertThat(PromptField.optional("Qty", FieldType.INTEGER)
                        .toFieldDefinition()
                        .nullable())
                .isTrue();
    }

    @Test
    void answersAreConvertedToTheDeclaredType() {
        // A form hands over text; a label bound to a numeric prompt should get a number.
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("Qty", FieldType.INTEGER));

        assertThat(answer(source, Map.of("Qty", "7")).value("Qty")).contains(7L);
    }

    @Test
    void aDefaultIsUsedWhenTheFieldIsLeftBlank() {
        PromptDataSource source = new PromptDataSource(
                "Prompt", PromptField.optional("Qty", FieldType.INTEGER).withDefault("1"));

        assertThat(answer(source, Map.of()).value("Qty")).contains(1L);
        assertThat(answer(source, Map.of("Qty", "   ")).value("Qty")).contains(1L);
    }

    @Test
    void anOptionalFieldLeftEmptyIsPresentButHasNoValue() {
        // A print condition can still ask whether it was filled in.
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.optional("Batch", FieldType.STRING));

        DataRecord record = answer(source, Map.of());

        assertThat(record.has("Batch")).isTrue();
        assertThat(record.value("Batch")).isEmpty();
        assertThat(record.text("Batch")).isEmpty();
    }

    // ---- validation: the last check before several hundred labels are printed ----

    @Test
    void aRequiredFieldLeftBlankStopsTheRun() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("SKU", FieldType.STRING));

        assertThatThrownBy(() -> source.open(Map.of()))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("'SKU' is required");
    }

    @Test
    void anAnswerMustMatchItsPattern() {
        PromptDataSource source = new PromptDataSource(
                "Prompt", PromptField.required("SKU", FieldType.STRING).withValidation("^[A-Z0-9-]{4,20}$"));

        assertThat(source.validate(Map.of("SKU", "AB-1234"))).isEmpty();
        assertThat(source.validate(Map.of("SKU", "ab")))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("format");
    }

    @Test
    void thePatternMustMatchInFullSoAPartialCodeIsRejected() {
        PromptDataSource source = new PromptDataSource(
                "Prompt", PromptField.required("SKU", FieldType.STRING).withValidation("[A-Z]{3}"));

        assertThat(source.validate(Map.of("SKU", "ABC"))).isEmpty();
        assertThat(source.validate(Map.of("SKU", "ABCD"))).hasSize(1);
    }

    @Test
    void aPickListAnswerMustBeOnTheList() {
        PromptDataSource source = new PromptDataSource(
                "Prompt", PromptField.required("Line", FieldType.STRING).withPickList("L1", "L2", "L3"));

        assertThat(source.validate(Map.of("Line", "L2"))).isEmpty();
        assertThat(source.validate(Map.of("Line", "L9")))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("L1, L2, L3");
    }

    @Test
    void aValueOfTheWrongTypeIsRejectedBeforeAnythingPrints() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("Qty", FieldType.INTEGER));

        assertThat(source.validate(Map.of("Qty", "five")))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("Qty");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        // An operator at a terminal should see everything wrong with the form, not one field per
        // attempt.
        PromptDataSource source = new PromptDataSource(
                "Prompt",
                PromptField.required("SKU", FieldType.STRING),
                PromptField.required("Qty", FieldType.INTEGER),
                PromptField.required("Line", FieldType.STRING).withPickList("L1", "L2"));

        assertThat(source.validate(Map.of("Qty", "five", "Line", "L9"))).hasSize(3);
        assertThatThrownBy(() -> source.open(Map.of("Qty", "five", "Line", "L9")))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("SKU")
                .hasMessageContaining("Qty")
                .hasMessageContaining("Line");
    }

    @Test
    void validateNeverThrowsSoAFormCanCheckAsItIsTyped() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("Qty", FieldType.INTEGER));

        assertThat(source.validate(Map.of())).isNotEmpty();
        assertThat(source.validate(Map.of("Qty", "nope"))).isNotEmpty();
        assertThat(source.validate(Map.of("Qty", "1"))).isEmpty();
    }

    @Test
    void answersForFieldsTheFormDoesNotHaveAreIgnored() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("SKU", FieldType.STRING));

        DataRecord record = answer(source, Map.of("SKU", "A1", "Unrelated", "x"));

        assertThat(record.fieldNames()).containsExactly("SKU");
    }

    @Test
    void eachOpenAsksAgainRatherThanReplayingTheLastAnswers() {
        PromptDataSource source = new PromptDataSource("Prompt", PromptField.required("SKU", FieldType.STRING));

        assertThat(answer(source, Map.of("SKU", "A1")).text("SKU")).isEqualTo("A1");
        assertThat(answer(source, Map.of("SKU", "A2")).text("SKU")).isEqualTo("A2");
    }

    // ---- design-time mistakes are caught at design time ----

    @Test
    void aBrokenValidationPatternIsRefusedWhenTheFieldIsDefined() {
        // Otherwise it would fail every answer at print time with no way to tell why.
        assertThatThrownBy(() -> PromptField.required("SKU", FieldType.STRING).withValidation("[unclosed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regular expression");
    }

    @Test
    void aDefaultOutsideItsOwnPickListIsRefused() {
        assertThatThrownBy(() -> PromptField.required("Line", FieldType.STRING)
                        .withPickList("L1", "L2")
                        .withDefault("L9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pick-list");
    }

    @Test
    void duplicateFieldNamesAreRefused() {
        assertThatThrownBy(() -> new PromptDataSource(
                        "Prompt",
                        PromptField.required("SKU", FieldType.STRING),
                        PromptField.optional("SKU", FieldType.STRING)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void aBlankFieldNameIsRefused() {
        assertThatThrownBy(() -> PromptField.required("  ", FieldType.STRING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPickListFieldKnowsItShouldBeADropdown() {
        assertThat(PromptField.required("Line", FieldType.STRING)
                        .withPickList("L1")
                        .hasPickList())
                .isTrue();
        assertThat(PromptField.required("SKU", FieldType.STRING).hasPickList()).isFalse();
    }
}
