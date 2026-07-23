package com.rohit.labelbuilder.desktop.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.desktop.dock.DockLayout.DockNode;
import java.util.List;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Pure-data layout transforms — fully headless (javafx.geometry needs no toolkit). */
class DockLayoutTest {

    @Test
    void centerOnlyHasNoPanels() {
        DockLayout layout = DockLayout.centerOnly();

        assertThat(layout.panelIds()).isEmpty();
        assertThat(layout.root()).isInstanceOf(DockNode.Center.class);
    }

    @Test
    void dockLeftPutsPanelStripBeforeCenterInAHorizontalSplit() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.toolbox", Side.LEFT);

        DockNode.Split split = (DockNode.Split) layout.root();
        assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(split.children().get(0)).isInstanceOf(DockNode.Group.class);
        assertThat(split.children().get(1)).isInstanceOf(DockNode.Center.class);
        assertThat(split.dividers()).containsExactly(0.25);
    }

    @Test
    void dockBottomPutsPanelStripAfterCenterInAVerticalSplit() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.log", Side.BOTTOM);

        DockNode.Split split = (DockNode.Split) layout.root();
        assertThat(split.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(split.children().get(0)).isInstanceOf(DockNode.Center.class);
        assertThat(split.children().get(1)).isInstanceOf(DockNode.Group.class);
        assertThat(split.dividers()).containsExactly(0.75);
    }

    @Test
    void dockBesideAddsTabToExistingGroupAndSelectsIt() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.properties", Side.RIGHT).dockBeside("p.layers", "p.properties");

        DockNode.Split split = (DockNode.Split) layout.root();
        DockNode.Group group = (DockNode.Group) split.children().get(1);
        assertThat(group.panelIds()).containsExactly("p.properties", "p.layers");
        assertThat(group.selectedId()).isEqualTo("p.layers");
    }

    @Test
    void duplicateDockIsRejected() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.same", Side.LEFT);

        assertThatThrownBy(() -> layout.dockToSide("p.same", Side.RIGHT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.same");
    }

    @Test
    void removingLastPanelOfAGroupCollapsesTheSplit() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.only", Side.LEFT).remove("p.only");

        assertThat(layout.root()).isInstanceOf(DockNode.Center.class);
        assertThat(layout.panelIds()).isEmpty();
    }

    @Test
    void removingOneTabKeepsTheGroupWithAValidSelection() {
        DockLayout layout = DockLayout.centerOnly()
                .dockToSide("p.a", Side.RIGHT)
                .dockBeside("p.b", "p.a")
                .remove("p.b"); // p.b was selected

        DockNode.Split split = (DockNode.Split) layout.root();
        DockNode.Group group = (DockNode.Group) split.children().get(1);
        assertThat(group.panelIds()).containsExactly("p.a");
        assertThat(group.selectedId()).isEqualTo("p.a");
    }

    @Test
    void removingUnknownPanelFailsFast() {
        assertThatThrownBy(() -> DockLayout.centerOnly().remove("p.ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.ghost");
    }

    @Test
    void layoutWithoutACenterIsRejected() {
        assertThatThrownBy(() -> new DockLayout(new DockNode.Group(List.of("p.orphan"), "p.orphan")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Center");
    }

    @Test
    void duplicatePanelIdsInATreeAreRejected() {
        DockNode dupTree = new DockNode.Split(
                Orientation.HORIZONTAL,
                List.of(
                        new DockNode.Group(List.of("p.dup"), "p.dup"),
                        new DockNode.Center(),
                        new DockNode.Group(List.of("p.dup"), "p.dup")),
                List.of(0.2, 0.8));

        assertThatThrownBy(() -> new DockLayout(dupTree))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void stackedDockingBuildsNestedSplits() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.toolbox", Side.LEFT).dockToSide("p.log", Side.BOTTOM);

        DockNode.Split outer = (DockNode.Split) layout.root();
        assertThat(outer.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(outer.children().get(0)).isInstanceOf(DockNode.Split.class);
        assertThat(layout.panelIds()).containsExactly("p.toolbox", "p.log");
    }
}
