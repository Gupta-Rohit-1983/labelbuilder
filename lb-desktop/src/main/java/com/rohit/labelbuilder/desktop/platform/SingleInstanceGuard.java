package com.rohit.labelbuilder.desktop.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Ensures only one instance of the application runs per user.
 *
 * <p>Implemented with an exclusive OS file lock on {@code <appdata>/instance.lock}. The lock is
 * held by the acquiring process for its entire lifetime and released automatically by the OS when
 * the process exits — including on a crash, which a socket- or PID-file-based scheme handles far
 * less reliably.
 *
 * <p>Plain class, no Spring: it must run in {@code main()} before the heavyweight startup so a
 * second launch fails fast without booting a whole context.
 */
public final class SingleInstanceGuard implements AutoCloseable {

    private final Path lockFile;
    private FileChannel channel;
    private FileLock lock;

    public SingleInstanceGuard(Path appDataRoot) {
        this.lockFile = appDataRoot.resolve("instance.lock");
    }

    /**
     * @return {@code true} if this process acquired the lock (no other instance is running);
     *     {@code false} if another instance already holds it.
     */
    public boolean tryAcquire() {
        try {
            channel = FileChannel.open(
                    lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                channel = null;
                return false;
            }
            return true;
        } catch (OverlappingFileLockException alreadyHeldInThisJvm) {
            return false;
        } catch (IOException e) {
            // If the lock cannot be established at all (e.g. unusual filesystem), fail open: better
            // to allow a second instance than to block the user from ever starting the app.
            return true;
        }
    }

    @Override
    public void close() {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (IOException ignored) {
            // Releasing on shutdown; the OS reclaims the lock regardless.
        } finally {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignored) {
                // nothing actionable during shutdown
            }
        }
    }
}
