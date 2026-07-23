package com.rohit.labelbuilder.desktop.dock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Side;

/**
 * Complete docking state: the docked {@link DockLayout} plus panels that are currently
 * auto-hidden (collapsed to an edge button, remembering which edge) or floating in their own
 * window. A panel lives in exactly one of the three places — the constructor enforces it.
 *
 * <p>Pure data with pure transforms (like {@link DockLayout}); {@link DockStation} renders it and
 * owns the windows/sidebars. Phase 5d persists this whole record.
 */
public record DockState(DockLayout layout, Map<String, Side> autoHidden, Set<String> floating) {

    public DockState {
        autoHidden = new LinkedHashMap<>(autoHidden);
        floating = new LinkedHashSet<>(floating);
        for (String id : autoHidden.keySet()) {
            if (layout.contains(id) || floating.contains(id)) {
                throw new IllegalArgumentException("Panel in more than one place: " + id);
            }
        }
        for (String id : floating) {
            if (layout.contains(id)) {
                throw new IllegalArgumentException("Panel in more than one place: " + id);
            }
        }
    }

    public static DockState of(DockLayout layout) {
        return new DockState(layout, Map.of(), Set.of());
    }

    /** Undocks the panel into its own floating window. */
    public DockState floatPanel(String panelId) {
        DockState without = without(panelId);
        Set<String> newFloating = new LinkedHashSet<>(without.floating);
        newFloating.add(panelId);
        return new DockState(without.layout, without.autoHidden, newFloating);
    }

    /** Collapses the panel to the edge button bar of the side its strip currently touches. */
    public DockState autoHidePanel(String panelId) {
        Side side = layout.sideOf(panelId);
        DockState without = without(panelId);
        Map<String, Side> newHidden = new LinkedHashMap<>(without.autoHidden);
        newHidden.put(panelId, side);
        return new DockState(without.layout, newHidden, without.floating);
    }

    /** Returns the panel from floating/auto-hidden state into the docked layout on {@code side}. */
    public DockState dockBack(String panelId, Side side) {
        DockState without = without(panelId);
        return new DockState(without.layout.dockToSide(panelId, side), without.autoHidden, without.floating);
    }

    /** Where the panel would return to: its remembered auto-hide side, or a sensible default. */
    public Side homeSideOf(String panelId) {
        return autoHidden.getOrDefault(panelId, layout.contains(panelId) ? layout.sideOf(panelId) : Side.RIGHT);
    }

    private DockState without(String panelId) {
        DockLayout newLayout = layout.contains(panelId) ? layout.remove(panelId) : layout;
        Map<String, Side> newHidden = new LinkedHashMap<>(autoHidden);
        newHidden.remove(panelId);
        Set<String> newFloating = new LinkedHashSet<>(floating);
        newFloating.remove(panelId);
        return new DockState(newLayout, newHidden, newFloating);
    }
}
