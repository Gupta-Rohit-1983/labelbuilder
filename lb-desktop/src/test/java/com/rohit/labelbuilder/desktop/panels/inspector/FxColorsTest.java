package com.rohit.labelbuilder.desktop.panels.inspector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.rohit.labelbuilder.model.style.RgbaColor;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

/** Colour conversion is lossless in both directions — {@code Color} needs no FX toolkit. */
class FxColorsTest {

    @Test
    void convertsToFxWithChannelsAndAlpha() {
        Color fx = FxColors.toFx(new RgbaColor(255, 128, 0, 51));

        assertThat(fx.getRed()).isEqualTo(1.0);
        // JavaFX stores channels as float, so 51/255 comes back as 0.20000000298… — hence the
        // tolerance here, and hence the rounding in FxColors that keeps the round trip exact.
        assertThat(fx.getOpacity()).isCloseTo(51 / 255.0, within(1e-6));
    }

    @Test
    void everyChannelValueSurvivesARoundTrip() {
        // Truncating instead of rounding loses values here; this is the regression that catches it.
        for (int v = 0; v <= 255; v++) {
            RgbaColor original = new RgbaColor(v, v, v, v);

            assertThat(FxColors.toModel(FxColors.toFx(original)))
                    .as("channel %d", v)
                    .isEqualTo(original);
        }
    }

    @Test
    void theExtremesLandExactlyOnBlackAndWhite() {
        assertThat(FxColors.toModel(new Color(1, 1, 1, 1))).isEqualTo(RgbaColor.WHITE);
        assertThat(FxColors.toModel(new Color(0, 0, 0, 1))).isEqualTo(RgbaColor.BLACK);
    }
}
