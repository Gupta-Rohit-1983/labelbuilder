package com.rohit.labelbuilder.data.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.rohit.labelbuilder.data.jdbc.JdbcDialect;
import com.rohit.labelbuilder.data.jdbc.NamedQuery;
import com.rohit.labelbuilder.data.jdbc.TableRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SqlRendererTest {

    private static final TableRef PRODUCTS = TableRef.of(null, "dbo", "products", "TABLE");
    private static final TableRef CATEGORIES = TableRef.of(null, "dbo", "categories", "TABLE");

    private static String sql(QuerySpec spec) {
        return SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).sql();
    }

    @Test
    void aTableWithNoColumnsChosenSelectsEverything() {
        assertThat(sql(QuerySpec.from(PRODUCTS))).isEqualTo("SELECT * FROM \"dbo\".\"products\"");
    }

    @Test
    void chosenColumnsAreQuotedAndAliased() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .select(QueryColumn.of("code"), QueryColumn.of("unit_price").as("Price"));

        assertThat(sql(spec)).isEqualTo("SELECT \"code\", \"unit_price\" AS \"Price\" FROM \"dbo\".\"products\"");
    }

    @Test
    void anAliasIsWhatTheTemplateBindsTo() {
        // ${Product.Price} must keep working when the database column is renamed.
        assertThat(QueryColumn.of("unit_price").as("Price").fieldName()).isEqualTo("Price");
        assertThat(QueryColumn.of("code").fieldName()).isEqualTo("code");
    }

    @Test
    void distinctIsRendered() {
        assertThat(sql(QuerySpec.from(PRODUCTS).withDistinct(true)))
                .isEqualTo("SELECT DISTINCT * FROM \"dbo\".\"products\"");
    }

    // ---- values never reach the SQL text ----

    @Test
    void aLiteralBecomesAGeneratedPlaceholderNotAnInlineValue() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(QueryColumn.of("active"), ComparisonOperator.EQUALS, true));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).isEqualTo("SELECT * FROM \"dbo\".\"products\" WHERE \"active\" = :p1");
        assertThat(rendered.literalValues()).containsExactly(entry("p1", true));
    }

    @Test
    void aValueThatLooksLikeSqlIsStillJustAValue() {
        // The guarantee: there is no path through the renderer that concatenates an operand.
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(
                        QueryColumn.of("code"), ComparisonOperator.EQUALS, "x'; DROP TABLE products; --"));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).doesNotContain("DROP").endsWith("WHERE \"code\" = :p1");
        assertThat(rendered.literalValues()).containsExactly(entry("p1", "x'; DROP TABLE products; --"));
    }

    @Test
    void aDeclaredParameterKeepsItsOwnName() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.whereParameter(QueryColumn.of("sku"), ComparisonOperator.EQUALS, "sku"));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).endsWith("WHERE \"sku\" = :sku");
        assertThat(rendered.literalValues()).isEmpty();
        assertThat(rendered.declaredParameters()).containsExactly("sku");
        assertThat(rendered.needsSuppliedValues()).isTrue();
    }

    @Test
    void literalsAndParametersMixInOneQuery() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(QueryColumn.of("active"), ComparisonOperator.EQUALS, true))
                .where(QueryFilter.whereParameter(QueryColumn.of("qty"), ComparisonOperator.GREATER_OR_EQUAL, "minQty"))
                .where(QueryFilter.where(QueryColumn.of("code"), ComparisonOperator.LIKE, "A%"));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).endsWith("WHERE \"active\" = :p1 AND \"qty\" >= :minQty AND \"code\" LIKE :p2");
        assertThat(rendered.literalValues()).containsExactly(entry("p1", true), entry("p2", "A%"));
        assertThat(rendered.declaredParameters()).containsExactly("minQty");
    }

    @Test
    void theBoundValuesAreOrderedAsTheyAppearInTheSql() {
        // Map.copyOf would scramble this: an immutable Map's iteration order comes from a salt
        // randomised once per JVM, so the order differed between a single-module run and a full build.
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(QueryColumn.of("a"), ComparisonOperator.EQUALS, "first"))
                .where(QueryFilter.where(QueryColumn.of("b"), ComparisonOperator.EQUALS, "second"))
                .where(QueryFilter.where(QueryColumn.of("c"), ComparisonOperator.EQUALS, "third"));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.literalValues().keySet()).containsExactly("p1", "p2", "p3");
        assertThat(rendered.bindings().keySet()).containsExactly("p1", "p2", "p3");
    }

    @Test
    void renderingTheSameSpecTwiceProducesTheSameSql() {
        // The preview repaints on every keystroke; SQL that changed each time would look like the
        // editor fighting the operator, and would defeat any caching keyed on the text.
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(QueryColumn.of("a"), ComparisonOperator.EQUALS, 1))
                .where(QueryFilter.where(QueryColumn.of("b"), ComparisonOperator.EQUALS, 2));

        assertThat(sql(spec)).isEqualTo(sql(spec));
        assertThat(SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).literalValues())
                .isEqualTo(SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).literalValues());
    }

    @Test
    void everyRenderedPlaceholderIsPositionedByNamedQuery() {
        // The two halves have to agree: whatever the renderer emits, NamedQuery must turn into ?s.
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.whereBetween(QueryColumn.of("qty"), 1, 10))
                .where(QueryFilter.whereParameter(QueryColumn.of("sku"), ComparisonOperator.EQUALS, "sku"));

        NamedQuery positional = NamedQuery.parse(
                SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).sql());

        assertThat(positional.parameterNames()).containsExactly("p1", "p2", "sku");
        assertThat(positional.sql()).doesNotContain(":");
    }

    // ---- operators ----

    @Test
    void nullChecksTakeNoOperands() {
        QuerySpec spec = QuerySpec.from(PRODUCTS).where(QueryFilter.whereNull(QueryColumn.of("batch"), true));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).endsWith("WHERE \"batch\" IS NULL");
        assertThat(rendered.literalValues()).isEmpty();
    }

    @Test
    void inRendersOnePlaceholderPerValue() {
        QuerySpec spec = QuerySpec.from(PRODUCTS).where(QueryFilter.whereIn(QueryColumn.of("code"), "A1", "A2", "A3"));

        RenderedQuery rendered = SqlRenderer.render(spec, JdbcDialect.POSTGRESQL);

        assertThat(rendered.sql()).endsWith("WHERE \"code\" IN (:p1, :p2, :p3)");
        assertThat(rendered.literalValues()).hasSize(3);
    }

    @Test
    void betweenRendersBothBounds() {
        QuerySpec spec = QuerySpec.from(PRODUCTS).where(QueryFilter.whereBetween(QueryColumn.of("qty"), 1, 10));

        assertThat(sql(spec)).endsWith("WHERE \"qty\" BETWEEN :p1 AND :p2");
    }

    @Test
    void aFilterWithTheWrongNumberOfOperandsIsRefusedWhenItIsBuilt() {
        // A half-filled filter row is refused where the operator can still be told about it.
        assertThatThrownBy(() -> new QueryFilter(QueryColumn.of("qty"), ComparisonOperator.BETWEEN, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BETWEEN");
        assertThatThrownBy(() -> new QueryFilter(QueryColumn.of("code"), ComparisonOperator.IN, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueryFilter(
                        QueryColumn.of("a"), ComparisonOperator.EQUALS, List.of(FilterValue.of(1), FilterValue.of(2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersCanBeCombinedWithOr() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .where(QueryFilter.where(QueryColumn.of("a"), ComparisonOperator.EQUALS, 1))
                .where(QueryFilter.where(QueryColumn.of("b"), ComparisonOperator.EQUALS, 2))
                .withCombinator(QuerySpec.FilterCombinator.OR);

        assertThat(sql(spec)).endsWith("WHERE \"a\" = :p1 OR \"b\" = :p2");
    }

    // ---- joins, sorting, limits ----

    @Test
    void aJoinQualifiesBothSidesOfItsCondition() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .select(
                        QueryColumn.of("products", "code"),
                        QueryColumn.of("categories", "name").as("Category"))
                .join(JoinSpec.left(
                        CATEGORIES, QueryColumn.of("products", "category_id"), QueryColumn.of("categories", "id")));

        assertThat(sql(spec))
                .isEqualTo("SELECT \"products\".\"code\", \"categories\".\"name\" AS \"Category\""
                        + " FROM \"dbo\".\"products\""
                        + " LEFT JOIN \"dbo\".\"categories\""
                        + " ON \"products\".\"category_id\" = \"categories\".\"id\"");
    }

    @Test
    void aJoinCanBeAliased() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .join(JoinSpec.inner(CATEGORIES, QueryColumn.of("p", "cid"), QueryColumn.of("c", "id"))
                        .as("c"));

        assertThat(sql(spec)).contains("INNER JOIN \"dbo\".\"categories\" AS \"c\" ON \"p\".\"cid\" = \"c\".\"id\"");
    }

    @Test
    void sortingIsExplicitBecauseItDecidesThePrintOrder() {
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .orderBy(
                        SortColumn.ascending(QueryColumn.of("category_id")),
                        SortColumn.descending(QueryColumn.of("code")));

        assertThat(sql(spec)).endsWith("ORDER BY \"category_id\" ASC, \"code\" DESC");
    }

    @Test
    void eachDialectLimitsRowsItsOwnWay() {
        QuerySpec spec = QuerySpec.from(PRODUCTS).withRowLimit(10);

        assertThat(SqlRenderer.render(spec, JdbcDialect.SQL_SERVER).sql())
                .isEqualTo("SELECT TOP (10) * FROM [dbo].[products]");
        assertThat(SqlRenderer.render(spec, JdbcDialect.MYSQL).sql())
                .isEqualTo("SELECT * FROM `dbo`.`products` LIMIT 10");
        assertThat(SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).sql())
                .isEqualTo("SELECT * FROM \"dbo\".\"products\" LIMIT 10");
        assertThat(SqlRenderer.render(spec, JdbcDialect.ORACLE).sql())
                .isEqualTo("SELECT * FROM \"dbo\".\"products\" FETCH FIRST 10 ROWS ONLY");
    }

    @Test
    void theLimitGoesAfterTheOrderBySoItTakesTheRightRows() {
        // TOP (n) before an ORDER BY would otherwise read as "any 10 rows, then sort them".
        QuerySpec spec = QuerySpec.from(PRODUCTS)
                .orderBy(SortColumn.ascending(QueryColumn.of("code")))
                .withRowLimit(10);

        assertThat(SqlRenderer.render(spec, JdbcDialect.POSTGRESQL).sql()).endsWith("ORDER BY \"code\" ASC LIMIT 10");
        assertThat(SqlRenderer.render(spec, JdbcDialect.SQL_SERVER).sql())
                .startsWith("SELECT TOP (10)")
                .endsWith("ORDER BY [code] ASC");
    }

    @Test
    void aRowLimitOfZeroIsRefusedRatherThanSelectingNothing() {
        assertThatThrownBy(() -> QuerySpec.from(PRODUCTS).withRowLimit(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- identifiers ----

    @ParameterizedTest
    @EnumSource(JdbcDialect.class)
    void everyDialectCanQuoteAReservedWordAndASpace(JdbcDialect dialect) {
        QuerySpec spec =
                QuerySpec.from(TableRef.of(null, null, "Order", "TABLE")).select(QueryColumn.of("Total Qty"));

        String rendered = SqlRenderer.render(spec, dialect).sql();

        assertThat(rendered).contains("Total Qty").contains("Order");
        // Never bare: a reserved word or a space would be a syntax error unquoted.
        assertThat(rendered).doesNotContain(" Order ").doesNotContain("SELECT Total Qty");
    }

    @Test
    void anIdentifierContainingTheQuoteCharacterIsEscaped() {
        assertThat(JdbcDialect.SQL_SERVER.quote("od]d")).isEqualTo("[od]]d]");
        assertThat(JdbcDialect.MYSQL.quote("od`d")).isEqualTo("`od``d`");
        assertThat(JdbcDialect.POSTGRESQL.quote("od\"d")).isEqualTo("\"od\"\"d\"");
    }

    @Test
    void theCatalogIsLeftOutSoATemplateCanBePromotedBetweenEnvironments() {
        // A three-part name ties the query to one database name; a template moved from test to
        // production would then read from the test database.
        QuerySpec spec = QuerySpec.from(TableRef.of("WarehouseTest", "dbo", "products", "TABLE"));

        assertThat(sql(spec)).isEqualTo("SELECT * FROM \"dbo\".\"products\"").doesNotContain("WarehouseTest");
    }

    @Test
    void anUnqualifiedTableRendersWithoutASchema() {
        assertThat(sql(QuerySpec.from(TableRef.of(null, null, "products", "TABLE"))))
                .isEqualTo("SELECT * FROM \"products\"");
    }
}
