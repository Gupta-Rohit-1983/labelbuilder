package com.rohit.labelbuilder.model.style;

/** Dithering method when reducing an image to 1-bit for thermal printing (lbl-format.md §4.5). */
public enum MonochromeMethod {
    THRESHOLD,
    FLOYD_STEINBERG,
    ORDERED
}
