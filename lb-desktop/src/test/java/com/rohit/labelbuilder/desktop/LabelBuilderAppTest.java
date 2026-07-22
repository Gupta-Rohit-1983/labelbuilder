package com.rohit.labelbuilder.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

class LabelBuilderAppTest {

    @Test
    void entryPointIsAJavaFxApplication() {
        // Compile-time proof the FX classpath is wired; no toolkit started in unit tests.
        assertThat(Application.class).isAssignableFrom(LabelBuilderApp.class);
    }
}
