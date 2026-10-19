package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.barcode.Barcode1DEncoder;
import com.rohit.labelbuilder.barcode.Barcode2DEncoder;
import com.rohit.labelbuilder.barcode.BarcodeException;
import com.rohit.labelbuilder.barcode.BarcodeGeometry;
import com.rohit.labelbuilder.barcode.BarcodeRequest;
import com.rohit.labelbuilder.barcode.BarcodeValidator;
import com.rohit.labelbuilder.barcode.ValidationReport;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * A live preview of the selected barcode, with whatever the validator has to say about it
 * (Phase 10d).
 *
 * <p>Barcodes are the one element type where what you typed and what will actually scan can diverge
 * invisibly — a value the symbology rejects, bars too narrow for a scanner, a quiet zone squeezed by
 * a neighbour. Showing the encoded symbol and its warnings beside the fields closes that gap while
 * the value is still being edited, rather than at the printer.
 *
 * <p>This is deliberately the inspector's one type-specific component: a preview is inherently about
 * what the element <em>is</em>, unlike property editing, which stays driven by the metadata.
 */
final class BarcodePreview {

    private static final double HEIGHT_PX = 90;
    private static final double PADDING_PX = 6;

    private BarcodePreview() {}

    /** A preview block for one barcode element. Never throws — an unencodable value is shown as such. */
    static javafx.scene.Node create(BarcodeElement element) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(4, 0, 8, 0));
        box.getStyleClass().add("barcode-preview");

        BarcodeRequest request = BarcodeRequest.from(element);
        try {
            BarcodeGeometry geometry = element.symbology().isTwoDimensional()
                    ? Barcode2DEncoder.encode(request)
                    : Barcode1DEncoder.encode(request);
            box.getChildren().add(render(geometry));
        } catch (BarcodeException e) {
            box.getChildren().add(message(e.getMessage(), true));
        }

        ValidationReport report = BarcodeValidator.validate(request);
        report.errors().forEach(error -> box.getChildren().add(message(error, true)));
        report.warnings().forEach(warning -> box.getChildren().add(message(warning, false)));
        return box;
    }

    /** Draw the encoded geometry, scaled to fit the preview strip. */
    private static Canvas render(BarcodeGeometry geometry) {
        double scale = Math.min(
                (260 - 2 * PADDING_PX) / Math.max(geometry.widthMm(), 0.001),
                (HEIGHT_PX - 2 * PADDING_PX) / Math.max(geometry.heightMm(), 0.001));
        Canvas canvas =
                new Canvas(geometry.widthMm() * scale + 2 * PADDING_PX, geometry.heightMm() * scale + 2 * PADDING_PX);

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setFill(Color.BLACK);
        for (BarcodeGeometry.Bar bar : geometry.bars()) {
            g.fillRect(
                    PADDING_PX + bar.xMm() * scale,
                    PADDING_PX + bar.yMm() * scale,
                    // Never let a bar round away to nothing: a missing bar reads as a different code.
                    Math.max(bar.widthMm() * scale, 1),
                    Math.max(bar.heightMm() * scale, 1));
        }
        return canvas;
    }

    private static Label message(String text, boolean error) {
        Label label = new Label((error ? "✖  " : "⚠  ") + text);
        label.setWrapText(true);
        label.getStyleClass().add(error ? "barcode-error" : "barcode-warning");
        label.setStyle(error ? "-fx-text-fill: #c5221f;" : "-fx-text-fill: #b06000;");
        return label;
    }
}
