package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import java.util.Objects;

/**
 * Set an element's bounds to an absolute rectangle — the commit of a move or resize gesture. Absolute
 * (not a delta), so repeated sets on the same element coalesce into one undo step, and re-applying
 * the newest to the pre-gesture document is correct.
 *
 * @throws IllegalArgumentException from {@link #apply} if no element has the id
 */
public record SetBoundsCommand(String elementId, Bounds bounds, String label) implements Command {

    public SetBoundsCommand {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(label, "label");
    }

    /** With a "Move" label. */
    public static SetBoundsCommand move(String elementId, Bounds bounds) {
        return new SetBoundsCommand(elementId, bounds, "Move");
    }

    /** With a "Resize" label. */
    public static SetBoundsCommand resize(String elementId, Bounds bounds) {
        return new SetBoundsCommand(elementId, bounds, "Resize");
    }

    @Override
    public String mergeKey() {
        return "bounds:" + elementId;
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        LabelElement element = document.findElement(elementId)
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + elementId));
        return document.replaceElement(element.withBounds(bounds));
    }
}
