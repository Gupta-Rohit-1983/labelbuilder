package com.rohit.labelbuilder.model.style;

import java.util.Objects;

/**
 * A shape interior fill. {@code enabled} is explicit (rather than a null colour) so a disabled fill
 * still remembers its last colour across a toggle in the inspector (lbl-format.md §4.6).
 */
public record Fill(RgbaColor color, boolean enabled) {

    public Fill {
        Objects.requireNonNull(color, "color");
    }

    /** An enabled fill of the given colour. */
    public static Fill of(RgbaColor color) {
        return new Fill(color, true);
    }

    /** A disabled (transparent) fill. */
    public static Fill none() {
        return new Fill(RgbaColor.WHITE, false);
    }

    public Fill withEnabled(boolean newEnabled) {
        return new Fill(color, newEnabled);
    }

    public Fill withColor(RgbaColor newColor) {
        return new Fill(newColor, enabled);
    }
}
