package com.rohit.labelbuilder.model.style;

/** How a text element reconciles its content with its bounds (lbl-format.md §4.2). */
public enum AutoFit {
    /** Content is clipped/overflows; bounds and font size are fixed. */
    NONE,
    /** Font size shrinks so the text fits the fixed bounds. */
    SHRINK_TO_FIT,
    /** Bounds grow so the fixed-size text fits. */
    GROW_BOUNDS
}
