package com.rohit.labelbuilder.render;

import com.rohit.labelbuilder.render.scene.RenderColor;
import com.rohit.labelbuilder.render.scene.RenderPrimitive;
import com.rohit.labelbuilder.render.scene.RenderScene;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * The reference renderer (architecture §2, risk R-03): rasterises a {@link RenderScene} to a
 * {@link BufferedImage} with Java2D. This is the single source of visual truth — the on-screen
 * JavaFX canvas and the thermal/PDF output are all measured against it, and its output is locked
 * by the PNG-diff regression suite.
 *
 * <p>Headless and framework-free (no JavaFX, no Spring — enforced by ArchUnit) so lb-server can
 * render server-side. Java2D's software pipeline is deterministic across platforms for vector
 * shapes, which is what makes pixel baselines viable; text is font-dependent and excluded from
 * baselines.
 *
 * <p>All drawing happens in a millimetre coordinate space (a uniform {@code dpi/25.4} scale), so
 * primitive coordinates and stroke widths are specified directly in mm.
 */
public final class Java2DRenderer {

    private static final double MM_PER_INCH = 25.4;

    /**
     * @param scene the label to draw
     * @param dpi output resolution in dots per inch (e.g. 96 for screen preview, 203/300 for
     *     thermal printers)
     * @return an ARGB image {@code round(widthMm/25.4*dpi)} by {@code round(heightMm/25.4*dpi)}
     */
    public BufferedImage render(RenderScene scene, double dpi) {
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi must be positive: " + dpi);
        }
        double pxPerMm = dpi / MM_PER_INCH;
        int widthPx = Math.max(1, (int) Math.round(scene.widthMm() * pxPerMm));
        int heightPx = Math.max(1, (int) Math.round(scene.heightMm() * pxPerMm));

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(awt(scene.background()));
            g.fillRect(0, 0, widthPx, heightPx);

            g.transform(AffineTransform.getScaleInstance(pxPerMm, pxPerMm)); // work in mm
            for (RenderPrimitive primitive : scene.primitives()) {
                draw(g, primitive);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private void draw(Graphics2D g, RenderPrimitive primitive) {
        switch (primitive) {
            case RenderPrimitive.Rect r ->
                fillAndStroke(
                        g,
                        new Rectangle2D.Double(r.xMm(), r.yMm(), r.wMm(), r.hMm()),
                        r.rotationDeg(),
                        r.xMm() + r.wMm() / 2,
                        r.yMm() + r.hMm() / 2,
                        r.fill(),
                        r.stroke(),
                        r.strokeWidthMm());
            case RenderPrimitive.Ellipse e ->
                fillAndStroke(
                        g,
                        new Ellipse2D.Double(e.xMm(), e.yMm(), e.wMm(), e.hMm()),
                        e.rotationDeg(),
                        e.xMm() + e.wMm() / 2,
                        e.yMm() + e.hMm() / 2,
                        e.fill(),
                        e.stroke(),
                        e.strokeWidthMm());
            case RenderPrimitive.Line line -> {
                if (line.stroke() != null) {
                    g.setColor(awt(line.stroke()));
                    g.setStroke(new BasicStroke((float) line.strokeWidthMm()));
                    g.draw(new Line2D.Double(line.x1Mm(), line.y1Mm(), line.x2Mm(), line.y2Mm()));
                }
            }
            case RenderPrimitive.Text t -> drawText(g, t);
        }
    }

    private void fillAndStroke(
            Graphics2D g,
            java.awt.Shape shape,
            double rotationDeg,
            double cx,
            double cy,
            RenderColor fill,
            RenderColor stroke,
            double strokeWidthMm) {
        AffineTransform saved = g.getTransform();
        if (rotationDeg != 0) {
            g.rotate(Math.toRadians(rotationDeg), cx, cy);
        }
        if (fill != null) {
            g.setColor(awt(fill));
            g.fill(shape);
        }
        if (stroke != null && strokeWidthMm > 0) {
            g.setColor(awt(stroke));
            g.setStroke(new BasicStroke((float) strokeWidthMm));
            g.draw(shape);
        }
        g.setTransform(saved);
    }

    private void drawText(Graphics2D g, RenderPrimitive.Text t) {
        if (t.color() == null || t.text() == null || t.text().isEmpty()) {
            return;
        }
        AffineTransform saved = g.getTransform();
        if (t.rotationDeg() != 0) {
            g.rotate(Math.toRadians(t.rotationDeg()), t.xMm(), t.yMm());
        }
        g.setColor(awt(t.color()));
        // Font sizes are in points internally; here the mm space is already scaled, so a
        // font "size" in mm draws text `fontSizeMm` tall. Logical family keeps it portable.
        int style = t.bold() ? Font.BOLD : Font.PLAIN;
        g.setFont(new Font(Font.SANS_SERIF, style, 100).deriveFont((float) t.fontSizeMm()));
        g.drawString(t.text(), (float) t.xMm(), (float) t.yMm());
        g.setTransform(saved);
    }

    private static Color awt(RenderColor c) {
        return new Color(c.r(), c.g(), c.b(), c.a());
    }
}
