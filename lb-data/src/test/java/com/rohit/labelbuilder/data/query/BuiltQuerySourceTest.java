package com.rohit.labelbuilder.data.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.RecordSet;
import com.rohit.labelbuilder.data.jdbc.ConnectionConfig;
import com.rohit.labelbuilder.data.jdbc.ConnectionManager;
import com.rohit.labelbuilder.data.jdbc.InMemoryCredentialStore;
import com.rohit.labelbuilder.data.jdbc.JdbcDialect;
import com.rohit.labelbuilder.data.jdbc.TableRef;
import com.rohit.labelbuilder.data.preview.DataPreview;
import com.rohit.labelbuilder.data.preview.DataPreviews;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The whole 11d chain, end to end against a real database. */
class BuiltQuerySourceTest {

    private static final String CONFIG = "cfg-1";
    private static final TableRef PRODUCTS = TableRef.of(null, "PUBLIC", "PRODUCTS", "TABLE");
    private static final TableRef CATEGORIES = TableRef.of(null, "PUBLIC", "CATEGORIES", "TABLE");

    private String url;
    private Connection admin;
    private ConnectionManager connections;

    @BeforeEach
    void startDatabase() throws SQLException {
        url = "jdbc:h2:mem:lb" + UUID.randomUUID().toString().replace("-", "");
        admin = DriverManager.getConnection(url, "sa", "s3cret");
        try (Statement statement = admin.createStatement()) {
            statement.execute("CREATE TABLE categories (id INTEGER, name VARCHAR(50))");
            statement.execute("""
                    CREATE TABLE products (
                      code VARCHAR(20), qty INTEGER, price DECIMAL(10,2), active BOOLEAN, category_id INTEGER
                    )
                    """);
            statement.execute("INSERT INTO categories VALUES (1, 'Fasteners'), (2, 'Tools')");
            statement.execute("""
                    INSERT INTO products VALUES
                      ('A1', 5,  1.50, TRUE,  1),
                      ('A2', 7,  0.80, TRUE,  1),
                      ('A3', 0,  9.99, FALSE, 2),
                      ('A4', 12, 2.00, TRUE,  NULL)
                    """);
        }
        connections = new ConnectionManager(new InMemoryCredentialStore(CONFIG, "s3cret".toCharArray()))
                .register(new ConnectionConfig(CONFIG, "Test", JdbcDialect.H2, url, "sa", Map.of()));
    }

    @AfterEach
    void stopDatabase() throws SQLException {
        admin.close();
    }

    private BuiltQuerySource source(QuerySpec spec, QueryParameter... parameters) {
        return new BuiltQuerySource("Product", connections, CONFIG, spec, List.of(parameters));
    }

    private static List<String> codes(RecordSet records) {
        List<String> values = new ArrayList<>();
        while (records.next()) {
            values.add(records.current().text("CODE"));
        }
        return values;
    }

