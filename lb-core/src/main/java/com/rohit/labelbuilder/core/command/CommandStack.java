package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The undo/redo history for one open document. Because {@link LabelDocument} is immutable, history is
 * a list of document snapshots with the {@link Command} that produced each; the cursor marks the
 * current one. Executing truncates any redo tail, applying an edit and appending a snapshot; undo and
 * redo just move the cursor. Consecutive commands with matching {@link Command#mergeKey()} collapse
 * into a single step (see {@link Command}).
 *
 * <p>Not thread-safe for concurrent mutation; intended to be driven from the UI thread. Change
 * listeners (e.g. to enable/disable Undo/Redo actions) are notified after every state change.
 */
public final class CommandStack {

    /** Default cap on undoable steps; older ones fall off the bottom. */
    public static final int DEFAULT_CAPACITY = 200;

    private record Entry(Command command, LabelDocument document) {}

    private final int capacity;
    private final List<Entry> history = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int cursor;

    public CommandStack(LabelDocument initial) {
        this(initial, DEFAULT_CAPACITY);
    }

    public CommandStack(LabelDocument initial, int capacity) {
        Objects.requireNonNull(initial, "initial");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        this.capacity = capacity;
        history.add(new Entry(null, initial)); // baseline (no undo below this)
        this.cursor = 0;
    }

    /** The current document. */
    public LabelDocument current() {
        return history.get(cursor).document();
    }

    /**
     * Apply {@code command} to the current document, making the result current and clearing any redo
     * tail. Merges with the previous step when both merge keys are non-null and equal.
     *
     * @return the new current document
     */
    public LabelDocument execute(Command command) {
        Objects.requireNonNull(command, "command");

        boolean merge = command.mergeKey() != null
                && cursor > 0
                && history.get(cursor).command() != null
                && command.mergeKey().equals(history.get(cursor).command().mergeKey());

        // Apply first so a throwing command leaves the stack untouched.
        LabelDocument base = merge ? history.get(cursor - 1).document() : current();
        LabelDocument next = command.apply(base);
        Objects.requireNonNull(next, "command.apply returned null");

        if (merge) {
            history.set(cursor, new Entry(command, next));
        } else {
            dropRedoTail();
            history.add(new Entry(command, next));
            cursor++;
            enforceCapacity();
        }
        fireChanged();
        return current();
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < history.size() - 1;
    }

    /** Undo one step (no-op if nothing to undo), returning the now-current document. */
    public LabelDocument undo() {
        if (canUndo()) {
            cursor--;
            fireChanged();
        }
        return current();
    }

    /** Redo one step (no-op if nothing to redo), returning the now-current document. */
    public LabelDocument redo() {
        if (canRedo()) {
            cursor++;
            fireChanged();
        }
        return current();
    }

    /** Label of the edit that undo would reverse, if any (for an "Undo {label}" menu item). */
    public Optional<String> undoLabel() {
        return canUndo() ? Optional.of(history.get(cursor).command().label()) : Optional.empty();
    }

    /** Label of the edit that redo would re-apply, if any. */
    public Optional<String> redoLabel() {
        return canRedo() ? Optional.of(history.get(cursor + 1).command().label()) : Optional.empty();
    }

    /** Number of steps that can be undone. */
    public int undoDepth() {
        return cursor;
    }

    /** Number of steps that can be redone. */
    public int redoDepth() {
        return history.size() - 1 - cursor;
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void dropRedoTail() {
        if (cursor < history.size() - 1) {
            history.subList(cursor + 1, history.size()).clear();
        }
    }

    private void enforceCapacity() {
        while (history.size() - 1 > capacity) {
            history.remove(0); // drop oldest; the next entry becomes the new baseline
            cursor--;
        }
    }

    private void fireChanged() {
        for (Runnable l : listeners) {
            l.run();
        }
    }
}
