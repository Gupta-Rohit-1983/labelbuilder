package com.rohit.labelbuilder.data.bind;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.MapRecord;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueTemplateTest {

    private static final Map<String, DataRecord> RECORDS =
            Map.of("Product", MapRecord.of("Code", "A1", "Name", "Bolt", "price.net", "1.50"));

    private static String resolve(String value) {
        return ValueTemplate.parse(value).resolve(RECORDS);
    }

    @Test
    void aValueWithNoPlaceholdersIsLiteral() {
        ValueTemplate template = ValueTemplate.parse("Fragile — handle with care");

        assertThat(template.isLiteral()).isTrue();
        assertThat(template.references()).isEmpty();
        assertThat(template.resolve(RECORDS)).isEqualTo("Fragile — handle with care");
    }

    @Test
    void literalTextAndFieldsMix() {
        assertThat(resolve("SKU: ${Product.Code}")).isEqualTo("SKU: A1");
        assertThat(resolve("${Product.Code} — ${Product.Name}")).isEqualTo("A1 — Bolt");
        assertThat(resolve("${Product.Code}")).isEqualTo("A1");
    }

    @Test
    void aFieldNameMayContainDotsBecauseJsonAndXmlFlattenThatWay() {
        // ${Product.price.net} is the field "price.net" of "Product", not a source called
        // "Product.price" — the split is at the first dot.
        assertThat(ValueTemplate.parse("${Product.price.net}").references())
                .containsExactly(new FieldReference("Product", "price.net"));
        assertThat(resolve("Net: ${Product.price.net}")).isEqualTo("Net: 1.50");
    }

    @Test
    void theSameFieldCanBeUsedTwice() {
        assertThat(resolve("${Product.Code}/${Product.Code}")).isEqualTo("A1/A1");
    }

    @Test
    void referencesAreListedInTheOrderTheyAppear() {
        assertThat(ValueTemplate.parse("${Product.Name} (${Product.Code})").references())
                .containsExactly(new FieldReference("Product", "Name"), new FieldReference("Product", "Code"));
    }

    @Test
    void aFieldPresentButNullRendersBlank() {
        // Printing "null" on a carton is worse than printing nothing.
        Map<String, Object> values = new HashMap<>();
        values.put("Batch", null);
        Map<String, DataRecord> records = Map.of("Product", new MapRecord(values));

        assertThat(ValueTemplate.parse("Batch: ${Product.Batch}").resolve(records))
                .isEqualTo("Batch: ");
    }

    @Test
    void anUnknownSourceOrFieldLeavesThePlaceholderVisible() {
        // Visible on the label is what gets noticed before the run leaves the building; a silent blank
        // would produce an unscannable barcode nobody questions.
        assertThat(resolve("${Nope.Code}")).isEqualTo("${Nope.Code}");
        assertThat(resolve("${Product.Nope}")).isEqualTo("${Product.Nope}");
    }

    // ---- what belongs to the Phase 12 engine ----

    @ParameterizedTest
    @ValueSource(strings = {"UPPER(Product.Code)", "Product.Code + 1", "Serial", "Product.Code = 'A1'", "1 + 2"})
    void anythingThatIsNotAPlainFieldReferenceIsDeferredNotGuessedAt(String expression) {
        ValueTemplate template = ValueTemplate.parse("${" + expression + "}");

        assertThat(template.references()).isEmpty();
        assertThat(template.deferredExpressions()).containsExactly(expression);
        // Written back verbatim, so nothing is lost before the engine exists.
        assertThat(template.resolve(RECORDS)).isEqualTo("${" + expression + "}");
    }

    @Test
    void aBareNameWithNoDotIsAVariableNotAField() {
        // ${Serial} is a variable (lbl-format §5.1); inventing semantics for it now would have to be
        // unpicked when Phase 12 arrives.
        assertThat(ValueTemplate.parse("${Serial}").deferredExpressions()).containsExactly("Serial");
    }

    @Test
    void deferredAndResolvedPlaceholdersCoexist() {
        ValueTemplate template = ValueTemplate.parse("${Product.Code}-${Serial}");

        assertThat(template.references()).containsExactly(new FieldReference("Product", "Code"));
        assertThat(template.deferredExpressions()).containsExactly("Serial");
        assertThat(template.resolve(RECORDS)).isEqualTo("A1-${Serial}");
    }

    // ---- malformed input ----

    @Test
    void anUnterminatedPlaceholderIsLiteralSoAHalfTypedOneLooksLikeItself() {
        assertThat(resolve("SKU: ${Product.Code")).isEqualTo("SKU: ${Product.Code");
        assertThat(ValueTemplate.parse("${").isLiteral()).isTrue();
    }

    @Test
    void aLoneDollarIsJustADollar() {
        // No escape syntax is needed, which is why there is none.
        assertThat(resolve("Price: $5.00")).isEqualTo("Price: $5.00");
        assertThat(resolve("100% $ {Product.Code}")).isEqualTo("100% $ {Product.Code}");
    }

    @Test
    void anEmptyPlaceholderIsDeferredRatherThanCrashing() {
        assertThat(ValueTemplate.parse("${}").deferredExpressions()).containsExactly("");
        assertThat(resolve("${}")).isEqualTo("${}");
    }

    @Test
    void nullAndEmptyValuesParseToNothing() {
        assertThat(ValueTemplate.parse(null).segments()).isEmpty();
        assertThat(ValueTemplate.parse("").segments()).isEmpty();
        assertThat(ValueTemplate.parse(null).isLiteral()).isTrue();
        assertThat(ValueTemplate.parse(null).resolve(RECORDS)).isEmpty();
    }

    @Test
    void theOriginalTextCanBeRecovered() {
        // The template is the stored form; resolving must not be the only way back.
        String original = "SKU: ${Product.Code} / ${UPPER(x)} / $5";

        assertThat(ValueTemplate.parse(original).text()).isEqualTo(original);
    }

    @Test
    void parsingIsDoneOnceAndResolvedPerRecord() {
        // What makes a 10 000-label run cheap: the template is immutable and shared.
        ValueTemplate template = ValueTemplate.parse("${Product.Code}");

        assertThat(template.resolve(Map.of("Product", MapRecord.of("Code", "A1"))))
                .isEqualTo("A1");
        assertThat(template.resolve(Map.of("Product", MapRecord.of("Code", "A2"))))
                .isEqualTo("A2");
    }

    // ---- field reference parsing ----

    @Test
    void fieldReferencesRoundTripThroughTheirPlaceholder() {
        FieldReference reference = new FieldReference("Product", "Code");

        assertThat(reference.placeholder()).isEqualTo("${Product.Code}");
        assertThat(reference.qualifiedName()).isEqualTo("Product.Code");
        assertThat(FieldReference.parse("Product.Code")).contains(reference);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Product", ".Code", "Product.", "9Product.Code", "Product..Code", "Pro duct.Code", ""})
    void malformedReferencesAreNotReferences(String text) {
        assertThat(FieldReference.parse(text)).isEmpty();
    }

    @Test
    void surroundingSpacesInsideAPlaceholderAreIgnored() {
        assertThat(resolve("${ Product.Code }")).isEqualTo("A1");
    }
}
