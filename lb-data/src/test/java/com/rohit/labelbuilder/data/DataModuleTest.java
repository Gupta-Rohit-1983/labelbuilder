package com.rohit.labelbuilder.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(DataModule.MODULE_NAME).isEqualTo("lb-data");
    }
}
