package com.rohit.labelbuilder.render.scene;

import com.rohit.labelbuilder.barcode.Barcode1DEncoder;
import com.rohit.labelbuilder.barcode.Barcode2DEncoder;
import com.rohit.labelbuilder.barcode.BarcodeException;
import com.rohit.labelbuilder.barcode.BarcodeGeometry;
import com.rohit.labelbuilder.barcode.BarcodeRequest;
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
            case BarcodeElement bc -> appendBarcode(out, bc);
            case GroupElement g -> {
                for (LabelElement child : g.children()) {
                    append(out, child, layers);
                }
            }
        }
    }

    /**
     * Encode a barcode and emit its bars (and human-readable text) as primitives (Phase 10d).
     *
     * <p>Rendered at its <b>natural size</b> from the element's X-dimension, anchored at the element's
     * top-left — not stretched to the bounds. The X-dimension is what decides whether a scanner can
     * read the symbol, so silently rescaling it to fill a box would quietly produce unscannable
     * labels. Fitting a barcode to a box is an explicit action ({@code BarcodeAutoSizer}).
     *
     * <p>A value that will not encode becomes a labelled placeholder rather than an exception: the
     * canvas repaints constantly while the user types, and a half-typed value must show as a problem,
     * never bring the renderer down.
     */
    private static void appendBarcode(List<RenderPrimitive> out, BarcodeElement element) {
        Bounds b = element.bounds();
        double rot = element.rotationDeg();
        BarcodeGeometry geometry;
        try {
            BarcodeRequest request = BarcodeRequest.from(element);
            geometry = element.symbology().isTwoDimensional()
                    ? Barcode2DEncoder.encode(request)
                    : Barcode1DEncoder.encode(request);
        } catch (BarcodeException e) {
            placeholder(out, b, rot, element.symbology() + "?");
            return;
        }

        // Each bar is rotated about the *element's* centre, not its own. A rectangle turned about an
        // outside point is the same rectangle turned about its own centre and moved so its centre
        // lands on the rotated position — which is exactly what RenderPrimitive.Rect can express.
        double centreX = b.centerXMm();
        double centreY = b.centerYMm();
        for (BarcodeGeometry.Bar bar : geometry.bars()) {
            double barCentreX = b.xMm() + bar.xMm() + bar.widthMm() / 2;
            double barCentreY = b.yMm() + bar.yMm() + bar.heightMm() / 2;
            double[] turned = rotateAbout(barCentreX, barCentreY, centreX, centreY, rot);
            out.add(new RenderPrimitive.Rect(
                    turned[0] - bar.widthMm() / 2,
                    turned[1] - bar.heightMm() / 2,
                    bar.widthMm(),
                    bar.heightMm(),
                    rot,
                    RenderColor.BLACK,
                    null,
                    0));
        }
        for (BarcodeGeometry.Caption caption : geometry.captions()) {
            appendCaption(out, caption, b, centreX, centreY, rot);
        }
    }

    /**
     * Place one human-readable caption. {@code RenderPrimitive.Text} has no alignment, so a centred
     * caption is anchored using an estimated text width. The estimate is crude, but both renderers use
     * the same one, so the design view and the printout still agree — which is the property that
     * matters here.
     */
    private static void appendCaption(
            List<RenderPrimitive> out,
            BarcodeGeometry.Caption caption,
            Bounds b,
            double centreX,
            double centreY,
            double rot) {
        double anchorX = b.xMm() + caption.xMm();
        if (caption.align() == BarcodeGeometry.CaptionAlign.CENTER) {
            double midX = b.xMm() + (caption.xMm() + caption.endXMm()) / 2;
            anchorX = midX - estimatedTextWidthMm(caption) / 2;
        } else if (caption.align() == BarcodeGeometry.CaptionAlign.RIGHT) {
            anchorX = b.xMm() + caption.endXMm() - estimatedTextWidthMm(caption);
        }
        double anchorY = b.yMm() + caption.baselineYMm();
        double[] turned = rotateAbout(anchorX, anchorY, centreX, centreY, rot);
        out.add(new RenderPrimitive.Text(
                turned[0], turned[1], caption.text(), caption.fontSizeMm(), RenderColor.BLACK, rot, false));
    }

    /** A rough average glyph width; enough to centre a short numeric caption believably. */
    private static double estimatedTextWidthMm(BarcodeGeometry.Caption caption) {
        return caption.text().length() * caption.fontSizeMm() * 0.6;
    }

    /** Rotate (x, y) clockwise about (cx, cy) by {@code deg}. */
    private static double[] rotateAbout(double x, double y, double cx, double cy, double deg) {
        if (deg == 0) {
            return new double[] {x, y};
        }
        double r = Math.toRadians(deg);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        double dx = x - cx;
        double dy = y - cy;
        return new double[] {cx + dx * cos - dy * sin, cy + dx * sin + dy * cos};
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
