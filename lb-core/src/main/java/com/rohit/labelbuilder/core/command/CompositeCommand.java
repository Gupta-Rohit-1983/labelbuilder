package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import java.util.List;
import java.util.Objects;

/**
 * A transaction: several commands applied in order but recorded as a single undo step. Because the
 * {@link CommandStack} undoes by restoring the pre-transaction snapshot, the whole batch reverses
 * atomically. If any child throws mid-way, {@link #apply} propagates and — since it has not mutated
 * anything — the stack is left untouched.
 */
public record CompositeCommand(String label, List<Command> children) implements Command {

    public CompositeCommand {
        Objects.requireNonNull(label, "label");
        children = List.copyOf(children);
        if (children.isEmpty()) {
            throw new IllegalArgumentException("a transaction needs at least one command");
        }
    }

    /** A transaction of the given commands. */
    public static CompositeCommand of(String label, Command... commands) {
        return new CompositeCommand(label, List.of(commands));
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        LabelDocument result = document;
        for (Command child : children) {
            result = child.apply(result);
        }
        return result;
    }
}
