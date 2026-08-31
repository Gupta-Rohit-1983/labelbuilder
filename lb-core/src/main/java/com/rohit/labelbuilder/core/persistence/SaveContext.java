package com.rohit.labelbuilder.core.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * The mutable-per-save metadata written into {@code manifest.json} (lbl-format.md §2) — app version,
 * author, and timestamps. Kept out of {@code document.json} so the document bytes stay stable across
 * saves (invariant §8.1); it lives only in the manifest.
 *
 * <p>The clock/user are injectable so tests get deterministic manifests.
 */
public record SaveContext(String appVersion, String user, Instant created, Instant modified) {

    public SaveContext {
        Objects.requireNonNull(appVersion, "appVersion");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(modified, "modified");
    }

    /** Defaults: current app version, OS user, now for both timestamps. */
    public static SaveContext defaults() {
        Instant now = Instant.now();
        String user = System.getProperty("user.name", "unknown");
        return new SaveContext("0.1.0", user, now, now);
    }

    /** A fixed-timestamp context (deterministic manifests in tests). */
    public static SaveContext fixed(String appVersion, String user, Instant timestamp) {
        return new SaveContext(appVersion, user, timestamp, timestamp);
    }
}
