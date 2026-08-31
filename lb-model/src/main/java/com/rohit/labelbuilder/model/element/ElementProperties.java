package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.geom.Bounds;
import java.util.Objects;

/**
 * The properties every {@link LabelElement} shares (lbl-format.md §4.1), held once by composition
 * rather than duplicated across each element record. Concrete elements embed one of these plus their
 * own type-specific fields, and {@link LabelElement} exposes these through default accessors.
 *
 * <p>Immutable; {@code withX} mutators return copies. {@code printCondition} is a nullable
 * expression — when set, the element is skipped at print time if it evaluates false.
 */
public record ElementProperties(
        String id,
        String name,
        String layerId,
        Bounds bounds,
        double rotationDeg,
        boolean locked,
        boolean visible,
        String printCondition) {

    public ElementProperties {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(rotationDeg)) {
            throw new IllegalArgumentException("rotationDeg must be finite, was " + rotationDeg);
        }
        // printCondition may be null
    }

    /** Defaults: rotation 0, unlocked, visible, no print condition. */
    public static ElementProperties of(String id, String name, String layerId, Bounds bounds) {
        return new ElementProperties(id, name, layerId, bounds, 0, false, true, null);
    }

    public ElementProperties withBounds(Bounds newBounds) {
        return new ElementProperties(id, name, layerId, newBounds, rotationDeg, locked, visible, printCondition);
    }

    public ElementProperties withRotationDeg(double newRotationDeg) {
        return new ElementProperties(id, name, layerId, bounds, newRotationDeg, locked, visible, printCondition);
    }

    public ElementProperties withName(String newName) {
        return new ElementProperties(id, newName, layerId, bounds, rotationDeg, locked, visible, printCondition);
    }

    public ElementProperties withLayerId(String newLayerId) {
        return new ElementProperties(id, name, newLayerId, bounds, rotationDeg, locked, visible, printCondition);
    }

    public ElementProperties withLocked(boolean newLocked) {
        return new ElementProperties(id, name, layerId, bounds, rotationDeg, newLocked, visible, printCondition);
    }

    public ElementProperties withVisible(boolean newVisible) {
        return new ElementProperties(id, name, layerId, bounds, rotationDeg, locked, newVisible, printCondition);
    }

    public ElementProperties withPrintCondition(String newPrintCondition) {
        return new ElementProperties(id, name, layerId, bounds, rotationDeg, locked, visible, newPrintCondition);
    }
}
