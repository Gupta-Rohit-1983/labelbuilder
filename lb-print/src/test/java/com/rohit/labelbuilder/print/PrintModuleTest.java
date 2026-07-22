package com.rohit.labelbuilder.print;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrintModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(PrintModule.MODULE_NAME).isEqualTo("lb-print");
    }
}
