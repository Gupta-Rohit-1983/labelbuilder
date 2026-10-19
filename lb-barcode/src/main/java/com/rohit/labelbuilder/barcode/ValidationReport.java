package com.rohit.labelbuilder.barcode;

import java.util.List;

/**
 * The outcome of checking a barcode (Phase 10c).
 *
 * <p>Two severities, because they mean genuinely different things. An <b>error</b> means the value
 * cannot be encoded at all — the barcode will not exist. A <b>warning</b> means it encodes but is
 * likely to scan badly: bars too narrow, quiet zone too tight, symbol too short. Conflating them
 * would either block a label a user knowingly wants, or let an unscannable one through silently.
 */
public record ValidationReport(List<Issue> issues) {

    public ValidationReport {
        issues = List.copyOf(issues);
    }

    public enum Severity {
        /** The value cannot be encoded. */
        ERROR,
        /** It encodes, but is likely to scan poorly. */
        WARNING
    }

    public record Issue(Severity severity, String message) {}

    public static ValidationReport ok() {
        return new ValidationReport(List.of());
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
    }

    /** True when there is nothing at all to report. */
    public boolean isClean() {
        return issues.isEmpty();
    }

    public List<String> errors() {
        return messagesOf(Severity.ERROR);
    }

    public List<String> warnings() {
        return messagesOf(Severity.WARNING);
    }

    private List<String> messagesOf(Severity severity) {
        return issues.stream()
                .filter(i -> i.severity() == severity)
                .map(Issue::message)
                .toList();
    }
}
