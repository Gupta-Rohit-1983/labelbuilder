package com.rohit.labelbuilder.desktop.shell;

import com.rohit.labelbuilder.desktop.dock.DockLayout;
import com.rohit.labelbuilder.desktop.dock.DockPanel;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import com.rohit.labelbuilder.desktop.panels.LayersPanel;
import com.rohit.labelbuilder.desktop.panels.ObjectsPanel;
import com.rohit.labelbuilder.desktop.panels.ToolboxPanel;
import jakarta.annotation.PostConstruct;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.springframework.stereotype.Component;

/**
 * Registers the four standard tool panels (Phase 5d). Toolbox, Object Explorer and Layers are live as
 * of Phase 8d; the Property Inspector is still a placeholder until Phase 9.
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

    public StandardPanels(DockPanelRegistry registry, ToolboxPanel toolbox, ObjectsPanel objects, LayersPanel layers) {
        this.registry = registry;
        this.toolbox = toolbox;
        this.objects = objects;
        this.layers = layers;
    }

    @PostConstruct
    void registerAll() {
        registry.register(new DockPanel(TOOLBOX, "Toolbox", toolbox::create));
        registry.register(new DockPanel(OBJECT_EXPLORER, "Objects", objects::create));
        registry.register(new DockPanel(
                PROPERTIES,
                "Properties",
                placeholder("Property Inspector — edits the selected element; arrives in Phase 9")));
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

    private static java.util.function.Supplier<Node> placeholder(String text) {
        return () -> {
            Label label = new Label(text);
            label.setWrapText(true);
            StackPane pane = new StackPane(label);
            pane.getStyleClass().add("dock-panel-placeholder");
            return pane;
        };
    }
}
