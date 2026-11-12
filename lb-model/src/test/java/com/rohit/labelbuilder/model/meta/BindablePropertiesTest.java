package com.rohit.labelbuilder.model.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which properties the data binder may substitute into (Phase 11e).
 *
 * <p>Declared in the metadata rather than decided by the binder, so these assertions are the single
 * statement of what counts as printed content.
 */
class BindablePropertiesTest {

    private static List<String> bindableKeysOf(Class<? extends LabelElement> type) {
        return ElementSchemas.schemaFor(type).properties().stream()
                .filter(PropertyDescriptor::bindable)
                .map(PropertyDescriptor::key)
                .toList();
    }

    @Test
    void textAndBarcodeValuesCarryPrintedContent() {
        assertThat(bindableKeysOf(TextElement.class)).containsExactly("value");
        assertThat(bindableKeysOf(BarcodeElement.class)).containsExactly("value", "hriCustomText");
    }

    @Test
    void anImagesAssetPathIsBindableSoALabelCanPickItsPicture() {
        assertThat(bindableKeysOf(ImageElement.class)).containsExactly("assetPath");
    }

    @Test
    void shapesAndGroupsHaveNothingToBind() {
        assertThat(bindableKeysOf(RectangleElement.class)).isEmpty();
        assertThat(bindableKeysOf(EllipseElement.class)).isEmpty();
        assertThat(bindableKeysOf(LineElement.class)).isEmpty();
        assertThat(bindableKeysOf(GroupElement.class)).isEmpty();
    }

    @Test
    void theNameAndPrintConditionAreNeverBindable() {
        // name is design metadata that never prints; printCondition is an expression for the Phase 12
        // engine to evaluate, and substituting a value into it would turn it into a constant.
        for (Class<? extends LabelElement> type : List.of(
                TextElement.class,
                BarcodeElement.class,
                ImageElement.class,
                RectangleElement.class,
                GroupElement.class)) {
            assertThat(bindableKeysOf(type))
                    .as("bindable properties of %s", type.getSimpleName())
                    .doesNotContain("name", "printCondition");
        }
    }

    @Test
    void everyBindablePropertyIsWritableTextSoTheBinderCanAlwaysActOnIt() {
        for (Class<? extends LabelElement> type : ElementSchemas.registeredTypes()) {
            assertThat(ElementSchemas.schemaFor(type).properties().stream()
                            .filter(PropertyDescriptor::bindable)
                            .map(descriptor -> tuple(descriptor.kind(), descriptor.readOnly())))
                    .as("bindable properties of %s", type.getSimpleName())
                    .allMatch(entry -> entry.equals(tuple(PropertyKind.TEXT, false)));
        }
    }

    @Test
    void propertiesAreNotBindableUnlessTheySaySo() {
        PropertyDescriptor plain = PropertyDescriptor.text(
                "x",
                "X",
                "General",
                LabelElement::name,
                (e, v) -> e.withProperties(e.properties().withName(v)));

        assertThat(plain.bindable()).isFalse();
        assertThat(plain.asBindable().bindable()).isTrue();
    }

    @Test
    void onlyTextCanBeBoundBecauseSubstitutionProducesAString() {
        assertThatThrownBy(
                        () -> PropertyDescriptor.decimal("rotationDeg", "Rotation", "Geometry", e -> 0.0, (e, v) -> e)
                                .asBindable())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DECIMAL");
    }

    @Test
    void markingBindableKeepsEverythingElseAboutTheDescriptor() {
        PropertyDescriptor original =
                ElementSchemas.schemaFor(TextElement.class).property("value").orElseThrow();

        assertThat(original.bindable()).isTrue();
        assertThat(original.kind()).isEqualTo(PropertyKind.TEXT);
        assertThat(original.displayName()).isEqualTo("Text");
        assertThat(original.readOnly()).isFalse();
    }
}
