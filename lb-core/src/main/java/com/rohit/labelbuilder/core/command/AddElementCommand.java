package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.Objects;

/** Add an element at the top of the z-order. */
public record AddElementCommand(LabelElement element) implements Command {

    public AddElementCommand {
        Objects.requireNonNull(element, "element");
    }

    @Override
    public String label() {
        return "Add " + element.getClass().getSimpleName().replace("Element", "");
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        return document.addElement(element);
    }
}
