package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LabelSurfaceTest {

    @Test
    void holdsPositiveDimensions() {
        LabelSurface surface = new LabelSurface(100, 60);

        assertThat(surface.widthMm()).isEqualTo(100);
        assertThat(surface.heightMm()).isEqualTo(60);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThatThrownBy(() -> new LabelSurface(0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LabelSurface(100, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
