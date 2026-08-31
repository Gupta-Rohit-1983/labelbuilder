package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.meta.ElementSchemas;
import java.util.Objects;

/**
 * Set one metadata-described property (by key, see {@link ElementSchemas}) on an element to an
 * absolute value. This is the command the Property Inspector (Phase 9) issues for every edit; because
 * the value is absolute, successive edits of the same property on the same element merge into one
 * undo step (typing in a field, nudging a spinner).
 *
 * @throws IllegalArgumentException from {@link #apply} if the element or property key is unknown
 */
public record SetPropertyCommand(String elementId, String propertyKey, Object value) implements Command {

    public SetPropertyCommand {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(propertyKey, "propertyKey");
    }

    @Override
    public String label() {
        return "Change " + propertyKey;
    }

    @Override
    public String mergeKey() {
        return "set:" + elementId + ":" + propertyKey;
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        LabelElement element = document.findElement(elementId)
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + elementId));
        LabelElement updated = ElementSchemas.schemaFor(element).set(element, propertyKey, value);
        return document.replaceElement(updated);
    }
}
