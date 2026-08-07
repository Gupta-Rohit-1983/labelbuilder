package com.rohit.labelbuilder.render.scene;

/**
 * A framework-neutral RGBA colour (channels 0–255). Kept free of {@code java.awt} and JavaFX so
 * the same {@link RenderScene} description can be drawn by the Java2D reference renderer
 * (lb-render) and the JavaFX canvas (lb-desktop), which is what makes screen/print parity testable
 * (architecture §2, risk R-03).
 */
public record RenderColor(int r, int g, int b, int a) {

    public RenderColor {
        requireByte(r, "r");
        requireByte(g, "g");
        requireByte(b, "b");
        requireByte(a, "a");
    }

    public static RenderColor rgb(int r, int g, int b) {
        return new RenderColor(r, g, b, 255);
    }

    public static final RenderColor BLACK = rgb(0, 0, 0);
    public static final RenderColor WHITE = rgb(255, 255, 255);

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Colour channel " + name + " out of range 0..255: " + value);
        }
    }
}
