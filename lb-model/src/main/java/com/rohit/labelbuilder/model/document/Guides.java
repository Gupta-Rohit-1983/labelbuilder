package com.rohit.labelbuilder.model.document;

import java.util.List;

/**
 * Ruler guide lines persisted with the document (lbl-format.md §3): {@code vertical} holds x
 * positions, {@code horizontal} holds y positions, all in millimetres. Both lists are defensively
 * copied and unmodifiable.
 */
public record Guides(List<Double> vertical, List<Double> horizontal) {

    public Guides {
        vertical = List.copyOf(vertical);
        horizontal = List.copyOf(horizontal);
    }

    /** No guides. */
    public static Guides none() {
        return new Guides(List.of(), List.of());
    }
}
