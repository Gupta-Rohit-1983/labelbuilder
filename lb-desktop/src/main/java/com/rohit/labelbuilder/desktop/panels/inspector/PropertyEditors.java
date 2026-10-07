package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * Builds the right control for a {@link PropertyDescriptor}'s {@link
 * com.rohit.labelbuilder.model.meta.PropertyKind} (Phase 9a).
 *
 * <p>This is the whole point of the 7b metadata: the inspector never switches on element types, only
 * on property kinds, so a new element type gets a working inspector the moment it has a schema.
 *
 * <p>Phase 9b fills in the real controls: a colour picker, a font picker, and spinners whose step and
 * bounds come from the descriptor's range. They slotted in here without the panel changing at all,
 * which is what the 9a seam was for.
 */
public final class PropertyEditors {

    private PropertyEditors() {}

    /**
     * A control for one property.
     *
     * @param commit receives the parsed value when the user commits an edit
     * @param onError receives a human-readable message when the typed text is rejected
     */
    public static PropertyEditor create(
            PropertyDescriptor descriptor, Object initial, Consumer<Object> commit, Consumer<String> onError) {
        if (descriptor.readOnly()) {
            return new ReadOnlyEditor(initial);
        }
        return switch (descriptor.kind()) {
            case BOOLEAN -> new BooleanEditor(initial, commit);
            case ENUM -> new EnumEditor(descriptor, initial, commit);
            case FONT -> new FontPropertyEditor(initial, commit);
            case COLOR -> new ColorPropertyEditor(initial, commit);
            case INTEGER, DECIMAL -> new NumberPropertyEditor(descriptor, initial, commit);
            case TEXT -> new TextEditor(descriptor, initial, commit, onError);
        };
    }

    /**
     * A text field that parses on commit. Commits on Enter and on focus loss; on bad input it reports
     * the problem and restores the last good text, so the field can never hold a value the model does
     * not have.
     */
    private static final class TextEditor implements PropertyEditor {

        private final TextField field = new TextField();
        private final PropertyDescriptor descriptor;
        private final Consumer<Object> commit;
        private final Consumer<String> onError;
        private String lastGood;

        TextEditor(PropertyDescriptor descriptor, Object initial, Consumer<Object> commit, Consumer<String> onError) {
            this.descriptor = descriptor;
            this.commit = commit;
            this.onError = onError;
            this.lastGood = PropertyValues.format(initial);
            field.setText(lastGood);
            field.setOnAction(e -> tryCommit());
            field.focusedProperty().addListener((o, had, has) -> {
                if (!has) {
                    tryCommit();
                }
            });
        }

        private void tryCommit() {
            String text = field.getText();
            if (text.equals(lastGood)) {
                return; // nothing changed; don't push a no-op edit
            }
            try {
                Object value = PropertyValues.parse(descriptor, text);
                lastGood = text;
                commit.accept(value);
            } catch (RuntimeException e) {
                onError.accept(e.getMessage());
                field.setText(lastGood);
            }
        }

        @Override
        public Node node() {
            return field;
        }

        @Override
        public void showValue(Object value) {
            lastGood = PropertyValues.format(value);
            field.setText(lastGood);
        }

        @Override
        public void showMixed() {
            // Blank with a hint: typing here will set the same text on every selected element.
            lastGood = "";
            field.setText("");
            field.setPromptText("(mixed)");
        }

        @Override
        public boolean isEditing() {
            return field.isFocused();
        }
    }

    private static final class BooleanEditor implements PropertyEditor {

        private final CheckBox box = new CheckBox();

        BooleanEditor(Object initial, Consumer<Object> commit) {
            box.setSelected(Boolean.TRUE.equals(initial));
            box.setOnAction(e -> {
                // Clicking resolves a mixed selection to a definite value for everything selected.
                box.setIndeterminate(false);
                commit.accept(box.isSelected());
            });
        }

        @Override
        public Node node() {
            return box;
        }

        @Override
        public void showValue(Object value) {
            box.setIndeterminate(false);
            box.setSelected(Boolean.TRUE.equals(value));
        }

        @Override
        public void showMixed() {
            // A tri-state checkbox says "some are, some aren't" exactly.
            box.setAllowIndeterminate(true);
            box.setIndeterminate(true);
        }

        @Override
        public boolean isEditing() {
            return false; // a checkbox has no in-progress state to protect
        }
    }

    private static final class EnumEditor implements PropertyEditor {

        private final ComboBox<Object> combo = new ComboBox<>();

        EnumEditor(PropertyDescriptor descriptor, Object initial, Consumer<Object> commit) {
            combo.getItems().setAll(descriptor.enumConstants());
            combo.setValue(initial);
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setOnAction(e -> {
                if (combo.getValue() != null) {
                    commit.accept(combo.getValue());
                }
            });
        }

        @Override
        public Node node() {
            return combo;
        }

        @Override
        public void showValue(Object value) {
            combo.setValue(value);
        }

        @Override
        public void showMixed() {
            combo.setValue(null);
            combo.setPromptText("(mixed)");
        }

        @Override
        public boolean isEditing() {
            return combo.isShowing();
        }
    }

    /** Displays a value the inspector cannot edit yet (fonts) or must not (read-only descriptors). */
    private static final class ReadOnlyEditor implements PropertyEditor {

        private final TextField field = new TextField();

        ReadOnlyEditor(Object initial) {
            field.setText(PropertyValues.format(initial));
            field.setEditable(false);
            field.setDisable(true);
        }

        @Override
        public Node node() {
            return field;
        }

        @Override
        public void showValue(Object value) {
            field.setText(PropertyValues.format(value));
        }

        @Override
        public boolean isEditing() {
            return false;
        }
    }
}
