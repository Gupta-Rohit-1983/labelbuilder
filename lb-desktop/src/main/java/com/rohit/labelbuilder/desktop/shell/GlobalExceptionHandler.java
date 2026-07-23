package com.rohit.labelbuilder.desktop.shell;

import jakarta.annotation.PostConstruct;
import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Catches uncaught exceptions from every thread — including the FX Application Thread, which routes
 * uncaught exceptions to the current thread's handler — logs them, and shows a non-fatal error
 * dialog with an expandable stack trace.
 *
 * <p>The goal is that a bug in one action never silently kills the UI or takes the whole app down
 * without explanation: the user sees what happened and the details land in the log file for support.
 */
@Component
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @PostConstruct
    void register() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        log.error("Uncaught exception on thread '{}'", thread.getName(), error);
        if (Platform.isFxApplicationThread()) {
            showDialog(error);
        } else if (isFxRunning()) {
            Platform.runLater(() -> showDialog(error));
        }
        // If FX is not running (e.g. a background thread during startup/shutdown) the log entry is
        // the record; there is no window to attach a dialog to.
    }

    private boolean isFxRunning() {
        try {
            Platform.runLater(() -> {});
            return true;
        } catch (IllegalStateException fxNotStarted) {
            return false;
        }
    }

    private void showDialog(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Unexpected Error");
        alert.setHeaderText("Something went wrong");
        alert.setContentText(
                error.getMessage() != null
                        ? error.getMessage()
                        : error.getClass().getSimpleName());

        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));

        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.setWrapText(false);
        details.setMaxWidth(Double.MAX_VALUE);
        details.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(details, Priority.ALWAYS);
        GridPane.setHgrow(details, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(new Label("Details (please include this when reporting the problem):"), 0, 0);
        content.add(details, 0, 1);

        alert.getDialogPane().setExpandableContent(content);
        alert.showAndWait();
    }
}
