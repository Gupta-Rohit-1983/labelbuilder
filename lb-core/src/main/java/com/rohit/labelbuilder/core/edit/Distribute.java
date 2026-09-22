package com.rohit.labelbuilder.core.edit;

/**
 * Axis along which to space a selection evenly. The outermost two elements stay put and the ones
 * between them are spread so their centres are equally spaced — the standard behaviour, and the only
 * one that is stable when applied twice.
 */
public enum Distribute {
    HORIZONTALLY("Distribute Horizontally"),
    VERTICALLY("Distribute Vertically");

    private final String displayName;

    Distribute(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
