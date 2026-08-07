package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

class BoundsMmTest {

    private static final double EPS = 1e-9;
    private final BoundsMm box = new BoundsMm(10, 10, 20, 12);

    @Test
    void derivedEdgesAndCentre() {
        assertThat(box.right()).isCloseTo(30, within(EPS));
        assertThat(box.bottom()).isCloseTo(22, within(EPS));
        assertThat(box.center()).isEqualTo(new Point2D(20, 16));
    }

    @Test
    void containsRespectsEdges() {
        assertThat(box.contains(10, 10)).isTrue();
        assertThat(box.contains(20, 16)).isTrue();
        assertThat(box.contains(9.9, 16)).isFalse();
        assertThat(box.contains(20, 22.1)).isFalse();
    }

    @Test
    void intersectsIsExclusiveAtEdges() {
        assertThat(box.intersects(new BoundsMm(25, 15, 10, 10))).isTrue();
        assertThat(box.intersects(new BoundsMm(30, 10, 5, 5))).isFalse(); // touching right edge only
        assertThat(box.intersects(new BoundsMm(100, 100, 5, 5))).isFalse();
    }

    @Test
    void handlePointsSitOnTheBox() {
        assertThat(box.handlePoint(ResizeHandle.NW)).isEqualTo(new Point2D(10, 10));
        assertThat(box.handlePoint(ResizeHandle.SE)).isEqualTo(new Point2D(30, 22));
        assertThat(box.handlePoint(ResizeHandle.N)).isEqualTo(new Point2D(20, 10));
        assertThat(box.handlePoint(ResizeHandle.E)).isEqualTo(new Point2D(30, 16));
    }

    @Test
    void resizeEastGrowsWidthOnly() {
        assertThat(box.resized(ResizeHandle.E, 5, 0, 1)).isEqualTo(new BoundsMm(10, 10, 25, 12));
    }

    @Test
    void resizeWestMovesLeftEdgeKeepingRightFixed() {
        BoundsMm r = box.resized(ResizeHandle.W, 5, 0, 1);
        assertThat(r).isEqualTo(new BoundsMm(15, 10, 15, 12));
        assertThat(r.right()).isCloseTo(box.right(), within(EPS));
    }

    @Test
    void resizeCornerMovesBothEdges() {
        assertThat(box.resized(ResizeHandle.SE, 5, 4, 1)).isEqualTo(new BoundsMm(10, 10, 25, 16));
        assertThat(box.resized(ResizeHandle.NW, 5, 4, 1)).isEqualTo(new BoundsMm(15, 14, 15, 8));
    }

    @Test
    void rotatedAabbIsUnchangedAtZeroOrFullTurns() {
        assertThat(box.rotatedAabb(0)).isEqualTo(box);
        assertThat(box.rotatedAabb(360)).isEqualTo(box);
    }

    @Test
    void rotatedAabbSwapsExtentsAtNinetyDegrees() {
        // box is 20x12 centred at (20,16); at 90° the enclosing AABB is 12x20, same centre.
        BoundsMm r = box.rotatedAabb(90);
        assertThat(r.w()).isCloseTo(12, within(1e-9));
        assertThat(r.h()).isCloseTo(20, within(1e-9));
        assertThat(r.center()).isEqualTo(box.center());
    }

    @Test
    void rotatedAabbGrowsAtFortyFiveDegrees() {
        BoundsMm r = box.rotatedAabb(45);
        double expected = (20 + 12) * Math.cos(Math.toRadians(45));
        assertThat(r.w()).isCloseTo(expected, within(1e-9));
        assertThat(r.h()).isCloseTo(expected, within(1e-9));
        assertThat(r.center()).isEqualTo(box.center());
    }

    @Test
    void resizeClampsToMinSizeWithoutMovingTheFixedEdge() {
        // Drag the west handle far past the right edge: width clamps, right edge stays put.
        BoundsMm r = box.resized(ResizeHandle.W, 25, 0, 1);
        assertThat(r.w()).isCloseTo(1, within(EPS));
        assertThat(r.right()).isCloseTo(box.right(), within(EPS));
        assertThat(r.x()).isCloseTo(29, within(EPS));
    }
}
