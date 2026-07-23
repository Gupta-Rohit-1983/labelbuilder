package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.dock.DockLayout;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Registration and default layout are pure — the panel content suppliers are never invoked. */
class StandardPanelsTest {

    @Test
    void registersTheFourStandardPanels() {
        DockPanelRegistry registry = new DockPanelRegistry();

        new StandardPanels(registry).registerAll();

        assertThat(registry.ids())
                .containsExactlyInAnyOrder(
                        StandardPanels.TOOLBOX,
                        StandardPanels.OBJECT_EXPLORER,
                        StandardPanels.PROPERTIES,
                        StandardPanels.LAYERS);
    }

    @Test
    void defaultLayoutShapesTheStandardWorkspace() {
        DockLayout layout = StandardPanels.defaultLayout();

        assertThat(layout.sideOf(StandardPanels.TOOLBOX)).isEqualTo(Side.LEFT);
        assertThat(layout.groupOf(StandardPanels.PROPERTIES).orElseThrow().panelIds())
                .containsExactly(StandardPanels.PROPERTIES, StandardPanels.OBJECT_EXPLORER, StandardPanels.LAYERS);
        // Properties is the foreground tab out of the box.
        assertThat(layout.groupOf(StandardPanels.PROPERTIES).orElseThrow().selectedId())
                .isEqualTo(StandardPanels.PROPERTIES);
    }
}
