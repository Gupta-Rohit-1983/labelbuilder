package com.rohit.labelbuilder.desktop.canvas;

import javafx.geometry.Orientation;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;

/**
 * Frames a {@link DesignCanvas} with millimetre rulers (Phase 6b): a corner box, a horizontal
 * ruler across the top and a vertical ruler down the left, with the canvas filling the rest.
 *
 * <p>Uses a 2×2 grid — fixed ruler track, then a growing track — so the top ruler shares the
 * canvas's x-origin and the left ruler shares its y-origin, keeping ticks aligned with the
 * surface. This is a pure view wrapper; zoom commands and status wiring still target the inner
 * {@link DesignCanvas} directly.
 */
public class CanvasView extends GridPane {

    private final DesignCanvas designCanvas;

    public CanvasView(DesignCanvas designCanvas) {
        this.designCanvas = designCanvas;
        getStyleClass().add("canvas-view");

        Region corner = new Region();
        corner.getStyleClass().add("ruler-corner");
        Ruler topRuler = new Ruler(designCanvas, Orientation.HORIZONTAL);
        Ruler leftRuler = new Ruler(designCanvas, Orientation.VERTICAL);

        ColumnConstraints fixedCol = new ColumnConstraints(Ruler.THICKNESS);
        ColumnConstraints growCol = new ColumnConstraints();
        growCol.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(fixedCol, growCol);

        RowConstraints fixedRow = new RowConstraints(Ruler.THICKNESS);
        RowConstraints growRow = new RowConstraints();
        growRow.setVgrow(Priority.ALWAYS);
        getRowConstraints().addAll(fixedRow, growRow);

        add(corner, 0, 0);
        add(topRuler, 1, 0);
        add(leftRuler, 0, 1);
        add(designCanvas, 1, 1);
    }

    public DesignCanvas designCanvas() {
        return designCanvas;
    }
}
