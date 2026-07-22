package com.rohit.labelbuilder.desktop;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Placeholder entry point proving the JavaFX toolchain works end-to-end.
 * Replaced by the Spring-integrated application shell in Phase 3.
 *
 * <p>Run with: {@code mvnw -pl lb-desktop javafx:run}
 */
public class LabelBuilderApp extends Application {

    @Override
    public void start(Stage stage) {
        Label placeholder = new Label("LabelBuilder — Phase 2 skeleton");
        BorderPane root = new BorderPane(placeholder);
        BorderPane.setAlignment(placeholder, Pos.CENTER);

        stage.setTitle("LabelBuilder 0.1.0-SNAPSHOT");
        stage.setScene(new Scene(root, 1024, 640));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
