package com.rohit.labelbuilder.model.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Quadrant;
import com.rohit.labelbuilder.model.style.Stroke;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The per-type property pages added in Phase 9c. */
class TypePropertyPagesTest {

    private static ElementProperties props(String id) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(0, 0, 40, 15));
    }

    private static BarcodeElement barcode() {
        return new BarcodeElement(
                props("b1"),
                Symbology.CODE_128,
                "12345678",
                0.25,
                12,
                2.5,
                CheckDigitMode.AUTO,
                Hri.below(FontSpec.of("Arial", 8)),
                0);
    }

    private static Object get(LabelElement element, String key) {
        return ElementSchemas.schemaFor(element).get(element, key);
    }

    private static LabelElement set(LabelElement element, String key, Object value) {
        return ElementSchemas.schemaFor(element).set(element, key, value);
    }

    // ---- barcode rotation, presented as quadrants ----

    @Test
    void barcodeRotationReadsAsAQuadrantThoughTheModelStoresDegrees() {
        assertThat(get(barcode(), "rotationQuadrant")).isEqualTo(Quadrant.DEG_0);
    }

    @Test
    void settingTheQuadrantWritesDegreesBackToTheModel() {
        BarcodeElement turned = (BarcodeElement) set(barcode(), "rotationQuadrant", Quadrant.DEG_270);

        assertThat(turned.rotationQuadrant()).isEqualTo(270);
        assertThat(get(turned, "rotationQuadrant")).isEqualTo(Quadrant.DEG_270);
    }

    @Test
    void quadrantOffersExactlyTheFourLegalRotations() {
        PropertyDescriptor descriptor =
                ElementSchemas.schemaFor(barcode()).property("rotationQuadrant").orElseThrow();

        assertThat(descriptor.enumConstants()).containsExactly((Object[]) Quadrant.values());
        assertThat(Quadrant.fromDegrees(180)).isEqualTo(Quadrant.DEG_180);
        assertThatThrownBy(() -> Quadrant.fromDegrees(45)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- HRI page ----

    @Test
    void hriPropertiesAreGroupedOnTheirOwnPage() {
        List<String> hriKeys = ElementSchemas.schemaFor(barcode()).byCategory().get("Human-readable text").stream()
                .map(PropertyDescriptor::key)
                .toList();

        assertThat(hriKeys).containsExactly("hriPosition", "hriFont", "hriShowCheckDigit", "hriCustomText");
    }

    @Test
    void editingOneHriFieldLeavesTheRestOfTheBlockIntact() {
        BarcodeElement edited = (BarcodeElement) set(barcode(), "hriPosition", HriPosition.ABOVE);

        assertThat(edited.hri().position()).isEqualTo(HriPosition.ABOVE);
        assertThat(edited.hri().showCheckDigit()).isTrue(); // untouched
        assertThat(edited.hri().font()).isEqualTo(FontSpec.of("Arial", 8));
        assertThat(edited.value()).isEqualTo("12345678"); // and the barcode itself is untouched
    }

    @Test
    void anEmptiedCustomTextBecomesNullNotAnEmptyString() {
        BarcodeElement withText = (BarcodeElement) set(barcode(), "hriCustomText", "OVERRIDE");
        assertThat(withText.hri().customText()).isEqualTo("OVERRIDE");

        BarcodeElement cleared = (BarcodeElement) set(withText, "hriCustomText", "   ");
        assertThat(cleared.hri().customText()).isNull();
    }

    // ---- print condition, on every element ----

    @Test
    void printConditionIsEditableOnEveryElementType() {
        for (Class<?> type : LabelElement.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            ElementSchema schema = ElementSchemas.schemaFor((Class<? extends LabelElement>) type);
            assertThat(schema.property("printCondition"))
                    .as("printCondition on %s", type.getSimpleName())
                    .isPresent();
        }
    }

    @Test
    void printConditionRoundTripsAndClearsToNull() {
        TextElement text = TextElement.of(props("t1"), "Hi", FontSpec.of("Arial", 10));

        LabelElement conditional = set(text, "printCondition", "${Qty} > 0");
        assertThat(conditional.properties().printCondition()).isEqualTo("${Qty} > 0");

        assertThat(set(conditional, "printCondition", "").properties().printCondition())
                .isNull();
    }

    // ---- group ----

    @Test
    void aGroupReportsItsChildCountReadOnly() {
        RectangleElement child = RectangleElement.of(props("c1"), Stroke.solid(0.2), Fill.none());
        GroupElement group = new GroupElement(props("g1"), List.of(child));

        PropertyDescriptor descriptor =
                ElementSchemas.schemaFor(group).property("childCount").orElseThrow();

        assertThat(descriptor.readOnly()).isTrue();
        assertThat(get(group, "childCount")).isEqualTo(1);
    }
}
