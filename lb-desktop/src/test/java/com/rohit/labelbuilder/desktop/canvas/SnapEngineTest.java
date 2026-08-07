package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SnapEngineTest {

    private static final GridSettings SNAP_5 = new GridSettings(5, true, true);
    private static final GridSettings NO_SNAP = new GridSettings(5, true, false);
    private static final double[] NO_GUIDES = new double[0];

    @Test
    void snapsToNearestGridLineWhenEnabled() {
        assertThat(SnapEngine.snap(11.2, SNAP_5, NO_GUIDES, 1)).isCloseTo(10, within(1e-9));
    }

    @Test
    void guideBeatsGridWhenStrictlyCloserAndWithinThreshold() {
        // Grid nearest is 10 (dist 1.2); guide at 11.0 (dist 0.2) is closer and within 0.5.
        assertThat(SnapEngine.snap(11.2, SNAP_5, new double[] {11.0}, 0.5)).isCloseTo(11.0, within(1e-9));
    }

    @Test
    void guideIgnoredBeyondThreshold() {
        assertThat(SnapEngine.snap(11.2, SNAP_5, new double[] {8.0}, 0.5)).isCloseTo(10, within(1e-9));
    }

    @Test
    void withGridDisabledAndNoGuideTheValueIsUnchanged() {
        assertThat(SnapEngine.snap(11.2, NO_SNAP, NO_GUIDES, 0.5)).isCloseTo(11.2, within(1e-9));
    }

    @Test
    void withGridDisabledStillSnapsToAGuideWithinThreshold() {
        assertThat(SnapEngine.snap(11.2, NO_SNAP, new double[] {11.0}, 0.5)).isCloseTo(11.0, within(1e-9));
    }

    @Test
    void strictlyNearestGuideWins() {
        // 11.3 (dist 0.1) beats 11.0 (dist 0.2); the far guide is beyond threshold.
        assertThat(SnapEngine.snap(11.2, NO_SNAP, new double[] {11.0, 11.3, 20.0}, 0.5))
                .isCloseTo(11.3, within(1e-9));
    }

    @Test
    void firstGuideKeptOnADistanceTie() {
        assertThat(SnapEngine.snap(11.2, NO_SNAP, new double[] {11.0, 11.4}, 0.5))
                .isCloseTo(11.0, within(1e-9));
    }
}
