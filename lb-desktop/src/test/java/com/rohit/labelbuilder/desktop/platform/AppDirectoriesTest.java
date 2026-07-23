package com.rohit.labelbuilder.desktop.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppDirectoriesTest {

    @TempDir
    Path tempHome;

    private String previousOverride;

    @BeforeEach
    void redirectHome() {
        previousOverride = System.getProperty(AppDirectories.HOME_OVERRIDE_PROPERTY);
        System.setProperty(AppDirectories.HOME_OVERRIDE_PROPERTY, tempHome.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousOverride == null) {
            System.clearProperty(AppDirectories.HOME_OVERRIDE_PROPERTY);
        } else {
            System.setProperty(AppDirectories.HOME_OVERRIDE_PROPERTY, previousOverride);
        }
    }

    @Test
    void createsFullDirectoryTree() {
        AppDirectories dirs = AppDirectories.resolveAndCreate();

        assertThat(dirs.root()).isEqualTo(tempHome);
        assertThat(dirs.logs()).exists().isDirectory();
        assertThat(dirs.config()).exists().isDirectory();
        assertThat(dirs.cache()).exists().isDirectory();
        assertThat(dirs.recovery()).exists().isDirectory();
    }

    @Test
    void isIdempotent() {
        AppDirectories first = AppDirectories.resolveAndCreate();
        Files.exists(first.logs());

        AppDirectories second = AppDirectories.resolveAndCreate();

        assertThat(second.root()).isEqualTo(first.root());
        assertThat(second.logs()).exists();
    }
}
