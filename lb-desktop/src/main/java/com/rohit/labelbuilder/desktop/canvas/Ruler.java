package com.rohit.labelbuilder.desktop.canvas;

import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A millimetre ruler along one edge of the canvas (Phase 6b). Reads the {@link DesignCanvas}'s
 * viewport so its ticks stay aligned with the surface as the user zooms and pans, marks the
 * current pointer position, and creates an alignment guide when clicked (a horizontal ruler makes
 * vertical guides and vice-versa — the ruler you pull from is perpendicular to the guide).
 *
 * <p>Composed by {@link CanvasView} in a grid so the top ruler shares the canvas's x-origin and
 * the left ruler shares its y-origin.
 */
public class Ruler extends javafx.scene.layout.Region {

    static final double THICKNESS = 22;
    private static final double TARGET_LABEL_PX = 64; // aim for a numbered tick about this far apart
    private static final Font LABEL_FONT = Font.font(9);

    private final Canvas canvas = new Canvas();
    private final DesignCanvas design;
    private final Orientation axis;

    public Ruler(DesignCanvas design, Orientation axis) {
        this.design = design;
        this.axis = axis;
        this.canvas.setManaged(false);
        getChildren().add(canvas);
        getStyleClass().add("ruler");
        if (axis == Orientation.HORIZONTAL) {
            setPrefHeight(THICKNESS);
            setMinHeight(THICKNESS);
            setMaxHeight(THICKNESS);
        } else {
            setPrefWidth(THICKNESS);
            setMinWidth(THICKNESS);
            setMaxWidth(THICKNESS);
        }

        design.viewportProperty().addListener((o, a, b) -> draw());
        design.pointerMmProperty().addListener((o, a, b) -> draw());
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());

        setOnMouseClicked(e -> {
            CanvasViewport vp = design.viewportProperty().get();
            if (axis == Orientation.HORIZONTAL) {
                design.addGuide(Orientation.VERTICAL, vp.toModelX(e.getX()));
            } else {
                design.addGuide(Orientation.HORIZONTAL, vp.toModelY(e.getY()));
            }
        });
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#f0f0f0"));
        g.fillRect(0, 0, w, h);
        g.setStroke(Color.web("#c8c8c8"));
        g.setLineWidth(1);
        if (axis == Orientation.HORIZONTAL) {
            g.strokeLine(0, h - 0.5, w, h - 0.5);
        } else {
            g.strokeLine(w - 0.5, 0, w - 0.5, h);
        }

        CanvasViewport vp = design.viewportProperty().get();
        double scale = vp.scale();
        if (scale <= 0) {
            return;
        }
        double step = niceStep(scale);
        double lengthPx = axis == Orientation.HORIZONTAL ? w : h;
        double startMm = axis == Orientation.HORIZONTAL ? vp.toModelX(0) : vp.toModelY(0);
        double endMm = axis == Orientation.HORIZONTAL ? vp.toModelX(lengthPx) : vp.toModelY(lengthPx);

        g.setFill(Color.web("#606060"));
        g.setStroke(Color.web("#a0a0a0"));
        g.setFont(LABEL_FONT);
        g.setTextAlign(TextAlignment.LEFT);
        double first = Math.ceil(startMm / step) * step;
        for (double mm = first; mm <= endMm; mm += step) {
            double px = axis == Orientation.HORIZONTAL ? vp.toDeviceX(mm) : vp.toDeviceY(mm);
            String label = formatMm(mm, step);
            if (axis == Orientation.HORIZONTAL) {
                g.strokeLine(px + 0.5, h - 8, px + 0.5, h);
                g.fillText(label, px + 2, 9);
            } else {
                g.strokeLine(w - 8, px + 0.5, w, px + 0.5);
                g.fillText(label, 1, px + 9);
            }
        }

        drawPointerMarker(g, vp, w, h);
    }

    private void drawPointerMarker(GraphicsContext g, CanvasViewport vp, double w, double h) {
        javafx.geometry.Point2D pointer = design.pointerMmProperty().get();
        if (pointer == null) {
            return;
        }
        g.setStroke(Color.web("#12b5cb"));
        g.setLineWidth(1);
        if (axis == Orientation.HORIZONTAL) {
            double px = vp.toDeviceX(pointer.getX()) + 0.5;
            g.strokeLine(px, 0, px, h);
        } else {
            double py = vp.toDeviceY(pointer.getY()) + 0.5;
            g.strokeLine(0, py, w, py);
        }
    }

    /** A 1–2–5 "nice" tick step (mm) giving numbered ticks roughly {@code TARGET_LABEL_PX} apart. */
    static double niceStep(double scale) {
        double rawMm = TARGET_LABEL_PX / scale;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawMm)));
        double normalised = rawMm / magnitude;
        double nice = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private static String formatMm(double mm, double step) {
        double rounded = Math.abs(mm) < 1e-6 ? 0 : mm; // avoid "-0"
        return step < 1 ? String.format("%.1f", rounded) : String.valueOf(Math.round(rounded));
    }
}
