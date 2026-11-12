package com.rohit.labelbuilder.data.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Passwords held as AES-GCM ciphertext and opened only when a connection is made (FR-DS-09).
 *
 * <p>The sealed bytes are what a persistent store writes to disk. This class owns the encryption and
 * the decrypt-on-demand behaviour; the H2-backed store in Phase 16a and the server store in Phase 21
 * supply the rows and the key, which is why {@link #sealedFor} and {@link #load} are both here —
 * {@code sealedFor} produces the blob to save, {@code load} takes blobs read back.
 *
 * <p>Holding the key in memory alongside the ciphertext does not protect against an attacker who
 * already has the process; it protects the <em>file</em>, which is what "at rest" means and what gets
 * copied to a backup, synced to a network share, or left on a decommissioned machine.
 */
public final class SealedCredentialStore implements CredentialStore {

    private final SecretKey key;
    private final Map<String, byte[]> sealed = new LinkedHashMap<>();

    public SealedCredentialStore(SecretKey key) {
        this.key = key;
    }

    /** Encrypts and keeps a password, returning the bytes a persistent store should write. */
    public byte[] put(String configId, char[] password) {
        byte[] blob = SecretCipher.seal(key, password);
        sealed.put(configId, blob);
        return blob.clone();
    }

    /** Takes already-sealed bytes, as read back from wherever they were stored. */
    public void load(String configId, byte[] sealedPassword) {
        sealed.put(configId, sealedPassword.clone());
    }

    /** The stored ciphertext for a config, for a store that is persisting it. */
    public Optional<byte[]> sealedFor(String configId) {
        return Optional.ofNullable(sealed.get(configId)).map(byte[]::clone);
    }

    public void remove(String configId) {
        sealed.remove(configId);
    }

    @Override
    public Optional<char[]> password(String configId) {
        byte[] blob = sealed.get(configId);
        // Decrypted per call rather than cached: the plaintext then exists only while a connection is
        // being opened, and the caller can wipe the array it was handed.
        return Optional.ofNullable(blob).map(b -> SecretCipher.open(key, b));
    }
}
