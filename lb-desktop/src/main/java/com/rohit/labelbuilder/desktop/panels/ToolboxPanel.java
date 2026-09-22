package com.rohit.labelbuilder.desktop.panels;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.shell.ShellActions;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

/**
 * The Toolbox panel (Phase 8d): one button per creation tool, arming the canvas for a placement.
 *
 * <p>The buttons are generated from the same {@link ActionRegistry} entries the ribbon's Insert group
 * uses, so the two surfaces cannot drift apart in wording, tooltip or behaviour — the point of the
 * central action table (architecture §10).
 */
@Component
public class ToolboxPanel {

    private static final List<String> TOOL_ACTION_IDS = List.of(
            ShellActions.INSERT_TEXT,
            ShellActions.INSERT_RECTANGLE,
            ShellActions.INSERT_ELLIPSE,
            ShellActions.INSERT_LINE,
            ShellActions.INSERT_IMAGE,
            ShellActions.INSERT_BARCODE);

    private final ActionRegistry actions;

    public ToolboxPanel(ActionRegistry actions) {
        this.actions = actions;
    }

    /** A fresh toolbox view. FX thread only. */
    public Node create() {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("toolbox-panel");
        for (String id : TOOL_ACTION_IDS) {
            Button button = actions.createToolBarButton(id);
            button.setMaxWidth(Double.MAX_VALUE); // fill the panel width
            VBox.setVgrow(button, Priority.NEVER);
            box.getChildren().add(button);
        }
        return box;
    }
}
