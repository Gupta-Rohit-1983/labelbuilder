package com.rohit.labelbuilder.model.style;

import java.util.Objects;

/**
 * 1-bit reduction settings for an image element (lbl-format.md §4.5). {@code enabled} is forced true
 * when printing to a 1-bit thermal device regardless of the stored value.
 */
public record Monochrome(boolean enabled, MonochromeMethod method, int threshold) {

    public Monochrome {
        Objects.requireNonNull(method, "method");
        if (threshold < 0 || threshold > 255) {
            throw new IllegalArgumentException("threshold must be 0..255, was " + threshold);
        }
    }

    /** Colour (no reduction); default threshold method retained for when it is later enabled. */
    public static Monochrome disabled() {
        return new Monochrome(false, MonochromeMethod.THRESHOLD, 128);
    }
}
