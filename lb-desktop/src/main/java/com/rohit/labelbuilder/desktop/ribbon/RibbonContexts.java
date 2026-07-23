package com.rohit.labelbuilder.desktop.ribbon;

import java.util.LinkedHashSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.springframework.stereotype.Component;

/**
 * The set of active ribbon context keys. Contextual tabs (a {@link RibbonSpec.TabSpec} with a
 * {@code contextKey}) are visible only while their key is active here.
 *
 * <p>Producers are the selection/editing subsystems — e.g. the canvas activates
 * {@code "selection.barcode"} when a barcode element is selected (Phase 6/8) — and the ribbon
 * reacts via a listener on {@link #active()}. FX thread only, like all UI state.
 */
@Component
public class RibbonContexts {

    private final ObservableSet<String> active = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableSet<String> readOnlyView = FXCollections.unmodifiableObservableSet(active);

    /** Read-only, observable — listen here; mutate via {@link #activate}/{@link #deactivate}. */
    public ObservableSet<String> active() {
        return readOnlyView;
    }

    public void activate(String contextKey) {
        active.add(contextKey);
    }

    public void deactivate(String contextKey) {
        active.remove(contextKey);
    }
}
