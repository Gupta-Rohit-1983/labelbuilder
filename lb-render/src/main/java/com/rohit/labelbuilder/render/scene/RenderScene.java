package com.rohit.labelbuilder.render.scene;

import java.util.List;

/**
 * A resolution-independent description of one label, in millimetres: the surface size, its
 * background, and an ordered list of primitives (painted back-to-front). This is the seam between
 * the label object model (Phase 7, which produces scenes) and the renderers (which consume them),
 * and the shared input that lets the Java2D reference and the JavaFX canvas be compared for parity.
 */
public record RenderScene(double widthMm, double heightMm, RenderColor background, List<RenderPrimitive> primitives) {

    public RenderScene {
        if (widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("Scene dimensions must be positive: " + widthMm + " x " + heightMm);
        }
        primitives = List.copyOf(primitives);
    }

    /** An empty white label of the given size. */
    public static RenderScene blank(double widthMm, double heightMm) {
        return new RenderScene(widthMm, heightMm, RenderColor.WHITE, List.of());
    }
}
