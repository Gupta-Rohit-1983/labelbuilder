package com.rohit.labelbuilder.data.jdbc;

import java.util.Optional;

/**
 * Resolves a connection's password, by config id, at the moment a connection is opened (risk R-08).
 *
 * <p>Late resolution is the point. If a {@link ConnectionConfig} held its own password, the secret
 * would travel with every copy of the config — into logs, into a template, into whatever serialises
 * it next. Asking for it only when connecting means the secret's lifetime is one method call, and the
 * store decides where it really lives: the encrypted client store, the server, an OS keychain, or a
 * prompt shown to the operator.
 *
 * <p>A {@code char[]} rather than a {@code String} because the caller can wipe it. An interned String
 * stays in the heap until it is collected, which means it also stays in a heap dump taken after a
 * crash. The caller owns the array it is handed and should zero it once the connection is up —
 * {@link ConnectionManager} does.
 */
@FunctionalInterface
public interface CredentialStore {

    /**
     * The password for a connection config, or empty when there is none to supply.
     *
     * <p>Empty is not an error: a trusted-connection or integrated-security setup, and a file-based
     * database such as SQLite, genuinely have no password.
     *
     * @return a fresh array the caller may wipe, never the store's own copy
     */
    Optional<char[]> password(String configId);

    /** A store that supplies nothing, for connections that need no password. */
    static CredentialStore none() {
        return configId -> Optional.empty();
    }
}
