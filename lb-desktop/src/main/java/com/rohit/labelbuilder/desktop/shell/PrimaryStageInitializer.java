package com.rohit.labelbuilder.desktop.shell;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Builds the main window when JavaFX hands over the primary stage.
 *
 * <p>Runs on the FX Application Thread (the event is published from {@code
 * FxApplication#start}).
 */
@Component
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private static final double INITIAL_WIDTH = 1200;
    private static final double INITIAL_HEIGHT = 800;

    private final FxmlViewLoader viewLoader;
    private final BuildInfo buildInfo;

    public PrimaryStageInitializer(FxmlViewLoader viewLoader, BuildInfo buildInfo) {
        this.viewLoader = viewLoader;
        this.buildInfo = buildInfo;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        Stage stage = event.stage();
        stage.setTitle(buildInfo.appName() + " " + buildInfo.version());
        stage.setScene(new Scene(viewLoader.load("/fxml/main-window.fxml"), INITIAL_WIDTH, INITIAL_HEIGHT));
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();
    }
}
