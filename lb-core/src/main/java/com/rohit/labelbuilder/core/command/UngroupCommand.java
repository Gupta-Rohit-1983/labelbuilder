package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dissolve a group, splicing its children back into the document at the group's own z-position so
 * their stacking relative to everything else is preserved. Children already carry absolute
 * coordinates, so nothing moves.
 *
 * @throws IllegalArgumentException from {@link #apply} if the id is missing or is not a group
 */
public record UngroupCommand(String groupId) implements Command {

    public UngroupCommand {
        Objects.requireNonNull(groupId, "groupId");
    }

    @Override
    public String label() {
        return "Ungroup";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        LabelElement found = document.findElement(groupId)
                .orElseThrow(() -> new IllegalArgumentException("no element with id " + groupId));
        if (!(found instanceof GroupElement group)) {
            throw new IllegalArgumentException("element " + groupId + " is not a group");
        }
        List<LabelElement> out = new ArrayList<>();
        for (LabelElement element : document.elements()) {
            if (element.id().equals(groupId)) {
                out.addAll(group.children());
            } else {
                out.add(element);
            }
        }
        return document.withElements(out);
    }
}
