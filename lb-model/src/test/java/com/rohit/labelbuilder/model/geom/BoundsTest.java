package com.rohit.labelbuilder.model.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BoundsTest {

    private static final double EPS = 1e-9;
    private final Bounds box = new Bounds(10, 20, 30, 12);

    @Test
    void derivedEdgesAndCentre() {
        assertThat(box.rightMm()).isCloseTo(40, within(EPS));
        assertThat(box.bottomMm()).isCloseTo(32, within(EPS));
        assertThat(box.centerXMm()).isCloseTo(25, within(EPS));
        assertThat(box.centerYMm()).isCloseTo(26, within(EPS));
    }

    @Test
    void translateKeepsSize() {
        Bounds moved = box.translated(5, -4);
        assertThat(moved).isEqualTo(new Bounds(15, 16, 30, 12));
    }

    @Test
    void withPositionAndWithSizeAreIndependent() {
        assertThat(box.withPosition(0, 0)).isEqualTo(new Bounds(0, 0, 30, 12));
        assertThat(box.withSize(5, 6)).isEqualTo(new Bounds(10, 20, 5, 6));
    }

    @Test
    void intersectsIsExclusiveAtEdges() {
        assertThat(box.intersects(new Bounds(35, 25, 10, 10))).isTrue();
        assertThat(box.intersects(new Bounds(40, 20, 5, 5))).isFalse(); // touching right edge only
        assertThat(box.intersects(new Bounds(100, 100, 5, 5))).isFalse();
    }

    @Test
    void zeroExtentIsAllowedForLines() {
        assertThat(new Bounds(10, 10, 0, 25).widthMm()).isZero();
    }

    @Test
    void negativeSizeIsRejected() {
        assertThatThrownBy(() -> new Bounds(0, 0, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bounds(0, 0, 10, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonFiniteIsRejected() {
        assertThatThrownBy(() -> new Bounds(Double.NaN, 0, 10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bounds(0, Double.POSITIVE_INFINITY, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
