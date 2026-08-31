package com.rohit.labelbuilder.model.style;

import java.util.Objects;

/**
 * Human-readable interpretation text shown alongside a barcode (lbl-format.md §4.3).
 * {@code customText} overrides the encoded value when non-null.
 */
public record Hri(HriPosition position, FontSpec font, boolean showCheckDigit, String customText) {

    public Hri {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(font, "font");
        // customText may be null (use the encoded value)
    }

    /** HRI below the bars, showing the check digit, no custom text. */
    public static Hri below(FontSpec font) {
        return new Hri(HriPosition.BELOW, font, true, null);
    }
}
