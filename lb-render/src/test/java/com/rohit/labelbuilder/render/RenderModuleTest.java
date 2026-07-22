package com.rohit.labelbuilder.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RenderModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(RenderModule.MODULE_NAME).isEqualTo("lb-render");
    }
}
