package com.rohit.labelbuilder.model.style;

import java.util.Objects;

/**
 * A font selection: family plus point size and the three independent style flags (lbl-format.md
 * §4.2). Point size, not millimetres — type is conventionally specified in points and converted at
 * render time.
 */
public record FontSpec(String family, double sizePt, boolean bold, boolean italic, boolean underline) {

    public FontSpec {
        Objects.requireNonNull(family, "family");
        if (family.isBlank()) {
            throw new IllegalArgumentException("family must not be blank");
        }
        if (!(sizePt > 0) || !Double.isFinite(sizePt)) {
            throw new IllegalArgumentException("sizePt must be a positive finite number, was " + sizePt);
        }
    }

    /** A plain (non-bold, non-italic, non-underline) font. */
    public static FontSpec of(String family, double sizePt) {
        return new FontSpec(family, sizePt, false, false, false);
    }

    public FontSpec withSizePt(double newSizePt) {
        return new FontSpec(family, newSizePt, bold, italic, underline);
    }

    public FontSpec withBold(boolean newBold) {
        return new FontSpec(family, sizePt, newBold, italic, underline);
    }
}
