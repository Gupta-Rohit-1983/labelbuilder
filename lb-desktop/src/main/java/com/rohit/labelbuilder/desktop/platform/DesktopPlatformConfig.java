package com.rohit.labelbuilder.desktop.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers platform services as Spring beans for injection into controllers and services. */
@Configuration
public class DesktopPlatformConfig {

    /**
     * The app data directory tree. {@code resolveAndCreate()} is idempotent, so binding it here is
     * consistent with the instance already created in {@code main()}; tests that never call
     * {@code main()} still get a valid bean (redirected via {@code -Dlabelbuilder.home}).
     */
    @Bean
    public AppDirectories appDirectories() {
        return AppDirectories.resolveAndCreate();
    }
}
