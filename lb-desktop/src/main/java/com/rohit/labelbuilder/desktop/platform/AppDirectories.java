package com.rohit.labelbuilder.desktop.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves and creates the per-user application data directory tree.
 *
 * <p>Layout (Windows): {@code %APPDATA%\LabelBuilder\} with {@code logs\}, {@code config\},
 * {@code cache\} and {@code recovery\} subdirectories. On other platforms it falls back to
 * {@code $XDG_DATA_HOME} / {@code ~/.local/share} (Linux) or {@code ~/Library/Application Support}
 * (macOS), so tests and CI on Linux runners behave.
 *
 * <p>The root can be overridden with {@code -Dlabelbuilder.home=<path>}; tests use this to redirect
 * to a temp directory instead of the real user profile.
 *
 * <p>Intentionally a plain class with no Spring dependency: it must run in {@code main()} before the
 * Spring context (and therefore logging) exists, so the log directory can be handed to Logback via
 * a system property. It is also exposed as a Spring bean for injection.
 */
public final class AppDirectories {

    public static final String HOME_OVERRIDE_PROPERTY = "labelbuilder.home";
    public static final String LOG_DIR_PROPERTY = "labelbuilder.log-dir";
    private static final String APP_FOLDER = "LabelBuilder";

    private final Path root;
    private final Path logs;
    private final Path config;
    private final Path cache;
    private final Path recovery;

    private AppDirectories(Path root) {
        this.root = root;
        this.logs = root.resolve("logs");
        this.config = root.resolve("config");
        this.cache = root.resolve("cache");
        this.recovery = root.resolve("recovery");
    }

    /** Resolves the directory tree and ensures every directory exists. Idempotent. */
    public static AppDirectories resolveAndCreate() {
        AppDirectories dirs = new AppDirectories(resolveRoot());
        dirs.createAll();
        return dirs;
    }

    private static Path resolveRoot() {
        String override = System.getProperty(HOME_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base =
                    (appData != null && !appData.isBlank()) ? Path.of(appData) : Path.of(home, "AppData", "Roaming");
            return base.resolve(APP_FOLDER);
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", APP_FOLDER);
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = (xdg != null && !xdg.isBlank()) ? Path.of(xdg) : Path.of(home, ".local", "share");
        return base.resolve(APP_FOLDER);
    }

    private void createAll() {
        try {
            Files.createDirectories(logs);
            Files.createDirectories(config);
            Files.createDirectories(cache);
            Files.createDirectories(recovery);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create application data directory: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    public Path logs() {
        return logs;
    }

    public Path config() {
        return config;
    }

    public Path cache() {
        return cache;
    }

    public Path recovery() {
        return recovery;
    }
}
