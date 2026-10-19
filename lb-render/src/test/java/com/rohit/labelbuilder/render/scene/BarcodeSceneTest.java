package com.rohit.labelbuilder.render.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rohit.labelbuilder.model.document.GridSpec;
import com.rohit.labelbuilder.model.document.Guides;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Symbology;
import com.rohit.labelbuilder.render.Java2DRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Barcodes become real bars in the scene both renderers consume (Phase 10d). */
class BarcodeSceneTest {

    private static final List<Layer> ONE_LAYER = List.of(Layer.of("layer-1", "Content"));

    private static BarcodeElement barcode(Symbology symbology, String value, HriPosition hri) {
        return BarcodeElement.of(
                ElementProperties.of("b1", "b1", "layer-1", new Bounds(10, 10, 50, 20)),
                symbology,
                value,
                0.33,
                12,
                3.3,
                CheckDigitMode.AUTO,
                new Hri(hri, FontSpec.of("Arial", 8), true, null));
    }

    private static LabelDocument documentOf(LabelElement element) {
        return new LabelDocument(
                "d", "T", Stock.of(100, 50), GridSpec.defaults(), Guides.none(), ONE_LAYER, List.of(element));
    }

    private static RenderScene sceneOf(LabelElement element) {
        return SceneMapper.toScene(documentOf(element));
    }

    @Test
    void aBarcodeBecomesManyBarsNotAPlaceholderBox() {
        RenderScene scene = sceneOf(barcode(Symbology.CODE_128, "12345678", HriPosition.NONE));

        // The placeholder was a single rect plus a caption; a real Code 128 is dozens of bars.
        assertThat(scene.primitives()).hasSizeGreaterThan(20);
        assertThat(scene.primitives()).allMatch(RenderPrimitive.Rect.class::isInstance);
    }

    @Test
    void barsAreOffsetToTheElementsPosition() {
        RenderScene scene = sceneOf(barcode(Symbology.CODE_128, "12345678", HriPosition.NONE));

        assertThat(scene.primitives()).allSatisfy(primitive -> {
            RenderPrimitive.Rect bar = (RenderPrimitive.Rect) primitive;
            assertThat(bar.xMm()).isGreaterThanOrEqualTo(10.0); // the element's x
            assertThat(bar.yMm()).isGreaterThanOrEqualTo(10.0); // the element's y
        });
    }

    @Test
    void humanReadableTextBecomesATextPrimitive() {
        RenderScene scene = sceneOf(barcode(Symbology.CODE_128, "12345678", HriPosition.BELOW));

        assertThat(scene.primitives()).anyMatch(RenderPrimitive.Text.class::isInstance);
    }

    @Test
    void aTwoDimensionalSymbolBecomesModuleRuns() {
        RenderScene scene = sceneOf(barcode(Symbology.QR, "LABELBUILDER", HriPosition.NONE));

        assertThat(scene.primitives()).isNotEmpty();
        assertThat(scene.primitives()).allMatch(RenderPrimitive.Rect.class::isInstance);
    }

    @Test
    void anUnencodableValueShowsAPlaceholderInsteadOfThrowing() {
        // The canvas repaints while the user types; a half-typed EAN must not take the renderer down.
        RenderScene scene = sceneOf(barcode(Symbology.EAN_13, "12", HriPosition.NONE));

        assertThat(scene.primitives()).isNotEmpty();
        assertThat(scene.primitives()).anySatisfy(p -> assertThat(p)
                .isInstanceOfSatisfying(RenderPrimitive.Text.class, text -> assertThat(text.text())
                        .contains("EAN_13")));
    }

    @Test
    void rotationTurnsTheWholeSymbolAboutTheElementCentre() {
        BarcodeElement upright = barcode(Symbology.CODE_128, "12345678", HriPosition.NONE);
        RenderScene straight = sceneOf(upright);
        RenderScene turned = sceneOf((BarcodeElement) upright.withRotationDeg(90));

        assertThat(turned.primitives()).hasSameSizeAs(straight.primitives());
        // Every bar carries the element's rotation, and the set has moved — not merely spun in place.
        assertThat(turned.primitives()).allSatisfy(p -> assertThat(((RenderPrimitive.Rect) p).rotationDeg())
                .isEqualTo(90.0));
        assertThat(firstBarX(turned)).isNotEqualTo(firstBarX(straight));
    }

    @Test
    void theReferenceRendererDrawsABarcodeWithoutComplaint() {
        // Parity in the direction that matters: the same scene the canvas paints also prints.
        RenderScene scene = sceneOf(barcode(Symbology.CODE_128, "12345678", HriPosition.BELOW));

        assertThatCode(() -> new Java2DRenderer().render(scene, 203)).doesNotThrowAnyException();
    }

    private static double firstBarX(RenderScene scene) {
        return ((RenderPrimitive.Rect) scene.primitives().getFirst()).xMm();
    }
}
