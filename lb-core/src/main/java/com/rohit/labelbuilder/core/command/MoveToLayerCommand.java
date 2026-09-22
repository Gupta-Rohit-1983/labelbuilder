package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reassign elements to another layer. Z-order is untouched — layer membership controls visibility and
 * locking, while stacking stays the document's element order.
 *
 * @throws IllegalArgumentException from {@link #apply} if the target layer does not exist
 */
public record MoveToLayerCommand(List<String> elementIds, String layerId) implements Command {

    public MoveToLayerCommand {
        elementIds = List.copyOf(elementIds);
        Objects.requireNonNull(layerId, "layerId");
    }

    @Override
    public String label() {
        return "Move to Layer";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        if (document.layers().stream().noneMatch(l -> l.id().equals(layerId))) {
            throw new IllegalArgumentException("no layer with id " + layerId);
        }
        Set<String> ids = new LinkedHashSet<>(elementIds);
        LabelDocument result = document;
        for (LabelElement element : document.elements()) {
            if (ids.contains(element.id()) && !element.layerId().equals(layerId)) {
                result = result.replaceElement(
                        element.withProperties(element.properties().withLayerId(layerId)));
            }
        }
        return result;
    }
}
