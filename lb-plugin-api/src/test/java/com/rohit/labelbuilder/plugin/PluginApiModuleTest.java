package com.rohit.labelbuilder.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PluginApiModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(PluginApiModule.MODULE_NAME).isEqualTo("lb-plugin-api");
    }
}
