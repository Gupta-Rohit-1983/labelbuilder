package com.rohit.labelbuilder.model.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a multi-element selection may show and edit (Phase 9d). */
class SharedPropertiesTest {

    private static ElementProperties props(String id, double x) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(x, 0, 10, 10));
    }

    private static RectangleElement rect(String id, double x) {
        return RectangleElement.of(props(id, x), Stroke.solid(0.2), Fill.none());
    }

    private static TextElement text(String id, double x) {
        return TextElement.of(props(id, x), "Hi", FontSpec.of("Arial", 10));
    }

    private static List<String> keys(List<PropertyDescriptor> descriptors) {
        return descriptors.stream().map(PropertyDescriptor::key).toList();
    }

    @Test
    void anEmptySelectionSharesNothing() {
        assertThat(SharedProperties.of(List.of())).isEmpty();
    }

    @Test
    void oneElementSharesItsWholeSchema() {
        LabelElement element = rect("a", 0);

        assertThat(SharedProperties.of(List.of(element)))
                .hasSameSizeAs(ElementSchemas.schemaFor(element).properties());
    }

    @Test
    void sameTypeElementsShareEverythingIncludingTypeSpecificProperties() {
        List<PropertyDescriptor> shared = SharedProperties.of(List.of(rect("a", 0), rect("b", 20)));

        assertThat(keys(shared)).contains("x", "width", "strokeColor", "fillColor", "cornerRadiusMm");
    }

    @Test
    void mixedTypesShareOnlyTheCommonProperties() {
        List<PropertyDescriptor> shared = SharedProperties.of(List.of(rect("a", 0), text("b", 20)));

        assertThat(keys(shared)).contains("name", "visible", "locked", "x", "y", "width", "height", "rotation");
        // A rectangle has no text value; a text element has no corner radius.
        assertThat(keys(shared)).doesNotContain("value", "cornerRadiusMm", "font");
    }

    @Test
    void propertiesCommonToTwoShapeTypesSurvive() {
        // Rectangle and ellipse both carry stroke and fill, so those stay editable together.
        List<PropertyDescriptor> shared = SharedProperties.of(
                List.of(rect("a", 0), new EllipseElement(props("b", 20), Stroke.solid(0.2), Fill.none())));

        assertThat(keys(shared)).contains("strokeColor", "strokeWidthMm", "fillColor", "fillEnabled");
        assertThat(keys(shared)).doesNotContain("cornerRadiusMm"); // rectangle only
    }

    @Test
    void anAgreedValueIsReportedAsUniform() {
        SharedProperties.CommonValue common = SharedProperties.commonValue(List.of(rect("a", 5), rect("b", 5)), "x");

        assertThat(common.uniform()).isTrue();
        assertThat(common.value()).isEqualTo(5.0);
    }

    @Test
    void differingValuesAreReportedAsMixed() {
        SharedProperties.CommonValue common = SharedProperties.commonValue(List.of(rect("a", 0), rect("b", 20)), "x");

        assertThat(common.uniform()).isFalse();
    }

    @Test
    void aUniformNullIsNotTheSameAsMixed() {
        // Every element has no print condition — that is agreement, not disagreement, and the
        // inspector must show an empty field rather than "(mixed)".
        SharedProperties.CommonValue common =
                SharedProperties.commonValue(List.of(rect("a", 0), rect("b", 20)), "printCondition");

        assertThat(common.uniform()).isTrue();
        assertThat(common.value()).isNull();
    }

    @Test
    void valuesAreReadThroughEachElementsOwnSchema() {
        // Mixed types agreeing on a common property still report uniform.
        SharedProperties.CommonValue common = SharedProperties.commonValue(List.of(rect("a", 7), text("b", 7)), "x");

        assertThat(common.uniform()).isTrue();
        assertThat(common.value()).isEqualTo(7.0);
    }
}
