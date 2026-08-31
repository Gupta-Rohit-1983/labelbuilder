package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.geom.Bounds;

/**
 * A single object placed on a label. Sealed so every consumer — the renderer mappers, the property
 * inspector, the {@code .lbl} serialiser — switches over one closed set; adding an element type
 * won't compile until each exhaustive switch handles it.
 *
 * <p>Common properties (id, name, bounds, rotation, lock, visibility…) live in a shared
 * {@link ElementProperties} accessed via {@link #properties()}; the default accessors and
 * transform helpers below delegate to it so callers rarely touch {@code properties()} directly.
 * All coordinates are model millimetres (NFR-07). Every element is immutable — transforms return a
 * new element of the same concrete type via {@link #withProperties(ElementProperties)}.
 */
public sealed interface LabelElement
        permits TextElement, RectangleElement, EllipseElement, LineElement, ImageElement, BarcodeElement, GroupElement {

    /** The shared common properties. */
    ElementProperties properties();

    /** Return a copy of this element (same concrete type) carrying the given common properties. */
    LabelElement withProperties(ElementProperties properties);

    // --- convenience accessors onto the common properties ---

    default String id() {
        return properties().id();
    }

    default String name() {
        return properties().name();
    }

    default String layerId() {
        return properties().layerId();
    }

    default Bounds bounds() {
        return properties().bounds();
    }

    default double rotationDeg() {
        return properties().rotationDeg();
    }

    default boolean locked() {
        return properties().locked();
    }

    default boolean visible() {
        return properties().visible();
    }

    // --- immutable transforms, returning the same concrete element type ---

    default LabelElement withBounds(Bounds newBounds) {
        return withProperties(properties().withBounds(newBounds));
    }

    default LabelElement movedBy(double dxMm, double dyMm) {
        return withBounds(bounds().translated(dxMm, dyMm));
    }

    default LabelElement withRotationDeg(double newRotationDeg) {
        return withProperties(properties().withRotationDeg(newRotationDeg));
    }

    default LabelElement withLocked(boolean newLocked) {
        return withProperties(properties().withLocked(newLocked));
    }

    default LabelElement withVisible(boolean newVisible) {
        return withProperties(properties().withVisible(newVisible));
    }
}
