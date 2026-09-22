package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.core.edit.ZOrder;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Restack elements within the document's back-to-front element list.
 *
 * <p>The one-step moves ({@link ZOrder#BRING_FORWARD}, {@link ZOrder#SEND_BACKWARD}) step each
 * selected element over its nearest <em>unselected</em> neighbour, walking the list in the direction
 * of travel. That keeps a multi-element selection together and stops members from leapfrogging each
 * other — the behaviour that makes repeated presses feel predictable.
 */
public record ReorderCommand(List<String> elementIds, ZOrder move) implements Command {

    public ReorderCommand {
        elementIds = List.copyOf(elementIds);
        Objects.requireNonNull(move, "move");
    }

    @Override
    public String label() {
        return move.displayName();
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        Set<String> ids = new LinkedHashSet<>(elementIds);
        List<LabelElement> all = new ArrayList<>(document.elements());

        switch (move) {
            case BRING_TO_FRONT -> {
                List<LabelElement> selected =
                        all.stream().filter(e -> ids.contains(e.id())).toList();
                all.removeIf(e -> ids.contains(e.id()));
                all.addAll(selected);
            }
            case SEND_TO_BACK -> {
                List<LabelElement> selected =
                        all.stream().filter(e -> ids.contains(e.id())).toList();
                all.removeIf(e -> ids.contains(e.id()));
                all.addAll(0, selected);
            }
            case BRING_FORWARD -> {
                for (int i = all.size() - 2; i >= 0; i--) { // from the top down
                    if (ids.contains(all.get(i).id())
                            && !ids.contains(all.get(i + 1).id())) {
                        all.add(i + 1, all.remove(i));
                    }
                }
            }
            case SEND_BACKWARD -> {
                for (int i = 1; i < all.size(); i++) { // from the bottom up
                    if (ids.contains(all.get(i).id())
                            && !ids.contains(all.get(i - 1).id())) {
                        all.add(i - 1, all.remove(i));
                    }
                }
            }
        }
        return document.withElements(all);
    }
}
