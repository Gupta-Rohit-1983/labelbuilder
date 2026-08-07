package com.rohit.labelbuilder.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.render.scene.RenderColor;
import com.rohit.labelbuilder.render.scene.RenderPrimitive;
import com.rohit.labelbuilder.render.scene.RenderScene;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class Java2DRendererTest {

    private final Java2DRenderer renderer = new Java2DRenderer();

    private static int red(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >> 16) & 0xFF;
    }

    private static int green(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >> 8) & 0xFF;
    }

    private static int blue(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFF;
    }

    private static int px(double mm, double dpi) {
        return (int) (mm * dpi / 25.4);
    }

    @Test
    void imageSizeFollowsDimensionsAndDpi() {
        BufferedImage img = renderer.render(RenderScene.blank(100, 60), 150);

        assertThat(img.getWidth()).isEqualTo((int) Math.round(100 * 150 / 25.4)); // 591
        assertThat(img.getHeight()).isEqualTo((int) Math.round(60 * 150 / 25.4)); // 354
    }

    @Test
    void higherDpiProducesProportionallyMorePixels() {
        BufferedImage lo = renderer.render(RenderScene.blank(100, 60), 150);
        BufferedImage hi = renderer.render(RenderScene.blank(100, 60), 300);

        assertThat(hi.getWidth()).isEqualTo((int) Math.round(100 * 300 / 25.4)); // 1181
        assertThat(hi.getWidth()).isGreaterThan(lo.getWidth() * 19 / 10); // ~2x
    }

    @Test
    void backgroundFillsTheWholeImage() {
        BufferedImage img = renderer.render(new RenderScene(20, 20, RenderColor.rgb(10, 20, 30), List.of()), 96);

        int corner = img.getRGB(0, 0);
        assertThat((corner >> 16) & 0xFF).isEqualTo(10);
        assertThat((corner >> 8) & 0xFF).isEqualTo(20);
        assertThat(corner & 0xFF).isEqualTo(30);
    }

    @Test
    void shapesRenderTheirFillColourAtTheirCentre() {
        double dpi = SampleScenes.BASELINE_DPI;
        BufferedImage img = renderer.render(SampleScenes.basicVector(), dpi);

        // Centre of the red rect (25, 20) mm
        int rx = px(25, dpi);
        int ry = px(20, dpi);
        assertThat(red(img, rx, ry)).isGreaterThan(200);
        assertThat(green(img, rx, ry)).isLessThan(70);

        // Centre of the blue ellipse (70, 27.5) mm
        int ex = px(70, dpi);
        int ey = px(27, dpi);
        assertThat(blue(img, ex, ey)).isGreaterThan(180);
        assertThat(red(img, ex, ey)).isLessThan(90);
    }

    @Test
    void emptyAreasKeepTheBackground() {
        BufferedImage img = renderer.render(SampleScenes.basicVector(), SampleScenes.BASELINE_DPI);
        int bx = px(50, SampleScenes.BASELINE_DPI);
        int by = px(5, SampleScenes.BASELINE_DPI);

        assertThat(red(img, bx, by)).isEqualTo(255);
        assertThat(green(img, bx, by)).isEqualTo(255);
        assertThat(blue(img, bx, by)).isEqualTo(255);
    }

    @Test
    void renderingIsDeterministic() {
        BufferedImage a = renderer.render(SampleScenes.basicVector(), SampleScenes.BASELINE_DPI);
        BufferedImage b = renderer.render(SampleScenes.basicVector(), SampleScenes.BASELINE_DPI);

        assertThat(ImageComparator.compare(a, b).isIdentical()).isTrue();
    }

    @Test
    void textDrawsSomethingOntoTheLabel() {
        RenderScene scene = new RenderScene(
                60,
                20,
                RenderColor.WHITE,
                List.of(new RenderPrimitive.Text(4, 14, "HELLO", 10, RenderColor.BLACK, 0, true)));

        BufferedImage img = renderer.render(scene, 150);

        long nonWhite = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    nonWhite++;
                }
            }
        }
        assertThat(nonWhite).isPositive();
    }

    @Test
    void nonPositiveDpiIsRejected() {
        assertThatThrownBy(() -> renderer.render(RenderScene.blank(10, 10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
