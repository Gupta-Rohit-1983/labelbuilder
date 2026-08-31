package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.Objects;

/** A rectangle, optionally with rounded corners (lbl-format.md §4.6). */
public record RectangleElement(ElementProperties properties, Stroke stroke, Fill fill, double cornerRadiusMm)
        implements LabelElement {

    public RectangleElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(stroke, "stroke");
        Objects.requireNonNull(fill, "fill");
        if (cornerRadiusMm < 0 || !Double.isFinite(cornerRadiusMm)) {
            throw new IllegalArgumentException("cornerRadiusMm must be finite and non-negative, was " + cornerRadiusMm);
        }
    }

    /** A sharp-cornered rectangle with the given stroke and fill. */
    public static RectangleElement of(ElementProperties properties, Stroke stroke, Fill fill) {
        return new RectangleElement(properties, stroke, fill, 0.0);
    }

    public RectangleElement withStroke(Stroke newStroke) {
        return new RectangleElement(properties, newStroke, fill, cornerRadiusMm);
    }

    public RectangleElement withFill(Fill newFill) {
        return new RectangleElement(properties, stroke, newFill, cornerRadiusMm);
    }

    @Override
    public RectangleElement withProperties(ElementProperties newProperties) {
        return new RectangleElement(newProperties, stroke, fill, cornerRadiusMm);
    }
}
