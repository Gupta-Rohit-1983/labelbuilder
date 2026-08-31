package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.AutoFit;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.TextAlign;
import com.rohit.labelbuilder.model.style.VerticalAlign;
import java.util.Objects;

/**
 * A block of text (lbl-format.md §4.2). {@code value} is the raw template string — literal text
 * mixed with {@code ${field}} placeholders and expressions; it is stored verbatim here and resolved
 * by the variable/expression engine (Phase 12), not by the model.
 */
public record TextElement(
        ElementProperties properties,
        String value,
        FontSpec font,
        RgbaColor color,
        TextAlign align,
        VerticalAlign verticalAlign,
        double lineSpacing,
        boolean wrap,
        AutoFit autoFit,
        double characterSpacing)
        implements LabelElement {

    public TextElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(align, "align");
        Objects.requireNonNull(verticalAlign, "verticalAlign");
        Objects.requireNonNull(autoFit, "autoFit");
        if (!(lineSpacing > 0) || !Double.isFinite(lineSpacing)) {
            throw new IllegalArgumentException("lineSpacing must be positive finite, was " + lineSpacing);
        }
        if (!Double.isFinite(characterSpacing)) {
            throw new IllegalArgumentException("characterSpacing must be finite, was " + characterSpacing);
        }
    }

    /** A left/top-aligned, wrapping, black text block with sensible defaults. */
    public static TextElement of(ElementProperties properties, String value, FontSpec font) {
        return new TextElement(
                properties,
                value,
                font,
                RgbaColor.BLACK,
                TextAlign.LEFT,
                VerticalAlign.TOP,
                1.0,
                true,
                AutoFit.NONE,
                0.0);
    }

    public TextElement withValue(String newValue) {
        return new TextElement(
                properties, newValue, font, color, align, verticalAlign, lineSpacing, wrap, autoFit, characterSpacing);
    }

    public TextElement withFont(FontSpec newFont) {
        return new TextElement(
                properties, value, newFont, color, align, verticalAlign, lineSpacing, wrap, autoFit, characterSpacing);
    }

    @Override
    public TextElement withProperties(ElementProperties newProperties) {
        return new TextElement(
                newProperties, value, font, color, align, verticalAlign, lineSpacing, wrap, autoFit, characterSpacing);
    }
}
