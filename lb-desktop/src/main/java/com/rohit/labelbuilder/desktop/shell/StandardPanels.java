package com.rohit.labelbuilder.desktop.shell;

import com.rohit.labelbuilder.desktop.dock.DockLayout;
import com.rohit.labelbuilder.desktop.dock.DockPanel;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import com.rohit.labelbuilder.desktop.panels.LayersPanel;
import com.rohit.labelbuilder.desktop.panels.ObjectsPanel;
import com.rohit.labelbuilder.desktop.panels.ToolboxPanel;
import com.rohit.labelbuilder.desktop.panels.inspector.PropertyInspectorPanel;
import jakarta.annotation.PostConstruct;
import javafx.geometry.Side;
import org.springframework.stereotype.Component;

/**
 * Registers the four standard tool panels (Phase 5d). All four serve real views as of Phase 9a —
 * Toolbox, Object Explorer and Layers from 8d, the Property Inspector from 9a.
 *
 * <p>Content is supplied lazily, so a panel's view is only built when the docking station actually
 * shows it.
 */
@Component
public class StandardPanels {

    public static final String TOOLBOX = "panel.toolbox";
    public static final String OBJECT_EXPLORER = "panel.objects";
    public static final String PROPERTIES = "panel.properties";
    public static final String LAYERS = "panel.layers";

    private final DockPanelRegistry registry;
    private final ToolboxPanel toolbox;
    private final ObjectsPanel objects;
    private final LayersPanel layers;
    private final PropertyInspectorPanel inspector;

    public StandardPanels(
            DockPanelRegistry registry,
            ToolboxPanel toolbox,
            ObjectsPanel objects,
            LayersPanel layers,
            PropertyInspectorPanel inspector) {
        this.registry = registry;
        this.toolbox = toolbox;
        this.objects = objects;
        this.layers = layers;
        this.inspector = inspector;
    }

    @PostConstruct
    void registerAll() {
        registry.register(new DockPanel(TOOLBOX, "Toolbox", toolbox::create));
        registry.register(new DockPanel(OBJECT_EXPLORER, "Objects", objects::create));
        registry.register(new DockPanel(PROPERTIES, "Properties", inspector::create));
        registry.register(new DockPanel(LAYERS, "Layers", this.layers::create));
    }

    /** The out-of-the-box workspace: Toolbox left; Properties/Objects/Layers tabbed right. */
    public static DockLayout defaultLayout() {
        return DockLayout.centerOnly()
                .dockToSide(TOOLBOX, Side.LEFT)
                .dockToSide(PROPERTIES, Side.RIGHT)
                .dockBeside(OBJECT_EXPLORER, PROPERTIES)
                .dockBeside(LAYERS, PROPERTIES)
                .withSelected(PROPERTIES);
    }
}
