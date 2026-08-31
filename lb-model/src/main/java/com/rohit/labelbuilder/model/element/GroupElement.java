package com.rohit.labelbuilder.model.element;

import java.util.List;
import java.util.Objects;

/**
 * A group of elements treated as one for selection and transforms (lbl-format.md §4.8). Children
 * keep <b>absolute</b> coordinates (not relative to the group), matching the format spec, so a child
 * renders identically whether or not it is grouped. The children list is defensively copied and
 * unmodifiable.
 */
public record GroupElement(ElementProperties properties, List<LabelElement> children) implements LabelElement {

    public GroupElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children); // defensive, immutable, rejects null entries
    }

    public GroupElement withChildren(List<LabelElement> newChildren) {
        return new GroupElement(properties, newChildren);
    }

    @Override
    public GroupElement withProperties(ElementProperties newProperties) {
        return new GroupElement(newProperties, children);
    }
}
