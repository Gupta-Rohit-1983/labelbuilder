package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Pure coordinate maths — fully headless. */
class CanvasViewportTest {

    private static final double EPS = 1e-9;
    private static final double PPM = CanvasViewport.DEFAULT_PIXELS_PER_MM;

    @Test
    void initialMapsMillimetresToPixelsAtNinetySixDpi() {
        CanvasViewport vp = CanvasViewport.initial();

        assertThat(vp.zoom()).isEqualTo(1.0);
        assertThat(vp.toDeviceX(0)).isCloseTo(0, within(EPS));
        assertThat(vp.toDeviceX(10)).isCloseTo(10 * PPM, within(EPS));
        assertThat(vp.toDeviceY(25.4)).isCloseTo(96.0, within(1e-6)); // 25.4 mm = 1 in = 96 px
    }

    @Test
    void modelDeviceRoundTrips() {
        CanvasViewport vp = new CanvasViewport(PPM, 2.5, 40, -15);

        for (double mm : new double[] {0, 12.5, -7.3, 100}) {
            assertThat(vp.toModelX(vp.toDeviceX(mm))).isCloseTo(mm, within(1e-9));
            assertThat(vp.toModelY(vp.toDeviceY(mm))).isCloseTo(mm, within(1e-9));
        }
    }

    @Test
    void zoomAtKeepsThePivotModelPointUnderThePivotPixel() {
        CanvasViewport vp = new CanvasViewport(PPM, 1.0, 30, 20);
        double pivotX = 150;
        double pivotY = 90;
        double modelX = vp.toModelX(pivotX);
        double modelY = vp.toModelY(pivotY);

        CanvasViewport zoomed = vp.withZoomAt(3.0, pivotX, pivotY);

        assertThat(zoomed.zoom()).isEqualTo(3.0);
        assertThat(zoomed.toDeviceX(modelX)).isCloseTo(pivotX, within(1e-6));
        assertThat(zoomed.toDeviceY(modelY)).isCloseTo(pivotY, within(1e-6));
    }

    @Test
    void panShiftsTheOffsetInPixels() {
        CanvasViewport vp = new CanvasViewport(PPM, 1.0, 10, 10).pannedBy(25, -5);

        assertThat(vp.offsetX()).isEqualTo(35);
        assertThat(vp.offsetY()).isEqualTo(5);
    }

    @Test
    void zoomIsClampedToBounds() {
        CanvasViewport vp = CanvasViewport.initial();

        assertThat(vp.withZoomAt(1000, 0, 0).zoom()).isEqualTo(CanvasViewport.MAX_ZOOM);
        assertThat(vp.withZoomAt(0.0001, 0, 0).zoom()).isEqualTo(CanvasViewport.MIN_ZOOM);
    }

    @Test
    void fitCentresAndScalesTheSurface() {
        CanvasViewport vp = CanvasViewport.initial().fit(100, 50, 400, 300, 10);

        double expectedZoom = Math.min((400 - 20) / (100 * PPM), (300 - 20) / (50 * PPM));
        assertThat(vp.zoom()).isCloseTo(expectedZoom, within(1e-9));

        // Surface is centred: left and right margins in the viewport are equal.
        double left = vp.toDeviceX(0);
        double right = 400 - vp.toDeviceX(100);
        assertThat(left).isCloseTo(right, within(1e-6));
        double top = vp.toDeviceY(0);
        double bottom = 300 - vp.toDeviceY(50);
        assertThat(top).isCloseTo(bottom, within(1e-6));
    }

    @Test
    void fitIsANoOpForNonPositiveDimensions() {
        CanvasViewport vp = CanvasViewport.initial();

        assertThat(vp.fit(100, 50, 0, 300, 10)).isEqualTo(vp);
        assertThat(vp.fit(0, 50, 400, 300, 10)).isEqualTo(vp);
    }

    @Test
    void visibleModelBoundsCoverTheViewportInMillimetres() {
        CanvasViewport vp = new CanvasViewport(PPM, 1.0, 10, 20);

        BoundsMm visible = vp.visibleModelBounds(100, 50);

        assertThat(visible.x()).isCloseTo(-10 / PPM, within(1e-9));
        assertThat(visible.y()).isCloseTo(-20 / PPM, within(1e-9));
        assertThat(visible.w()).isCloseTo(100 / PPM, within(1e-9));
        assertThat(visible.h()).isCloseTo(50 / PPM, within(1e-9));
        // Corner round-trips: the viewport's top-left device pixel maps to the bounds' origin.
        assertThat(vp.toModelX(0)).isCloseTo(visible.x(), within(1e-9));
    }

    @Test
    void nonPositivePixelsPerMmIsRejected() {
        assertThatThrownBy(() -> new CanvasViewport(0, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CanvasViewport(-1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
