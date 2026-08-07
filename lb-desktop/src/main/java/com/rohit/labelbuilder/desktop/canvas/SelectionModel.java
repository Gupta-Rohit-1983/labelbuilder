package com.rohit.labelbuilder.desktop.canvas;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Selection state: an ordered set of selected items with a distinguished <em>primary</em> (the
 * most recently added — the one whose resize/rotate handles are shown). Pure and generic, so the
 * selection semantics are unit-testable and reusable once real elements replace the placeholder
 * items in Phase 7.
 */
public final class SelectionModel<T> {

    private final LinkedHashSet<T> selected = new LinkedHashSet<>();
    private T primary;

    /** Selects exactly {@code item}, replacing any prior selection. */
    public void replaceWith(T item) {
        selected.clear();
        selected.add(item);
        primary = item;
    }

    /** Adds the item if absent (becoming primary), or removes it if already selected. */
    public void toggle(T item) {
        if (selected.remove(item)) {
            if (item.equals(primary)) {
                primary = selected.isEmpty() ? null : last();
            }
        } else {
            selected.add(item);
            primary = item;
        }
    }

    public void addAll(Collection<? extends T> items) {
        for (T item : items) {
            selected.add(item);
            primary = item;
        }
    }

    public void clear() {
        selected.clear();
        primary = null;
    }

    public boolean isSelected(T item) {
        return selected.contains(item);
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public int size() {
        return selected.size();
    }

    public Set<T> selected() {
        return Collections.unmodifiableSet(selected);
    }

    /** The primary item (handles are drawn on it), or {@code null} when nothing is selected. */
    public T primary() {
        return primary;
    }

    private T last() {
        T result = null;
        for (T item : selected) {
            result = item;
        }
        return result;
    }
}
