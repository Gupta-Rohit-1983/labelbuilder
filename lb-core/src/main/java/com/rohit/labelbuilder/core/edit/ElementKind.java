package com.rohit.labelbuilder.core.edit;

/**
 * The kinds of element a user can place on a label. This is the tool vocabulary — the UI's creation
 * tools and the {@link ElementFactory} share it, so adding a placeable type is one enum constant
 * plus one factory branch.
 */
public enum ElementKind {
    TEXT("Text", 40, 8),
    RECTANGLE("Rectangle", 30, 20),
    ELLIPSE("Ellipse", 20, 20),
    LINE("Line", 30, 0),
    IMAGE("Image", 25, 25),
    BARCODE("Barcode", 40, 15);

    private final String displayName;
    private final double defaultWidthMm;
    private final double defaultHeightMm;

    ElementKind(String displayName, double defaultWidthMm, double defaultHeightMm) {
        this.displayName = displayName;
        this.defaultWidthMm = defaultWidthMm;
        this.defaultHeightMm = defaultHeightMm;
    }

    /** Human-readable name, used for tool buttons and as a new element's default name. */
    public String displayName() {
        return displayName;
    }

    /** Size used when the tool is clicked rather than dragged out. */
    public double defaultWidthMm() {
        return defaultWidthMm;
    }

    public double defaultHeightMm() {
        return defaultHeightMm;
    }
}
