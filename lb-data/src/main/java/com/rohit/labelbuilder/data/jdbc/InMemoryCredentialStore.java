package com.rohit.labelbuilder.data.jdbc;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passwords held only for this session: what a "remember for now" prompt and the offline grace period
 * (FR-S-04) need, and what the tests use.
 *
 * <p>Nothing here is persisted, which is the feature. A password the operator typed once stays
 * reachable until the application exits and then is gone, so a shared workstation does not accumulate
 * credentials. For the encrypted-at-rest case see {@link SealedCredentialStore}.
 *
 * <p>Arrays are copied on the way in and on the way out, so neither the caller that supplied a
 * password nor the one that consumed it can wipe the other's copy — or the store's.
 */
public final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, char[]> passwords = new ConcurrentHashMap<>();

    public InMemoryCredentialStore() {}

    public InMemoryCredentialStore(String configId, char[] password) {
        put(configId, password);
    }

    public void put(String configId, char[] password) {
        passwords.put(configId, password.clone());
    }

    public void remove(String configId) {
        wipe(passwords.remove(configId));
    }

    /** Forgets everything, zeroing as it goes — for sign-out, or a lock screen. */
    public void clear() {
        passwords.keySet().forEach(this::remove);
    }

    @Override
    public Optional<char[]> password(String configId) {
        char[] stored = passwords.get(configId);
        return Optional.ofNullable(stored).map(char[]::clone);
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
