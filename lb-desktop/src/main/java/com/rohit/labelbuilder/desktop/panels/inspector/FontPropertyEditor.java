package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.style.FontSpec;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * A font picker for a {@code FONT} property (Phase 9b): family, point size, and the three style
 * toggles. Replaces 9a's read-only display.
 *
 * <p>A font is a composite value, so this control assembles a whole {@link FontSpec} from its parts
 * rather than parsing text — which is why {@code PropertyValues.parse} still refuses {@code FONT}.
 * Any change commits the complete font, so the model never sees a half-updated one.
 */
final class FontPropertyEditor implements PropertyEditor {

    private static final double MIN_PT = 1;
    private static final double MAX_PT = 999;

    private final VBox root = new VBox(4);
    private final ComboBox<String> family = new ComboBox<>();
    private final Spinner<Double> size = new Spinner<>();
    private final ToggleButton bold = new ToggleButton("B");
    private final ToggleButton italic = new ToggleButton("I");
    private final ToggleButton underline = new ToggleButton("U");
    private boolean suppress;

    FontPropertyEditor(Object initial, Consumer<Object> commit) {
        family.getItems().setAll(Font.getFamilies());
        family.setMaxWidth(Double.MAX_VALUE);
        family.setVisibleRowCount(12);

        size.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(MIN_PT, MAX_PT, 10, 1));
        size.setEditable(true);
        size.setPrefWidth(90);

        bold.setTooltip(new Tooltip("Bold"));
        italic.setTooltip(new Tooltip("Italic"));
        underline.setTooltip(new Tooltip("Underline"));

        showValue(initial);

        Runnable fire = () -> {
            if (!suppress) {
                commit.accept(current());
            }
        };
        family.setOnAction(e -> fire.run());
        size.valueProperty().addListener((o, a, b) -> fire.run());
        // An editable spinner does not apply typed text on focus loss; push it through by hand.
        size.focusedProperty().addListener((o, had, has) -> {
            if (!has) {
                commitTypedSize();
            }
        });
        bold.setOnAction(e -> fire.run());
        italic.setOnAction(e -> fire.run());
        underline.setOnAction(e -> fire.run());

        HBox styleRow = new HBox(4, size, bold, italic, underline);
        styleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(size, Priority.NEVER);
        root.getChildren().setAll(family, styleRow);
    }

    private void commitTypedSize() {
        try {
            double typed =
                    Math.clamp(Double.parseDouble(size.getEditor().getText().trim()), MIN_PT, MAX_PT);
            size.getValueFactory().setValue(typed);
        } catch (RuntimeException e) {
            size.getEditor().setText(PropertyValues.format(size.getValue()));
        }
    }

    /** The font currently described by the controls. */
    private FontSpec current() {
        String chosen = family.getValue();
        double points = size.getValue() == null ? 10 : size.getValue();
        return new FontSpec(
                chosen == null || chosen.isBlank() ? "Arial" : chosen,
                points <= 0 ? 10 : points,
                bold.isSelected(),
                italic.isSelected(),
                underline.isSelected());
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void showValue(Object value) {
        if (!(value instanceof FontSpec font)) {
            return;
        }
        suppress = true;
        try {
            family.setValue(font.family());
            size.getValueFactory().setValue(font.sizePt());
            bold.setSelected(font.bold());
            italic.setSelected(font.italic());
            underline.setSelected(font.underline());
        } finally {
            suppress = false;
        }
    }

    @Override
    public void showMixed() {
        suppress = true;
        try {
            family.setValue(null);
            family.setPromptText("(mixed)");
        } finally {
            suppress = false;
        }
    }

    @Override
    public boolean isEditing() {
        return family.isShowing() || size.getEditor().isFocused();
    }
}
