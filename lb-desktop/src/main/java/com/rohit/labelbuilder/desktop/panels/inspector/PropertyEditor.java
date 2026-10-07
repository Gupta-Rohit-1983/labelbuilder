package com.rohit.labelbuilder.desktop.panels.inspector;

import javafx.scene.Node;

/**
 * One row's control in the Property Inspector: shows a property's value and reports edits back.
 *
 * <p>{@link #isEditing()} exists because the inspector is a <em>view</em> of a document that its own
 * edits change. When a commit comes back round as a document change, the panel refreshes every editor
 * except the one being typed in — otherwise the field would be rewritten under the caret mid-word.
 */
public interface PropertyEditor {

    /** The control to place in the form. */
    Node node();

    /** Display a value coming from the model. */
    void showValue(Object value);

    /**
     * Display "these elements disagree" for a multi-selection (Phase 9d). The default leaves whatever
     * is shown in place — honest, because the row's label is marked "(mixed)" either way — and controls
     * that can show a genuinely blank or indeterminate state override it.
     */
    default void showMixed() {
        // intentionally empty: see above
    }

    /** True while the user is in this control, so a refresh should leave it alone. */
    boolean isEditing();
}
