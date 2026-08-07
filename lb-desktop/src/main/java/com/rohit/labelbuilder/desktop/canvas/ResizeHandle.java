package com.rohit.labelbuilder.desktop.canvas;

/**
 * The eight resize handles of a selection box. Which edges each handle moves is captured here so
 * the resize maths in {@link BoundsMm#resized} stays declarative. Rotation is a separate gesture,
 * not a handle.
 */
public enum ResizeHandle {
    NW,
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W;

    public boolean movesLeft() {
        return this == NW || this == W || this == SW;
    }

    public boolean movesRight() {
        return this == NE || this == E || this == SE;
    }

    public boolean movesTop() {
        return this == NW || this == N || this == NE;
    }

    public boolean movesBottom() {
        return this == SW || this == S || this == SE;
    }
}
