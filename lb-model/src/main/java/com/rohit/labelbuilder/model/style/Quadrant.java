package com.rohit.labelbuilder.model.style;

/**
 * A quarter-turn rotation: the only rotations thermal printer firmware applies to a barcode
 * (lbl-format.md §4.3).
 *
 * <p>The element and the file format both store plain degrees, because that is what the format
 * specifies. This enum exists so the <em>editor</em> can offer the four legal choices instead of a
 * free integer field, where a stepper would otherwise walk straight into 1° and be rejected by the
 * element's constructor. Converting between the two is the property descriptor's job — the model is
 * unchanged.
 */
public enum Quadrant {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    private final int degrees;

    Quadrant(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    /**
     * @throws IllegalArgumentException if {@code degrees} is not 0, 90, 180 or 270
     */
    public static Quadrant fromDegrees(int degrees) {
        for (Quadrant quadrant : values()) {
            if (quadrant.degrees == degrees) {
                return quadrant;
            }
        }
        throw new IllegalArgumentException("not a quadrant: " + degrees);
    }
}
