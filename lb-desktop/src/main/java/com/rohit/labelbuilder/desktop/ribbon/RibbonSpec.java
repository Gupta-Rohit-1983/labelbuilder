package com.rohit.labelbuilder.desktop.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Declarative description of a ribbon: tabs contain titled groups, groups contain action-backed
 * items. Pure data, no JavaFX — so a ribbon's structure can be validated headlessly (ids exist,
 * no duplicates) while {@link RibbonBuilder} owns the one place that turns it into controls.
 *
 * <p>Item vocabulary (Phase 4c):
 *
 * <ul>
 *   <li>{@link ActionItem} — a plain button. {@code LARGE} items get their own full-height
 *       column; consecutive {@code SMALL} items stack vertically, up to three per column.
 *   <li>{@link SplitItem} — a split button: a primary action plus a dropdown of related actions
 *       (e.g. Save ▾ Save As). Stacks like its size.
 *   <li>{@link GalleryItem} — a strip of option cells with a dropdown showing the full grid.
 *       Gets real content in Phase 8 (styles, shapes); the mechanism lands here.
 * </ul>
 *
 * <p>A tab with a {@code contextKey} is <em>contextual</em>: it is only shown while that key is
 * active in {@link RibbonContexts} (e.g. {@code "selection.barcode"} once the canvas exists).
 */
public record RibbonSpec(List<TabSpec> tabs) {

    /** Tabs visible for the given active context keys — static tabs plus active contextual ones, in spec order. */
    public List<TabSpec> visibleTabs(Set<String> activeContextKeys) {
        return tabs.stream()
                .filter(tab -> !tab.contextual() || activeContextKeys.contains(tab.contextKey()))
                .toList();
    }

    public record TabSpec(String title, String contextKey, List<GroupSpec> groups) {

        /** A static (always visible) tab. */
        public TabSpec(String title, List<GroupSpec> groups) {
            this(title, null, groups);
        }

        public boolean contextual() {
            return contextKey != null;
        }
    }

    public record GroupSpec(String title, List<ItemSpec> items) {}

    /** One entry in a group. Sealed so {@link RibbonBuilder} can switch exhaustively. */
    public sealed interface ItemSpec {

        static ActionItem large(String actionId) {
            return new ActionItem(actionId, Size.LARGE);
        }

        static ActionItem small(String actionId) {
            return new ActionItem(actionId, Size.SMALL);
        }

        static SplitItem splitLarge(String primaryActionId, String... menuActionIds) {
            return new SplitItem(primaryActionId, Size.LARGE, List.of(menuActionIds));
        }

        static SplitItem splitSmall(String primaryActionId, String... menuActionIds) {
            return new SplitItem(primaryActionId, Size.SMALL, List.of(menuActionIds));
        }

        static GalleryItem gallery(String title, int columns, String... optionActionIds) {
            return new GalleryItem(title, columns, List.of(optionActionIds));
        }

        /** Every action id the item references — for validation sweeps. */
        default List<String> actionIds() {
            return switch (this) {
                case ActionItem item -> List.of(item.actionId());
                case SplitItem item -> {
                    List<String> ids = new ArrayList<>();
                    ids.add(item.actionId());
                    ids.addAll(item.menuActionIds());
                    yield List.copyOf(ids);
                }
                case GalleryItem item -> item.optionActionIds();
            };
        }
    }

    public record ActionItem(String actionId, Size size) implements ItemSpec {}

    public record SplitItem(String actionId, Size size, List<String> menuActionIds) implements ItemSpec {}

    public record GalleryItem(String title, int columns, List<String> optionActionIds) implements ItemSpec {}

    public enum Size {
        LARGE,
        SMALL
    }
}
