package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.LineCap;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.Objects;

/**
 * A straight line segment (lbl-format.md §4.6). Unlike other elements, a line reads its bounds'
 * opposite corners as the two endpoints: top-left → bottom-right. A zero-extent axis is therefore
 * legal (a perfectly horizontal or vertical line).
 */
public record LineElement(ElementProperties properties, Stroke stroke, LineCap lineCap) implements LabelElement {

    public LineElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(stroke, "stroke");
        Objects.requireNonNull(lineCap, "lineCap");
    }

    /** The start endpoint (bounds' top-left corner), in millimetres. */
    public double x1Mm() {
        return bounds().xMm();
    }

    public double y1Mm() {
        return bounds().yMm();
    }

    /** The end endpoint (bounds' bottom-right corner), in millimetres. */
    public double x2Mm() {
        return bounds().rightMm();
    }

    public double y2Mm() {
        return bounds().bottomMm();
    }

    /** A solid butt-capped line of the given stroke. */
    public static LineElement of(ElementProperties properties, Stroke stroke) {
        return new LineElement(properties, stroke, LineCap.BUTT);
    }

    @Override
    public LineElement withProperties(ElementProperties newProperties) {
        return new LineElement(newProperties, stroke, lineCap);
    }
}
