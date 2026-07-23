package com.rohit.labelbuilder.desktop.dock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Orientation;
import javafx.geometry.Side;

/**
 * Immutable docking layout: a tree of splits and tab groups around exactly one {@link
 * DockNode.Center} (the document/workspace slot — the canvas from Phase 6). Pure data with pure
 * transforms, so docking logic is fully unit-testable headlessly; {@link DockStationBuilder} owns
 * the one place the tree becomes {@code SplitPane}/{@code TabPane} controls (architecture §10:
 * build-from-scratch, kept minimal).
 *
 * <p>Every transform returns a new layout; the tree is normalised on the way out (empty groups
 * pruned, single-child splits collapsed), so no operation can produce a degenerate tree.
 */
public record DockLayout(DockNode root) {

    /** Share of the split given to a newly docked panel strip. */
    private static final double PANEL_SHARE = 0.25;

    public DockLayout {
        List<String> ids = new ArrayList<>();
        int centers = countCenters(root, ids);
        if (centers != 1) {
            throw new IllegalArgumentException("Layout must contain exactly one Center, found " + centers);
        }
        if (ids.size() != new LinkedHashSet<>(ids).size()) {
            throw new IllegalArgumentException("Duplicate panel ids in layout: " + ids);
        }
    }

    /** The starting layout: just the document area, no panels. */
    public static DockLayout centerOnly() {
        return new DockLayout(new DockNode.Center());
    }

    /** Docks a panel as a new strip on the given side of the whole workspace. */
    public DockLayout dockToSide(String panelId, Side side) {
        requireAbsent(panelId);
        DockNode.Group group = new DockNode.Group(List.of(panelId), panelId);
        boolean leading = side == Side.LEFT || side == Side.TOP;
        Orientation orientation =
                (side == Side.LEFT || side == Side.RIGHT) ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        List<DockNode> children = leading ? List.of(group, root) : List.of(root, group);
        double divider = leading ? PANEL_SHARE : 1 - PANEL_SHARE;
        return new DockLayout(new DockNode.Split(orientation, children, List.of(divider)));
    }

    /** Docks a panel as a new tab in the group that already holds {@code existingPanelId}, selected. */
    public DockLayout dockBeside(String panelId, String existingPanelId) {
        requireAbsent(panelId);
        if (!contains(existingPanelId)) {
            throw new IllegalArgumentException("No group holds panel: " + existingPanelId);
        }
        return new DockLayout(mapGroups(root, group -> {
            if (!group.panelIds().contains(existingPanelId)) {
                return group;
            }
            List<String> ids = new ArrayList<>(group.panelIds());
            ids.add(panelId);
            return new DockNode.Group(List.copyOf(ids), panelId);
        }));
    }

    /** Makes the panel the selected tab of its group. */
    public DockLayout withSelected(String panelId) {
        if (!contains(panelId)) {
            throw new IllegalArgumentException("Panel not in layout: " + panelId);
        }
        return new DockLayout(mapGroups(
                root,
                group -> group.panelIds().contains(panelId) ? new DockNode.Group(group.panelIds(), panelId) : group));
    }

    /** Removes a panel; its group is pruned when empty and single-child splits collapse. */
    public DockLayout remove(String panelId) {
        if (!contains(panelId)) {
            throw new IllegalArgumentException("Panel not in layout: " + panelId);
        }
        DockNode pruned = prune(root, panelId);
        // The Center can never be removed, so the tree never prunes to nothing.
        return new DockLayout(pruned);
    }

    public boolean contains(String panelId) {
        return panelIds().contains(panelId);
    }

    /** The tab group currently holding the panel, if any. */
    public java.util.Optional<DockNode.Group> groupOf(String panelId) {
        return findGroup(root, panelId);
    }

    /**
     * Which workspace edge the panel's strip sits on, judged at the topmost split where the panel's
     * subtree and the Center's subtree diverge. Falls back to {@code RIGHT} for panels not in the
     * layout — callers use this as the "put it back somewhere sensible" default.
     */
    public Side sideOf(String panelId) {
        return findSide(root, panelId).orElse(Side.RIGHT);
    }

