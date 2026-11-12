package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

class CsvDataSourceTest {

    @TempDir
    Path folder;

    private Path csv(String content) throws IOException {
        Path file = folder.resolve("data.csv");
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
    void readsTheHeaderAsFieldNamesAndTheRestAsRows() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Name,Qty
                A1,Bolt,5
                A2,Nut,7
                """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Name", "Qty");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "Name")).containsExactly("Bolt", "Nut");
        }
    }

    @Test
    void aByteOrderMarkDoesNotBecomePartOfTheFirstFieldName() throws IOException {
        // Excel's "CSV UTF-8" export writes a BOM. Left in the header the column is named
        // "﻿Code", so ${Product.Code} resolves to nothing while the panel still shows "Code".
        Path file = folder.resolve("bom.csv");
        Files.writeString(file, "﻿Code,Name\nA1,Bolt\n", StandardCharsets.UTF_8);
        CsvDataSource source = new CsvDataSource("Product", file);

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Name");
        try (RecordSet records = source.open()) {
            assertThat(records.next()).isTrue();
            assertThat(records.current().text("Code")).isEqualTo("A1");
        }
    }

    @Test
    void valuesAreHandedOverExactlyAsTheFileHoldsThem() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Sku,Price
                0012,1.50
                0013,0.80
                """));

        try (RecordSet records = source.open()) {
            records.next();
            // A reformatting source would print "12" and "1.5" — a wrong article number and a price
            // missing its pence.
            assertThat(records.current().text("Sku")).isEqualTo("0012");
            assertThat(records.current().text("Price")).isEqualTo("1.50");
        }
    }

    @Test
    void columnTypesAreInferredFromTheData() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Sku,Qty,Price,Due,Active
                0012,5,1.50,2026-10-26,yes
                0013,7,0.80,2026-11-02,no
                """));

        assertThat(source.fields())
                .extracting(FieldDefinition::name, FieldDefinition::type)
                .containsExactly(
                        tuple("Sku", FieldType.STRING),
                        tuple("Qty", FieldType.INTEGER),
                        tuple("Price", FieldType.DECIMAL),
                        tuple("Due", FieldType.DATE),
                        tuple("Active", FieldType.BOOLEAN));
    }

    @Test
    void aBlankHeaderCellGetsAPositionalName() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,,Qty
                A1,x,5
                """));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Column2", "Qty");
    }

    @Test
    void duplicateHeadersAreRenamedRatherThanCollapsed() throws IOException {
        // A report with two "Description" columns must not lose one of them silently.
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Description,Description
                A1,short,long
                """));

        assertThat(source.fields())
                .extracting(FieldDefinition::name)
                .containsExactly("Code", "Description", "Description_2");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Description")).isEqualTo("short");
            assertThat(records.current().text("Description_2")).isEqualTo("long");
        }
    }

    @Test
    void withoutAHeaderRowEveryRowIsData() throws IOException {
        CsvDataSource source =
                new CsvDataSource("Product", csv("A1,Bolt\nA2,Nut\n"), CsvOptions.DEFAULT.withHeaderRow(false));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Column1", "Column2");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "Column1")).containsExactly("A1", "A2");
        }
    }

    @Test
    void aShortRowLeavesTheColumnPresentButEmpty() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Name,Qty
                A1,Bolt,5
                A2
                """));

        try (RecordSet records = source.open()) {
            records.next();
            records.next();
            DataRecord sparse = records.current();
            // The exporter stopped writing cells; it did not delete the column.
            assertThat(sparse.has("Qty")).isTrue();
            assertThat(sparse.value("Qty")).isEmpty();
            assertThat(sparse.text("Qty")).isEmpty();
        }
    }

    @Test
    void aRowWiderThanTheHeaderKeepsItsExtraCells() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Name
                A1,Bolt,surprise
                """));

        try (RecordSet records = source.open()) {
            records.next();
            // Malformed, but dropping the cell would hide the fault instead of exposing it.
            assertThat(records.current().text("Column3")).isEqualTo("surprise");
        }
    }

    @Test
    void aColumnWithGapsIsNullable() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Batch
                A1,B9
                A2,
                """));

        assertThat(source.fields())
                .filteredOn(field -> field.name().equals("Batch"))
                .singleElement()
                .extracting(FieldDefinition::nullable)
                .isEqualTo(true);
    }

    @Test
    void eachOpenRereadsFromTheStart() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("Code\nA1\nA2\n"));

        try (RecordSet first = source.open()) {
            assertThat(column(first, "Code")).containsExactly("A1", "A2");
        }
        try (RecordSet second = source.open()) {
            assertThat(column(second, "Code")).containsExactly("A1", "A2");
        }
    }

    @Test
    void aStreamingSourceDoesNotClaimToKnowItsSize() throws IOException {
        try (RecordSet records = new CsvDataSource("Product", csv("Code\nA1\n")).open()) {
            // Answering would mean reading the file to the end first.
            assertThat(records.size()).isEmpty();
        }
    }

    @Test
    void theCursorHonoursTheSharedContract() throws IOException {
        RecordSet records = new CsvDataSource("Product", csv("Code\nA1\n")).open();

        assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);
        assertThat(records.next()).isTrue();
        assertThat(records.next()).isFalse();
        assertThat(records.next()).isFalse(); // exhausted stays exhausted
        assertThatThrownBy(records::current).isInstanceOf(IllegalStateException.class);

        records.close();
        records.close(); // harmless
        assertThatThrownBy(records::next).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEmptyFileOpensWithNoFieldsRatherThanFailing() throws IOException {
        // A nightly export can legitimately have nothing in it today; the template must still open.
        CsvDataSource source = new CsvDataSource("Product", csv(""));

        assertThat(source.fields()).isEmpty();
        try (RecordSet records = source.open()) {
            assertThat(records.next()).isFalse();
        }
    }

    @Test
    void aSniffedSourceReadsASemicolonFileWithoutBeingTold() throws IOException {
        Path file = folder.resolve("de.csv");
        Files.writeString(file, "Code;Name\nA1;Schraube\n", StandardCharsets.UTF_8);

        CsvDataSource source = CsvDataSource.sniffing("Product", file);

        assertThat(source.options().delimiter()).isEqualTo(';');
        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Name");
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Name")).isEqualTo("Schraube");
        }
    }

    @Test
    void aQuotedValueKeepsTheDelimitersInsideIt() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("""
                Code,Address
                A1,"12 High Street, Leeds"
                """));

        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Address")).isEqualTo("12 High Street, Leeds");
        }
    }

    @Test
    void surroundingSpacesArePreserved() throws IOException {
        // A column padded to a fixed width may be padded because the label expects it that way.
        CsvDataSource source = new CsvDataSource("Product", csv("Code\n  A1  \n"));

        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Code")).isEqualTo("  A1  ");
        }
    }

    @Test
    void theSchemaIsReadOnlyOnceHoweverOftenItIsAsked() throws IOException {
        CsvDataSource source = new CsvDataSource("Product", csv("Code\nA1\n"));

        List<FieldDefinition> first = source.fields();

        assertThat(source.fields()).isSameAs(first);
    }
}
