package com.rohit.labelbuilder.model.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ElementSchemasTest {

    private static ElementProperties props(String id) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(3, 4, 20, 10));
    }

    private final TextElement text = TextElement.of(props("t1"), "Hi", FontSpec.of("Arial", 10));
    private final RectangleElement rect = RectangleElement.of(props("r1"), Stroke.solid(0.3), Fill.of(RgbaColor.WHITE));

    /** Every concrete element in the sealed hierarchy must have a registered schema. */
    static java.util.List<Class<?>> allElementTypes() {
        return java.util.List.of(LabelElement.class.getPermittedSubclasses());
    }

    @ParameterizedTest
    @MethodSource("allElementTypes")
    void everyElementTypeHasASchema(Class<?> type) {
        @SuppressWarnings("unchecked")
        Class<? extends LabelElement> elementType = (Class<? extends LabelElement>) type;

        ElementSchema schema = ElementSchemas.schemaFor(elementType);

        assertThat(schema.elementType()).isEqualTo(elementType);
        assertThat(schema.properties()).isNotEmpty(); // at least the common properties
    }

    @Test
    void everySchemaCarriesTheCommonProperties() {
        for (Class<?> type : allElementTypes()) {
            @SuppressWarnings("unchecked")
            ElementSchema schema = ElementSchemas.schemaFor((Class<? extends LabelElement>) type);
            assertThat(schema.properties())
                    .extracting(PropertyDescriptor::key)
                    .contains("name", "visible", "locked", "x", "y", "width", "height", "rotation");
        }
    }

    @Test
    void schemaForInstanceMatchesSchemaForClass() {
        assertThat(ElementSchemas.schemaFor(text)).isSameAs(ElementSchemas.schemaFor(TextElement.class));
    }

    @Test
    void geometryDescriptorReadsAndWritesBoundsImmutably() {
        ElementSchema schema = ElementSchemas.schemaFor(text);

        assertThat(schema.get(text, "x")).isEqualTo(3.0);

        LabelElement moved = schema.set(text, "x", 50.0);

        assertThat(moved.bounds().xMm()).isEqualTo(50.0);
        assertThat(moved.bounds().yMm()).isEqualTo(4.0); // y preserved
        assertThat(text.bounds().xMm()).isEqualTo(3.0); // original untouched
    }

    @Test
    void typeSpecificDescriptorRoundTrips() {
        ElementSchema schema = ElementSchemas.schemaFor(rect);

        LabelElement red = schema.set(rect, "strokeColor", RgbaColor.rgb(255, 0, 0));

        assertThat(((RectangleElement) red).stroke().color()).isEqualTo(RgbaColor.rgb(255, 0, 0));
        assertThat(((RectangleElement) red).stroke().widthMm()).isEqualTo(0.3); // width preserved by decomposition
    }

    @Test
    void widthCarriesANonNegativeRange() {
        PropertyDescriptor width =
                ElementSchemas.schemaFor(text).property("width").orElseThrow();
        assertThat(width.min()).contains(0.0);
    }

    @Test
    void writingAnInvalidGeometryIsRejectedByTheModel() {
        ElementSchema schema = ElementSchemas.schemaFor(text);
        assertThatThrownBy(() -> schema.set(text, "width", -5.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownKeyIsRejected() {
        ElementSchema schema = ElementSchemas.schemaFor(text);
        assertThatThrownBy(() -> schema.get(text, "nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byCategoryGroupsGeneralAndGeometryFirst() {
        ElementSchema schema = ElementSchemas.schemaFor(text);
        assertThat(schema.byCategory().keySet()).containsSequence("General", "Geometry", "Text");
    }
}
