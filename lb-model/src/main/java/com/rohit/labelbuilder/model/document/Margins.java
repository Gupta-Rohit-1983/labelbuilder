package com.rohit.labelbuilder.model.document;

/** Non-printable margins around the label, in millimetres (lbl-format.md §3). */
public record Margins(double topMm, double rightMm, double bottomMm, double leftMm) {

    public Margins {
        requireNonNegativeFinite(topMm, "topMm");
        requireNonNegativeFinite(rightMm, "rightMm");
        requireNonNegativeFinite(bottomMm, "bottomMm");
        requireNonNegativeFinite(leftMm, "leftMm");
    }

    /** Equal margins on all four sides. */
    public static Margins uniform(double mm) {
        return new Margins(mm, mm, mm, mm);
    }

    /** No margins. */
    public static Margins none() {
        return uniform(0);
    }

    private static void requireNonNegativeFinite(double v, String field) {
        if (v < 0 || !Double.isFinite(v)) {
            throw new IllegalArgumentException(field + " must be finite and non-negative, was " + v);
        }
    }
}
