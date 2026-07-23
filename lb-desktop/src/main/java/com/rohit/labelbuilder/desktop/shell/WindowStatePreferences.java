package com.rohit.labelbuilder.desktop.shell;

import java.util.prefs.Preferences;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists and restores the main window's size, position and maximised state across runs.
 *
 * <p>Backed by {@link Preferences} (per-user, OS-managed) — window geometry is throwaway UI state
 * that does not belong in a shared file or the server. Phase 17 may move richer preferences to the
 * app data store; window geometry can stay here.
 *
 * <p>Restore is defensive: a saved position that no longer lands on any connected screen (monitor
 * unplugged, resolution changed) is discarded and the window is centred, so the app can never open
 * off-screen.
 */
@Component
public class WindowStatePreferences {

    private static final Logger log = LoggerFactory.getLogger(WindowStatePreferences.class);

    private static final String KEY_X = "window.x";
    private static final String KEY_Y = "window.y";
    private static final String KEY_W = "window.width";
    private static final String KEY_H = "window.height";
    private static final String KEY_MAXIMIZED = "window.maximized";
    private static final double UNSET = -1;

    private final Preferences prefs = Preferences.userRoot().node("com/rohit/labelbuilder/window");

    /** Applies saved geometry to the stage, or sensible defaults on first run / invalid state. */
    public void restore(Stage stage, double defaultWidth, double defaultHeight) {
        double w = prefs.getDouble(KEY_W, UNSET);
        double h = prefs.getDouble(KEY_H, UNSET);
        double x = prefs.getDouble(KEY_X, UNSET);
        double y = prefs.getDouble(KEY_Y, UNSET);

        stage.setWidth(w > 0 ? w : defaultWidth);
        stage.setHeight(h > 0 ? h : defaultHeight);

        if (x != UNSET && y != UNSET && isOnAScreen(x, y, stage.getWidth(), stage.getHeight())) {
            stage.setX(x);
            stage.setY(y);
        } else {
            stage.centerOnScreen();
        }

        stage.setMaximized(prefs.getBoolean(KEY_MAXIMIZED, false));
    }

    /** Captures the stage's current geometry. Skips position/size while maximised so restore keeps
     * the pre-maximise "restored" bounds. */
    public void save(Stage stage) {
        prefs.putBoolean(KEY_MAXIMIZED, stage.isMaximized());
        if (!stage.isMaximized()) {
            prefs.putDouble(KEY_X, stage.getX());
            prefs.putDouble(KEY_Y, stage.getY());
            prefs.putDouble(KEY_W, stage.getWidth());
            prefs.putDouble(KEY_H, stage.getHeight());
        }
        try {
            prefs.flush();
        } catch (Exception e) {
            // Non-fatal: losing window geometry is a cosmetic annoyance, never a reason to block exit.
            log.warn("Could not persist window state", e);
        }
    }

    private boolean isOnAScreen(double x, double y, double w, double h) {
        Rectangle2D window = new Rectangle2D(x, y, Math.max(w, 1), Math.max(h, 1));
        return Screen.getScreens().stream()
                .anyMatch(screen -> screen.getVisualBounds().intersects(window));
    }
}
