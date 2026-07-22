package com.rohit.labelbuilder.desktop.shell;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

/** Published on the FX Application Thread when JavaFX hands over the primary stage. */
public class StageReadyEvent extends ApplicationEvent {

    public StageReadyEvent(Stage stage) {
        super(stage);
    }

    public Stage stage() {
        return (Stage) getSource();
    }
}
