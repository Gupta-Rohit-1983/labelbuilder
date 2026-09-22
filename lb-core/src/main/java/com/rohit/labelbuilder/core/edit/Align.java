package com.rohit.labelbuilder.core.edit;

/**
 * Edge or centre to align a selection to. Alignment is relative to the selection's own bounding box,
 * so aligning never moves the group as a whole — only the elements within it.
 */
public enum Align {
    /** Left edges to the selection's left edge. */
    LEFT("Align Left"),
    /** Horizontal centres to the selection's horizontal centre. */
    CENTER("Align Center"),
    /** Right edges to the selection's right edge. */
    RIGHT("Align Right"),
    /** Top edges to the selection's top edge. */
    TOP("Align Top"),
    /** Vertical centres to the selection's vertical centre. */
    MIDDLE("Align Middle"),
    /** Bottom edges to the selection's bottom edge. */
    BOTTOM("Align Bottom");

    private final String displayName;

    Align(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True for the three that move elements horizontally. */
    public boolean isHorizontal() {
        return this == LEFT || this == CENTER || this == RIGHT;
    }
}
