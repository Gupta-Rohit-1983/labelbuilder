package com.rohit.labelbuilder.model.meta;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Works out what a <b>multi-element selection</b> has in common (Phase 9d): which properties every
 * selected element exposes, and whether they currently agree on a value.
 *
 * <p>Pure, so the two rules that decide what a multi-selection inspector may show are tested on their
 * own. Properties are matched by key, and each element is read through <em>its own</em> schema — a
 * rectangle and a text element both have {@code width}, but their descriptors are different objects
 * with different accessors, so only the key is portable.
 */
public final class SharedProperties {

    private SharedProperties() {}

    /**
     * Whether a selection agrees on a value. A uniform {@code null} (every element has no print
     * condition) is genuinely different from a mixed value, so this cannot be an {@code Optional}.
     */
    public record CommonValue(boolean uniform, Object value) {

        public static CommonValue mixed() {
            return new CommonValue(false, null);
        }

        public static CommonValue of(Object value) {
            return new CommonValue(true, value);
        }
    }

    /**
     * The descriptors every element in the selection exposes, in the first element's order. The
     * returned descriptors belong to the first element's schema and are safe to use for presentation
     * (name, kind, range, enum constants) — but never to read or write another element; use that
     * element's own schema with the same key.
     */
    public static List<PropertyDescriptor> of(List<LabelElement> elements) {
        if (elements.isEmpty()) {
            return List.of();
        }
        List<ElementSchema> schemas =
                elements.stream().map(ElementSchemas::schemaFor).toList();
        ElementSchema first = schemas.getFirst();

        List<PropertyDescriptor> shared = new ArrayList<>();
        for (PropertyDescriptor candidate : first.properties()) {
            boolean inAll = schemas.stream().allMatch(schema -> matches(schema, candidate));
            if (inAll) {
                shared.add(candidate);
            }
        }
        return shared;
    }

    /**
     * A property is shared only if every element has it <em>and</em> agrees on its kind. Two types
     * could reuse a key for different kinds; editing those together would corrupt one of them.
     */
    private static boolean matches(ElementSchema schema, PropertyDescriptor candidate) {
        return schema.property(candidate.key())
                .filter(other -> other.kind() == candidate.kind())
                .filter(other -> other.valueType().equals(candidate.valueType()))
                .isPresent();
    }

    /** Whether the selection currently agrees on {@code key}, and the value if so. */
    public static CommonValue commonValue(List<LabelElement> elements, String key) {
        if (elements.isEmpty()) {
            return CommonValue.mixed();
        }
        Object first = ElementSchemas.schemaFor(elements.getFirst()).get(elements.getFirst(), key);
        for (LabelElement element : elements) {
            Object value = ElementSchemas.schemaFor(element).get(element, key);
            if (!Objects.equals(first, value)) {
                return CommonValue.mixed();
            }
        }
        return CommonValue.of(first);
    }
}
