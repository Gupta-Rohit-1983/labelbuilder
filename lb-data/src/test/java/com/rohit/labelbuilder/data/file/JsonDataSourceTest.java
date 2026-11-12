package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonDataSourceTest {

    @TempDir
    Path folder;

    private Path json(String content) throws IOException {
        Path file = folder.resolve("data.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> column(RecordSet records, String field) {
        List<String> values = new ArrayList<>();
        while (records.next()) {
            values.add(records.current().text(field));
        }
        return values;
    }

    @Test
    void readsATopLevelArrayOfObjects() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("""
                        [ {"code": "A1", "name": "Bolt"},
                          {"code": "A2", "name": "Nut"} ]
                        """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("code", "name");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "name")).containsExactly("Bolt", "Nut");
        }
    }

    @Test
    void followsARecordPathIntoTheResponseEnvelope() throws IOException {
        // Almost no real feed puts its rows at the top level.
        Path file = json("""
                {"status": "ok",
                 "meta": {"page": 1},
                 "data": {"items": [ {"code": "A1"}, {"code": "A2"} ]}}
                """);

        JsonDataSource source = new JsonDataSource("Product", file, JsonOptions.DEFAULT.withRecordPath("data.items"));

        try (RecordSet records = source.open()) {
            assertThat(column(records, "code")).containsExactly("A1", "A2");
        }
    }

    @Test
    void aJsonPathStyleDollarPrefixIsAccepted() throws IOException {
        Path file = json("{\"items\": [ {\"code\": \"A1\"} ]}");

        JsonDataSource source = new JsonDataSource("Product", file, JsonOptions.DEFAULT.withRecordPath("$.items"));

        try (RecordSet records = source.open()) {
            assertThat(column(records, "code")).containsExactly("A1");
        }
    }

    @Test
    void nestedObjectsFlattenWithDottedNames() throws IOException {
        JsonDataSource source = new JsonDataSource(
                "Product", json("[ {\"code\": \"A1\", \"price\": {\"net\": 1.50, \"vat\": {\"rate\": 20}}} ]"));

        assertThat(source.fields())
                .extracting(FieldDefinition::name)
                .containsExactly("code", "price.net", "price.vat.rate");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("price.vat.rate")).isEqualTo("20");
        }
    }

    @Test
    void aQuotedNumberStaysTextWhileABareOneIsANumber() throws IOException {
        // JSON draws this distinction deliberately, and a label is exactly where it matters: "0012"
        // is an article number, 12 is a quantity.
        JsonDataSource source = new JsonDataSource("Product", json("[ {\"sku\": \"0012\", \"qty\": 12} ]"));

        assertThat(source.fields())
                .extracting(FieldDefinition::name, FieldDefinition::type)
                .containsExactly(tuple("sku", FieldType.STRING), tuple("qty", FieldType.INTEGER));
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("sku")).isEqualTo("0012");
        }
    }

    @Test
    void aDecimalKeepsTheTrailingZeroTheDocumentWrote() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("[ {\"price\": 1.50} ]"));

        assertThat(source.fields())
                .singleElement()
                .extracting(FieldDefinition::type)
                .isEqualTo(FieldType.DECIMAL);
        try (RecordSet records = source.open()) {
            records.next();
            // Parsing to a double and printing it back would turn 1.50 into 1.5.
            assertThat(records.current().text("price")).isEqualTo("1.50");
        }
    }

    @Test
    void anIsoDateStringIsStillTypedAsADate() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("[ {\"due\": \"2026-10-26\"} ]"));

        assertThat(source.fields())
                .singleElement()
                .extracting(FieldDefinition::type)
                .isEqualTo(FieldType.DATE);
    }

    @Test
    void theSchemaIsTheUnionOfTheSampledRowsNotTheFirstRow() throws IOException {
        // JSON omits a field rather than writing null, so taking row one as the schema would hide
        // every optional field in the feed.
        JsonDataSource source =
                new JsonDataSource("Product", json("[ {\"code\": \"A1\"}, {\"code\": \"A2\", \"batch\": \"B9\"} ]"));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("code", "batch");
        assertThat(source.fields())
                .filteredOn(field -> field.name().equals("batch"))
                .singleElement()
                .extracting(FieldDefinition::nullable)
                .isEqualTo(true);
    }

    @Test
    void aRowMissingAnOptionalFieldRendersItBlankRatherThanFailing() throws IOException {
        JsonDataSource source =
                new JsonDataSource("Product", json("[ {\"code\": \"A1\", \"batch\": \"B9\"}, {\"code\": \"A2\"} ]"));

        try (RecordSet records = source.open()) {
            records.next();
            records.next();
            DataRecord second = records.current();
            assertThat(second.has("batch")).isTrue();
            assertThat(second.value("batch")).isEmpty();
            assertThat(second.text("batch")).isEmpty();
        }
    }

    @Test
    void anExplicitNullIsPresentButEmpty() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("[ {\"code\": \"A1\", \"batch\": null} ]"));

        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().has("batch")).isTrue();
            assertThat(records.current().value("batch")).isEmpty();
        }
    }

    @Test
    void anArrayNestedInsideARowIsSkippedRatherThanFlattened() throws IOException {
        // A row with a list of components is two tables, not one; repeating sub-records belong to the
        // table element's rowSource.
        JsonDataSource source =
                new JsonDataSource("Product", json("[ {\"code\": \"A1\", \"parts\": [1, 2, 3], \"name\": \"Kit\"} ]"));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("code", "name");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("name")).isEqualTo("Kit");
        }
    }

    @Test
    void aSingleObjectIsOneRecord() throws IOException {
        // A feed that returns one record returns an object, not an array of one.
        JsonDataSource source = new JsonDataSource("Product", json("{\"code\": \"A1\"}"));

        try (RecordSet records = source.open()) {
            assertThat(column(records, "code")).containsExactly("A1");
        }
    }

    @Test
    void anArrayOfScalarsIsAOneColumnTable() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("[\"A1\", \"A2\"]"));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("value");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "value")).containsExactly("A1", "A2");
        }
    }

    @Test
    void anEmptyArrayOpensWithNoFields() throws IOException {
        JsonDataSource source = new JsonDataSource("Product", json("[]"));

        assertThat(source.fields()).isEmpty();
        try (RecordSet records = source.open()) {
            assertThat(records.next()).isFalse();
        }
    }

    @Test
    void aRecordPathThatIsNotThereFailsClearly() throws IOException {
        Path file = json("{\"data\": {\"other\": []}}");

        assertThatThrownBy(() ->
                        new JsonDataSource("Product", file, JsonOptions.DEFAULT.withRecordPath("data.items")).fields())
                .isInstanceOf(DataException.class)
                .hasMessageContaining("items");
    }

    @Test
    void malformedJsonFailsAsADataProblem() throws IOException {
        Path file = json("{ not json at all");

        assertThatThrownBy(() -> new JsonDataSource("Product", file).fields()).isInstanceOf(DataException.class);
    }

    @Test
    void aStreamingSourceDoesNotClaimToKnowItsSize() throws IOException {
        try (RecordSet records = new JsonDataSource("Product", json("[ {\"code\": \"A1\"} ]")).open()) {
            assertThat(records.size()).isEmpty();
        }
    }

    @Test
    void recordPathParsingIsForgivingAboutShape() {
        assertThat(RecordPath.parse("data.items").segments()).containsExactly("data", "items");
        assertThat(RecordPath.parse("$.data.items").segments()).containsExactly("data", "items");
        assertThat(RecordPath.parse("$")).isEqualTo(RecordPath.ROOT);
        assertThat(RecordPath.parse("")).isEqualTo(RecordPath.ROOT);
        assertThat(RecordPath.parse(null)).isEqualTo(RecordPath.ROOT);
        assertThat(RecordPath.ROOT.isRoot()).isTrue();
        assertThat(RecordPath.parse("data.items")).hasToString("data.items");
        assertThat(RecordPath.ROOT).hasToString("$");
    }
}
