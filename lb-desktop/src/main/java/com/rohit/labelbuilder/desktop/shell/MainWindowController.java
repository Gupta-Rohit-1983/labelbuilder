package com.rohit.labelbuilder.desktop.shell;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
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
 * <p>The menu/toolbar handlers here are deliberately thin placeholders. Phase 4 replaces them with
 * a central {@code ActionRegistry} so ribbon, menus, context menus and shortcuts share one command
 * definition; Phases 6+ give the real editing behaviour.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainWindowController {

    private final BuildInfo buildInfo;

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

    public MainWindowController(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @FXML
    private void initialize() {
        centerPlaceholder.setText("Design workspace — canvas arrives in Phase 6");
        statusMessage.setText("Ready");
        cursorPositionLabel.setText("—");
        zoomLabel.setText("100%");
        environmentLabel.setText("Java %s · JavaFX %s"
                .formatted(System.getProperty("java.version"), System.getProperty("javafx.version")));
    }

    @FXML
    private void onNew() {
        statusMessage.setText("New label — not yet implemented (Phase 7)");
    }

    @FXML
    private void onOpen() {
        statusMessage.setText("Open — not yet implemented (Phase 7)");
    }

    @FXML
    private void onSave() {
        statusMessage.setText("Save — not yet implemented (Phase 7)");
    }

    @FXML
    private void onSaveAs() {
        statusMessage.setText("Save As — not yet implemented (Phase 7)");
    }

    @FXML
    private void onNotImplemented() {
        statusMessage.setText("Not yet implemented");
    }

    @FXML
    private void onExit() {
        // Fire a close request (rather than Stage.close()) so the onCloseRequest handler runs and
        // window state is saved — matching the window-manager close button's behaviour.
        Stage stage = (Stage) centerPlaceholder.getScene().getWindow();
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    private void onAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About " + buildInfo.appName());
        about.setHeaderText(buildInfo.appName() + " " + buildInfo.version());
        about.setContentText("Label design and printing suite.\n\nRunning on Java %s, JavaFX %s."
                .formatted(System.getProperty("java.version"), System.getProperty("javafx.version")));
        about.initOwner(centerPlaceholder.getScene().getWindow());
        about.showAndWait();
    }
}