    private static java.util.Optional<Side> findSide(DockNode node, String panelId) {
        if (!(node instanceof DockNode.Split split)) {
            return java.util.Optional.empty();
        }
        int panelIndex = -1;
        int centerIndex = -1;
        for (int i = 0; i < split.children().size(); i++) {
            DockNode child = split.children().get(i);
            if (subtreeContainsPanel(child, panelId)) {
                panelIndex = i;
            }
            if (subtreeContainsCenter(child)) {
                centerIndex = i;
            }
        }
        if (panelIndex < 0) {
            return java.util.Optional.empty();
        }
        if (panelIndex == centerIndex) {
            return findSide(split.children().get(panelIndex), panelId);
        }
        boolean before = panelIndex < centerIndex;
        return java.util.Optional.of(
                split.orientation() == Orientation.HORIZONTAL
                        ? (before ? Side.LEFT : Side.RIGHT)
                        : (before ? Side.TOP : Side.BOTTOM));
    }

    private static boolean subtreeContainsPanel(DockNode node, String panelId) {
        List<String> ids = new ArrayList<>();
        countCenters(node, ids);
        return ids.contains(panelId);
    }

    private static boolean subtreeContainsCenter(DockNode node) {
        return countCenters(node, new ArrayList<>()) > 0;
    }

    private static java.util.Optional<DockNode.Group> findGroup(DockNode node, String panelId) {
        return switch (node) {
            case DockNode.Center center -> java.util.Optional.empty();
            case DockNode.Group group ->
                group.panelIds().contains(panelId) ? java.util.Optional.of(group) : java.util.Optional.empty();
            case DockNode.Split split ->
                split.children().stream()
                        .map(child -> findGroup(child, panelId))
                        .flatMap(java.util.Optional::stream)
                        .findFirst();
        };
    }

    public Set<String> panelIds() {
        List<String> ids = new ArrayList<>();
        countCenters(root, ids);
        return new LinkedHashSet<>(ids);
    }

    private void requireAbsent(String panelId) {
        if (contains(panelId)) {
            throw new IllegalArgumentException("Panel already docked: " + panelId);
        }
    }

    private static int countCenters(DockNode node, List<String> idsOut) {
        return switch (node) {
            case DockNode.Center center -> 1;
            case DockNode.Group group -> {
                idsOut.addAll(group.panelIds());
                yield 0;
            }
            case DockNode.Split split ->
                split.children().stream()
                        .mapToInt(child -> countCenters(child, idsOut))
                        .sum();
        };
    }

    private static DockNode mapGroups(DockNode node, java.util.function.UnaryOperator<DockNode.Group> mapper) {
        return switch (node) {
            case DockNode.Center center -> center;
            case DockNode.Group group -> mapper.apply(group);
            case DockNode.Split split ->
                new DockNode.Split(
                        split.orientation(),
                        split.children().stream()
                                .map(child -> mapGroups(child, mapper))
                                .toList(),
                        split.dividers());
        };
    }

    /** Removes the panel, pruning empty groups and collapsing single-child splits. */
    private static DockNode prune(DockNode node, String panelId) {
        return switch (node) {
            case DockNode.Center center -> center;
            case DockNode.Group group -> {
                if (!group.panelIds().contains(panelId)) {
                    yield group;
                }
                List<String> remaining = group.panelIds().stream()
                        .filter(id -> !id.equals(panelId))
                        .toList();
                if (remaining.isEmpty()) {
                    yield null;
                }
                String selected = remaining.contains(group.selectedId()) ? group.selectedId() : remaining.get(0);
                yield new DockNode.Group(remaining, selected);
            }
            case DockNode.Split split -> {
                List<DockNode> children = split.children().stream()
                        .map(child -> prune(child, panelId))
                        .filter(child -> child != null)
                        .toList();
                if (children.size() == 1) {
                    yield children.get(0);
                }
                // Divider positions no longer line up after a removal; fall back to even spread.
                yield new DockNode.Split(split.orientation(), children, evenDividers(children.size()));
            }
        };
    }

    private static List<Double> evenDividers(int childCount) {
        List<Double> dividers = new ArrayList<>();
        for (int i = 1; i < childCount; i++) {
            dividers.add((double) i / childCount);
        }
        return List.copyOf(dividers);
    }

    /** The layout tree. Sealed so renderers and transforms switch exhaustively. */
    public sealed interface DockNode {

        /** The document/workspace slot — exactly one per layout, never removable. */
        record Center() implements DockNode {}

        /** A tab group of panels; {@code selectedId} is the visible tab. */
        record Group(List<String> panelIds, String selectedId) implements DockNode {}

        /** A split container; {@code dividers} has {@code children.size() - 1} positions in (0,1). */
        record Split(Orientation orientation, List<DockNode> children, List<Double> dividers) implements DockNode {}
    }
}
