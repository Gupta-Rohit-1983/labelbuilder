package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.Objects;

/**
 * An ellipse inscribed in its bounds (lbl-format.md §4.6). A circle is the special case of equal
 * width and height — the roadmap's "circle" element is this general form.
 */
public record EllipseElement(ElementProperties properties, Stroke stroke, Fill fill) implements LabelElement {

    public EllipseElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(stroke, "stroke");
        Objects.requireNonNull(fill, "fill");
    }

    public EllipseElement withStroke(Stroke newStroke) {
        return new EllipseElement(properties, newStroke, fill);
    }

    public EllipseElement withFill(Fill newFill) {
        return new EllipseElement(properties, stroke, newFill);
    }

    @Override
    public EllipseElement withProperties(ElementProperties newProperties) {
        return new EllipseElement(newProperties, stroke, fill);
    }
}
