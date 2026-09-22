package com.rohit.labelbuilder.core.edit;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.Stroke;
import com.rohit.labelbuilder.model.style.Symbology;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates new elements with sensible defaults for the creation tools (Phase 8a). Pure and headless —
 * the id is supplied by the caller so the factory stays deterministic and testable; {@link #newId()}
 * offers a convenient unique id for interactive use.
 */
public final class ElementFactory {

    /** Default placeholder asset for a newly placed image, until the user picks a file. */
    public static final String PLACEHOLDER_ASSET = "assets/placeholder.png";

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private ElementFactory() {}

    /** A process-unique element id. */
    public static String newId() {
        return "el-" + Long.toHexString(System.currentTimeMillis()) + "-" + SEQUENCE.incrementAndGet();
    }

    /** A new element of {@code kind} occupying {@code bounds} on {@code layerId}. */
    public static LabelElement create(ElementKind kind, String id, String layerId, Bounds bounds) {
        ElementProperties props = ElementProperties.of(id, kind.displayName(), layerId, bounds);
        return switch (kind) {
            case TEXT -> TextElement.of(props, "Text", FontSpec.of("Arial", 10));
            case RECTANGLE -> RectangleElement.of(props, Stroke.solid(0.3), Fill.none());
            case ELLIPSE -> new EllipseElement(props, Stroke.solid(0.3), Fill.none());
            case LINE -> LineElement.of(props, Stroke.solid(0.3));
            case IMAGE -> ImageElement.of(props, PLACEHOLDER_ASSET);
            case BARCODE ->
                new BarcodeElement(
                        props,
                        Symbology.CODE_128,
                        "12345678",
                        0.25,
                        Math.max(1, bounds.heightMm()),
                        2.5,
                        CheckDigitMode.AUTO,
                        Hri.below(FontSpec.of("Arial", 8)),
                        0);
        };
    }

    /** A new element at {@code (xMm, yMm)} using the kind's default size — the click-to-place path. */
    public static LabelElement createDefault(ElementKind kind, String id, String layerId, double xMm, double yMm) {
        Bounds bounds = new Bounds(xMm, yMm, kind.defaultWidthMm(), kind.defaultHeightMm());
        return create(kind, id, layerId, bounds);
    }
}
