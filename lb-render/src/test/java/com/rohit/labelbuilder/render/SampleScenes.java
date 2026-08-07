package com.rohit.labelbuilder.render;

import com.rohit.labelbuilder.render.scene.RenderColor;
import com.rohit.labelbuilder.render.scene.RenderPrimitive;
import com.rohit.labelbuilder.render.scene.RenderScene;
import java.util.List;

/** Fixed scenes shared by the renderer tests, so the baseline and the analytical checks agree. */
final class SampleScenes {

    private SampleScenes() {}

    static final double BASELINE_DPI = 150;

    /** Vector-only (no text) so the pixel baseline is deterministic across platforms. */
    static RenderScene basicVector() {
        return new RenderScene(
                100,
                60,
                RenderColor.WHITE,
                List.of(
                        RenderPrimitive.rect(10, 10, 30, 20, RenderColor.rgb(220, 40, 40), RenderColor.BLACK, 0.5),
                        new RenderPrimitive.Ellipse(
                                55, 15, 30, 25, 0, RenderColor.rgb(40, 80, 220), RenderColor.BLACK, 0.5),
                        new RenderPrimitive.Line(10, 50, 90, 50, RenderColor.rgb(30, 160, 60), 1.0)));
    }
}
