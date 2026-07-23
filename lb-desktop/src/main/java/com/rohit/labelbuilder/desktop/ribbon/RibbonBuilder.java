package com.rohit.labelbuilder.desktop.ribbon;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.action.AppAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javafx.collections.SetChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link RibbonSpec} into the ribbon control — a styled {@code TabPane} (architecture
 * §10: build-from-scratch, kept deliberately minimal). All buttons are generated from the
 * {@link ActionRegistry}, so the ribbon shares text, tooltip, enablement and behaviour with the
 * menus generated from the same actions.
 *
 * <p>Contextual tabs are wired to {@link RibbonContexts}: the tab list is recomputed whenever the
 * active context set changes, preserving the current selection when it survives. The listener is
 * intentionally strong — the ribbon lives as long as the main window, which lives as long as the
 * app.
 *
 * <p>Must run on the FX thread (creates controls); structure validation belongs on the spec, not
 * here.
 */
@Component
public class RibbonBuilder {

    private static final int MAX_SMALL_PER_COLUMN = 3;

    private final ActionRegistry actions;

    public RibbonBuilder(ActionRegistry actions) {
        this.actions = actions;
    }

    public TabPane build(RibbonSpec spec, RibbonContexts contexts) {
        TabPane ribbon = new TabPane();
        ribbon.getStyleClass().add("ribbon");
        ribbon.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Build every tab once; visibility is which of them are in ribbon.getTabs().
        Map<RibbonSpec.TabSpec, Tab> tabs = new LinkedHashMap<>();
        for (RibbonSpec.TabSpec tabSpec : spec.tabs()) {
            tabs.put(tabSpec, buildTab(tabSpec));
        }

        Runnable sync = () -> {
            Tab selected = ribbon.getSelectionModel().getSelectedItem();
            ribbon.getTabs()
                    .setAll(spec.visibleTabs(Set.copyOf(contexts.active())).stream()
                            .map(tabs::get)
                            .toList());
            if (selected != null && ribbon.getTabs().contains(selected)) {
                ribbon.getSelectionModel().select(selected);
            }
        };
        contexts.active().addListener((SetChangeListener<String>) change -> sync.run());
        sync.run();
        return ribbon;
    }

    private Tab buildTab(RibbonSpec.TabSpec tabSpec) {
        Tab tab = new Tab(tabSpec.title());
        if (tabSpec.contextual()) {
            tab.getStyleClass().add("ribbon-contextual-tab");
        }
        HBox content = new HBox();
        content.getStyleClass().add("ribbon-tab-content");
        boolean first = true;
        for (RibbonSpec.GroupSpec groupSpec : tabSpec.groups()) {
            if (!first) {
                content.getChildren().add(new Separator(Orientation.VERTICAL));
            }
            first = false;
            content.getChildren().add(buildGroup(groupSpec));
        }
        tab.setContent(content);
        return tab;
    }

    private VBox buildGroup(RibbonSpec.GroupSpec spec) {
        HBox items = new HBox();
        items.getStyleClass().add("ribbon-group-items");

        VBox smallStack = null;
        for (RibbonSpec.ItemSpec item : spec.items()) {
            Node node = buildItem(item);
            if (isSmall(item)) {
                if (smallStack == null || smallStack.getChildren().size() == MAX_SMALL_PER_COLUMN) {
                    smallStack = new VBox();
                    smallStack.getStyleClass().add("ribbon-small-stack");
                    items.getChildren().add(smallStack);
                }
                smallStack.getChildren().add(node);
            } else {
                smallStack = null;
                items.getChildren().add(node);
            }
        }

        Label title = new Label(spec.title());
        title.getStyleClass().add("ribbon-group-title");

        VBox group = new VBox(items, title);
        group.getStyleClass().add("ribbon-group");
        group.setAlignment(Pos.BOTTOM_CENTER);
        return group;
    }

    private static boolean isSmall(RibbonSpec.ItemSpec item) {
        return switch (item) {
            case RibbonSpec.ActionItem a -> a.size() == RibbonSpec.Size.SMALL;
            case RibbonSpec.SplitItem s -> s.size() == RibbonSpec.Size.SMALL;
            case RibbonSpec.GalleryItem g -> false;
        };
    }

    private Node buildItem(RibbonSpec.ItemSpec item) {
        return switch (item) {
            case RibbonSpec.ActionItem a ->
                a.size() == RibbonSpec.Size.LARGE
                        ? asLarge(actionButton(new Button(), a.actionId()))
                        : asSmall(actionButton(new Button(), a.actionId()));
            case RibbonSpec.SplitItem s -> splitButton(s);
            case RibbonSpec.GalleryItem g -> gallery(g);
        };
    }

    private SplitMenuButton splitButton(RibbonSpec.SplitItem spec) {
        SplitMenuButton button = actionButton(new SplitMenuButton(), spec.actionId());
        button.getStyleClass().add("ribbon-split-button");
        for (String menuId : spec.menuActionIds()) {
            button.getItems().add(actions.createMenuItem(menuId));
        }
        return spec.size() == RibbonSpec.Size.LARGE ? asLarge(button) : asSmall(button);
    }

    /**
     * The gallery mechanism: the first {@code columns} options render inline as cells; a dropdown
     * arrow opens the full grid. Cells are plain action-backed buttons until galleries gain
     * previews with their real content (Phase 8).
     */
    private Node gallery(RibbonSpec.GalleryItem spec) {
        HBox strip = new HBox();
        strip.getStyleClass().add("ribbon-gallery");

        spec.optionActionIds().stream().limit(spec.columns()).forEach(id -> strip.getChildren()
                .add(galleryCell(id, null)));

        MenuButton more = new MenuButton("▾");
        more.getStyleClass().add("ribbon-gallery-more");
        more.setFocusTraversable(false);
        TilePane grid = new TilePane();
        grid.getStyleClass().add("ribbon-gallery-popup");
        grid.setPrefColumns(spec.columns());
        spec.optionActionIds().forEach(id -> grid.getChildren().add(galleryCell(id, more)));
        CustomMenuItem holder = new CustomMenuItem(grid, false);
        more.getItems().add(holder);
        strip.getChildren().add(more);
        return strip;
    }

    private Button galleryCell(String actionId, MenuButton popupToClose) {
        Button cell = actionButton(new Button(), actionId);
        cell.getStyleClass().add("ribbon-gallery-cell");
        if (popupToClose != null) {
            AppAction action = actions.get(actionId);
            cell.setOnAction(e -> {
                popupToClose.hide();
                action.run();
            });
        }
        return cell;
    }

    /** Base wiring shared by every ribbon button kind: text, tooltip, enablement, behaviour. */
    private <T extends ButtonBase> T actionButton(T button, String actionId) {
        AppAction action = actions.get(actionId);
        button.setText(action.text().replace("_", ""));
        if (action.longText() != null) {
            button.setTooltip(new Tooltip(action.longText()));
        }
        button.disableProperty().bind(action.enabledProperty().not());
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    private <T extends ButtonBase> T asLarge(T button) {
        button.getStyleClass().add("ribbon-large-button");
        button.setContentDisplay(ContentDisplay.TOP); // icon above text once icons arrive
        button.setWrapText(true);
        return button;
    }

    private <T extends ButtonBase> T asSmall(T button) {
        button.getStyleClass().add("ribbon-small-button");
        button.setMaxWidth(Double.MAX_VALUE); // fill the stack column so labels align
        button.setAlignment(Pos.CENTER_LEFT);
        return button;
    }
}
