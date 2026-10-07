package com.rohit.labelbuilder.desktop.panels.inspector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.meta.ElementSchemas;
import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.TextAlign;
import org.junit.jupiter.api.Test;

/** The inspector's value formatting and parsing — pure, so no FX toolkit is needed. */
class PropertyValuesTest {

    private final TextElement text = TextElement.of(
            ElementProperties.of("t1", "Title", "layer-1", new Bounds(3, 4, 20, 10)), "Hi", FontSpec.of("Arial", 10));

    private PropertyDescriptor descriptor(String key) {
        return ElementSchemas.schemaFor((LabelElement) text).property(key).orElseThrow();
    }

    // ---- formatting ----

    @Test
    void wholeNumbersLoseTheirTrailingZero() {
        assertThat(PropertyValues.format(10.0)).isEqualTo("10");
        assertThat(PropertyValues.format(0.25)).isEqualTo("0.25");
        assertThat(PropertyValues.format(-3.5)).isEqualTo("-3.5");
    }

    @Test
    void coloursFormatAsHexAndEnumsAsTheirName() {
        assertThat(PropertyValues.format(RgbaColor.rgb(255, 0, 0))).isEqualTo("#FF0000");
        assertThat(PropertyValues.format(TextAlign.CENTER)).isEqualTo("CENTER");
    }

    @Test
    void nullFormatsAsEmptyRatherThanTheWordNull() {
        assertThat(PropertyValues.format(null)).isEmpty();
    }

    @Test
    void fontsFormatWithFamilySizeAndStyle() {
        assertThat(PropertyValues.describeFont(new FontSpec("Arial", 12, true, true, false)))
                .isEqualTo("Arial, 12pt Bold Italic");
        assertThat(PropertyValues.describeFont(FontSpec.of("Arial", 10))).isEqualTo("Arial, 10pt");
    }

    // ---- parsing ----

    @Test
    void decimalsParseAndRoundTripThroughFormat() {
        Object parsed = PropertyValues.parse(descriptor("x"), "12.5");

        assertThat(parsed).isEqualTo(12.5);
        assertThat(PropertyValues.format(parsed)).isEqualTo("12.5");
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(PropertyValues.parse(descriptor("x"), "  7  ")).isEqualTo(7.0);
    }

    @Test
    void nonNumericTextIsRejectedWithAReadableMessage() {
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("x"), "wide"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected a number");
    }

    @Test
    void infinitiesAndNaNAreRejected() {
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("x"), "Infinity"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("x"), "NaN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDescriptorsRangeIsEnforcedBeforeTheModelSeesTheValue() {
        // "width" carries a min of 0 — a negative would otherwise blow up inside Bounds.
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("width"), "-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be less than 0");
    }

    @Test
    void coloursParseFromHex() {
        assertThat(PropertyValues.parse(descriptor("color"), "#00FF00")).isEqualTo(RgbaColor.rgb(0, 255, 0));
    }

    @Test
    void enumsParseCaseInsensitivelyAndRejectUnknownNames() {
        assertThat(PropertyValues.parse(descriptor("align"), "center")).isEqualTo(TextAlign.CENTER);
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("align"), "sideways"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sideways");
    }

    @Test
    void booleansAcceptTrueFalseAndRejectAnythingElse() {
        assertThat(PropertyValues.parse(descriptor("visible"), "true")).isEqualTo(true);
        assertThat(PropertyValues.parse(descriptor("visible"), "FALSE")).isEqualTo(false);
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("visible"), "maybe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textPropertiesTakeTheTextAsIs() {
        assertThat(PropertyValues.parse(descriptor("name"), "Product Name")).isEqualTo("Product Name");
    }

    @Test
    void fontsCannotBeEditedAsTextYet() {
        assertThatThrownBy(() -> PropertyValues.parse(descriptor("font"), "Arial, 12pt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
