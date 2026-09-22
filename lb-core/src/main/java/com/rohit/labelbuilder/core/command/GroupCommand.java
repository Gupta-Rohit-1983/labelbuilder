package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Collect elements into a {@link GroupElement}. The group takes the z-position of its <b>topmost</b>
 * member, so grouping never pushes the selection behind something it was previously in front of.
 * Children keep their absolute coordinates (lbl-format.md §4.8), so nothing moves visually.
 *
 * @throws IllegalArgumentException from {@link #apply} if fewer than two of the ids are present
 */
public record GroupCommand(List<String> elementIds, String groupId, String groupName) implements Command {

    public GroupCommand {
        elementIds = List.copyOf(elementIds);
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(groupName, "groupName");
    }

    /** With the conventional "Group" name. */
    public static GroupCommand of(List<String> elementIds, String groupId) {
        return new GroupCommand(elementIds, groupId, "Group");
    }

    @Override
    public String label() {
        return "Group";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        Set<String> ids = new LinkedHashSet<>(elementIds);
        List<LabelElement> members =
                document.elements().stream().filter(e -> ids.contains(e.id())).toList();
        if (members.size() < 2) {
            throw new IllegalArgumentException("grouping needs at least two existing elements");
        }
        LabelElement topmost = members.getLast();

        Bounds box = union(members);
        GroupElement group = new GroupElement(
                ElementProperties.of(groupId, groupName, members.getFirst().layerId(), box), members);

        List<LabelElement> out = new ArrayList<>();
        for (LabelElement element : document.elements()) {
            if (ids.contains(element.id())) {
                if (element.id().equals(topmost.id())) {
                    out.add(group); // the group inherits the topmost member's z-order slot
                }
            } else {
                out.add(element);
            }
        }
        return document.withElements(out);
    }

    private static Bounds union(List<LabelElement> elements) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (LabelElement element : elements) {
            Bounds b = element.bounds();
            minX = Math.min(minX, b.xMm());
            minY = Math.min(minY, b.yMm());
            maxX = Math.max(maxX, b.rightMm());
            maxY = Math.max(maxY, b.bottomMm());
        }
        return new Bounds(minX, minY, maxX - minX, maxY - minY);
    }
}
