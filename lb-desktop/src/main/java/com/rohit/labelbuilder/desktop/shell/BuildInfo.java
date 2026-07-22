package com.rohit.labelbuilder.desktop.shell;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Application identity, sourced from {@code application.yml}. */
@Component
public class BuildInfo {

    private final String appName;
    private final String version;

    public BuildInfo(
            @Value("${labelbuilder.app-name}") String appName, @Value("${labelbuilder.version}") String version) {
        this.appName = appName;
        this.version = version;
    }

    public String appName() {
        return appName;
    }

    public String version() {
        return version;
    }
}
