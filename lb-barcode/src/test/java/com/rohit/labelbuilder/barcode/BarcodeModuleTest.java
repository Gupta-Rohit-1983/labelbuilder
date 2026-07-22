package com.rohit.labelbuilder.barcode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BarcodeModuleTest {

    @Test
    void moduleNameMatchesReactorArtifactId() {
        assertThat(BarcodeModule.MODULE_NAME).isEqualTo("lb-barcode");
    }
}
