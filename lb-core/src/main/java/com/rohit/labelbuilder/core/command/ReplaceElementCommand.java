package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.Objects;

/**
 * Swap in a new version of an element, keeping its z-order (matched by id). The general-purpose edit
 * behind most element mutations that a caller has already computed.
 */
public record ReplaceElementCommand(LabelElement replacement, String label) implements Command {

    public ReplaceElementCommand {
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(label, "label");
    }

    /** With a generic "Edit" label. */
    public static ReplaceElementCommand of(LabelElement replacement) {
        return new ReplaceElementCommand(replacement, "Edit");
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        return document.replaceElement(replacement);
    }
}
