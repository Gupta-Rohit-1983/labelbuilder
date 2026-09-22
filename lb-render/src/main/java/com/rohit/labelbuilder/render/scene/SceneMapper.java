package com.rohit.labelbuilder.render.scene;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a {@link LabelDocument} into a {@link RenderScene} — the single seam both renderers
 * consume: the Java2D reference renderer (print, PNG baselines) and the JavaFX design canvas. Keeping
 * this mapping in one pure, headless place is what makes "the canvas matches the printout" a testable
 * property rather than a hope (risk R-03).
 *
 * <p>Elements that are hidden, or sit on a hidden layer, are dropped. Groups are flattened (their
 * children already carry absolute coordinates). Image and barcode elements render as labelled
 * placeholders until their engines land (Phases 10 and 14); the geometry they occupy is already
 * correct, so layout work done now survives.
 */
public final class SceneMapper {

    private static final double PT_TO_MM = 25.4 / 72.0;

    /** Placeholder styling for element types whose real renderer has not landed yet. */
    private static final RenderColor PLACEHOLDER_FILL = new RenderColor(245, 245, 245, 255);

    private static final RenderColor PLACEHOLDER_STROKE = new RenderColor(150, 150, 150, 255);

    private SceneMapper() {}

    /** Flatten a document into a renderable scene. */
    public static RenderScene toScene(LabelDocument document) {
        Map<String, Layer> layers = new HashMap<>();
        for (Layer layer : document.layers()) {
            layers.put(layer.id(), layer);
        }
        List<RenderPrimitive> primitives = new ArrayList<>();
        for (LabelElement element : document.elements()) {
            append(primitives, element, layers);
        }
        return new RenderScene(
                document.stock().widthMm(),
                document.stock().heightMm(),
                color(document.stock().backgroundColor()),
                primitives);
    }

    private static void append(List<RenderPrimitive> out, LabelElement element, Map<String, Layer> layers) {
        if (!isVisible(element, layers)) {
            return;
        }
        Bounds b = element.bounds();
        double rot = element.rotationDeg();
        switch (element) {
            case RectangleElement r ->
                out.add(new RenderPrimitive.Rect(
                        b.xMm(),
                        b.yMm(),
                        b.widthMm(),
                        b.heightMm(),
                        rot,
                        fillColor(r.fill()),
                        strokeColor(r.stroke()),
                        r.stroke().widthMm()));
            case EllipseElement e ->
                out.add(new RenderPrimitive.Ellipse(
                        b.xMm(),
                        b.yMm(),
                        b.widthMm(),
                        b.heightMm(),
                        rot,
                        fillColor(e.fill()),
                        strokeColor(e.stroke()),
                        e.stroke().widthMm()));
            case LineElement l ->
                out.add(new RenderPrimitive.Line(
                        l.x1Mm(),
                        l.y1Mm(),
                        l.x2Mm(),
                        l.y2Mm(),
                        strokeColor(l.stroke()),
                        l.stroke().widthMm()));
            case TextElement t -> {
                double sizeMm = t.font().sizePt() * PT_TO_MM;
                // RenderPrimitive.Text is baseline-anchored; approximate the ascent from the size.
                out.add(new RenderPrimitive.Text(
                        b.xMm(),
                        b.yMm() + sizeMm,
                        t.value(),
                        sizeMm,
                        color(t.color()),
                        rot,
                        t.font().bold()));
            }
            case ImageElement i -> placeholder(out, b, rot, "Image");
            case BarcodeElement bc -> placeholder(out, b, rot, bc.symbology().name());
            case GroupElement g -> {
                for (LabelElement child : g.children()) {
                    append(out, child, layers);
                }
            }
        }
    }

    /** A boxed, labelled stand-in for an element whose real renderer has not landed. */
    private static void placeholder(List<RenderPrimitive> out, Bounds b, double rot, String caption) {
        out.add(new RenderPrimitive.Rect(
                b.xMm(), b.yMm(), b.widthMm(), b.heightMm(), rot, PLACEHOLDER_FILL, PLACEHOLDER_STROKE, 0.2));
        double sizeMm = Math.min(3.0, Math.max(1.5, b.heightMm() / 4));
        out.add(new RenderPrimitive.Text(
                b.xMm() + 1, b.yMm() + sizeMm + 1, caption, sizeMm, PLACEHOLDER_STROKE, rot, false));
    }

    private static boolean isVisible(LabelElement element, Map<String, Layer> layers) {
        if (!element.visible()) {
            return false;
        }
        Layer layer = layers.get(element.layerId());
        return layer == null || layer.visible(); // unknown layer: lenient here, rejected at save
    }

    /** {@code null} means "no fill" to the renderers. */
    private static RenderColor fillColor(Fill fill) {
        return fill.enabled() ? color(fill.color()) : null;
    }

    /** {@code null} means "no outline" to the renderers. */
    private static RenderColor strokeColor(Stroke stroke) {
        return stroke.widthMm() > 0 ? color(stroke.color()) : null;
    }

    private static RenderColor color(RgbaColor c) {
        return new RenderColor(c.r(), c.g(), c.b(), c.a());
    }
}
