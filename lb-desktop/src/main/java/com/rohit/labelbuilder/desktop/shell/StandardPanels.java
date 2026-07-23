package com.rohit.labelbuilder.desktop.shell;

import com.rohit.labelbuilder.desktop.dock.DockLayout;
import com.rohit.labelbuilder.desktop.dock.DockPanel;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import jakarta.annotation.PostConstruct;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.springframework.stereotype.Component;

/**
 * Registers the four standard tool panels (Phase 5d) with placeholder content; the owning
 * feature phases replace the content: Toolbox in Phase 8, Property Inspector in Phase 9,
 * Object Explorer and Layers alongside the object model work (Phases 7–8).
 */
@Component
public class StandardPanels {

    public static final String TOOLBOX = "panel.toolbox";
    public static final String OBJECT_EXPLORER = "panel.objects";
    public static final String PROPERTIES = "panel.properties";
    public static final String LAYERS = "panel.layers";

    private final DockPanelRegistry registry;

    public StandardPanels(DockPanelRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void registerAll() {
        registry.register(new DockPanel(TOOLBOX, "Toolbox", placeholder("Toolbox — tools arrive in Phase 8")));
        registry.register(new DockPanel(
                OBJECT_EXPLORER, "Objects", placeholder("Object Explorer — arrives with the object model (Phase 7)")));
        registry.register(
                new DockPanel(PROPERTIES, "Properties", placeholder("Property Inspector — arrives in Phase 9")));
        registry.register(new DockPanel(LAYERS, "Layers", placeholder("Layers — arrive in Phase 7")));
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
