package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns whatever a form, a prompt or an automation handed over into values a query can be bound to.
 *
 * <p>Everything that supplies a parameter supplies text: a prompt field, a command line, a watched
 * file's name, a REST call's query string. Binding that text straight into
 * {@code WHERE qty >= ?} is the bug this class exists to prevent — the comparison becomes a string
 * comparison, so {@code "10" < "5"} and the run quietly selects the wrong products. No exception, no
 * empty result, just the wrong labels. The declared {@link FieldType} is what decides the conversion.
 *
 * <p>A value that cannot be converted is an error, not a silent fallback. "The date 32/13/2026 is not
 * a date" has to reach the operator while they can retype it.
 */
public final class ParameterValues {

    private ParameterValues() {}

    /**
     * Resolves the values to bind, applying defaults and converting to each parameter's type.
     *
     * @param declared the parameters the query declares
     * @param supplied values by name; unknown names are ignored, since a shared prompt form may feed
     *     several queries
     * @throws DataException if a required parameter has no value, or a value cannot be converted
     */
    public static Map<String, Object> resolve(List<QueryParameter> declared, Map<String, Object> supplied) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (QueryParameter parameter : declared) {
            Object raw = supplied.get(parameter.name());
            if (isAbsent(raw)) {
                if (parameter.isRequired()) {
                    missing.add(parameter.name());
                    continue;
                }
                resolved.put(parameter.name(), parameter.defaultValue().orElse(null));
                continue;
            }
            resolved.put(parameter.name(), coerce(parameter.type(), raw, parameter.name()));
        }
        if (!missing.isEmpty()) {
            throw new DataException(
                    "no value for required parameter(s) " + missing + "; the query cannot run without them");
        }
        return resolved;
    }

    /**
     * Converts one value to a declared type.
     *
     * <p>A value already of the right shape is passed through untouched — only text is parsed, and
     * only where the type calls for it.
     */
    public static Object coerce(FieldType type, Object value, String parameterName) {
        if (value == null) {
            return null;
        }
        try {
            return switch (type) {
                case STRING -> value instanceof String text ? text : String.valueOf(value);
                case INTEGER -> value instanceof Number number ? number.longValue() : Long.valueOf(text(value));
                case DECIMAL ->
                    value instanceof BigDecimal decimal
                            ? decimal
                            // via toString, not doubleValue: 1.50 must stay 1.50
                            : new BigDecimal(value instanceof Number number ? number.toString() : text(value));
                case BOOLEAN -> value instanceof Boolean flag ? flag : parseBoolean(text(value), parameterName);
                case DATE -> value instanceof LocalDate date ? date : LocalDate.parse(text(value));
                case TIME -> value instanceof LocalTime time ? time : LocalTime.parse(text(value));
                case TIMESTAMP -> value instanceof LocalDateTime dateTime ? dateTime : LocalDateTime.parse(text(value));
                // Binary parameters are not something a form supplies; pass whatever came through.
                case BINARY -> value;
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new DataException("'" + value + "' is not a valid "
                    + type.name().toLowerCase(Locale.ROOT) + " for parameter '" + parameterName + "'");
        }
    }

    /**
     * Whether a supplied value counts as "nothing was given".
     *
     * <p>Blank text is absent, not an empty string: an untouched form field hands over {@code ""}, and
     * treating that as a real value would make an optional parameter's default unreachable. A
     * parameter that genuinely wants an empty string has to be given one as a non-text value or have
     * {@code ""} as its default.
     */
    private static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().isBlank();
        }
        return value instanceof Collection<?> collection && collection.isEmpty();
    }

    private static String text(Object value) {
        return String.valueOf(value).trim();
    }

    /**
     * Accepts {@code 1} and {@code 0} here, unlike {@code TypeInference}, and for the opposite reason:
     * there the type was being guessed from a column of data, where a 1 is far more often a quantity,
     * whereas here the parameter is <em>declared</em> boolean and somebody typed 1 meaning yes.
     */
    private static boolean parseBoolean(String text, String parameterName) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default ->
                throw new DataException("'" + text + "' is not a yes/no value for parameter '" + parameterName + "'");
        };
    }
}
