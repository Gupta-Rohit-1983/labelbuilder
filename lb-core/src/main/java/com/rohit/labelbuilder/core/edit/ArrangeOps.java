package com.rohit.labelbuilder.core.edit;

import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The geometry behind the arrange tools (Phase 8b) — align, distribute, nudge and duplicate — as
 * pure functions over elements. Keeping them here rather than in the canvas means the fiddly cases
 * (fewer than two elements, locked elements, duplicated group children) are unit-tested headlessly.
 *
 * <p>The align/distribute/nudge methods return only the elements whose bounds actually change, keyed
 * by id, so a no-op produces an empty map and never lands on the undo stack.
 */
public final class ArrangeOps {

    private ArrangeOps() {}

    /**
     * Align elements to the selection's own bounding box. Needs at least two elements (aligning one
     * element to itself is a no-op); locked elements are left alone.
     */
    public static Map<String, Bounds> align(List<LabelElement> elements, Align align) {
        List<LabelElement> movable = movable(elements);
        if (movable.size() < 2) {
            return Map.of();
        }
        Bounds box = boundingBox(movable);
        Map<String, Bounds> changes = new LinkedHashMap<>();
        for (LabelElement element : movable) {
            Bounds b = element.bounds();
            Bounds target =
                    switch (align) {
                        case LEFT -> b.withPosition(box.xMm(), b.yMm());
                        case CENTER -> b.withPosition(box.centerXMm() - b.widthMm() / 2, b.yMm());
                        case RIGHT -> b.withPosition(box.rightMm() - b.widthMm(), b.yMm());
                        case TOP -> b.withPosition(b.xMm(), box.yMm());
                        case MIDDLE -> b.withPosition(b.xMm(), box.centerYMm() - b.heightMm() / 2);
                        case BOTTOM -> b.withPosition(b.xMm(), box.bottomMm() - b.heightMm());
                    };
            if (!target.equals(b)) {
                changes.put(element.id(), target);
            }
        }
        return changes;
    }

    /**
     * Space elements evenly along an axis, holding the two outermost in place. Needs at least three
     * elements — with two there is nothing between them to move.
     */
    public static Map<String, Bounds> distribute(List<LabelElement> elements, Distribute axis) {
        List<LabelElement> movable = movable(elements);
        if (movable.size() < 3) {
            return Map.of();
        }
        boolean horizontal = axis == Distribute.HORIZONTALLY;
        List<LabelElement> ordered = new ArrayList<>(movable);
        ordered.sort(Comparator.comparingDouble(e -> centre(e.bounds(), horizontal)));

        double first = centre(ordered.getFirst().bounds(), horizontal);
        double last = centre(ordered.getLast().bounds(), horizontal);
        double step = (last - first) / (ordered.size() - 1);

        Map<String, Bounds> changes = new LinkedHashMap<>();
        for (int i = 1; i < ordered.size() - 1; i++) { // ends stay put
            LabelElement element = ordered.get(i);
            Bounds b = element.bounds();
            double target = first + step * i;
            Bounds moved = horizontal
                    ? b.withPosition(target - b.widthMm() / 2, b.yMm())
                    : b.withPosition(b.xMm(), target - b.heightMm() / 2);
            if (!moved.equals(b)) {
                changes.put(element.id(), moved);
            }
        }
        return changes;
    }

    /** Translate every unlocked element by a delta. */
    public static Map<String, Bounds> nudge(List<LabelElement> elements, double dxMm, double dyMm) {
        if (dxMm == 0 && dyMm == 0) {
            return Map.of();
        }
        Map<String, Bounds> changes = new LinkedHashMap<>();
        for (LabelElement element : movable(elements)) {
            changes.put(element.id(), element.bounds().translated(dxMm, dyMm));
        }
        return changes;
    }

    /**
     * Copies of the given elements, offset and carrying fresh ids. Group children are re-idded too,
     * so a duplicated group shares no id with the original — ids must stay unique document-wide.
     *
     * @param ids supplies each new id; injected so tests are deterministic
     */
    public static List<LabelElement> duplicate(
            List<LabelElement> elements, double offsetXMm, double offsetYMm, Supplier<String> ids) {
        List<LabelElement> copies = new ArrayList<>();
        for (LabelElement element : elements) {
            copies.add(copyOf(element, offsetXMm, offsetYMm, ids));
        }
        return copies;
    }

    private static LabelElement copyOf(LabelElement element, double offsetXMm, double offsetYMm, Supplier<String> ids) {
        LabelElement copy = element.withProperties(element.properties()
                .withId(ids.get())
                .withBounds(element.bounds().translated(offsetXMm, offsetYMm)));
        if (copy instanceof GroupElement group) {
            List<LabelElement> children = new ArrayList<>();
            for (LabelElement child : group.children()) {
                children.add(copyOf(child, offsetXMm, offsetYMm, ids));
            }
            return group.withChildren(children);
        }
        return copy;
    }

    /** The smallest box containing every element. */
    public static Bounds boundingBox(List<LabelElement> elements) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("no elements");
        }
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

    private static double centre(Bounds b, boolean horizontal) {
        return horizontal ? b.centerXMm() : b.centerYMm();
    }

    private static List<LabelElement> movable(List<LabelElement> elements) {
        return elements.stream().filter(e -> !e.locked()).toList();
    }
}
