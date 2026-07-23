package com.rohit.labelbuilder.desktop.action;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import org.springframework.stereotype.Component;

/**
 * The central action table (architecture §10): every command is registered here once, and all UI
 * surfaces — menus, toolbar, later the ribbon and context menus — are generated from it, so text,
 * shortcut, enablement and behaviour can never drift apart between surfaces.
 *
 * <p>Lookup of an unknown id throws immediately: a typo in a menu declaration must fail at startup
 * (covered by the context test), never as a dead menu item discovered by a user.
 */
@Component
public class ActionRegistry {

    /** Sentinel for {@link #createMenu} marking a separator position. */
    public static final String SEPARATOR = "|";

    private final Map<String, AppAction> actions = new LinkedHashMap<>();

    public void register(AppAction action) {
        AppAction previous = actions.putIfAbsent(action.id(), action);
        if (previous != null) {
            throw new IllegalStateException("Action id already registered: " + action.id());
        }
    }

    public AppAction get(String id) {
        AppAction action = actions.get(id);
        if (action == null) {
            throw new IllegalArgumentException("Unknown action id: " + id);
        }
        return action;
    }

    /** A menu item rendering the action: text with mnemonic, accelerator, live enablement. */
    public MenuItem createMenuItem(String id) {
        AppAction action = get(id);
        MenuItem item = new MenuItem(action.text());
        if (action.accelerator() != null) {
            item.setAccelerator(action.accelerator());
        }
        item.disableProperty().bind(action.enabledProperty().not());
        item.setOnAction(e -> action.run());
        return item;
    }

    /** A whole menu from action ids; {@link #SEPARATOR} entries become separators. */
    public Menu createMenu(String text, String... actionIds) {
        Menu menu = new Menu(text);
        for (String id : actionIds) {
            menu.getItems().add(SEPARATOR.equals(id) ? new SeparatorMenuItem() : createMenuItem(id));
        }
        return menu;
    }

    /** A toolbar button rendering the action: plain text (no mnemonic), tooltip, live enablement. */
    public Button createToolBarButton(String id) {
        AppAction action = get(id);
        Button button = new Button(action.text().replace("_", ""));
        if (action.longText() != null) {
            button.setTooltip(new Tooltip(action.longText()));
        }
        button.disableProperty().bind(action.enabledProperty().not());
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }
}
