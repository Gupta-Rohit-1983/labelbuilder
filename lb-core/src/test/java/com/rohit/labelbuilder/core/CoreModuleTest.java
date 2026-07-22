package com.rohit.labelbuilder.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(CoreModule.MODULE_NAME).isEqualTo("lb-core");
    }
}
