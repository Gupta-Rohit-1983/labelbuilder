package com.rohit.labelbuilder.model.meta;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered set of {@link PropertyDescriptor}s for one concrete {@link LabelElement} type — its
 * editable schema. Descriptor order is the display order; {@link #byCategory()} preserves it within
 * each group. Keys are unique within a schema (enforced at construction).
 */
public final class ElementSchema {

    private final Class<? extends LabelElement> elementType;
    private final List<PropertyDescriptor> properties;
    private final Map<String, PropertyDescriptor> byKey;

    public ElementSchema(Class<? extends LabelElement> elementType, List<PropertyDescriptor> properties) {
        this.elementType = Objects.requireNonNull(elementType, "elementType");
        this.properties = List.copyOf(properties);
        Map<String, PropertyDescriptor> map = new LinkedHashMap<>();
        for (PropertyDescriptor p : this.properties) {
            if (map.put(p.key(), p) != null) {
                throw new IllegalArgumentException("duplicate property key '" + p.key() + "' in " + elementType);
            }
        }
        this.byKey = Map.copyOf(map);
    }

    public Class<? extends LabelElement> elementType() {
        return elementType;
    }

    public List<PropertyDescriptor> properties() {
        return properties;
    }

    public Optional<PropertyDescriptor> property(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** Read a property value by key. */
    public Object get(LabelElement element, String key) {
        return require(key).read(element);
    }

    /** Return a copy of {@code element} with the keyed property set to {@code value}. */
    public LabelElement set(LabelElement element, String key, Object value) {
        return require(key).write(element, value);
    }

    /** Descriptors grouped by {@link PropertyDescriptor#category()}, groups in first-seen order. */
    public Map<String, List<PropertyDescriptor>> byCategory() {
        Map<String, List<PropertyDescriptor>> grouped = new LinkedHashMap<>();
        for (PropertyDescriptor p : properties) {
            grouped.computeIfAbsent(p.category(), c -> new java.util.ArrayList<>())
                    .add(p);
        }
        return grouped;
    }

    private PropertyDescriptor require(String key) {
        PropertyDescriptor p = byKey.get(key);
        if (p == null) {
            throw new IllegalArgumentException("no property '" + key + "' on " + elementType.getSimpleName());
        }
        return p;
    }
}
