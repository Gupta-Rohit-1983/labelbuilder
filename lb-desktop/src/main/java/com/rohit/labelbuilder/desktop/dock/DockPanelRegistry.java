package com.rohit.labelbuilder.desktop.dock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * All dockable panels, keyed by id — the docking analogue of the ActionRegistry. Layouts refer to
 * panels by id only; lookup of an unknown id fails fast so a typo'd layout explodes at startup,
 * not as a mysteriously empty tab.
 */
@Component
public class DockPanelRegistry {

    private final Map<String, DockPanel> panels = new LinkedHashMap<>();

    public void register(DockPanel panel) {
        DockPanel previous = panels.putIfAbsent(panel.id(), panel);
        if (previous != null) {
            throw new IllegalStateException("Panel id already registered: " + panel.id());
        }
    }

    public DockPanel get(String id) {
        DockPanel panel = panels.get(id);
        if (panel == null) {
            throw new IllegalArgumentException("Unknown panel id: " + id);
        }
        return panel;
    }

    public Set<String> ids() {
        return Set.copyOf(panels.keySet());
    }
}
