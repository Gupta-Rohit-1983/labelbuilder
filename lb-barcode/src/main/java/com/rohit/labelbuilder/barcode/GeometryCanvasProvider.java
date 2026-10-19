package com.rohit.labelbuilder.barcode;

import java.util.ArrayList;
import java.util.List;
import org.krysalis.barcode4j.BarcodeDimension;
import org.krysalis.barcode4j.TextAlignment;
import org.krysalis.barcode4j.output.AbstractCanvasProvider;

/**
 * Captures Barcode4J's drawing calls as millimetre geometry instead of painting them.
 *
 * <p>Barcode4J ships canvas providers that write SVG, EPS or a bitmap; none of those suit us, because
 * we need the bars as data to feed the renderer seam and, later, the thermal drivers. Its {@code
 * CanvasProvider} abstraction is the supported way in: the library hands us every rectangle it would
 * have drawn, already in millimetres, and we keep them.
 */
final class GeometryCanvasProvider extends AbstractCanvasProvider {

    /** 0 = no rotation; the element's own rotation is applied by the renderer, not the encoder. */
    private static final int UPRIGHT = 0;

    private final List<BarcodeGeometry.Bar> bars = new ArrayList<>();
    private final List<BarcodeGeometry.Caption> captions = new ArrayList<>();

    GeometryCanvasProvider() {
        super(UPRIGHT);
    }

    @Override
    public void deviceFillRect(double x, double y, double w, double h) {
        bars.add(new BarcodeGeometry.Bar(x, y, w, h));
    }

    @Override
    public void deviceText(
            String text, double x1, double x2, double y1, String fontName, double fontSize, TextAlignment textAlign) {
        captions.add(new BarcodeGeometry.Caption(text, x1, x2, y1, fontSize, align(textAlign)));
    }

    /** The captured result. Valid only after the bean has generated the barcode. */
    BarcodeGeometry geometry() {
        BarcodeDimension dimensions = getDimensions();
        if (dimensions == null) {
            throw new BarcodeException("the encoder produced no dimensions");
        }
        // "PlusQuiet" includes the quiet zones, which are part of the barcode's footprint: leaving
        // them out would let neighbouring artwork crowd the code and make it unscannable.
        return new BarcodeGeometry(dimensions.getWidthPlusQuiet(), dimensions.getHeightPlusQuiet(), bars, captions);
    }

    private static BarcodeGeometry.CaptionAlign align(TextAlignment alignment) {
        if (TextAlignment.TA_LEFT.equals(alignment)) {
            return BarcodeGeometry.CaptionAlign.LEFT;
        }
        if (TextAlignment.TA_RIGHT.equals(alignment)) {
            return BarcodeGeometry.CaptionAlign.RIGHT;
        }
        if (TextAlignment.TA_JUSTIFY.equals(alignment)) {
            return BarcodeGeometry.CaptionAlign.JUSTIFY;
        }
        return BarcodeGeometry.CaptionAlign.CENTER;
    }
}
