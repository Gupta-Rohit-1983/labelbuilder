package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The exact visibility predicate the canvas uses to cull off-screen items (Phase 6e), exercised
 * headlessly: {@code visibleModelBounds ∩ item.rotatedAabb}. Correctness matters in one direction
 * — a visible item must never be culled.
 */
class CanvasCullingTest {

    private static boolean visible(CanvasViewport vp, double viewW, double viewH, BoundsMm item, double rotationDeg) {
        return vp.visibleModelBounds(viewW, viewH).intersects(item.rotatedAabb(rotationDeg));
    }

    @Test
    void itemInsideTheViewportIsKept() {
        CanvasViewport vp = CanvasViewport.initial(); // origin at top-left, 100%
        BoundsMm item = new BoundsMm(5, 5, 10, 10);

        assertThat(visible(vp, 400, 300, item, 0)).isTrue();
    }

    @Test
    void itemFarOutsideTheViewportIsCulled() {
        CanvasViewport vp = CanvasViewport.initial();
        // Viewport shows model 0..(400/PPM) mm ≈ 0..67.7 mm; this item sits well past that.
        BoundsMm item = new BoundsMm(500, 500, 10, 10);

        assertThat(visible(vp, 400, 300, item, 0)).isFalse();
    }

    @Test
    void rotationCanBringAnItemBackIntoView() {
        CanvasViewport vp = CanvasViewport.initial();
        double viewRightMm = vp.visibleModelBounds(400, 300).right();

        // A tall, thin item just past the right edge when upright...
        BoundsMm item = new BoundsMm(viewRightMm + 2, 10, 4, 40);
        assertThat(visible(vp, 400, 300, item, 0)).isFalse();

        // ...but rotated 90° its 40mm length spans back across the edge into view.
        assertThat(visible(vp, 400, 300, item, 90)).isTrue();
    }
}
