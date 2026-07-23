package com.rohit.labelbuilder.desktop.dock;

import javafx.geometry.Side;

/**
 * Composed docking gestures as pure layout transforms: a drag-and-drop is always
 * "remove from wherever it is, then dock at the target". Kept separate from {@link DockLayout}'s
 * primitive transforms so the drag layer ({@link DockStation}) contains no layout logic at all —
 * every gesture outcome is unit-testable headlessly.
 */
public final class DockMoves {

    private DockMoves() {}

    /** Moves a panel to a new strip on the given workspace side. */
    public static DockLayout toSide(DockLayout layout, String panelId, Side side) {
        DockLayout without = layout.contains(panelId) ? layout.remove(panelId) : layout;
        return without.dockToSide(panelId, side);
    }

    /**
     * Moves a panel into the tab group holding {@code targetPanelId}. Dropping a panel onto its
     * own group (or onto itself) is a no-op, never an error — that is the natural end of an
     * aborted drag gesture.
     */
    public static DockLayout intoGroupOf(DockLayout layout, String panelId, String targetPanelId) {
        if (panelId.equals(targetPanelId)) {
            return layout;
        }
        boolean alreadyThere = layout.groupOf(targetPanelId)
                .map(group -> group.panelIds().contains(panelId))
                .orElse(false);
        if (alreadyThere) {
            return layout;
        }
        DockLayout without = layout.contains(panelId) ? layout.remove(panelId) : layout;
        return without.dockBeside(panelId, targetPanelId);
    }
}
