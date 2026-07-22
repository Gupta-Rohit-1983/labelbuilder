package com.rohit.labelbuilder.automation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutomationModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(AutomationModule.MODULE_NAME).isEqualTo("lb-automation");
    }
}
