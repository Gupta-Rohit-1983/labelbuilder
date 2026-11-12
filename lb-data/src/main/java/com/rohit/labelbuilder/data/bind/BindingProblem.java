package com.rohit.labelbuilder.data.bind;

import java.util.Objects;

/**
 * Something wrong with one binding, reported rather than thrown.
 *
 * <p>The same split as {@code ValidationReport} in the barcode engine, and for the same reason: the
 * designer has to keep working while a label is half-finished, so binding reports and the caller
 * decides. A preview shows these beside the canvas; a print run refuses on any {@link Severity#ERROR}.
 *
 * @param elementId the element the problem is on, so the UI can select it
 * @param propertyKey the property within that element
 */
public record BindingProblem(Severity severity, String elementId, String propertyKey, String message) {

    public BindingProblem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(propertyKey, "propertyKey");
        Objects.requireNonNull(message, "message");
    }

    /** How bad it is. */
    public enum Severity {
        /**
         * The label would print wrong. A reference to a source or field that does not exist leaves a
         * placeholder on the label — or, worse, an empty barcode that scans as nothing.
         */
        ERROR,
        /** The label will print, but not everything merged — a placeholder Phase 12 will own. */
        WARNING
    }

    static BindingProblem error(String elementId, String propertyKey, String message) {
        return new BindingProblem(Severity.ERROR, elementId, propertyKey, message);
    }

    static BindingProblem warning(String elementId, String propertyKey, String message) {
        return new BindingProblem(Severity.WARNING, elementId, propertyKey, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + elementId + "." + propertyKey + ": " + message;
    }
}
