package com.rohit.labelbuilder.desktop.shell;

import java.util.prefs.Preferences;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.springframework.stereotype.Component;

/**
 * Persists which ribbon tab was selected across runs, keyed by tab title (titles are stable;
 * indices shift when contextual tabs appear). Same {@link Preferences} store and rationale as
 * {@link WindowStatePreferences}: throwaway per-user UI state, not document data.
 *
 * <p>A remembered title that is not currently visible (e.g. a contextual tab whose context is
 * inactive at startup) is simply ignored and the first tab stays selected.
 */
@Component
public class RibbonStatePreferences {

    private static final String KEY_SELECTED_TAB = "ribbon.selectedTab";

    private final Preferences prefs = Preferences.userRoot().node("com/rohit/labelbuilder/ribbon");

    /** Re-selects the remembered tab and starts persisting future selection changes. */
    public void bind(TabPane ribbon) {
        String remembered = prefs.get(KEY_SELECTED_TAB, null);
        if (remembered != null) {
            ribbon.getTabs().stream()
                    .filter(tab -> remembered.equals(tab.getText()))
                    .findFirst()
                    .ifPresent(tab -> ribbon.getSelectionModel().select(tab));
        }
        ribbon.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> save(selected));
    }

    private void save(Tab selected) {
        if (selected != null) {
            prefs.put(KEY_SELECTED_TAB, selected.getText());
        }
    }
}
