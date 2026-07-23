package com.rohit.labelbuilder.desktop;

import com.rohit.labelbuilder.desktop.SplashPreloader.StatusNotification;
import com.rohit.labelbuilder.desktop.shell.StageReadyEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Bridges the JavaFX lifecycle and the Spring context.
 *
 * <p>Bootstrap sequence:
 *
 * <ol>
 *   <li>{@link #init()} (JavaFX-Launcher thread): start the Spring context while the
 *       {@link SplashPreloader} is on screen. No UI exists yet, so slow bean initialisation
 *       happens before the window appears.
 *   <li>{@link #start(Stage)} (FX Application Thread): publish {@link StageReadyEvent}; the shell
 *       listener builds the scene from FXML with Spring-managed controllers. Listeners run
 *       synchronously, so when {@code publishEvent} returns the main window is showing and the
 *       splash can be dismissed.
 *   <li>{@link #stop()}: shutdown ordering is <em>flush UI state, then close the context</em> —
 *       the main window's {@code onHiding} handler has already saved window geometry by the time
 *       this runs (JavaFX hides all windows before calling {@code stop()}); closing the context
 *       then runs {@code @PreDestroy} hooks (flush settings, cancel executors).
 * </ol>
 */
public class FxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        notifyPreloader(StatusNotification.progress("Starting services…"));
        context = new SpringApplicationBuilder(LabelBuilderDesktop.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
        notifyPreloader(StatusNotification.progress("Preparing workspace…"));
    }

    @Override
    public void start(Stage primaryStage) {
        context.publishEvent(new StageReadyEvent(primaryStage));
        notifyPreloader(StatusNotification.completed());
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
