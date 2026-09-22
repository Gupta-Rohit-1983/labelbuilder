package com.rohit.labelbuilder.desktop.canvas;

import com.rohit.labelbuilder.render.scene.RenderColor;
import com.rohit.labelbuilder.render.scene.RenderPrimitive;
import com.rohit.labelbuilder.render.scene.RenderScene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Paints a {@link RenderScene} onto the JavaFX canvas through a {@link CanvasViewport}. The scene is
 * produced by {@code SceneMapper} from the document, which is the <em>same</em> input the Java2D
 * reference renderer consumes — so what the designer shows and what gets printed come from one
 * description rather than two hand-kept-in-sync drawing routines (risk R-03).
 *
 * <p>Primitives outside the visible model region are skipped (Phase 6e culling). Strokes are floored
 * at one device pixel so hairlines stay visible when zoomed out; that is a deliberate on-screen
 * affordance, not a geometry change.
 */
final class ScenePainter {

    private static final double MIN_STROKE_PX = 1.0;

    private ScenePainter() {}

    static void paint(GraphicsContext g, CanvasViewport vp, RenderScene scene, BoundsMm visible) {
        for (RenderPrimitive primitive : scene.primitives()) {
            if (!visible.intersects(boundsOf(primitive))) {
                continue;
            }
            switch (primitive) {
                case RenderPrimitive.Rect r -> paintRect(g, vp, r);
                case RenderPrimitive.Ellipse e -> paintEllipse(g, vp, e);
                case RenderPrimitive.Line l -> paintLine(g, vp, l);
                case RenderPrimitive.Text t -> paintText(g, vp, t);
            }
        }
    }

    private static void paintRect(GraphicsContext g, CanvasViewport vp, RenderPrimitive.Rect r) {
        double scale = vp.scale();
        double dw = r.wMm() * scale;
        double dh = r.hMm() * scale;
        g.save();
        g.translate(vp.toDeviceX(r.xMm() + r.wMm() / 2), vp.toDeviceY(r.yMm() + r.hMm() / 2));
        g.rotate(r.rotationDeg());
        if (r.fill() != null) {
            g.setFill(color(r.fill()));
            g.fillRect(-dw / 2, -dh / 2, dw, dh);
        }
        if (r.stroke() != null) {
            g.setStroke(color(r.stroke()));
            g.setLineWidth(strokePx(r.strokeWidthMm(), scale));
            g.strokeRect(-dw / 2, -dh / 2, dw, dh);
        }
        g.restore();
    }

    private static void paintEllipse(GraphicsContext g, CanvasViewport vp, RenderPrimitive.Ellipse e) {
        double scale = vp.scale();
        double dw = e.wMm() * scale;
        double dh = e.hMm() * scale;
        g.save();
        g.translate(vp.toDeviceX(e.xMm() + e.wMm() / 2), vp.toDeviceY(e.yMm() + e.hMm() / 2));
        g.rotate(e.rotationDeg());
        if (e.fill() != null) {
            g.setFill(color(e.fill()));
            g.fillOval(-dw / 2, -dh / 2, dw, dh);
        }
        if (e.stroke() != null) {
            g.setStroke(color(e.stroke()));
            g.setLineWidth(strokePx(e.strokeWidthMm(), scale));
            g.strokeOval(-dw / 2, -dh / 2, dw, dh);
        }
        g.restore();
    }

    private static void paintLine(GraphicsContext g, CanvasViewport vp, RenderPrimitive.Line l) {
        if (l.stroke() == null) {
            return;
        }
        g.setStroke(color(l.stroke()));
        g.setLineWidth(strokePx(l.strokeWidthMm(), vp.scale()));
        g.strokeLine(
                vp.toDeviceX(l.x1Mm()), vp.toDeviceY(l.y1Mm()),
                vp.toDeviceX(l.x2Mm()), vp.toDeviceY(l.y2Mm()));
    }

    private static void paintText(GraphicsContext g, CanvasViewport vp, RenderPrimitive.Text t) {
        double sizePx = t.fontSizeMm() * vp.scale();
        if (sizePx < 3) {
            return; // unreadable at this zoom; the box outline already conveys the element
        }
        g.save();
        g.translate(vp.toDeviceX(t.xMm()), vp.toDeviceY(t.yMm()));
        g.rotate(t.rotationDeg());
        g.setFill(color(t.color()));
        g.setFont(t.bold() ? Font.font("System", FontWeight.BOLD, sizePx) : Font.font("System", sizePx));
        g.fillText(t.text(), 0, 0);
        g.restore();
    }

    /** Model-space box used for culling; rotation is ignored (a conservative over-estimate). */
    private static BoundsMm boundsOf(RenderPrimitive primitive) {
        return switch (primitive) {
            case RenderPrimitive.Rect r -> new BoundsMm(r.xMm(), r.yMm(), r.wMm(), r.hMm());
            case RenderPrimitive.Ellipse e -> new BoundsMm(e.xMm(), e.yMm(), e.wMm(), e.hMm());
            case RenderPrimitive.Line l ->
                new BoundsMm(
                        Math.min(l.x1Mm(), l.x2Mm()),
                        Math.min(l.y1Mm(), l.y2Mm()),
                        Math.abs(l.x2Mm() - l.x1Mm()),
                        Math.abs(l.y2Mm() - l.y1Mm()));
            // Text is baseline-anchored: allow a generous box above and below the baseline.
            case RenderPrimitive.Text t ->
                new BoundsMm(
                        t.xMm(),
                        t.yMm() - t.fontSizeMm(),
                        Math.max(1, t.text().length() * t.fontSizeMm()),
                        t.fontSizeMm() * 1.5);
        };
    }

    private static double strokePx(double widthMm, double scale) {
        return Math.max(widthMm * scale, MIN_STROKE_PX);
    }

    private static Color color(RenderColor c) {
        return Color.rgb(c.r(), c.g(), c.b(), c.a() / 255.0);
    }
}
