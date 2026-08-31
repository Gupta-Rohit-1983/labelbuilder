package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;

/**
 * A single, named, undoable edit to a {@link LabelDocument}. A command is a <b>pure forward
 * transform</b>: {@link #apply(LabelDocument)} returns a new document and never mutates its argument
 * (the model is immutable). Undo is handled by the {@link CommandStack} restoring the prior document
 * snapshot, so commands need not compute their own inverse — this keeps them trivial and correct by
 * construction.
 *
 * <p>{@link #mergeKey()} lets a run of related commands collapse into one undo step (a spinner being
 * dragged, characters being typed): consecutive commands whose merge keys are non-null and equal are
 * coalesced, the newer replacing the older. For that to be correct such commands must be
 * <b>absolute</b> (each expresses the full change from before the run, not a delta), so re-applying
 * the newest to the pre-run document yields the right result.
 */
public interface Command {

    /** Short human-readable name, shown after "Undo"/"Redo" and in the history panel. */
    String label();

    /** Apply this edit, returning a new document. Must not mutate {@code document}. */
    LabelDocument apply(LabelDocument document);

    /**
     * A coalescing key, or {@code null} (default) to never merge. Consecutive commands with equal
     * non-null keys are merged into a single undo step.
     */
    default String mergeKey() {
        return null;
    }
}
