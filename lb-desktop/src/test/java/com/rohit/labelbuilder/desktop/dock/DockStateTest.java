package com.rohit.labelbuilder.desktop.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Pure float/auto-hide state transforms — fully headless. */
class DockStateTest {

    private static DockState docked(String panelId, Side side) {
        return DockState.of(DockLayout.centerOnly().dockToSide(panelId, side));
    }

    @Test
    void floatingRemovesFromLayoutAndTracksThePanel() {
        DockState state = docked("p.props", Side.RIGHT).floatPanel("p.props");

        assertThat(state.layout().contains("p.props")).isFalse();
        assertThat(state.floating()).containsExactly("p.props");
        assertThat(state.autoHidden()).isEmpty();
    }

    @Test
    void autoHideRemembersTheEdgeThePanelCameFrom() {
        DockState state = docked("p.toolbox", Side.LEFT).autoHidePanel("p.toolbox");

        assertThat(state.layout().contains("p.toolbox")).isFalse();
        assertThat(state.autoHidden()).containsEntry("p.toolbox", Side.LEFT);
    }

    @Test
    void dockBackReturnsThePanelToTheLayout() {
        DockState state = docked("p.props", Side.RIGHT).floatPanel("p.props").dockBack("p.props", Side.RIGHT);

        assertThat(state.layout().contains("p.props")).isTrue();
        assertThat(state.floating()).isEmpty();
        assertThat(state.autoHidden()).isEmpty();
    }

    @Test
    void homeSideIsTheRememberedAutoHideSide() {
        DockState state = docked("p.toolbox", Side.LEFT).autoHidePanel("p.toolbox");

        assertThat(state.homeSideOf("p.toolbox")).isEqualTo(Side.LEFT);
        assertThat(state.homeSideOf("p.unknown")).isEqualTo(Side.RIGHT);
    }

    @Test
    void aPanelCannotBeInTwoPlaces() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.dup", Side.LEFT);

        assertThatThrownBy(() -> new DockState(layout, Map.of("p.dup", Side.LEFT), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.dup");
        assertThatThrownBy(() -> new DockState(layout, Map.of(), Set.of("p.dup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.dup");
        assertThatThrownBy(() -> new DockState(DockLayout.centerOnly(), Map.of("p.dup", Side.LEFT), Set.of("p.dup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.dup");
    }

    @Test
    void sideOfJudgesTheTopmostDivergence() {
        DockLayout layout = DockLayout.centerOnly()
                .dockToSide("p.left", Side.LEFT)
                .dockToSide("p.bottom", Side.BOTTOM)
                .dockToSide("p.right", Side.RIGHT);

        assertThat(layout.sideOf("p.left")).isEqualTo(Side.LEFT);
        assertThat(layout.sideOf("p.bottom")).isEqualTo(Side.BOTTOM);
        assertThat(layout.sideOf("p.right")).isEqualTo(Side.RIGHT);
        assertThat(layout.sideOf("p.ghost")).isEqualTo(Side.RIGHT); // sensible default
    }

    @Test
    void tabbedPanelsShareTheirGroupsSide() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.props", Side.RIGHT).dockBeside("p.layers", "p.props");

        assertThat(layout.sideOf("p.layers")).isEqualTo(Side.RIGHT);
    }
}
