package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.style.RgbaColor;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;

/**
 * A colour swatch and picker for a {@code COLOR} property (Phase 9b), replacing 9a's hex text field.
 * The picker's custom-colour dialog carries an opacity slider, so alpha survives the round trip.
 */
final class ColorPropertyEditor implements PropertyEditor {

    private final ColorPicker picker = new ColorPicker();

    ColorPropertyEditor(Object initial, Consumer<Object> commit) {
        picker.setMaxWidth(Double.MAX_VALUE);
        showValue(initial);
        // onAction fires only for user choices, not for setValue, so no re-entrancy guard is needed.
        picker.setOnAction(e -> commit.accept(FxColors.toModel(picker.getValue())));
    }

    @Override
    public Node node() {
        return picker;
    }

    @Override
    public void showValue(Object value) {
        if (value instanceof RgbaColor color) {
            picker.setValue(FxColors.toFx(color));
        }
    }

    @Override
    public boolean isEditing() {
        return picker.isShowing();
    }
}
