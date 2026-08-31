package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.Objects;

/**
 * Translate an element by a delta in millimetres. A delta (not an absolute position), so it is
 * deliberately <b>not</b> mergeable — a continuous drag in Phase 8 should use an absolute-position
 * command if it wants the whole drag to collapse into one undo step.
 *
 * @throws IllegalArgumentException from {@link #apply} if no element has the id
 */
public record MoveElementCommand(String elementId, double dxMm, double dyMm) implements Command {

    public MoveElementCommand {
        Objects.requireNonNull(elementId, "elementId");
    }

    @Override
    public String label() {
        return "Move";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        LabelElement element = document.findElement(elementId)
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + elementId));
        return document.replaceElement(element.movedBy(dxMm, dyMm));
    }
}
