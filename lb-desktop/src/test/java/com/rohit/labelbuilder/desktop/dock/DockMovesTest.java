package com.rohit.labelbuilder.desktop.dock;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.dock.DockLayout.DockNode;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Every drag-gesture outcome as a pure transform — fully headless. */
class DockMovesTest {

    @Test
    void movingAPanelBetweenSidesLeavesOneStrip() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.props", Side.RIGHT);

        DockLayout moved = DockMoves.toSide(layout, "p.props", Side.LEFT);

        DockNode.Split split = (DockNode.Split) moved.root();
        assertThat(split.children().get(0)).isInstanceOf(DockNode.Group.class);
        assertThat(split.children().get(1)).isInstanceOf(DockNode.Center.class);
        assertThat(moved.panelIds()).containsExactly("p.props");
    }

    @Test
    void movingIntoAnotherGroupMergesTabs() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.props", Side.RIGHT).dockToSide("p.layers", Side.BOTTOM);

        DockLayout moved = DockMoves.intoGroupOf(layout, "p.layers", "p.props");

        assertThat(moved.groupOf("p.props").orElseThrow().panelIds()).containsExactly("p.props", "p.layers");
        // The bottom strip collapsed away with its last panel.
        DockNode.Split root = (DockNode.Split) moved.root();
        assertThat(root.children()).hasSize(2);
    }

    @Test
    void droppingOntoOwnGroupIsANoOp() {
        DockLayout layout =
                DockLayout.centerOnly().dockToSide("p.props", Side.RIGHT).dockBeside("p.layers", "p.props");

        assertThat(DockMoves.intoGroupOf(layout, "p.layers", "p.props")).isEqualTo(layout);
        assertThat(DockMoves.intoGroupOf(layout, "p.props", "p.props")).isEqualTo(layout);
    }

    @Test
    void groupOfFindsThePanelsGroup() {
        DockLayout layout = DockLayout.centerOnly().dockToSide("p.a", Side.LEFT).dockToSide("p.b", Side.BOTTOM);

        assertThat(layout.groupOf("p.b").orElseThrow().panelIds()).containsExactly("p.b");
        assertThat(layout.groupOf("p.ghost")).isEmpty();
    }
}
