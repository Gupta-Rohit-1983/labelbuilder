package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionManagerTest {

    private H2Database database;

    @BeforeEach
    void startDatabase() {
        database = new H2Database();
        database.execute("""
                CREATE TABLE products (
                  code  VARCHAR(20) NOT NULL,
                  name  VARCHAR(100),
                  price DECIMAL(10,2)
                )
                """, "CREATE VIEW cheap_products AS SELECT code FROM products WHERE price < 1");
    }

    @AfterEach
    void stopDatabase() {
        database.close();
    }

    @Test
    void registeredConfigsAreListedInOrder() {
        ConnectionManager manager = ConnectionManager.withoutCredentials()
                .register(database.config("cfg-1"))
                .register(ConnectionConfig.of("cfg-2", "Other", JdbcDialect.MYSQL, "db01", 0, "w", "r"));

        assertThat(manager.configs()).extracting(ConnectionConfig::id).containsExactly("cfg-1", "cfg-2");
        assertThat(manager.config("cfg-1")).isPresent();
        assertThat(manager.config("nope")).isEmpty();

        manager.remove("cfg-1");
        assertThat(manager.configs()).extracting(ConnectionConfig::id).containsExactly("cfg-2");
    }

    @Test
    void aSuccessfulTestNamesWhatAnswered() {
        ConnectionManager.ConnectionTest result = database.manager("cfg-1").test("cfg-1");

        assertThat(result.successful()).isTrue();
        assertThat(result.productName()).isEqualTo("H2");
        assertThat(result.productVersion()).isNotBlank();
        assertThat(result.message()).contains("H2");
    }

    @Test
    void aTestNeverThrowsBecauseAFailedTestIsAnAnswer() {
        // The wizard's Test button must show the driver's own words, not a stack trace.
        ConnectionManager wrongPassword = new ConnectionManager(
                        new InMemoryCredentialStore("cfg-1", "wrong".toCharArray()))
                .register(database.config("cfg-1"));

        ConnectionManager.ConnectionTest result = wrongPassword.test("cfg-1");

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void testingAnUnknownConfigSaysWhichIdWasAskedFor() {
        ConnectionManager.ConnectionTest result =
                ConnectionManager.withoutCredentials().test("cfg-missing");

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("cfg-missing");
    }

    @Test
    void aMissingDriverIsReportedAsSomethingToInstallNotAsAClassNotFound() {
        ConnectionManager manager = ConnectionManager.withoutCredentials()
                .register(ConnectionConfig.of("cfg-oracle", "ERP", JdbcDialect.ORACLE, "db01", 0, "ORCL", "reader"));

        ConnectionManager.ConnectionTest result = manager.test("cfg-oracle");

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("driver").contains("Oracle");
    }

    @Test
    void connectingToAnUnknownConfigExplainsWhatIsMissing() {
        assertThatThrownBy(() -> ConnectionManager.withoutCredentials().connect("cfg-missing"))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("cfg-missing")
                .hasMessageContaining("this machine");
    }

    @Test
    void aMissingPasswordIsRefusedByTheDatabaseRatherThanSkipped() {
        // The point of the indirection: a config alone is not enough to reach the database.
        ConnectionManager noCredentials = ConnectionManager.withoutCredentials().register(database.config("cfg-1"));

        assertThatThrownBy(() -> noCredentials.connect("cfg-1"))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("cannot connect");
    }

    @Test
    void resolvingASecretDoesNotConsumeIt() {
        // ConnectionManager wipes its copy after each connect; the credential store must be unharmed,
        // or the second label of a run would fail.
        ConnectionManager manager = database.manager("cfg-1");

        for (int attempt = 0; attempt < 3; attempt++) {
            try (Connection connection = manager.connect("cfg-1")) {
                assertThat(connection.isValid(5)).isTrue();
            } catch (Exception e) {
                throw new AssertionError("connection attempt " + attempt + " failed", e);
            }
        }
    }

    @Test
    void theSchemaBrowserListsTablesAndViews() {
        var tables = database.manager("cfg-1").tables("cfg-1");

        assertThat(tables).extracting(TableRef::name).contains("PRODUCTS", "CHEAP_PRODUCTS");
        assertThat(tables)
                .filteredOn(table -> table.name().equals("PRODUCTS"))
                .singleElement()
                .extracting(TableRef::qualifiedName)
                .isEqualTo("PUBLIC.PRODUCTS");
    }

    @Test
    void theSchemaBrowserReadsColumnsWithoutReadingRows() {
        var table = database.manager("cfg-1").tables("cfg-1").stream()
                .filter(t -> t.name().equals("PRODUCTS"))
                .findFirst()
                .orElseThrow();

        var columns = database.manager("cfg-1").columns("cfg-1", table);

        assertThat(columns)
                .extracting(FieldDefinition::name, FieldDefinition::type)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("CODE", FieldType.STRING),
                        org.assertj.core.api.Assertions.tuple("NAME", FieldType.STRING),
                        org.assertj.core.api.Assertions.tuple("PRICE", FieldType.DECIMAL));
    }

    @Test
    void aDeclaredLengthAndScaleComeFromTheDatabaseRatherThanBeingGuessed() {
        var manager = database.manager("cfg-1");
        var table = manager.tables("cfg-1").stream()
                .filter(t -> t.name().equals("PRODUCTS"))
                .findFirst()
                .orElseThrow();

        var columns = manager.columns("cfg-1", table);

        assertThat(columns.get(0).length()).hasValue(20);
        assertThat(columns.get(0).nullable()).isFalse();
        assertThat(columns.get(1).nullable()).isTrue();
        assertThat(columns.get(2).scale()).hasValue(2);
    }

    @Test
    void readingColumnsOfATableThatIsNotThereSaysSo() {
        var manager = database.manager("cfg-1");

        assertThatThrownBy(() -> manager.columns("cfg-1", TableRef.of(null, "PUBLIC", "NOPE", "TABLE")))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void everyConnectionTheManagerHandsOutIsClosedByItsCaller() {
        ConnectionManager manager = database.manager("cfg-1");
        int before = database.openSessions();

        try (Connection connection = manager.connect("cfg-1")) {
            assertThat(database.openSessions()).isEqualTo(before + 1);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(database.openSessions()).isEqualTo(before);
    }
}
