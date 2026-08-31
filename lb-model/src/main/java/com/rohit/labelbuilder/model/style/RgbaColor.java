package com.rohit.labelbuilder.model.style;

/**
 * An sRGB colour with 8-bit channels and alpha, framework-neutral (no AWT/JavaFX). Serialises to
 * the {@code #RRGGBB} / {@code #RRGGBBAA} hex form used by the {@code .lbl} format (lbl-format.md
 * §3); opaque colours drop the alpha pair.
 */
public record RgbaColor(int r, int g, int b, int a) {

    public static final RgbaColor BLACK = new RgbaColor(0, 0, 0, 255);
    public static final RgbaColor WHITE = new RgbaColor(255, 255, 255, 255);
    public static final RgbaColor TRANSPARENT = new RgbaColor(0, 0, 0, 0);

    public RgbaColor {
        requireByte(r, "r");
        requireByte(g, "g");
        requireByte(b, "b");
        requireByte(a, "a");
    }

    /** Opaque colour from r/g/b. */
    public static RgbaColor rgb(int r, int g, int b) {
        return new RgbaColor(r, g, b, 255);
    }

    /**
     * Parse {@code #RRGGBB} or {@code #RRGGBBAA} (case-insensitive, leading {@code #} required).
     *
     * @throws IllegalArgumentException if the string is not one of those two forms
     */
    public static RgbaColor parse(String hex) {
        if (hex == null || (hex.length() != 7 && hex.length() != 9) || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("expected #RRGGBB or #RRGGBBAA, was: " + hex);
        }
        int r = parseByte(hex, 1);
        int g = parseByte(hex, 3);
        int b = parseByte(hex, 5);
        int a = hex.length() == 9 ? parseByte(hex, 7) : 255;
        return new RgbaColor(r, g, b, a);
    }

    /** The {@code #RRGGBB} form when opaque, otherwise {@code #RRGGBBAA}. */
    public String toHex() {
        String base = String.format("#%02X%02X%02X", r, g, b);
        return a == 255 ? base : base + String.format("%02X", a);
    }

    private static int parseByte(String hex, int from) {
        return Integer.parseInt(hex.substring(from, from + 2), 16);
    }

    private static void requireByte(int v, String channel) {
        if (v < 0 || v > 255) {
            throw new IllegalArgumentException(channel + " must be 0..255, was " + v);
        }
    }
}
