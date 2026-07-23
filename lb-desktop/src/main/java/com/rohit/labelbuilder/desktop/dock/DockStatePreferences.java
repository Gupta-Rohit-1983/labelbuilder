package com.rohit.labelbuilder.desktop.dock;

import java.util.Set;
import java.util.prefs.Preferences;
import javafx.geometry.Side;
import org.springframework.stereotype.Component;

/**
 * Persists the docking {@link DockState} across runs — same {@code Preferences} store and
 * rationale as the window/ribbon state: throwaway per-user UI state.
 *
 * <p>Restore is defensive: malformed text or references to panels that no longer exist fall back
 * to the given default. Panels persisted as floating are folded back into the docked layout on
 * load — restoring free-floating windows before the main window exists is more startup complexity
 * than the feature is worth in v1.
 */
@Component
public class DockStatePreferences {

    private static final String KEY_STATE = "dock.state";

    private final Preferences prefs = Preferences.userRoot().node("com/rohit/labelbuilder/dock");

    public DockState load(DockState fallback, Set<String> validPanelIds) {
        String text = prefs.get(KEY_STATE, null);
        if (text == null) {
            return fallback;
        }
        return DockStateCodec.decode(text, validPanelIds)
                .map(DockStatePreferences::foldFloatingBack)
                .orElse(fallback);
    }

    public void save(DockState state) {
        prefs.put(KEY_STATE, DockStateCodec.encode(state));
    }

    private static DockState foldFloatingBack(DockState state) {
        DockState result = state;
        for (String panelId : state.floating()) {
            result = result.dockBack(panelId, Side.RIGHT);
        }
        return result;
    }
}
