package com.rohit.labelbuilder.model.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behaviour shared across every element: common-property delegation and immutable transforms. */
class LabelElementTest {

    private static final double EPS = 1e-9;

    private static ElementProperties props(String id) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(10, 10, 20, 8));
    }

    private final TextElement text = TextElement.of(props("t1"), "Hello", FontSpec.of("Arial", 10));

    @Test
    void defaultAccessorsDelegateToCommonProperties() {
        assertThat(text.id()).isEqualTo("t1");
        assertThat(text.layerId()).isEqualTo("layer-1");
        assertThat(text.bounds()).isEqualTo(new Bounds(10, 10, 20, 8));
        assertThat(text.rotationDeg()).isZero();
        assertThat(text.locked()).isFalse();
        assertThat(text.visible()).isTrue();
    }

    @Test
    void movedByReturnsSameConcreteTypeAndShiftsBounds() {
        LabelElement moved = text.movedBy(5, -3);

        assertThat(moved).isInstanceOf(TextElement.class);
        assertThat(moved.bounds()).isEqualTo(new Bounds(15, 7, 20, 8));
        // type-specific state survives the transform
        assertThat(((TextElement) moved).value()).isEqualTo("Hello");
    }

    @Test
    void transformsDoNotMutateTheOriginal() {
        text.movedBy(100, 100).withRotationDeg(45).withLocked(true);

        assertThat(text.bounds()).isEqualTo(new Bounds(10, 10, 20, 8));
        assertThat(text.rotationDeg()).isZero();
        assertThat(text.locked()).isFalse();
    }

    @Test
    void withRotationAndLockFlowThroughProperties() {
        LabelElement r = text.withRotationDeg(90).withLocked(true).withVisible(false);

        assertThat(r.rotationDeg()).isCloseTo(90, within(EPS));
        assertThat(r.locked()).isTrue();
        assertThat(r.visible()).isFalse();
    }

    @Test
    void rectanglePreservesStyleAcrossPropertyChange() {
        RectangleElement rect = RectangleElement.of(
                props("r1"), Stroke.solid(0.3), Fill.of(com.rohit.labelbuilder.model.style.RgbaColor.WHITE));

        LabelElement moved = rect.movedBy(1, 1);

        assertThat(moved).isInstanceOf(RectangleElement.class);
        assertThat(((RectangleElement) moved).stroke()).isEqualTo(Stroke.solid(0.3));
    }

    @Test
    void groupChildrenAreImmutable() {
        GroupElement group = new GroupElement(props("g1"), List.of(text));

        assertThatThrownBy(() -> group.children().add(text)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lineExposesEndpointsFromBoundsCorners() {
        LineElement line =
                LineElement.of(ElementProperties.of("l1", "l1", "layer-1", new Bounds(5, 5, 30, 0)), Stroke.solid(0.2));

        assertThat(line.x1Mm()).isCloseTo(5, within(EPS));
        assertThat(line.y1Mm()).isCloseTo(5, within(EPS));
        assertThat(line.x2Mm()).isCloseTo(35, within(EPS));
        assertThat(line.y2Mm()).isCloseTo(5, within(EPS));
    }
}
