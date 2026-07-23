package com.rohit.labelbuilder.desktop.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceGuardTest {

    @TempDir
    Path root;

    @Test
    void firstAcquireSucceedsSecondFails() {
        try (SingleInstanceGuard first = new SingleInstanceGuard(root)) {
            assertThat(first.tryAcquire()).isTrue();

            try (SingleInstanceGuard second = new SingleInstanceGuard(root)) {
                assertThat(second.tryAcquire()).isFalse();
            }
        }
    }

    @Test
    void lockIsReusableAfterRelease() {
        SingleInstanceGuard first = new SingleInstanceGuard(root);
        assertThat(first.tryAcquire()).isTrue();
        first.close();

        try (SingleInstanceGuard second = new SingleInstanceGuard(root)) {
            assertThat(second.tryAcquire()).isTrue();
        }
    }
}
