package com.rohit.labelbuilder.data.bind;

import com.rohit.labelbuilder.data.DataRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A property value split into literal text and {@code ${…}} placeholders (lbl-format.md §4.2).
 *
 * <p>A label's text is usually a mixture: {@code "SKU: ${Product.Code}"} prints a fixed caption beside
 * a merged value. Parsing once and resolving per record is what makes a 10 000-label run cheap — the
 * template is immutable and shared, and only {@link #resolve} runs per row.
 *
 * <p><b>Only field references are resolved here.</b> Anything else inside {@code ${…}} — a function
 * call, an operator, a bare variable name like {@code ${Serial}} — is kept as a
 * {@link Segment.Deferred} segment and written back out verbatim, because evaluating it is the Phase
 * 12 expression engine's job (and risk R-04 means that engine will be a whitelisted registry, not
 * something improvised here). A deferred segment is reported rather than silently dropped, so a
 * designer can see that part of the label will not merge yet.
 *
 * <p>There is no escape syntax, and none is needed: a lone {@code $} is literal, and only the exact
 * sequence {@code ${} opens a placeholder. An unterminated {@code ${} is treated as literal text, so
 * a half-typed placeholder shows on the canvas as what it is rather than swallowing the rest of the
 * value.
 */
public record ValueTemplate(List<Segment> segments) {

    public ValueTemplate {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }

    /** One piece of a property value. */
    public sealed interface Segment {

        /** Text to print as it stands. */
        record Literal(String text) implements Segment {}

        /** A field to substitute from a record. */
        record Reference(FieldReference reference) implements Segment {}

        /**
         * A placeholder this phase does not understand, kept exactly as written.
         *
         * @param expression the contents of the braces, without {@code ${} and {@code }}
         */
        record Deferred(String expression) implements Segment {}
    }

    public static ValueTemplate parse(String value) {
        if (value == null || value.isEmpty()) {
            return new ValueTemplate(List.of());
        }
        List<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int open = value.indexOf("${", i);
            if (open < 0) {
                literal.append(value, i, value.length());
                break;
            }
            int close = value.indexOf('}', open + 2);
            if (close < 0) {
                // Unterminated: the rest is literal, which is what a half-typed placeholder should look
                // like on the canvas.
                literal.append(value, i, value.length());
                break;
            }
            literal.append(value, i, open);
            String inside = value.substring(open + 2, close);
            flush(segments, literal);
            FieldReference.parse(inside)
                    .ifPresentOrElse(
                            reference -> segments.add(new Segment.Reference(reference)),
                            () -> segments.add(new Segment.Deferred(inside)));
            i = close + 1;
        }
        flush(segments, literal);
        return new ValueTemplate(segments);
    }

    private static void flush(List<Segment> segments, StringBuilder literal) {
        if (!literal.isEmpty()) {
            segments.add(new Segment.Literal(literal.toString()));
            literal.setLength(0);
        }
    }

    /** Whether the value has no placeholders at all, and so never needs re-resolving per record. */
    public boolean isLiteral() {
        return segments.stream().allMatch(Segment.Literal.class::isInstance);
    }

    /** The fields this value reads, in the order they appear. */
    public List<FieldReference> references() {
        return segments.stream()
                .filter(Segment.Reference.class::isInstance)
                .map(segment -> ((Segment.Reference) segment).reference())
                .toList();
    }

    /** The placeholders left for the Phase 12 expression engine. */
    public List<String> deferredExpressions() {
        return segments.stream()
                .filter(Segment.Deferred.class::isInstance)
                .map(segment -> ((Segment.Deferred) segment).expression())
                .toList();
    }

    /**
     * Substitutes the field references from {@code records}, keyed by source name.
     *
     * <p>A field with no value renders as empty, matching {@link DataRecord#text}: printing the word
     * "null" on a carton is worse than printing nothing. An unknown source or field is left as its
     * placeholder here and reported by {@link DocumentBinder}, so the caller decides whether that is a
     * warning to show or a reason to refuse the run — this method has nowhere to put a diagnostic.
     */
    public String resolve(Map<String, DataRecord> records) {
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) {
            switch (segment) {
                case Segment.Literal literal -> out.append(literal.text());
                case Segment.Deferred deferred ->
                    out.append("${").append(deferred.expression()).append('}');
                case Segment.Reference reference -> out.append(valueOf(reference.reference(), records));
            }
        }
        return out.toString();
    }

    private static String valueOf(FieldReference reference, Map<String, DataRecord> records) {
        DataRecord record = records.get(reference.sourceName());
        if (record == null || !record.has(reference.fieldName())) {
            return reference.placeholder();
        }
        return record.text(reference.fieldName());
    }

    /** The value as originally written, placeholders and all. */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) {
            switch (segment) {
                case Segment.Literal literal -> out.append(literal.text());
                case Segment.Deferred deferred ->
                    out.append("${").append(deferred.expression()).append('}');
                case Segment.Reference reference ->
                    out.append(reference.reference().placeholder());
            }
        }
        return out.toString();
    }
}
