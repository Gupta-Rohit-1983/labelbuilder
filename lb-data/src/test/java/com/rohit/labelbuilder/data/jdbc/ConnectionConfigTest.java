package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Risk R-08 made structural: a connection config cannot express a credential. */
class ConnectionConfigTest {

    @Test
    void buildsItsUrlFromTheDialect() {
        ConnectionConfig config =
                ConnectionConfig.of("cfg-1", "Warehouse", JdbcDialect.MYSQL, "db01", 0, "warehouse", "reader");

        assertThat(config.url()).isEqualTo("jdbc:mysql://db01:3306/warehouse");
        assertThat(config.username()).isEqualTo("reader");
        assertThat(config.properties()).isEmpty();
    }

    @Test
    void recognisesTheDialectOfAPastedUrl() {
        ConnectionConfig config =
                ConnectionConfig.ofUrl("cfg-1", "Warehouse", "jdbc:postgresql://db01/warehouse", "reader");

        assertThat(config.dialect()).isEqualTo(JdbcDialect.POSTGRESQL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "Password", "db.password", "pwd", "secret", "apiToken", "accessKey"})
    void aPasswordShapedPropertyIsRefused(String key) {
        // The property bag is the obvious place for a secret to reappear once the record itself has no
        // field for one.
        assertThatThrownBy(() -> new ConnectionConfig(
                        "cfg-1",
                        "Warehouse",
                        JdbcDialect.MYSQL,
                        "jdbc:mysql://db01/w",
                        "reader",
                        Map.of(key, "s3cret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FR-DS-09");
    }

    @Test
    void addingAPasswordPropertyLaterIsRefusedToo() {
        ConnectionConfig config =
                ConnectionConfig.of("cfg-1", "Warehouse", JdbcDialect.MYSQL, "db01", 0, "warehouse", "reader");

        assertThatThrownBy(() -> config.withProperty("password", "s3cret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:sqlserver://db01:1433;databaseName=W;password=s3cret",
                "jdbc:mysql://db01/w?user=reader&password=s3cret",
                "jdbc:sqlserver://db01;PWD=s3cret",
                "jdbc:postgresql://reader:s3cret@db01/warehouse"
            })
    void aUrlCarryingACredentialIsRefused(String url) {
        assertThatThrownBy(() -> ConnectionConfig.ofUrl("cfg-1", "Warehouse", url, "reader"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:mysql://db01:3306/warehouse",
                "jdbc:sqlserver://db01:1433;databaseName=Warehouse;encrypt=true",
                "jdbc:postgresql://db01:5432/warehouse",
                "jdbc:oracle:thin:@//db01:1521/ORCL",
                "jdbc:sqlite:C:/data/stock.db",
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            })
    void anOrdinaryUrlIsAccepted(String url) {
        assertThat(ConnectionConfig.ofUrl("cfg-1", "Warehouse", url, "reader").url())
                .isEqualTo(url);
    }

    @Test
    void aHostAndPortAreNotMistakenForALoginAndPassword() {
        // "host:1433" has the same shape as "user:secret"; only the @ distinguishes them.
        assertThat(ConnectionConfig.containsCredential("jdbc:mysql://db01:3306/warehouse"))
                .isFalse();
        assertThat(ConnectionConfig.containsCredential("jdbc:mysql://reader:s3cret@db01:3306/warehouse"))
                .isTrue();
    }

    @Test
    void aBlankIdOrUrlIsRefusedBecauseADocumentRefersToTheIdAlone() {
        assertThatThrownBy(() -> new ConnectionConfig("  ", "W", JdbcDialect.MYSQL, "jdbc:mysql://h/w", "r", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectionConfig("cfg-1", "W", JdbcDialect.MYSQL, "  ", "r", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesAreCopiedSoALaterMutationCannotSmuggleOneIn() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("ssl", "true");
        ConnectionConfig config =
                new ConnectionConfig("cfg-1", "W", JdbcDialect.MYSQL, "jdbc:mysql://h/w", "r", mutable);

        mutable.put("password", "s3cret");

        assertThat(config.properties()).containsExactly(org.assertj.core.api.Assertions.entry("ssl", "true"));
    }

    @Test
    void theRecordHasNoPasswordComponentAtAll() {
        // The clearest statement of the invariant: there is nowhere to put one.
        assertThat(ConnectionConfig.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "displayName", "dialect", "url", "username", "properties");
    }
}
