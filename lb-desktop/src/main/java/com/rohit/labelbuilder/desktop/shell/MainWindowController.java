package com.rohit.labelbuilder.desktop.shell;

import static com.rohit.labelbuilder.desktop.action.ActionRegistry.SEPARATOR;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_COPY;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_CUT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_PASTE;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_REDO;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_UNDO;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_EXIT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_NEW;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_OPEN;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_PRINT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_SAVE;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_SAVE_AS;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.HELP_ABOUT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_FIT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_IN;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_OUT;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller of the main window shell.
 *
 * <p>A Spring bean (created via {@link FxmlViewLoader}'s controller factory), so services inject
 * through the constructor. Prototype-scoped: every FXML load must get a fresh controller — FXML
 * controllers hold per-view node references and are never shareable singletons.
 *
 * <p>The FXML supplies layout only; the menus are generated here from the {@link ActionRegistry}
 * and the ribbon from {@link ShellRibbon}'s spec, so this class decides <em>which</em> commands
 * appear <em>where</em> while the registry owns what they say and do.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainWindowController {

    private final ActionRegistry actions;
    private final StatusBus statusBus;
    private final ShellRibbon ribbon;
    private final RibbonStatePreferences ribbonState;

    @FXML
    private VBox topBox;

    @FXML
    private MenuBar menuBar;

    @FXML
    private HBox quickAccessBar;

    @FXML
    private Label centerPlaceholder;

    @FXML
    private Label statusMessage;

    @FXML
    private Label cursorPositionLabel;

    @FXML
    private Label zoomLabel;

    @FXML
    private Label environmentLabel;

    public MainWindowController(
            ActionRegistry actions, StatusBus statusBus, ShellRibbon ribbon, RibbonStatePreferences ribbonState) {
        this.actions = actions;
        this.statusBus = statusBus;
        this.ribbon = ribbon;
        this.ribbonState = ribbonState;
    }

    @FXML
    private void initialize() {
        menuBar.getMenus()
                .setAll(
                        actions.createMenu(
                                "_File",
                                FILE_NEW,
                                FILE_OPEN,
                                SEPARATOR,
                                FILE_SAVE,
                                FILE_SAVE_AS,
                                SEPARATOR,
                                FILE_PRINT,
                                SEPARATOR,
                                FILE_EXIT),
                        actions.createMenu("_Edit", EDIT_UNDO, EDIT_REDO, SEPARATOR, EDIT_CUT, EDIT_COPY, EDIT_PASTE),
                        actions.createMenu("_View", VIEW_ZOOM_IN, VIEW_ZOOM_OUT, VIEW_ZOOM_FIT),
                        actions.createMenu("_Help", HELP_ABOUT));
        ribbon.quickAccessActionIds().forEach(id -> {
            var button = actions.createToolBarButton(id);
            button.getStyleClass().add("qat-button");
            quickAccessBar.getChildren().add(button);
        });

        var ribbonPane = ribbon.create();
        ribbonState.bind(ribbonPane);
        topBox.getChildren().add(ribbonPane);

        centerPlaceholder.setText("Design workspace — canvas arrives in Phase 6");
        statusMessage.textProperty().bind(statusBus.messageProperty());
        cursorPositionLabel.setText("—");
        zoomLabel.setText("100%");
        environmentLabel.setText("Java %s · JavaFX %s"
                .formatted(System.getProperty("java.version"), System.getProperty("javafx.version")));
    }
}
