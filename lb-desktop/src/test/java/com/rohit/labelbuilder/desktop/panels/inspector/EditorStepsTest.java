package com.rohit.labelbuilder.desktop.panels.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.meta.ElementSchemas;
import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import com.rohit.labelbuilder.model.style.FontSpec;
import org.junit.jupiter.api.Test;

/** Spinner step and bounds derived from the property metadata. */
class EditorStepsTest {

    private static final TextElement TEXT = TextElement.of(
            ElementProperties.of("t1", "t1", "layer-1", new Bounds(0, 0, 10, 10)), "Hi", FontSpec.of("Arial", 10));

    private static final ImageElement IMAGE =
            ImageElement.of(ElementProperties.of("i1", "i1", "layer-1", new Bounds(0, 0, 10, 10)), "assets/a.png");

    private static PropertyDescriptor of(LabelElement element, String key) {
        return ElementSchemas.schemaFor(element).property(key).orElseThrow();
    }

    @Test
    void millimetresStepByAHalf() {
        assertThat(EditorSteps.stepFor(of(TEXT, "x"))).isEqualTo(0.5);
        assertThat(EditorSteps.stepFor(of(TEXT, "width"))).isEqualTo(0.5);
    }

    @Test
    void degreesStepByOne() {
        assertThat(EditorSteps.stepFor(of(TEXT, "rotation"))).isEqualTo(1);
    }

    @Test
    void aZeroToOneRatioStepsFinely() {
        // Opacity runs 0..1; a 0.5 step would jump straight from transparent to opaque.
        assertThat(EditorSteps.stepFor(of(IMAGE, "opacity"))).isEqualTo(0.05);
    }

    @Test
    void wholeNumbersStepByOne() {
        assertThat(EditorSteps.stepFor(of(IMAGE, "monochromeThreshold"))).isEqualTo(1);
    }

    @Test
    void boundsComeFromTheDescriptorWhenDeclared() {
        PropertyDescriptor opacity = of(IMAGE, "opacity");

        assertThat(EditorSteps.minFor(opacity)).isEqualTo(0.0);
        assertThat(EditorSteps.maxFor(opacity)).isEqualTo(1.0);
    }

    @Test
    void anUnboundedPropertyGetsWideFiniteBounds() {
        // Spinner factories require finite bounds even when the property has none.
        PropertyDescriptor rotation = of(TEXT, "rotation");

        assertThat(EditorSteps.minFor(rotation)).isNegative().isFinite();
        assertThat(EditorSteps.maxFor(rotation)).isPositive().isFinite();
    }

    @Test
    void aMinimumOnlyPropertyKeepsItsFloor() {
        PropertyDescriptor width = of(TEXT, "width");

        assertThat(EditorSteps.minFor(width)).isEqualTo(0.0);
        assertThat(EditorSteps.maxFor(width)).isPositive().isFinite();
    }
}
