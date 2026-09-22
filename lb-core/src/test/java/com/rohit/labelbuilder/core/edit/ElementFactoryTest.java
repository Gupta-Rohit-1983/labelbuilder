package com.rohit.labelbuilder.core.edit;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ElementFactoryTest {

    private static final Bounds BOX = new Bounds(5, 6, 30, 20);

    @ParameterizedTest
    @EnumSource(ElementKind.class)
    void everyKindCreatesAnElementCarryingTheGivenIdentityAndGeometry(ElementKind kind) {
        LabelElement element = ElementFactory.create(kind, "el-1", "layer-2", BOX);

        assertThat(element.id()).isEqualTo("el-1");
        assertThat(element.layerId()).isEqualTo("layer-2");
        assertThat(element.bounds()).isEqualTo(BOX);
        assertThat(element.name()).isEqualTo(kind.displayName());
        assertThat(element.visible()).isTrue();
        assertThat(element.locked()).isFalse();
    }

    @Test
    void eachKindMapsToItsElementType() {
        assertThat(ElementFactory.create(ElementKind.TEXT, "a", "l", BOX)).isInstanceOf(TextElement.class);
        assertThat(ElementFactory.create(ElementKind.RECTANGLE, "a", "l", BOX)).isInstanceOf(RectangleElement.class);
        assertThat(ElementFactory.create(ElementKind.ELLIPSE, "a", "l", BOX)).isInstanceOf(EllipseElement.class);
        assertThat(ElementFactory.create(ElementKind.LINE, "a", "l", BOX)).isInstanceOf(LineElement.class);
        assertThat(ElementFactory.create(ElementKind.IMAGE, "a", "l", BOX)).isInstanceOf(ImageElement.class);
        assertThat(ElementFactory.create(ElementKind.BARCODE, "a", "l", BOX)).isInstanceOf(BarcodeElement.class);
    }

    @Test
    void createDefaultUsesTheKindsDefaultSizeAtThePoint() {
        LabelElement element = ElementFactory.createDefault(ElementKind.TEXT, "a", "l", 12, 34);

        assertThat(element.bounds())
                .isEqualTo(new Bounds(12, 34, ElementKind.TEXT.defaultWidthMm(), ElementKind.TEXT.defaultHeightMm()));
    }

    @Test
    void aZeroHeightLineIsLegalButABarcodeStillGetsAPositiveBarHeight() {
        Bounds flat = new Bounds(0, 0, 40, 0);

        assertThat(ElementFactory.create(ElementKind.LINE, "a", "l", flat)
                        .bounds()
                        .heightMm())
                .isZero();
        BarcodeElement barcode = (BarcodeElement) ElementFactory.create(ElementKind.BARCODE, "a", "l", flat);
        assertThat(barcode.barHeightMm()).isPositive();
    }

    @Test
    void newIdsAreUnique() {
        assertThat(ElementFactory.newId()).isNotEqualTo(ElementFactory.newId());
    }
}
