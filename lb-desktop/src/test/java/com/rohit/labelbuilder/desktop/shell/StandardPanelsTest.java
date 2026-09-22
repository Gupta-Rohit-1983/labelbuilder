package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.dock.DockLayout;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.desktop.panels.LayersPanel;
import com.rohit.labelbuilder.desktop.panels.ObjectsPanel;
import com.rohit.labelbuilder.desktop.panels.ToolboxPanel;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Registration and default layout are pure — the panel content suppliers are never invoked. */
class StandardPanelsTest {

    // The panel beans are cheap to construct; their FX views are only built when a supplier runs,
    // which registration never does — so this stays toolkit-free.
    private static ToolboxPanel toolbox() {
        return new ToolboxPanel(new ActionRegistry());
    }

    private static ObjectsPanel objects() {
        return new ObjectsPanel(new DocumentSession());
    }

    private static LayersPanel layers() {
        return new LayersPanel(new DocumentSession());
    }

    @Test
    void registersTheFourStandardPanels() {
        DockPanelRegistry registry = new DockPanelRegistry();

        new StandardPanels(registry, toolbox(), objects(), layers()).registerAll();

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
