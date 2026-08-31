package com.rohit.labelbuilder.core.persistence;

import com.rohit.labelbuilder.model.document.GridSpec;
import com.rohit.labelbuilder.model.document.Guides;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Stroke;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.List;

/** Shared test data: a document exercising every element type, and its package. */
final class Fixtures {

    static final String LOGO = "assets/logo.png";

    private Fixtures() {}

    private static ElementProperties props(String id, double x, double y, double w, double h) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(x, y, w, h));
    }

    static LabelDocument richDocument() {
        TextElement text = TextElement.of(props("t1", 5, 5, 60, 8), "SKU: ${Product.Code}", FontSpec.of("Arial", 10))
                .withValue("Hello")
                .withFont(new FontSpec("Arial", 12, true, false, true));
        RectangleElement rectangle = new RectangleElement(
                props("r1", 2, 2, 30, 20), Stroke.solid(0.3), Fill.of(RgbaColor.rgb(240, 240, 240)), 1.5);
        EllipseElement ellipse = new EllipseElement(props("e1", 40, 2, 15, 15), Stroke.solid(0.2), Fill.none());
        LineElement line = LineElement.of(props("l1", 0, 30, 100, 0), Stroke.solid(0.25));
        ImageElement image = ImageElement.of(props("i1", 70, 5, 20, 20), LOGO);
        BarcodeElement barcode = new BarcodeElement(
                props("b1", 5, 25, 50, 15),
                Symbology.CODE_128,
                "${Product.Code}",
                0.25,
                12.0,
                2.5,
                CheckDigitMode.AUTO,
                Hri.below(FontSpec.of("Arial", 8)),
                0);
        TextElement grouped = TextElement.of(props("g-child", 10, 40, 20, 5), "grouped", FontSpec.of("Arial", 8));
        GroupElement group = new GroupElement(props("grp", 10, 40, 20, 5), List.of(grouped));

        return new LabelDocument(
                "doc-1",
                "Carton Label 100x50",
                Stock.of(100, 50),
                new GridSpec(true, 1.0, true),
                new Guides(List.of(25.0, 75.0), List.of(10.0)),
                List.of(new Layer("layer-1", "Content", true, false)),
                List.of(text, rectangle, ellipse, line, image, barcode, group));
    }

    static LabelPackage richPackage() {
        return new LabelPackage(
                richDocument(),
                java.util.Map.of(LOGO, new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4}),
                new byte[] {'t', 'h', 'u', 'm', 'b'});
    }
}
