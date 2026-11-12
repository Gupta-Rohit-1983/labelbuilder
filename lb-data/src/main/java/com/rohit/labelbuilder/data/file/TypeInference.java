package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.FieldType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Guesses a column's {@link FieldType} from sample values (FR-DS-03).
 *
 * <p>A file declares no schema, so the type has to be read off the data. The inferred type is
 * <b>metadata</b> — it drives field binding, sorting and query building. It is deliberately <em>not</em>
 * a conversion instruction: file sources carry each value as the text the file held, because a label
 * must print what the operator saw in the spreadsheet. Parsing {@code 1.50} to a double and printing
 * it back as {@code 1.5} would quietly change a price.
 *
 * <p>The same reasoning drives the two rules that look odd in isolation:
 *
 * <ul>
 *   <li><b>A leading zero keeps a column textual.</b> {@code 0012} is an article number, not the
 *       integer twelve. Calling it INTEGER invites the first consumer that round-trips it to drop the
 *       zeros, and a barcode printed from a truncated SKU scans to the wrong product.
 *   <li><b>{@code 1} and {@code 0} are not booleans.</b> They are far more often a quantity, and a
 *       column of quantities typed BOOLEAN would offer nonsense comparisons in the query builder.
 *       {@code true/false/yes/no/y/n} are unambiguous and are accepted.
 * </ul>
 */
public final class TypeInference {

    /** How many rows a file source reads to guess its schema. */
    public static final int DEFAULT_SAMPLE_ROWS = 50;

    /** Beyond this a digit string cannot be a {@code long}, so it stays text. */
    private static final int MAX_INTEGER_DIGITS = 18;

    private static final Set<String> BOOLEAN_WORDS = Set.of("true", "false", "yes", "no", "y", "n");

    private TypeInference() {}

    /**
     * The type that fits every non-blank sample, or STRING when none does.
     *
     * <p>Blank samples are ignored rather than forcing STRING: an empty cell means "no value here",
     * not "this column is text". A column with no values at all is STRING, the type that can hold
     * anything.
     */
    public static FieldType inferType(List<String> samples) {
        List<String> values = samples.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
        if (values.isEmpty()) {
            return FieldType.STRING;
        }
        // Order matters: integers also parse as decimals, and dates must not be reached by a column of
        // plain numbers.
        if (values.stream().allMatch(TypeInference::isBooleanWord)) {
            return FieldType.BOOLEAN;
        }
        if (values.stream().allMatch(TypeInference::isInteger)) {
            return FieldType.INTEGER;
        }
        if (values.stream().allMatch(TypeInference::isDecimal)) {
            return FieldType.DECIMAL;
        }
        for (FieldType temporal : List.of(FieldType.DATE, FieldType.TIME, FieldType.TIMESTAMP)) {
            if (values.stream()
                    .allMatch(v -> temporalType(v).filter(temporal::equals).isPresent())) {
                return temporal;
            }
        }
        return FieldType.STRING;
    }

    /**
     * The temporal type an ISO-8601 value denotes, if it is one.
     *
     * <p>Only ISO forms are recognised. A locale-formatted date such as {@code 26/10/2026} is
     * ambiguous — the same text is October 26th in Europe and nothing at all in the United States —
     * so it stays text rather than being silently misread.
     */
    public static Optional<FieldType> temporalType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = value.trim();
        if (parses(text, LocalDate::parse)) {
            return Optional.of(FieldType.DATE);
        }
        if (parses(text, LocalTime::parse)) {
            return Optional.of(FieldType.TIME);
        }
        if (parses(text, LocalDateTime::parse) || parses(text, OffsetDateTime::parse) || parses(text, Instant::parse)) {
            return Optional.of(FieldType.TIMESTAMP);
        }
        return Optional.empty();
    }

    /**
     * The single type that covers a set of per-value types, for sources that know each value's type —
     * JSON tokens, Excel cells — rather than having to guess from text.
     *
     * <p>Widening beats falling back where there is a wider type: a column holding {@code 1} and
     * {@code 2.5} is DECIMAL, and a column holding a date and a timestamp is TIMESTAMP. Anything else
     * mixed is STRING, which is the only type that can hold all of it.
     */
    public static FieldType reconcile(Collection<FieldType> types) {
        EnumSet<FieldType> distinct = EnumSet.noneOf(FieldType.class);
        for (FieldType type : types) {
            if (type != null) {
                distinct.add(type);
            }
        }
        if (distinct.isEmpty()) {
            return FieldType.STRING;
        }
        if (distinct.size() == 1) {
            return distinct.iterator().next();
        }
        if (EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL).containsAll(distinct)) {
            return FieldType.DECIMAL;
        }
        if (EnumSet.of(FieldType.DATE, FieldType.TIME, FieldType.TIMESTAMP).containsAll(distinct)) {
            return FieldType.TIMESTAMP;
        }
        return FieldType.STRING;
    }

    private static boolean isBooleanWord(String value) {
        return BOOLEAN_WORDS.contains(value.toLowerCase());
    }

    private static boolean isInteger(String value) {
        String digits = unsigned(value);
        if (digits.isEmpty() || digits.length() > MAX_INTEGER_DIGITS) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }
        return !hasLeadingZero(digits);
    }

    private static boolean isDecimal(String value) {
        String unsigned = unsigned(value);
        String integerPart = unsigned.split("[.eE]", 2)[0];
        if (hasLeadingZero(integerPart)) {
            return false;
        }
        if (integerPart.length() == unsigned.length() && integerPart.length() > MAX_INTEGER_DIGITS) {
            // A run of digits with no decimal point, too long to be a long, is an identifier — an
            // 18-digit SSCC, an EPC, an IMEI — not a measurement. Calling it DECIMAL invites the next
            // consumer to hold it in a double, which silently rounds away its last digits, and a
            // shipping label carrying a corrupted SSCC is scanned at the wrong destination.
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** "0012" yes; "0" and "0.5" no — a lone zero is the number zero, not a padded code. */
    private static boolean hasLeadingZero(String digits) {
        return digits.length() > 1 && digits.charAt(0) == '0';
    }

    private static String unsigned(String value) {
        return value.startsWith("-") || value.startsWith("+") ? value.substring(1) : value;
    }

    private static boolean parses(String text, java.util.function.Consumer<String> parser) {
        try {
            parser.accept(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
