package com.rohit.labelbuilder.desktop;

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
 *   <li>{@link #init()} (JavaFX-Launcher thread): start the Spring context. No UI exists yet, so
 *       slow bean initialisation happens before the window appears.
 *   <li>{@link #start(Stage)} (FX Application Thread): publish {@link StageReadyEvent}; the shell
 *       listener builds the scene from FXML with Spring-managed controllers.
 *   <li>{@link #stop()}: close the context so {@code @PreDestroy} hooks run (flush settings,
 *       cancel executors), then let the FX toolkit exit.
 * </ol>
 */
public class FxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(LabelBuilderDesktop.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        context.publishEvent(new StageReadyEvent(primaryStage));
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
