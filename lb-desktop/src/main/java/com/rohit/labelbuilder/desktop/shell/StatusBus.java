package com.rohit.labelbuilder.desktop.shell;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.springframework.stereotype.Component;

/**
 * Decouples "something wants to say a status line" from the status bar control: actions and
 * services post here; the shell binds its status label to {@link #messageProperty()}. Keeps
 * action handlers free of any reference to scene-graph nodes.
 */
@Component
public class StatusBus {

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("Ready");

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** Safe from any thread; marshals to the FX thread when the toolkit is running. */
    public void post(String text) {
        if (Platform.isFxApplicationThread()) {
            message.set(text);
        } else {
            try {
                Platform.runLater(() -> message.set(text));
            } catch (IllegalStateException toolkitNotRunning) {
                // Headless (unit tests): no FX thread exists, so plain set is race-free.
                message.set(text);
            }
        }
    }
}
