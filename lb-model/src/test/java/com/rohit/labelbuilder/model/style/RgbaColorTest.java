package com.rohit.labelbuilder.model.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RgbaColorTest {

    @Test
    void parseOpaqueSixDigit() {
        RgbaColor c = RgbaColor.parse("#1A2B3C");
        assertThat(c).isEqualTo(new RgbaColor(0x1A, 0x2B, 0x3C, 255));
    }

    @Test
    void parseEightDigitWithAlpha() {
        RgbaColor c = RgbaColor.parse("#1A2B3C80");
        assertThat(c.a()).isEqualTo(0x80);
    }

    @Test
    void parseIsCaseInsensitive() {
        assertThat(RgbaColor.parse("#aabbcc")).isEqualTo(RgbaColor.parse("#AABBCC"));
    }

    @Test
    void toHexDropsAlphaWhenOpaque() {
        assertThat(new RgbaColor(255, 0, 0, 255).toHex()).isEqualTo("#FF0000");
    }

    @Test
    void toHexKeepsAlphaWhenTranslucent() {
        assertThat(new RgbaColor(255, 0, 0, 128).toHex()).isEqualTo("#FF000080");
    }

    @Test
    void parseAndToHexRoundTrip() {
        for (String hex : new String[] {"#000000", "#FFFFFF", "#0A141E", "#12345678"}) {
            assertThat(RgbaColor.parse(hex).toHex()).isEqualTo(hex);
        }
    }

    @Test
    void malformedStringsAreRejected() {
        assertThatThrownBy(() -> RgbaColor.parse("123456")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RgbaColor.parse("#123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RgbaColor.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelOutOfRangeIsRejected() {
        assertThatThrownBy(() -> new RgbaColor(256, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RgbaColor(0, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
