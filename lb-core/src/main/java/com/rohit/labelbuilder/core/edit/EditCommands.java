package com.rohit.labelbuilder.core.edit;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.command.Command;
import com.rohit.labelbuilder.core.command.CompositeCommand;
import com.rohit.labelbuilder.core.command.GroupCommand;
import com.rohit.labelbuilder.core.command.RemoveElementCommand;
import com.rohit.labelbuilder.core.command.ReorderCommand;
import com.rohit.labelbuilder.core.command.SetBoundsCommand;
import com.rohit.labelbuilder.core.command.UngroupCommand;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Turns the pure {@link ArrangeOps} results into undoable {@link Command}s. Each returns an empty
 * {@link Optional} when there is nothing to do, so a no-op arrange (one element selected, an already
 * aligned selection) never pushes a dead entry onto the undo stack.
 *
 * <p>A multi-element result is one {@link CompositeCommand}, so an align of eight elements is a
 * single undo step. A single-element result stays a bare {@link SetBoundsCommand}, which lets a run
 * of nudges on one element coalesce into one undo step via its merge key.
 */
public final class EditCommands {

    private EditCommands() {}

    public static Optional<Command> align(List<LabelElement> elements, Align align) {
        return boundsCommand(align.displayName(), ArrangeOps.align(elements, align));
    }

    public static Optional<Command> distribute(List<LabelElement> elements, Distribute axis) {
        return boundsCommand(axis.displayName(), ArrangeOps.distribute(elements, axis));
    }

    public static Optional<Command> nudge(List<LabelElement> elements, double dxMm, double dyMm) {
        return boundsCommand("Nudge", ArrangeOps.nudge(elements, dxMm, dyMm));
    }

    /** Adds offset copies of the given elements; the copies are returned via {@code created}. */
    public static Optional<Command> duplicate(
            List<LabelElement> elements,
            double offsetXMm,
            double offsetYMm,
            Supplier<String> ids,
            List<LabelElement> created) {
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        List<LabelElement> copies = ArrangeOps.duplicate(elements, offsetXMm, offsetYMm, ids);
        created.addAll(copies);
        List<Command> adds = new ArrayList<>();
        for (LabelElement copy : copies) {
            adds.add(new AddElementCommand(copy));
        }
        return Optional.of(adds.size() == 1 ? adds.getFirst() : new CompositeCommand("Duplicate", adds));
    }

    /** Remove the given elements; one undo step regardless of how many. */
    public static Optional<Command> delete(List<LabelElement> elements) {
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        List<Command> removals = new ArrayList<>();
        for (LabelElement element : elements) {
            removals.add(new RemoveElementCommand(element.id()));
        }
        return Optional.of(removals.size() == 1 ? removals.getFirst() : new CompositeCommand("Delete", removals));
    }

    /** Collect the elements into a group. Needs at least two. */
    public static Optional<Command> group(List<LabelElement> elements, Supplier<String> ids) {
        if (elements.size() < 2) {
            return Optional.empty();
        }
        List<String> memberIds = elements.stream().map(LabelElement::id).toList();
        return Optional.of(GroupCommand.of(memberIds, ids.get()));
    }

    /** Dissolve every group in the selection; non-groups are ignored. */
    public static Optional<Command> ungroup(List<LabelElement> elements) {
        List<Command> ungroups = new ArrayList<>();
        for (LabelElement element : elements) {
            if (element instanceof GroupElement) {
                ungroups.add(new UngroupCommand(element.id()));
            }
        }
        if (ungroups.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ungroups.size() == 1 ? ungroups.getFirst() : new CompositeCommand("Ungroup", ungroups));
    }

    /** Restack the elements. */
    public static Optional<Command> reorder(List<LabelElement> elements, ZOrder move) {
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new ReorderCommand(elements.stream().map(LabelElement::id).toList(), move));
    }

    /**
     * Paste clipboard contents as new elements. Identical in shape to {@link #duplicate} — a paste is
     * a duplicate whose source is the clipboard rather than the selection — so pasted elements also
     * get fresh ids and never collide with what is already in the document.
     */
    public static Optional<Command> paste(
            List<LabelElement> clipboard,
            double offsetXMm,
            double offsetYMm,
            Supplier<String> ids,
            List<LabelElement> created) {
        return duplicate(clipboard, offsetXMm, offsetYMm, ids, created);
    }

    private static Optional<Command> boundsCommand(String label, Map<String, Bounds> changes) {
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        List<Command> edits = new ArrayList<>();
        for (Map.Entry<String, Bounds> change : changes.entrySet()) {
            edits.add(new SetBoundsCommand(change.getKey(), change.getValue(), label));
        }
        return Optional.of(edits.size() == 1 ? edits.getFirst() : new CompositeCommand(label, edits));
    }
}
