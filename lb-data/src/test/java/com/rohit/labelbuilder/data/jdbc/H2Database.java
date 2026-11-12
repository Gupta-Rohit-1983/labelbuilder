package com.rohit.labelbuilder.data.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * A throwaway in-memory database for the JDBC tests.
 *
 * <p>A real database rather than a mocked {@code ResultSet}: the behaviour 11c has to get right —
 * metadata without executing, decimal scale surviving, a password actually being required, a
 * connection actually being released — is behaviour of a driver, and a mock would only assert what
 * the test already believes.
 *
 * <p>Each instance gets a fresh name, so tests cannot see each other's tables, and holds one
 * connection open for the duration: an H2 memory database exists only while something is connected to
 * it.
 */
final class H2Database implements AutoCloseable {

    static final String USER = "sa";
    static final String PASSWORD = "s3cret";

    private final String url;
    private final Connection admin;

    H2Database() {
        this.url = "jdbc:h2:mem:lb" + UUID.randomUUID().toString().replace("-", "");
        try {
            this.admin = DriverManager.getConnection(url, USER, PASSWORD);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot start the test database", e);
        }
    }

    String url() {
        return url;
    }

    /** A config pointing at this database, with the dialect's own driver and URL handling. */
    ConnectionConfig config(String id) {
        return new ConnectionConfig(id, "Test database", JdbcDialect.H2, url, USER, java.util.Map.of());
    }

    /** A manager that can reach this database, with the password resolved at open time. */
    ConnectionManager manager(String configId) {
        return new ConnectionManager(new InMemoryCredentialStore(configId, PASSWORD.toCharArray()))
                .register(config(configId));
    }

    void execute(String... statements) {
        try (Statement statement = admin.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("test setup failed: " + e.getMessage(), e);
        }
    }

    /** How many sessions are open, so a test can prove a cursor released its connection. */
    int openSessions() {
        try (Statement statement = admin.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot count sessions: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            admin.close();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot stop the test database", e);
        }
    }
}
