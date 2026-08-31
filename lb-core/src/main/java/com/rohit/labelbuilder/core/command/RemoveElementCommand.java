package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import java.util.Objects;

/** Remove the element with the given id (no-op if it is already gone). */
public record RemoveElementCommand(String elementId) implements Command {

    public RemoveElementCommand {
        Objects.requireNonNull(elementId, "elementId");
    }

    @Override
    public String label() {
        return "Delete";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        return document.removeElement(elementId);
    }
}
