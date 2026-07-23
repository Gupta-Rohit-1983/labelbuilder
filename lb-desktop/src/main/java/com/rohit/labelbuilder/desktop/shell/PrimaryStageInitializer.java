package com.rohit.labelbuilder.desktop.shell;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Builds the main window when JavaFX hands over the primary stage.
 *
 * <p>Runs on the FX Application Thread (the event is published from {@code FxApplication#start}).
 * Owns the window-geometry lifecycle: restore on show, save on close.
 */
@Component
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double MIN_WIDTH = 800;
    private static final double MIN_HEIGHT = 600;

    private final FxmlViewLoader viewLoader;
    private final BuildInfo buildInfo;
    private final WindowStatePreferences windowState;

    public PrimaryStageInitializer(FxmlViewLoader viewLoader, BuildInfo buildInfo, WindowStatePreferences windowState) {
        this.viewLoader = viewLoader;
        this.buildInfo = buildInfo;
        this.windowState = windowState;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        Stage stage = event.stage();
        stage.setTitle(buildInfo.appName() + " " + buildInfo.version());
        stage.setScene(new Scene(viewLoader.load("/fxml/main-window.fxml")));
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        windowState.restore(stage, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        // onHiding (not onCloseRequest) so state is saved on every exit route — close button, Exit
        // menu, programmatic Platform.exit() — and always before FxApplication.stop() closes the
        // context. onCloseRequest stays free for Phase 14's "unsaved changes" veto.
        stage.setOnHiding(e -> windowState.save(stage));

        stage.show();
    }
}
