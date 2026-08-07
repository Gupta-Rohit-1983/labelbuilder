package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GridSettingsTest {

    @Test
    void snapRoundsToNearestGridLine() {
        GridSettings grid = new GridSettings(5, true, true);

        assertThat(grid.snap(11.2)).isCloseTo(10, within(1e-9));
        assertThat(grid.snap(13)).isCloseTo(15, within(1e-9));
        assertThat(grid.snap(-2)).isCloseTo(0, within(1e-9));
        assertThat(grid.snap(-3)).isCloseTo(-5, within(1e-9));
    }

    @Test
    void togglesPreserveOtherFields() {
        GridSettings grid = GridSettings.defaults();

        assertThat(grid.withShowGrid(false).showGrid()).isFalse();
        assertThat(grid.withShowGrid(false).snapToGrid()).isTrue();
        assertThat(grid.withSnapToGrid(false).snapToGrid()).isFalse();
        assertThat(grid.withSnapToGrid(false).spacingMm()).isEqualTo(grid.spacingMm());
    }

    @Test
    void rejectsNonPositiveSpacing() {
        assertThatThrownBy(() -> new GridSettings(0, true, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
