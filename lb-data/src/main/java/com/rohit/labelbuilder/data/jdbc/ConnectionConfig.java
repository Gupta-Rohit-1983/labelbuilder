package com.rohit.labelbuilder.data.jdbc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Everything needed to reach a database <b>except the secret</b> (FR-DS-09, risk R-08).
 *
 * <p>A template never stores one of these. It stores this config's {@link #id()}, and the config
 * itself lives in the client store or on the server, where the matching password is held encrypted at
 * rest. That indirection is the whole mechanism: a {@code .lbl} mailed to a colleague carries the
 * shape of the query and nothing that would let the recipient run it against production.
 *
 * <p><b>There is no password component, and there never may be.</b> The compact constructor rejects a
 * password-shaped entry in {@link #properties()} too, because a JDBC URL or property bag is the
 * obvious place for one to reappear — and {@code LblValidator.assertNoCredentialLeak} scanning the
 * saved archive for {@code pwd=} is a last line of defence, not the first. Being unable to express a
 * credential here is what makes the invariant structural rather than a rule somebody has to remember.
 *
 * @param id the stable identifier a document refers to, e.g. {@code cfg-3a91}
 * @param displayName what the operator sees in the connection list
 * @param url the full JDBC URL, which {@link JdbcDialect#url} can build
 * @param username the login name; not a secret, and never written to a {@code .lbl} either
 * @param properties extra driver properties, none of them credentials
 */
public record ConnectionConfig(
        String id,
        String displayName,
        JdbcDialect dialect,
        String url,
        String username,
        Map<String, String> properties) {

    /** Property keys that would amount to storing a credential here. */
    private static final String[] FORBIDDEN_KEYS = {"password", "pwd", "secret", "token", "passwd", "accesskey"};

    public ConnectionConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(url, "url");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a connection config needs a stable id");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException("a connection config needs a JDBC URL");
        }
        properties = Map.copyOf(properties == null ? Map.of() : properties);
        for (String key : properties.keySet()) {
            String normalised = key.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_KEYS) {
                if (normalised.contains(forbidden)) {
                    throw new IllegalArgumentException("'" + key
                            + "' looks like a credential; secrets are resolved from the credential store at open"
                            + " time and must not be stored with the connection (FR-DS-09)");
                }
            }
        }
        if (containsCredential(url)) {
            throw new IllegalArgumentException("the JDBC URL carries a credential; put the login name in username"
                    + " and the password in the credential store (FR-DS-09)");
        }
    }

    /** A networked config built from its parts. */
    public static ConnectionConfig of(
            String id,
            String displayName,
            JdbcDialect dialect,
            String host,
            int port,
            String database,
            String username) {
        return new ConnectionConfig(id, displayName, dialect, dialect.url(host, port, database), username, Map.of());
    }

    /** A config from a URL somebody pasted in, with the dialect recognised from it. */
    public static ConnectionConfig ofUrl(String id, String displayName, String url, String username) {
        return new ConnectionConfig(id, displayName, JdbcDialect.ofUrl(url), url, username, Map.of());
    }

    public ConnectionConfig withUsername(String username) {
        return new ConnectionConfig(id, displayName, dialect, url, username, properties);
    }

    public ConnectionConfig withProperty(String key, String value) {
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.put(key, value);
        return new ConnectionConfig(id, displayName, dialect, url, username, merged);
    }

    public ConnectionConfig withDisplayName(String displayName) {
        return new ConnectionConfig(id, displayName, dialect, url, username, properties);
    }

    /**
     * Whether some text embeds a password.
     *
     * <p>The same shapes {@code LblValidator.assertNoCredentialLeak} looks for in a saved archive, so
     * a URL rejected here could not have slipped past that check either.
     */
    static boolean containsCredential(String text) {
        String normalised = text.toLowerCase(Locale.ROOT);
        return normalised.contains("password=")
                || normalised.contains("pwd=")
                || normalised.contains("passwd=")
                // jdbc:…://user:secret@host — a colon-separated secret before an @ in the authority
                || normalised.matches("^[a-z0-9+.:-]*//[^/@:]+:[^/@]+@.*");
    }

    /** Omits {@link #properties()} to keep a log line readable; they are validated credential-free. */
    @Override
    public String toString() {
        return "ConnectionConfig[" + id + " '" + displayName + "' " + dialect + " " + url + " user=" + username + "]";
    }
}
