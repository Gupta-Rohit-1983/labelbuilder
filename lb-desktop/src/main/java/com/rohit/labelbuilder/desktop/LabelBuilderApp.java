package com.rohit.labelbuilder.desktop;

import com.rohit.labelbuilder.desktop.platform.AppDirectories;
import com.rohit.labelbuilder.desktop.platform.SingleInstanceGuard;
import java.awt.GraphicsEnvironment;
import javafx.application.Application;
import javax.swing.JOptionPane;

/**
 * Process entry point.
 *
 * <p>Runs the platform bootstrap that must happen <em>before</em> the Spring context and JavaFX:
 *
 * <ol>
 *   <li>create the app data directory tree and tell Logback where to write (system property read by
 *       {@code logback-spring.xml});
 *   <li>acquire the single-instance lock, exiting early if another instance already holds it;
 *   <li>launch JavaFX, which starts Spring in {@link FxApplication#init()}.
 * </ol>
 *
 * <p>Kept separate from the {@code Application} subclass so the executable-jar main class does not
 * extend {@code Application}.
 */
public final class LabelBuilderApp {

    // Held for the process lifetime so the file lock is never released early by GC.
    @SuppressWarnings("unused")
    private static SingleInstanceGuard instanceGuard;

    private LabelBuilderApp() {}

    public static void main(String[] args) {
        AppDirectories dirs = AppDirectories.resolveAndCreate();
        System.setProperty(AppDirectories.LOG_DIR_PROPERTY, dirs.logs().toString());

        SingleInstanceGuard guard = new SingleInstanceGuard(dirs.root());
        if (!guard.tryAcquire()) {
            reportAlreadyRunning();
            return;
        }
        instanceGuard = guard;
        Runtime.getRuntime().addShutdownHook(new Thread(guard::close, "instance-guard-release"));

        // The splash must be registered before launch; there is no API equivalent of this property.
        System.setProperty("javafx.preloader", SplashPreloader.class.getName());
        Application.launch(FxApplication.class, args);
    }

    private static void reportAlreadyRunning() {
        String message = "LabelBuilder is already running.";
        System.err.println(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "LabelBuilder", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
