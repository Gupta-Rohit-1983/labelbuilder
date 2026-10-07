package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.List;

/**
 * What the inspector should be showing for a given selection (Phase 9a). Pure, so the decision is
 * testable on its own and the panel is left with nothing but rendering.
 */
public sealed interface InspectorState {

    /** Nothing selected — the inspector shows a hint rather than an empty form. */
    record Empty() implements InspectorState {}

    /** Exactly one element: its schema drives the form. */
    record Single(LabelElement element) implements InspectorState {}

    /** More than one: editing the common properties arrives in Phase 9d. */
    record Multiple(int count) implements InspectorState {}

    static InspectorState of(List<LabelElement> selection) {
        return switch (selection.size()) {
            case 0 -> new Empty();
            case 1 -> new Single(selection.getFirst());
            default -> new Multiple(selection.size());
        };
    }
}
