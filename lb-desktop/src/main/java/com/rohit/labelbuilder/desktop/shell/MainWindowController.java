package com.rohit.labelbuilder.desktop.shell;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller of the main window shell.
 *
 * <p>A Spring bean (created via {@link FxmlViewLoader}'s controller factory), so services inject
 * through the constructor. Prototype-scoped: every FXML load must get a fresh controller — FXML
 * controllers hold per-view node references and are never shareable singletons.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainWindowController {

    private final BuildInfo buildInfo;

    @FXML
    private Label centerPlaceholder;

    @FXML
    private Label statusLabel;

    public MainWindowController(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @FXML
    private void initialize() {
        // Values arriving from an injected service prove the Spring↔FXML wiring end-to-end.
        centerPlaceholder.setText("Spring + JavaFX bootstrap OK — canvas arrives in Phase 6");
        statusLabel.setText("%s %s · Java %s · JavaFX %s"
                .formatted(
                        buildInfo.appName(),
                        buildInfo.version(),
                        System.getProperty("java.version"),
                        System.getProperty("javafx.version")));
    }
}
