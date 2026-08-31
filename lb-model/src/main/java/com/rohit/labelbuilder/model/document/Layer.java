package com.rohit.labelbuilder.model.document;

import java.util.Objects;

/**
 * A named layer (lbl-format.md §3). Elements reference a layer by {@code id}; the layer's visibility
 * and lock apply to every element on it, on top of each element's own flags.
 */
public record Layer(String id, String name, boolean visible, boolean locked) {

    public Layer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    /** A visible, unlocked layer. */
    public static Layer of(String id, String name) {
        return new Layer(id, name, true, false);
    }

    public Layer withVisible(boolean newVisible) {
        return new Layer(id, name, newVisible, locked);
    }

    public Layer withLocked(boolean newLocked) {
        return new Layer(id, name, visible, newLocked);
    }
}
