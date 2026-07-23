package com.rohit.labelbuilder.desktop.dock;

import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.Node;

/**
 * A dockable tool panel: identity, display title and a content factory. The factory is invoked
 * each time the panel is (re)rendered into a layout — content must not be cached across layouts,
 * because a node can only live in one scene-graph place at a time.
 *
 * <p>The standard panels (Property Inspector, Object Explorer, Layers, Toolbox) register in
 * Phase 5d; feature phases replace their placeholder content.
 */
public record DockPanel(String id, String title, Supplier<Node> content) {

    public DockPanel {
        Objects.requireNonNull(id, "panel id");
        Objects.requireNonNull(title, "panel title");
        Objects.requireNonNull(content, "panel content factory");
    }
}
