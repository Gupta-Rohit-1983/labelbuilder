package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
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

class XmlDataSourceTest {

    @TempDir
    Path folder;

    private Path xml(String content) throws IOException {
        Path file = folder.resolve("data.xml");
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
    void readsAttributesAndChildElementsAsFields() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product sku="A1"><name>Bolt</name><qty>5</qty></product>
                          <product sku="A2"><name>Nut</name><qty>7</qty></product>
                        </products>
                        """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("sku", "name", "qty");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "name")).containsExactly("Bolt", "Nut");
        }
    }

    @Test
    void nestedElementsFlattenWithDottedNames() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><price><net>1.50</net><gross>1.80</gross></price></product>
                          <product><price><net>0.80</net><gross>0.96</gross></price></product>
                        </products>
                        """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("price.net", "price.gross");
        try (RecordSet records = source.open()) {
            records.next();
            // The wrapping element holds only whitespace; it must not become a field of its own.
            assertThat(records.current().has("price")).isFalse();
            assertThat(records.current().text("price.net")).isEqualTo("1.50");
        }
    }

    @Test
    void aRepeatedChildIsRenamedRatherThanOverwritten() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><tag>new</tag><tag>sale</tag></product>
                          <product><tag>old</tag><tag>clearance</tag></product>
                        </products>
                        """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("tag", "tag_2");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("tag")).isEqualTo("new");
            assertThat(records.current().text("tag_2")).isEqualTo("sale");
        }
    }

    @Test
    void theRepeatingElementIsDetected() throws IOException {
        XmlDataSource source =
                new XmlDataSource("Order", xml("<orders><order><id>1</id></order><order><id>2</id></order></orders>"));

        assertThat(source.rowElement()).isEqualTo("order");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "id")).containsExactly("1", "2");
        }
    }

    @Test
    void detectionLooksPastASingletonWrapper() throws IOException {
        // An RSS-shaped document has one <channel> around many <item>s; the row is the item.
        XmlDataSource source = new XmlDataSource(
                "Item", xml("<rss><channel><item><t>a</t></item><item><t>b</t></item></channel></rss>"));

        assertThat(source.rowElement()).isEqualTo("item");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "t")).containsExactly("a", "b");
        }
    }

    @Test
    void aDocumentWithOneRowStillReadsAsOneRow() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("<products><product><id>1</id></product></products>"));

        try (RecordSet records = source.open()) {
            assertThat(column(records, "id")).containsExactly("1");
        }
    }

    @Test
    void anExplicitRowElementOverridesDetection() throws IOException {
        // Detection cannot settle a one-order-many-lines document; naming the element can.
        Path file = xml("""
                <orders>
                  <order id="7"><line><sku>A1</sku></line><line><sku>A2</sku></line></order>
                </orders>
                """);

        XmlDataSource source = new XmlDataSource("Order", file, XmlOptions.DEFAULT.withRowElement("order"));

        assertThat(source.fields()).extracting(FieldDefinition::name).contains("id");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "id")).containsExactly("7");
        }
    }

    @Test
    void columnTypesAreInferredFromTheText() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><sku>0012</sku><qty>5</qty><due>2026-10-26</due></product>
                          <product><sku>0013</sku><qty>7</qty><due>2026-11-02</due></product>
                        </products>
                        """));

        assertThat(source.fields())
                .extracting(FieldDefinition::type)
                .containsExactly(FieldType.STRING, FieldType.INTEGER, FieldType.DATE);
    }

    @Test
    void cdataAndEntitiesAreRead() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><name><![CDATA[Bolt & Nut]]></name></product>
                          <product><name>Washer &amp; Shim</name></product>
                        </products>
                        """));

        try (RecordSet records = source.open()) {
            assertThat(column(records, "name")).containsExactly("Bolt & Nut", "Washer & Shim");
        }
    }

    @Test
    void aDoctypeIsRefusedBecauseEntitiesAreAnAttackSurface() throws IOException {
        // The classic XXE: an entity resolving to a local file, in a document supplied by whoever is
        // sending the order. A label feed has no legitimate need for a DTD.
        Path file = xml("""
                <!DOCTYPE products [ <!ENTITY secret SYSTEM "file:///etc/passwd"> ]>
                <products><product><name>&secret;</name></product></products>
                """);

        assertThatThrownBy(() -> new XmlDataSource("Product", file).fields()).isInstanceOf(DataException.class);
    }

    @Test
    void aRowMissingAnElementRendersItBlank() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><id>1</id><batch>B9</batch></product>
                          <product><id>2</id></product>
                        </products>
                        """));

        try (RecordSet records = source.open()) {
            records.next();
            records.next();
            assertThat(records.current().has("batch")).isTrue();
            assertThat(records.current().text("batch")).isEmpty();
        }
    }

    @Test
    void nestedAttributesAreNamedByTheirPath() throws IOException {
        XmlDataSource source = new XmlDataSource("Product", xml("""
                        <products>
                          <product><price currency="GBP">1.50</price></product>
                          <product><price currency="EUR">1.80</price></product>
                        </products>
                        """));

        assertThat(source.fields()).extracting(FieldDefinition::name).contains("price.currency", "price");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("price.currency")).isEqualTo("GBP");
            assertThat(records.current().text("price")).isEqualTo("1.50");
        }
    }

    @Test
    void anUndetectableDocumentSaysSoRatherThanGuessing() throws IOException {
        Path file = xml("<empty/>");

        assertThatThrownBy(() -> new XmlDataSource("Product", file).rowElement())
                .isInstanceOf(DataException.class)
                .hasMessageContaining("row");
    }

    @Test
    void eachOpenRereadsTheFile() throws IOException {
        XmlDataSource source = new XmlDataSource(
                "Product", xml("<products><product><id>1</id></product><product><id>2</id></product></products>"));

        try (RecordSet first = source.open()) {
            assertThat(column(first, "id")).containsExactly("1", "2");
        }
        try (RecordSet second = source.open()) {
            assertThat(column(second, "id")).containsExactly("1", "2");
        }
    }

    @Test
    void aStreamingSourceDoesNotClaimToKnowItsSize() throws IOException {
        try (RecordSet records =
                new XmlDataSource("Product", xml("<products><product><id>1</id></product></products>")).open()) {
            assertThat(records.size()).isEmpty();
        }
    }
}
