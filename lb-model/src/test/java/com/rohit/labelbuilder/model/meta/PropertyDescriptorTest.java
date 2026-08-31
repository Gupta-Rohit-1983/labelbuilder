package com.rohit.labelbuilder.model.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.AutoFit;
import com.rohit.labelbuilder.model.style.FontSpec;
import org.junit.jupiter.api.Test;

class PropertyDescriptorTest {

    private final TextElement text = TextElement.of(
            ElementProperties.of("t1", "Title", "layer-1", new Bounds(0, 0, 10, 5)), "Hi", FontSpec.of("Arial", 10));

    @Test
    void readReturnsCurrentValue() {
        PropertyDescriptor d = PropertyDescriptor.text(
                "value", "Text", "Text", e -> ((TextElement) e).value(), (e, v) -> ((TextElement) e).withValue(v));

        assertThat(d.read(text)).isEqualTo("Hi");
    }

    @Test
    void writeReturnsNewElementAndLeavesOriginalUntouched() {
        PropertyDescriptor d = PropertyDescriptor.text(
                "value", "Text", "Text", e -> ((TextElement) e).value(), (e, v) -> ((TextElement) e).withValue(v));

        LabelElement updated = d.write(text, "Bye");

        assertThat(((TextElement) updated).value()).isEqualTo("Bye");
        assertThat(text.value()).isEqualTo("Hi"); // original immutable
    }

    @Test
    void enumDescriptorExposesItsConstants() {
        PropertyDescriptor d = PropertyDescriptor.enumProp(
                "autoFit", "Auto-fit", "Text", AutoFit.class, e -> ((TextElement) e).autoFit(), (e, v) -> text);

        assertThat(d.kind()).isEqualTo(PropertyKind.ENUM);
        assertThat(d.enumConstants()).containsExactly((Object[]) AutoFit.values());
    }

    @Test
    void rangeIsAdvisoryMetadata() {
        PropertyDescriptor d = PropertyDescriptor.decimal(
                        "w", "W", "Geometry", e -> e.bounds().widthMm(), (e, v) -> e)
                .withRange(0.0, 100.0);

        assertThat(d.min()).contains(0.0);
        assertThat(d.max()).contains(100.0);
    }

    @Test
    void readOnlyDescriptorRejectsWrites() {
        PropertyDescriptor d =
                PropertyDescriptor.readOnly("id", "Id", "General", PropertyKind.TEXT, String.class, LabelElement::id);

        assertThat(d.readOnly()).isTrue();
        assertThat(d.read(text)).isEqualTo("t1");
        assertThatThrownBy(() -> d.write(text, "x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
