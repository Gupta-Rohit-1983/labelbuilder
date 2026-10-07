package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.RgbaColor;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Converts property values between the model and the text an editor shows (Phase 9a). Pure, so the
 * formatting and parsing rules — the part most likely to be subtly wrong — are unit-tested without an
 * FX toolkit.
 *
 * <p>Numbers are printed without trailing zeros ({@code 10} rather than {@code 10.0}), because a
 * millimetre field reading "10.0" invites a stray keystroke. Parsing enforces the descriptor's
 * advisory range, so the inspector rejects a bad value instead of letting the model constructor throw
 * from somewhere less helpful.
 */
public final class PropertyValues {

    private PropertyValues() {}

    /** The text an editor displays for a model value. */
    public static String format(Object value) {
        return switch (value) {
            case null -> "";
            case RgbaColor color -> color.toHex();
            case FontSpec font -> describeFont(font);
            case Double d -> number(d);
            case Float f -> number(f.doubleValue());
            case Enum<?> e -> e.name();
            default -> String.valueOf(value);
        };
    }

    /**
     * Parse edited text back into a model value for {@code descriptor}.
     *
     * @throws IllegalArgumentException if the text is not valid for the property's kind or range
     */
    public static Object parse(PropertyDescriptor descriptor, String text) {
        String trimmed = text == null ? "" : text.trim();
        return switch (descriptor.kind()) {
            case TEXT -> trimmed;
            case BOOLEAN -> parseBoolean(trimmed);
            case INTEGER -> (int) requireInRange(descriptor, parseLong(trimmed));
            case DECIMAL -> requireInRange(descriptor, parseDouble(trimmed));
            case COLOR -> RgbaColor.parse(trimmed); // already throws a clear message
            case ENUM -> parseEnum(descriptor, trimmed);
            case FONT ->
                throw new UnsupportedOperationException(
                        "fonts are edited with the font picker, not as text (Phase 9b)");
        };
    }

    /** A font rendered for display: family, size and any style flags. */
    public static String describeFont(FontSpec font) {
        StringBuilder text = new StringBuilder(font.family());
        text.append(", ").append(number(font.sizePt())).append("pt");
        if (font.bold()) {
            text.append(" Bold");
        }
        if (font.italic()) {
            text.append(" Italic");
        }
        if (font.underline()) {
            text.append(" Underline");
        }
        return text.toString();
    }

    /** {@code 10} not {@code 10.0}; {@code 0.25} stays {@code 0.25}. */
    private static String number(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean parseBoolean(String text) {
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes")) {
            return true;
        }
        if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false, was: " + text);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a whole number, was: " + text, e);
        }
    }

    private static double parseDouble(String text) {
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number, was: " + text, e);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("expected a finite number, was: " + text);
        }
        return value;
    }

    private static double requireInRange(PropertyDescriptor descriptor, double value) {
        double min = descriptor.min().orElse(Double.NEGATIVE_INFINITY);
        double max = descriptor.max().orElse(Double.POSITIVE_INFINITY);
        if (value < min) {
            throw new IllegalArgumentException(descriptor.displayName() + " cannot be less than " + number(min));
        }
        if (value > max) {
            throw new IllegalArgumentException(descriptor.displayName() + " cannot be more than " + number(max));
        }
        return value;
    }

    private static Object parseEnum(PropertyDescriptor descriptor, String text) {
        for (Object constant : descriptor.enumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not one of "
                + descriptor.enumConstants().stream()
                        .map(c -> ((Enum<?>) c).name().toLowerCase(Locale.ROOT))
                        .toList());
    }
}
