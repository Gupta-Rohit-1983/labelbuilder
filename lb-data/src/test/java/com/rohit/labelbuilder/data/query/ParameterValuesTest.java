package com.rohit.labelbuilder.data.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParameterValuesTest {

    @Test
    void textFromAFormBecomesANumberForANumericParameter() {
        // Bound as text, "qty >= '5'" compares as a string, so "10" < "5" and the run quietly selects
        // the wrong products — no exception, no empty result, just the wrong labels.
        Object coerced = ParameterValues.coerce(FieldType.INTEGER, "5", "minQty");

        assertThat(coerced).isEqualTo(5L).isInstanceOf(Long.class);
    }

    @Test
    void aDecimalKeepsItsScaleThroughCoercion() {
        assertThat(ParameterValues.coerce(FieldType.DECIMAL, "1.50", "price")).isEqualTo(new BigDecimal("1.50"));
    }

    @Test
    void aNumberSuppliedAsANumberIsNotRoundTrippedThroughADouble() {
        assertThat(ParameterValues.coerce(FieldType.DECIMAL, new BigDecimal("1.50"), "price"))
                .isEqualTo(new BigDecimal("1.50"));
        assertThat(ParameterValues.coerce(FieldType.INTEGER, 7, "qty")).isEqualTo(7L);
    }

    @ParameterizedTest
    @CsvSource({
        "true,true",
        "TRUE,true",
        "yes,true",
        "Y,true",
        "1,true",
        "on,true",
        "false,false",
        "no,false",
        "N,false",
        "0,false",
        "off,false"
    })
    void yesNoValuesFromAFormAreUnderstood(String supplied, boolean expected) {
        assertThat(ParameterValues.coerce(FieldType.BOOLEAN, supplied, "active"))
                .isEqualTo(expected);
    }

    @Test
    void temporalParametersAreParsedToJavaTime() {
        assertThat(ParameterValues.coerce(FieldType.DATE, "2026-10-26", "due")).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(ParameterValues.coerce(FieldType.TIME, "08:30", "at")).isEqualTo(LocalTime.of(8, 30));
        assertThat(ParameterValues.coerce(FieldType.TIMESTAMP, "2026-10-26T08:30", "made"))
                .isEqualTo(LocalDateTime.of(2026, 10, 26, 8, 30));
    }

    @Test
    void surroundingWhitespaceFromATypedFieldIsIgnored() {
        assertThat(ParameterValues.coerce(FieldType.INTEGER, "  5  ", "qty")).isEqualTo(5L);
    }

    @Test
    void aValueThatCannotBeConvertedIsReportedRatherThanGuessedAt() {
        // The operator has to hear about it while they can still retype it.
        assertThatThrownBy(() -> ParameterValues.coerce(FieldType.INTEGER, "five", "minQty"))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("minQty")
                .hasMessageContaining("integer");
        assertThatThrownBy(() -> ParameterValues.coerce(FieldType.DATE, "32/13/2026", "due"))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("due");
        assertThatThrownBy(() -> ParameterValues.coerce(FieldType.BOOLEAN, "maybe", "active"))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("yes/no");
    }

    @Test
    void anythingCanBeAStringParameter() {
        assertThat(ParameterValues.coerce(FieldType.STRING, 5, "code")).isEqualTo("5");
        assertThat(ParameterValues.coerce(FieldType.STRING, "0012", "code")).isEqualTo("0012");
    }

    @Test
    void aNullValueStaysNull() {
        assertThat(ParameterValues.coerce(FieldType.INTEGER, null, "qty")).isNull();
    }

    // ---- resolving a whole parameter list ----

    @Test
    void suppliedValuesAreConvertedToTheirDeclaredTypes() {
        List<QueryParameter> declared = List.of(
                QueryParameter.required("minQty", FieldType.INTEGER), QueryParameter.required("sku", FieldType.STRING));

        Map<String, Object> resolved = ParameterValues.resolve(declared, Map.of("minQty", "5", "sku", "A1"));

        assertThat(resolved).containsExactly(entry("minQty", 5L), entry("sku", "A1"));
    }

    @Test
    void aMissingOptionalParameterFallsBackToItsDefault() {
        List<QueryParameter> declared = List.of(QueryParameter.withDefault("minQty", FieldType.INTEGER, 1L));

        assertThat(ParameterValues.resolve(declared, Map.of())).containsExactly(entry("minQty", 1L));
    }

    @Test
    void anUntouchedFormFieldCountsAsNotSuppliedSoTheDefaultIsReachable() {
        // A blank text field hands over "", which must not shadow the default.
        List<QueryParameter> declared = List.of(QueryParameter.withDefault("sku", FieldType.STRING, "A1"));

        assertThat(ParameterValues.resolve(declared, Map.of("sku", "   "))).containsExactly(entry("sku", "A1"));
    }

    @Test
    void aMissingRequiredParameterNamesWhatIsNeeded() {
        List<QueryParameter> declared = List.of(
                QueryParameter.required("minQty", FieldType.INTEGER), QueryParameter.required("sku", FieldType.STRING));

        assertThatThrownBy(() -> ParameterValues.resolve(declared, Map.of("sku", "A1")))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("minQty");
    }

    @Test
    void allMissingParametersAreReportedTogether() {
        // One round trip for the operator, not one error per attempt.
        List<QueryParameter> declared =
                List.of(QueryParameter.required("a", FieldType.STRING), QueryParameter.required("b", FieldType.STRING));

        assertThatThrownBy(() -> ParameterValues.resolve(declared, Map.of()))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b");
    }

    @Test
    void valuesForParametersTheQueryDoesNotDeclareAreIgnored() {
        // A shared prompt form may feed several queries, each taking what it needs.
        List<QueryParameter> declared = List.of(QueryParameter.required("sku", FieldType.STRING));

        assertThat(ParameterValues.resolve(declared, Map.of("sku", "A1", "unrelated", "x")))
                .containsExactly(entry("sku", "A1"));
    }

    @Test
    void aParameterWithNoDefaultIsRequiredByDefinition() {
        assertThat(QueryParameter.required("sku", FieldType.STRING).isRequired())
                .isTrue();
        assertThat(QueryParameter.withDefault("sku", FieldType.STRING, "A1").isRequired())
                .isFalse();
    }

    @Test
    void aParameterRemembersWhereItsValueComesFrom() {
        // Opaque until the expression engine arrives in Phase 12.
        QueryParameter parameter =
                QueryParameter.required("sku", FieldType.STRING).boundTo("${Prompt.SKU}");

        assertThat(parameter.boundTo()).contains("${Prompt.SKU}");
    }
}
