package com.rohit.labelbuilder.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(ModelModule.MODULE_NAME).isEqualTo("lb-model");
    }
}
