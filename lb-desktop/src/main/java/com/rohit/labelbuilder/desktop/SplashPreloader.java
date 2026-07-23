package com.rohit.labelbuilder.desktop;

import javafx.application.Preloader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Startup splash, shown while the Spring context boots in {@link FxApplication#init()}.
 *
 * <p>A JavaFX {@link Preloader} is the only mechanism that can put a window on screen before
 * {@code init()} runs — which is precisely where the slow work (Spring startup) happens. It is
 * registered via the {@code javafx.preloader} system property in {@link LabelBuilderApp#main}.
 *
 * <p>Styled in code, not via {@code shell.css}: the splash exists before any of the app's styling
 * infrastructure and should stay dependency-free.
 */
public final class SplashPreloader extends Preloader {

    /** Status update pushed from {@link FxApplication} via {@code notifyPreloader}. */
    public record StatusNotification(String message, boolean done) implements PreloaderNotification {

        public static StatusNotification progress(String message) {
            return new StatusNotification(message, false);
        }

        public static StatusNotification completed() {
            return new StatusNotification(null, true);
        }
    }

    private Stage stage;
    private Label status;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Label title = new Label("LabelBuilder");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #303030;");

        ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setMaxWidth(Double.MAX_VALUE);

        status = new Label("Starting…");
        status.setStyle("-fx-text-fill: #606060;");

        VBox root = new VBox(14, title, progress, status);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color: #fafafa; -fx-border-color: #c0c0c0; -fx-border-width: 1;");

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(new Scene(root, 380, 150));
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification notification) {
        if (notification instanceof StatusNotification update) {
            if (update.done()) {
                // The main window is already on screen (sent after StageReadyEvent handling).
                stage.hide();
            } else {
                status.setText(update.message());
            }
        }
    }
}