    @Test
    void aBuiltQueryRunsAgainstTheDatabase() {
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("CODE"))
                .orderBy(SortColumn.ascending(QueryColumn.of("CODE"))));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("CODE");
        try (RecordSet records = source.open()) {
            assertThat(codes(records)).containsExactly("A1", "A2", "A3", "A4");
        }
    }

    @Test
    void theSqlShownToTheOperatorIsExactlyWhatRuns() {
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS).select(QueryColumn.of("CODE")));

        assertThat(source.sql()).isEqualTo("SELECT \"CODE\" FROM \"PUBLIC\".\"PRODUCTS\"");
        try (RecordSet records = source.open()) {
            assertThat(records.next()).isTrue();
        }
    }

    @Test
    void aLiteralFilterIsBoundRatherThanInlined() {
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("CODE"))
                .where(QueryFilter.where(QueryColumn.of("ACTIVE"), ComparisonOperator.EQUALS, true))
                .orderBy(SortColumn.ascending(QueryColumn.of("CODE"))));

        assertThat(source.sql()).contains(":p1");
        try (RecordSet records = source.open()) {
            assertThat(codes(records)).containsExactly("A1", "A2", "A4");
        }
    }

    @Test
    void aDeclaredParameterIsSuppliedAtOpenTime() {
        BuiltQuerySource source = source(
                QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .where(QueryFilter.whereParameter(
                                QueryColumn.of("QTY"), ComparisonOperator.GREATER_OR_EQUAL, "minQty"))
                        .orderBy(SortColumn.ascending(QueryColumn.of("CODE"))),
                QueryParameter.required("minQty", FieldType.INTEGER));

        try (RecordSet records = source.open(Map.of("minQty", 7))) {
            assertThat(codes(records)).containsExactly("A2", "A4");
        }
    }

    @Test
    void aParameterSuppliedAsTextIsStillComparedAsANumber() {
        // The bug this guards: bound as text, "qty >= '7'" would also match 12 — and miss it, or match
        // it, depending on the database's string collation rather than on arithmetic.
        BuiltQuerySource source = source(
                QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .where(QueryFilter.whereParameter(
                                QueryColumn.of("QTY"), ComparisonOperator.GREATER_OR_EQUAL, "minQty"))
                        .orderBy(SortColumn.ascending(QueryColumn.of("CODE"))),
                QueryParameter.required("minQty", FieldType.INTEGER));

        try (RecordSet records = source.open(Map.of("minQty", "7"))) {
            assertThat(codes(records)).containsExactly("A2", "A4");
        }
    }

    @Test
    void literalsAndSuppliedValuesBindTogether() {
        BuiltQuerySource source = source(
                QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .where(QueryFilter.where(QueryColumn.of("ACTIVE"), ComparisonOperator.EQUALS, true))
                        .where(QueryFilter.whereParameter(
                                QueryColumn.of("QTY"), ComparisonOperator.GREATER_OR_EQUAL, "minQty"))
                        .orderBy(SortColumn.ascending(QueryColumn.of("CODE"))),
                QueryParameter.required("minQty", FieldType.INTEGER));

        try (RecordSet records = source.open(Map.of("minQty", "6"))) {
            assertThat(codes(records)).containsExactly("A2", "A4");
        }
    }

    @Test
    void anOptionalParameterUsesItsDefaultWhenNothingIsSupplied() {
        BuiltQuerySource source = source(
                QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .where(QueryFilter.whereParameter(
                                QueryColumn.of("QTY"), ComparisonOperator.GREATER_OR_EQUAL, "minQty")),
                QueryParameter.withDefault("minQty", FieldType.INTEGER, 100L));

        try (RecordSet records = source.open()) {
            assertThat(records.next()).isFalse();
        }
    }

    @Test
    void aQueryUsingAnUndeclaredParameterIsRefusedWhenItIsBuilt() {
        // Nothing would ever prompt for it, so the run would fail at the printer with the operator
        // standing next to it.
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.whereParameter(QueryColumn.of("QTY"), ComparisonOperator.EQUALS, "ghost"));

        assertThatThrownBy(() -> source(spec))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("ghost")
                .hasMessageContaining("not declared");
    }

    @Test
    void aValueThatLooksLikeSqlMatchesNothingInsteadOfRunning() {
        BuiltQuerySource source = source(
                QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .where(QueryFilter.whereParameter(QueryColumn.of("CODE"), ComparisonOperator.EQUALS, "code")),
                QueryParameter.required("code", FieldType.STRING));

        try (RecordSet records = source.open(Map.of("code", "A1'; DROP TABLE products; --"))) {
            assertThat(records.next()).isFalse();
        }
        assertThat(source(QuerySpec.from(PRODUCTS)).fields()).isNotEmpty();
    }

    @Test
    void aLeftJoinKeepsTheRowWhoseLookupIsMissing() {
        // A4 has no category. An inner join would drop it, and the run would be one carton short with
        // nothing to show why.
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS)
                .select(
                        QueryColumn.of("PRODUCTS", "CODE"),
                        QueryColumn.of("CATEGORIES", "NAME").as("Category"))
                .join(JoinSpec.left(
                        CATEGORIES, QueryColumn.of("PRODUCTS", "CATEGORY_ID"), QueryColumn.of("CATEGORIES", "ID")))
                .orderBy(SortColumn.ascending(QueryColumn.of("PRODUCTS", "CODE"))));

        try (RecordSet records = source.open()) {
            assertThat(codes(records)).containsExactly("A1", "A2", "A3", "A4");
        }
    }

    @Test
    void anInnerJoinDropsItAndTheBuilderOffersBoth() {
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("PRODUCTS", "CODE"))
                .join(JoinSpec.inner(
                        CATEGORIES, QueryColumn.of("PRODUCTS", "CATEGORY_ID"), QueryColumn.of("CATEGORIES", "ID")))
                .orderBy(SortColumn.ascending(QueryColumn.of("PRODUCTS", "CODE"))));

        try (RecordSet records = source.open()) {
            assertThat(codes(records)).containsExactly("A1", "A2", "A3");
        }
    }

    @Test
    void aRowLimitIsHonouredByTheDatabase() {
        BuiltQuerySource source = source(QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("CODE"))
                .orderBy(SortColumn.ascending(QueryColumn.of("CODE")))
                .withRowLimit(2));

        try (RecordSet records = source.open()) {
            assertThat(codes(records)).containsExactly("A1", "A2");
        }
    }

    @Test
    void theStructureIsKeptSoTheQueryCanBeReopenedAndEdited() {
        // A template that stored only SQL text would be editable in the SQL editor alone.
        QuerySpec spec = QuerySpec.from(PRODUCTS).select(QueryColumn.of("CODE"));
        BuiltQuerySource source = source(spec);

        QuerySpec edited = source.spec().plusColumn(QueryColumn.of("QTY"));

        assertThat(source.spec()).isEqualTo(spec);
        assertThat(source(edited).fields()).extracting(FieldDefinition::name).containsExactly("CODE", "QTY");
    }

    @Test
    void buildingAgainstAnUnknownConnectionFailsBeforeAnySqlIsGenerated() {
        assertThatThrownBy(() -> new BuiltQuerySource(
                        "Product", ConnectionManager.withoutCredentials(), "cfg-missing", QuerySpec.from(PRODUCTS)))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("dialect");
    }

    // ---- live preview ----

    @Test
    void thePreviewShowsTheFirstRowsAndSaysSo() {
        DataPreview preview = DataPreviews.of(
                source(QuerySpec.from(PRODUCTS)
                        .select(QueryColumn.of("CODE"))
                        .orderBy(SortColumn.ascending(QueryColumn.of("CODE")))),
                Map.of(),
                2);

        assertThat(preview.isSuccessful()).isTrue();
        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.truncated()).isTrue();
        assertThat(preview.text(0, "CODE")).isEqualTo("A1");
        assertThat(preview.summary()).contains("first 2 rows");
    }

    @Test
    void aPreviewThatFitsIsNotMarkedTruncated() {
        DataPreview preview = DataPreviews.of(source(QuerySpec.from(PRODUCTS).select(QueryColumn.of("CODE"))));

        assertThat(preview.rows()).hasSize(4);
        assertThat(preview.truncated()).isFalse();
        assertThat(preview.summary()).contains("4 rows");
    }

    @Test
    void aPreviewOfABrokenQueryShowsTheMessageRatherThanThrowing() {
        // The designer is where queries get fixed; a stack trace would be the worst response.
        DataPreview preview = DataPreviews.of(new com.rohit.labelbuilder.data.jdbc.JdbcDataSource(
                "Product", connections, CONFIG, "SELECT nope FROM products"));

        assertThat(preview.isSuccessful()).isFalse();
        assertThat(preview.error()).isPresent();
        assertThat(preview.summary()).isNotBlank();
        assertThat(preview.rows()).isEmpty();
    }

    @Test
    void aPreviewOfAQueryMatchingNothingStillCarriesItsColumns() {
        // A label must be layoutable against a query that matches nothing today.
        DataPreview preview = DataPreviews.of(source(QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("CODE"), QueryColumn.of("QTY"))
                .where(QueryFilter.where(QueryColumn.of("CODE"), ComparisonOperator.EQUALS, "nope"))));

        assertThat(preview.isEmpty()).isTrue();
        assertThat(preview.fields()).extracting(FieldDefinition::name).containsExactly("CODE", "QTY");
        assertThat(preview.summary()).isEqualTo("no rows matched");
    }

    @Test
    void aPreviewOfAParameterisedQueryWithNoValueReportsWhatIsMissing() {
        DataPreview preview = DataPreviews.of(source(
                QuerySpec.from(PRODUCTS)
                        .where(QueryFilter.whereParameter(QueryColumn.of("QTY"), ComparisonOperator.EQUALS, "minQty")),
                QueryParameter.required("minQty", FieldType.INTEGER)));

        assertThat(preview.isSuccessful()).isFalse();
        assertThat(preview.error().orElseThrow()).contains("minQty");
    }

    @Test
    void aPreviewReleasesItsConnection() {
        int before = openSessions();

        DataPreviews.of(source(QuerySpec.from(PRODUCTS)));

        // Reading a preview must not hold the connection while the operator looks at the result.
        assertThat(openSessions()).isEqualTo(before);
    }

    @Test
    void aPreviewTimesItselfSoTheUiCanWarnAboutASlowQuery() {
        DataPreview preview = DataPreviews.of(source(QuerySpec.from(PRODUCTS)));

        assertThat(preview.elapsed()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        assertThat(preview.summary()).contains("ms");
    }

    @Test
    void aPreviewOfZeroRowsIsRefusedBecauseItWouldShowNothing() {
        assertThatThrownBy(() -> DataPreviews.of(source(QuerySpec.from(PRODUCTS)), Map.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private int openSessions() {
        try (Statement statement = admin.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
