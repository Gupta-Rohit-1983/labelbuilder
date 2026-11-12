package com.rohit.labelbuilder.model.meta;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Declarative metadata for a single editable property of a {@link LabelElement}: how to read it, how
 * to produce a new element with it changed, and enough type/editor information for the Property
 * Inspector and serialiser to work <b>without reflection</b>.
 *
 * <p>The getter and wither are explicit functions supplied at registration (see
 * {@link com.rohit.labelbuilder.model.meta.ElementSchemas}), so there are no field-name string
 * lookups or bean-introspection hacks. Because a descriptor is bound to one element type, its
 * functions cast the incoming element; the {@link ElementSchemas} registry hands out the right
 * schema per element type so that cast is always safe.
 *
 * <p>Immutable. Values flow through the erased {@link #read(LabelElement)} /
 * {@link #write(LabelElement, Object)} pair; {@code write} returns a new element (the model is
 * immutable) and never mutates its argument.
 */
public final class PropertyDescriptor {

    private final String key;
    private final String displayName;
    private final String category;
    private final PropertyKind kind;
    private final Class<?> valueType;
    private final Function<LabelElement, Object> getter;
    private final BiFunction<LabelElement, Object, LabelElement> setter;
    private final boolean readOnly;
    private final boolean bindable;
    private final Double min;
    private final Double max;
    private final List<Object> enumConstants;

    private PropertyDescriptor(
            String key,
            String displayName,
            String category,
            PropertyKind kind,
            Class<?> valueType,
            Function<LabelElement, Object> getter,
            BiFunction<LabelElement, Object, LabelElement> setter,
            boolean readOnly,
            boolean bindable,
            Double min,
            Double max,
            List<Object> enumConstants) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.category = Objects.requireNonNull(category, "category");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.readOnly = readOnly;
        this.bindable = bindable;
        this.min = min;
        this.max = max;
        this.enumConstants = List.copyOf(enumConstants);
    }

    /** Fully-general typed factory; the specific-kind helpers below are usually clearer. */
    public static <V> PropertyDescriptor of(
            String key,
            String displayName,
            String category,
            PropertyKind kind,
            Class<V> valueType,
            Function<LabelElement, V> getter,
            BiFunction<LabelElement, V, LabelElement> setter) {
        List<Object> constants = valueType.isEnum() ? List.of((Object[]) valueType.getEnumConstants()) : List.of();
        return new PropertyDescriptor(
                key,
                displayName,
                category,
                kind,
                valueType,
                getter::apply,
                (e, v) -> setter.apply(e, valueType.cast(v)),
                false,
                false,
                null,
                null,
                constants);
    }

    public static PropertyDescriptor text(
            String key,
            String displayName,
            String category,
            Function<LabelElement, String> getter,
            BiFunction<LabelElement, String, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.TEXT, String.class, getter, setter);
    }

    public static PropertyDescriptor bool(
            String key,
            String displayName,
            String category,
            Function<LabelElement, Boolean> getter,
            BiFunction<LabelElement, Boolean, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.BOOLEAN, Boolean.class, getter, setter);
    }

    public static PropertyDescriptor integer(
            String key,
            String displayName,
            String category,
            Function<LabelElement, Integer> getter,
            BiFunction<LabelElement, Integer, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.INTEGER, Integer.class, getter, setter);
    }

    public static PropertyDescriptor decimal(
            String key,
            String displayName,
            String category,
            Function<LabelElement, Double> getter,
            BiFunction<LabelElement, Double, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.DECIMAL, Double.class, getter, setter);
    }

    public static <V> PropertyDescriptor color(
            String key,
            String displayName,
            String category,
            Class<V> colorType,
            Function<LabelElement, V> getter,
            BiFunction<LabelElement, V, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.COLOR, colorType, getter, setter);
    }

    public static <V> PropertyDescriptor font(
            String key,
            String displayName,
            String category,
            Class<V> fontType,
            Function<LabelElement, V> getter,
            BiFunction<LabelElement, V, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.FONT, fontType, getter, setter);
    }

    public static <E extends Enum<E>> PropertyDescriptor enumProp(
            String key,
            String displayName,
            String category,
            Class<E> enumType,
            Function<LabelElement, E> getter,
            BiFunction<LabelElement, E, LabelElement> setter) {
        return of(key, displayName, category, PropertyKind.ENUM, enumType, getter, setter);
    }

    /** A read-only view (no editor); {@link #write} throws. */
    public static <V> PropertyDescriptor readOnly(
            String key,
            String displayName,
            String category,
            PropertyKind kind,
            Class<V> valueType,
            Function<LabelElement, V> getter) {
        List<Object> constants = valueType.isEnum() ? List.of((Object[]) valueType.getEnumConstants()) : List.of();
        return new PropertyDescriptor(
                key,
                displayName,
                category,
                kind,
                valueType,
                getter::apply,
                (e, v) -> {
                    throw new UnsupportedOperationException("property is read-only: " + key);
                },
                true,
                false,
                null,
                null,
                constants);
    }

    /** A copy carrying an advisory numeric range (used by the inspector to bound INTEGER/DECIMAL). */
    public PropertyDescriptor withRange(Double min, Double max) {
        return new PropertyDescriptor(
                key,
                displayName,
                category,
                kind,
                valueType,
                getter,
                setter,
                readOnly,
                bindable,
                min,
                max,
                enumConstants);
    }

    /**
     * A copy marked as carrying printed content, so the data binder may substitute
     * {@code ${Source.Field}} references in it.
     *
     * <p>Declared here rather than decided by the binder, for the same reason the inspector reads its
     * controls from this metadata: the binder then has no per-element-type knowledge at all, and making
     * a new property bindable is a one-line change beside the property itself.
     *
     * <p>Most text properties are deliberately <em>not</em> bindable. An element's {@code name} is
     * design metadata that never prints, and {@code printCondition} is an expression for the Phase 12
     * engine to <em>evaluate</em> — substituting a value into it would turn a condition into a constant.
     */
    public PropertyDescriptor asBindable() {
        if (kind != PropertyKind.TEXT) {
            throw new IllegalArgumentException("only TEXT properties can be bound, " + key + " is " + kind);
        }
        return new PropertyDescriptor(
                key, displayName, category, kind, valueType, getter, setter, readOnly, true, min, max, enumConstants);
    }

    /** Read the current value from an element. */
    public Object read(LabelElement element) {
        return getter.apply(element);
    }

    /**
     * Return a copy of {@code element} with this property set to {@code value}.
     *
     * @throws UnsupportedOperationException if this descriptor is read-only
     */
    public LabelElement write(LabelElement element, Object value) {
        if (readOnly) {
            throw new UnsupportedOperationException("property is read-only: " + key);
        }
        return setter.apply(element, value);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String category() {
        return category;
    }

    public PropertyKind kind() {
        return kind;
    }

    public Class<?> valueType() {
        return valueType;
    }

    public boolean readOnly() {
        return readOnly;
    }

    /** Whether this property carries printed content that may contain {@code ${Source.Field}}. */
    public boolean bindable() {
        return bindable;
    }

    public Optional<Double> min() {
        return Optional.ofNullable(min);
    }

    public Optional<Double> max() {
        return Optional.ofNullable(max);
    }

    /** The permitted values for an {@link PropertyKind#ENUM} property, else empty. */
    public List<Object> enumConstants() {
        return enumConstants;
    }

    @Override
    public String toString() {
        return "PropertyDescriptor[" + key + " (" + kind + ")]";
    }
}
