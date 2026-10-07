package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import com.rohit.labelbuilder.model.meta.PropertyKind;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

/**
 * A spinner for {@code DECIMAL} and {@code INTEGER} properties (Phase 9b) — position, size, rotation,
 * opacity and friends. Step and bounds come from {@link EditorSteps}, so the descriptor's declared
 * range becomes the control's range.
 *
 * <p>Two JavaFX {@link Spinner} traps are handled here. Text typed into an editable spinner is
 * <b>not</b> applied when focus leaves — without the focus listener below, a typed value is silently
 * discarded. And {@code setValue} fires the value listener just like a user click would, so a
 * {@code suppress} flag stops a refresh from being echoed back as a fresh edit.
 */
final class NumberPropertyEditor implements PropertyEditor {

    private final Spinner<?> spinner;
    private final boolean integral;
    private final double min;
    private final double max;
    private boolean suppress;

    NumberPropertyEditor(PropertyDescriptor descriptor, Object initial, Consumer<Object> commit) {
        this.integral = descriptor.kind() == PropertyKind.INTEGER;
        this.min = EditorSteps.minFor(descriptor);
        this.max = EditorSteps.maxFor(descriptor);
        double step = EditorSteps.stepFor(descriptor);

        if (integral) {
            Spinner<Integer> intSpinner = new Spinner<>();
            intSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    (int) min, (int) max, (int) toDouble(initial), (int) step));
            this.spinner = intSpinner;
        } else {
            Spinner<Double> doubleSpinner = new Spinner<>();
            SpinnerValueFactory.DoubleSpinnerValueFactory factory =
                    new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, toDouble(initial), step);
            // Reuse the inspector's number formatting so the field reads "10", not "10.0".
            factory.setConverter(new StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return value == null ? "" : PropertyValues.format(value);
                }

                @Override
                public Double fromString(String text) {
                    return Double.parseDouble(text.trim());
                }
            });
            doubleSpinner.setValueFactory(factory);
            this.spinner = doubleSpinner;
        }

        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);
        spinner.valueProperty().addListener((o, was, now) -> {
            if (!suppress && now != null) {
                commit.accept(integral ? (Object) ((Number) now).intValue() : (Object) ((Number) now).doubleValue());
            }
        });
        spinner.focusedProperty().addListener((o, had, has) -> {
            if (!has) {
                commitTypedText();
            }
        });
    }

    /** Apply whatever is in the text field; restore the current value if it will not parse. */
    private void commitTypedText() {
        String text = spinner.getEditor().getText();
        try {
            double parsed = Math.clamp(Double.parseDouble(text.trim()), min, max);
            setValue(parsed);
        } catch (RuntimeException e) {
            showValue(spinner.getValue()); // not a number — put the real value back
        }
    }

    @SuppressWarnings("unchecked")
    private void setValue(double value) {
        if (integral) {
            ((Spinner<Integer>) spinner).getValueFactory().setValue((int) Math.round(value));
        } else {
            ((Spinner<Double>) spinner).getValueFactory().setValue(value);
        }
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    public Node node() {
        return spinner;
    }

    @Override
    public void showValue(Object value) {
        suppress = true;
        try {
            setValue(Math.clamp(toDouble(value), min, max));
        } finally {
            suppress = false;
        }
    }

    @Override
    public void showMixed() {
        // A spinner always holds a number, so blank the text instead. Typing one applies it to the
        // whole selection; leaving it alone commits nothing.
        suppress = true;
        try {
            spinner.getEditor().setText("");
            spinner.getEditor().setPromptText("(mixed)");
        } finally {
            suppress = false;
        }
    }

    @Override
    public boolean isEditing() {
        return spinner.getEditor().isFocused();
    }
}
