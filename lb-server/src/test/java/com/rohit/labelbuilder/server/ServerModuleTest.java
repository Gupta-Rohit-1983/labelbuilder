package com.rohit.labelbuilder.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(ServerModule.MODULE_NAME).isEqualTo("lb-server");
    }
}
