package com.rohit.labelbuilder.model.style;

/** How an image is scaled into its element bounds (lbl-format.md §4.5). */
public enum ImageFit {
    /** Scale to fit entirely inside, preserving aspect ratio (letterboxed). */
    CONTAIN,
    /** Scale to cover the bounds, preserving aspect ratio (cropped). */
    COVER,
    /** Stretch to the bounds, ignoring aspect ratio. */
    STRETCH,
    /** No scaling; draw at native size, clipped to bounds. */
    NONE
}
