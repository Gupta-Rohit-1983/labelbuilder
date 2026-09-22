package com.rohit.labelbuilder.render.scene;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.model.document.GridSpec;
import com.rohit.labelbuilder.model.document.Guides;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The document → scene flattening both renderers depend on. Pure and headless. */
class SceneMapperTest {

    private static ElementProperties props(String id) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(2, 3, 20, 10));
    }

    private static LabelDocument documentOf(List<LabelElement> elements, List<Layer> layers) {
        return new LabelDocument("d", "T", Stock.of(100, 50), GridSpec.defaults(), Guides.none(), layers, elements);
    }

    private static final List<Layer> ONE_LAYER = List.of(Layer.of("layer-1", "Content"));

    @Test
    void sceneTakesItsSizeAndBackgroundFromTheStock() {
        RenderScene scene = SceneMapper.toScene(documentOf(List.of(), ONE_LAYER));

        assertThat(scene.widthMm()).isEqualTo(100);
        assertThat(scene.heightMm()).isEqualTo(50);
        assertThat(scene.background()).isEqualTo(new RenderColor(255, 255, 255, 255));
    }

    @Test
    void shapesMapToTheMatchingPrimitives() {
        RectangleElement rect =
                new RectangleElement(props("r"), Stroke.solid(0.3), Fill.of(RgbaColor.rgb(10, 20, 30)), 0);

        RenderScene scene = SceneMapper.toScene(documentOf(List.of(rect), ONE_LAYER));

        assertThat(scene.primitives()).hasSize(1);
        RenderPrimitive.Rect primitive =
                (RenderPrimitive.Rect) scene.primitives().getFirst();
        assertThat(primitive.xMm()).isEqualTo(2);
        assertThat(primitive.wMm()).isEqualTo(20);
        assertThat(primitive.fill()).isEqualTo(new RenderColor(10, 20, 30, 255));
        assertThat(primitive.stroke()).isEqualTo(new RenderColor(0, 0, 0, 255));
    }

    @Test
    void disabledFillAndZeroWidthStrokeBecomeNull() {
        RectangleElement rect = new RectangleElement(props("r"), Stroke.none(), Fill.none(), 0);

        RenderPrimitive.Rect primitive =
                (RenderPrimitive.Rect) SceneMapper.toScene(documentOf(List.of(rect), ONE_LAYER))
                        .primitives()
                        .getFirst();

        assertThat(primitive.fill()).isNull();
        assertThat(primitive.stroke()).isNull();
    }

    @Test
    void textConvertsPointsToMillimetresAndAnchorsOnTheBaseline() {
        TextElement text = TextElement.of(props("t"), "Hi", FontSpec.of("Arial", 72));

        RenderPrimitive.Text primitive =
                (RenderPrimitive.Text) SceneMapper.toScene(documentOf(List.of(text), ONE_LAYER))
                        .primitives()
                        .getFirst();

        assertThat(primitive.fontSizeMm()).isEqualTo(25.4); // 72pt = 1 inch
        assertThat(primitive.text()).isEqualTo("Hi");
        assertThat(primitive.yMm()).isEqualTo(3 + 25.4); // baseline sits below the box top
    }

    @Test
    void hiddenElementsAreDropped() {
        LabelElement hidden = new RectangleElement(props("r"), Stroke.solid(0.2), Fill.none(), 0).withVisible(false);

        assertThat(SceneMapper.toScene(documentOf(List.of(hidden), ONE_LAYER)).primitives())
                .isEmpty();
    }

    @Test
    void elementsOnAHiddenLayerAreDropped() {
        RectangleElement rect = new RectangleElement(props("r"), Stroke.solid(0.2), Fill.none(), 0);
        List<Layer> hiddenLayer = List.of(new Layer("layer-1", "Content", false, false));

        assertThat(SceneMapper.toScene(documentOf(List.of(rect), hiddenLayer)).primitives())
                .isEmpty();
    }

    @Test
    void groupsAreFlattenedIntoTheirChildren() {
        RectangleElement child = new RectangleElement(props("c"), Stroke.solid(0.2), Fill.none(), 0);
        GroupElement group = new GroupElement(props("g"), List.of(child));

        assertThat(SceneMapper.toScene(documentOf(List.of(group), ONE_LAYER)).primitives())
                .hasSize(1)
                .allMatch(RenderPrimitive.Rect.class::isInstance);
    }

    @Test
    void imagesRenderAsALabelledPlaceholderBox() {
        ImageElement image = ImageElement.of(props("i"), "assets/logo.png");

        List<RenderPrimitive> primitives =
                SceneMapper.toScene(documentOf(List.of(image), ONE_LAYER)).primitives();

        // A box plus its caption, occupying the element's real geometry.
        assertThat(primitives).hasSize(2);
        assertThat(primitives.getFirst()).isInstanceOf(RenderPrimitive.Rect.class);
        assertThat(((RenderPrimitive.Text) primitives.get(1)).text()).isEqualTo("Image");
    }
}
