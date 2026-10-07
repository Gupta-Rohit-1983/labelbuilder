package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.style.RgbaColor;
import javafx.scene.paint.Color;

/**
 * Converts between the model's {@link RgbaColor} (8-bit channels) and JavaFX's {@link Color}
 * (0..1 doubles).
 *
 * <p>The rounding matters: FX stores channels as doubles, so a naive {@code (int)(red * 255)} turns
 * 254.999… into 254 and a colour drifts every time it passes through the picker. Rounding keeps the
 * round-trip exact, which {@code FxColorsTest} pins for all 256 channel values.
 *
 * <p>{@code Color} is a plain value object — no toolkit required — so this stays unit-testable.
 */
final class FxColors {

    private FxColors() {}

    static Color toFx(RgbaColor color) {
        return Color.rgb(color.r(), color.g(), color.b(), color.a() / 255.0);
    }

    static RgbaColor toModel(Color color) {
        return new RgbaColor(
                channel(color.getRed()),
                channel(color.getGreen()),
                channel(color.getBlue()),
                channel(color.getOpacity()));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 1.0) * 255.0);
    }
}
