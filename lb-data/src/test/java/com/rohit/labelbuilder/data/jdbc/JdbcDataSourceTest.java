package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.RecordSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcDataSourceTest {

    private static final String CONFIG = "cfg-1";

    private H2Database database;
    private ConnectionManager connections;

    @BeforeEach
    void startDatabase() {
        database = new H2Database();
        database.execute("""
                CREATE TABLE products (
                  code   VARCHAR(20) NOT NULL,
                  name   VARCHAR(100),
                  qty    INTEGER,
                  price  DECIMAL(10,2),
                  active BOOLEAN,
                  due    DATE,
                  made   TIMESTAMP
                )
                """, """
                INSERT INTO products VALUES
                  ('A1', 'Bolt', 5, 1.50, TRUE,  DATE '2026-10-26', TIMESTAMP '2026-10-26 08:30:00'),
                  ('A2', 'Nut',  7, 0.80, FALSE, DATE '2026-11-02', TIMESTAMP '2026-11-02 09:15:00'),
                  ('A3', NULL,   0, 0.00, NULL,  NULL,              NULL)
                """);
        connections = database.manager(CONFIG);
    }

    @AfterEach
    void stopDatabase() {
        database.close();
    }

    private JdbcDataSource source(String sql) {
        return new JdbcDataSource("Product", connections, CONFIG, sql);
    }

    private static List<String> column(RecordSet records, String field) {
        List<String> values = new ArrayList<>();
        while (records.next()) {
            values.add(records.current().text(field));
        }
        return values;
    }

    @Test
    void theSchemaComesFromTheDatabaseRatherThanBeingInferred() {
        JdbcDataSource source = source("SELECT code, name, qty, price, active, due, made FROM products");

        assertThat(source.fields())
                .extracting(FieldDefinition::name, FieldDefinition::type)
                .containsExactly(
                        tuple("CODE", FieldType.STRING),
                        tuple("NAME", FieldType.STRING),
                        tuple("QTY", FieldType.INTEGER),
                        tuple("PRICE", FieldType.DECIMAL),
                        tuple("ACTIVE", FieldType.BOOLEAN),
                        tuple("DUE", FieldType.DATE),
                        tuple("MADE", FieldType.TIMESTAMP));
    }

    @Test
    void declaredLengthAndScaleAreReportedBecauseTheDatabaseKnowsThem() {
        JdbcDataSource source = source("SELECT code, price FROM products");

        assertThat(source.fields().get(0).length()).hasValue(20);
        assertThat(source.fields().get(0).nullable()).isFalse();
        assertThat(source.fields().get(1).scale()).hasValue(2);
    }

    @Test
    void anAliasIsWhatTheFieldBindsTo() {
        // SELECT price AS Preis must bind as ${Product.Preis} — the operator's name, not the column's.
        JdbcDataSource source = source("SELECT price AS Preis FROM products");

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("PREIS");
    }

    @Test
    void rowsStreamInQueryOrder() {
        try (RecordSet records =
                source("SELECT code FROM products ORDER BY code").open()) {
            assertThat(column(records, "CODE")).containsExactly("A1", "A2", "A3");
        }
    }

    @Test
    void aDecimalKeepsItsScaleSoAPriceLabelKeepsItsPence() {
        try (RecordSet records =
                source("SELECT price FROM products ORDER BY code").open()) {
            records.next();

            // Not 1.5: DECIMAL(10,2) means two places, and the scale is part of the value.
            assertThat(records.current().value("PRICE")).contains(new BigDecimal("1.50"));
            assertThat(records.current().text("PRICE")).isEqualTo("1.50");
        }
    }

    @Test
    void datesAndTimestampsArriveAsJavaTimeValues() {
        // java.sql.Timestamp.toString() would render "2026-10-26 08:30:00.0" — a trailing tenth of a
        // second nobody asked for, and not the ISO form Phase 12 will parse.
        try (RecordSet records =
                source("SELECT due, made FROM products ORDER BY code").open()) {
            records.next();

            assertThat(records.current().value("DUE")).contains(LocalDate.of(2026, 10, 26));
            assertThat(records.current().value("MADE")).contains(LocalDateTime.of(2026, 10, 26, 8, 30));
        }
    }

    @Test
    void aNullColumnIsPresentButEmpty() {
        try (RecordSet records =
                source("SELECT code, name FROM products ORDER BY code").open()) {
            while (records.next()) {
                if ("A3".equals(records.current().text("CODE"))) {
                    DataRecord sparse = records.current();
                    assertThat(sparse.has("NAME")).isTrue();
                    assertThat(sparse.value("NAME")).isEmpty();
                    assertThat(sparse.text("NAME")).isEmpty();
                    return;
                }
            }
            throw new AssertionError("row A3 was not returned");
        }
    }

    @Test
    void anEmptyResultIsNotAnError() {
        try (RecordSet records =
                source("SELECT code FROM products WHERE code = 'nope'").open()) {
            assertThat(records.next()).isFalse();
        }
    }

    @Test
    void aCursorDoesNotClaimToKnowHowManyRowsThereAre() {
        try (RecordSet records = source("SELECT code FROM products").open()) {
            // Answering would mean reading the whole result set first.
            assertThat(records.size()).isEmpty();
        }
    }

    @Test
    void theCursorHonoursTheSharedContract() {
        RecordSet records =
                source("SELECT code FROM products WHERE code = 'A1'").open();

        assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);
        assertThat(records.next()).isTrue();
        assertThat(records.next()).isFalse();
        assertThat(records.next()).isFalse();
        assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);

        records.close();
        records.close();
        assertThatThrownBy(records::next).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eachOpenRunsTheQueryAgain() {
        JdbcDataSource source = source("SELECT code FROM products ORDER BY code");

        try (RecordSet first = source.open()) {
            assertThat(column(first, "CODE")).containsExactly("A1", "A2", "A3");
        }
        database.execute("INSERT INTO products (code) VALUES ('A4')");
        try (RecordSet second = source.open()) {
            // A print run re-reads rather than replaying a snapshot.
            assertThat(column(second, "CODE")).containsExactly("A1", "A2", "A3", "A4");
        }
    }

    // ---- parameters ----

    @Test
    void namedParametersAreBoundNotPastedIn() {
        JdbcDataSource source = source("SELECT code FROM products WHERE qty >= :minQty ORDER BY code");

        assertThat(source.parameterNames()).containsExactly("minQty");
        try (RecordSet records = source.open(Map.of("minQty", 5))) {
            assertThat(column(records, "CODE")).containsExactly("A1", "A2");
        }
    }

    @Test
    void aValueThatLooksLikeSqlIsJustAValue() {
        // The injection guarantee: the parameter travels as data, so this matches nothing rather than
        // dropping the table.
        JdbcDataSource source = source("SELECT code FROM products WHERE code = :code");

        try (RecordSet records = source.open(Map.of("code", "A1'; DROP TABLE products; --"))) {
            assertThat(records.next()).isFalse();
        }
        assertThat(source("SELECT code FROM products").fields()).isNotEmpty();
    }

    @Test
    void aMissingParameterIsRefusedRatherThanBoundToNull() {
        // NULL would make the query succeed and quietly return the wrong rows, which is far harder to
        // diagnose than being told which parameter was not supplied.
        JdbcDataSource source = source("SELECT code FROM products WHERE qty >= :minQty");

        assertThatThrownBy(source::open).isInstanceOf(DataException.class).hasMessageContaining("minQty");
    }

    @Test
    void aNameUsedTwiceNeedsOnlyOneValue() {
        JdbcDataSource source = source("SELECT code FROM products WHERE qty >= :q OR qty = :q ORDER BY code");

        try (RecordSet records = source.open(Map.of("q", 7))) {
            assertThat(column(records, "CODE")).containsExactly("A2");
        }
    }

    @Test
    void theSchemaOfAParameterisedQueryIsReadableWithoutSupplyingValues() {
        // Designing a label must not require the operator to invent a parameter value first.
        JdbcDataSource source = source("SELECT code, price FROM products WHERE qty >= :minQty");

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("CODE", "PRICE");
    }

    // ---- offline design, and resource release ----

    @Test
    void aCachedSchemaLetsATemplateBeEditedWithTheDatabaseOutOfReach() {
        // FieldDefinition's promise: a template opened on a laptop with no VPN is still editable.
        ConnectionManager unreachable = ConnectionManager.withoutCredentials()
                .register(ConnectionConfig.of(
                        "cfg-far", "ERP", JdbcDialect.H2, "", 0, "mem:nowhere;IFEXISTS=TRUE", "sa"));
        List<FieldDefinition> cached = List.of(FieldDefinition.text("CODE", 20), FieldDefinition.decimal("PRICE", 2));

        JdbcDataSource source =
                new JdbcDataSource("Product", unreachable, "cfg-far", "SELECT code, price FROM products", cached);

        assertThat(source.fields()).isEqualTo(cached);
        // It just cannot merge.
        assertThatThrownBy(source::open).isInstanceOf(DataException.class);
    }

    @Test
    void refreshingTheSchemaPicksUpAnEditedQuery() {
        JdbcDataSource source = new JdbcDataSource(
                "Product", connections, CONFIG, "SELECT code FROM products", List.of(FieldDefinition.text("STALE", 1)));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("STALE");
        assertThat(source.refreshFields()).extracting(FieldDefinition::name).containsExactly("CODE");
        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("CODE");
    }

    @Test
    void readingTheSchemaDoesNotLeaveAConnectionOpen() {
        int before = database.openSessions();

        source("SELECT code FROM products").fields();

        assertThat(database.openSessions()).isEqualTo(before);
    }

    @Test
    void closingTheCursorReleasesTheConnection() {
        // The reason the cursor owns the connection: try-with-resources around the record set has to be
        // enough, including when a print job fails halfway.
        int before = database.openSessions();
        RecordSet records = source("SELECT code FROM products").open();
        records.next();

        assertThat(database.openSessions()).isEqualTo(before + 1);

        records.close();

        assertThat(database.openSessions()).isEqualTo(before);
    }

    @Test
    void aFailedOpenDoesNotLeaveAConnectionOpenEither() {
        int before = database.openSessions();

        assertThatThrownBy(() -> source("SELECT nope FROM products").open()).isInstanceOf(DataException.class);

        assertThat(database.openSessions()).isEqualTo(before);
    }

    @Test
    void aBrokenQueryNamesTheDataSourceSoTheOperatorKnowsWhichOneToFix() {
        assertThatThrownBy(() -> source("SELECT nope FROM products").fields())
                .isInstanceOf(DataException.class)
                .hasMessageContaining("Product");
    }

    @Test
    void theSourceHoldsAConfigIdRatherThanAConnection() {
        // The whole R-08 mechanism in one assertion: what a template stores is this id.
        assertThat(source("SELECT code FROM products").configId()).isEqualTo(CONFIG);
    }
}
