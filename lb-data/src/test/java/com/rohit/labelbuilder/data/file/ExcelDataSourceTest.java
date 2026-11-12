package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelDataSourceTest {

    @TempDir
    Path folder;

    /** Builds a workbook and saves it, having evaluated its formulas the way Excel would. */
    private Path workbook(Consumer<XSSFWorkbook> build) throws IOException {
        Path file = folder.resolve("book.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            build.accept(wb);
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    private static void row(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean flag) {
                cell.setCellValue(flag);
            } else if (value != null) {
                cell.setCellValue(value.toString());
            }
        }
    }

    private static CellStyle format(XSSFWorkbook wb, String pattern) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat(pattern));
        return style;
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
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Code", "Name", "Qty");
            row(sheet, 1, "A1", "Bolt", 5);
            row(sheet, 2, "A2", "Nut", 7);
        });
        ExcelDataSource source = new ExcelDataSource("Product", file);

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Name", "Qty");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "Name")).containsExactly("Bolt", "Nut");
        }
    }

    @Test
    void aFormattedNumberKeepsTheTrailingZeroTheOperatorSaw() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Prices");
            row(sheet, 0, "Code", "Price");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("A1");
            Cell price = data.createCell(1);
            price.setCellValue(1.5);
            price.setCellStyle(format(wb, "0.00"));
        });

        try (RecordSet records = new ExcelDataSource("Product", file).open()) {
            records.next();
            // The cell reads 1.50 on screen. Printing 1.5 on a price label is a defect somebody has to
            // explain to a customer.
            assertThat(records.current().text("Price")).isEqualTo("1.50");
        }
    }

    @Test
    void aFormulaIsReadFromItsCachedResultRatherThanRecalculated() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Prices");
            row(sheet, 0, "Net", "Pair");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(10.0);
            data.createCell(1).setCellFormula("A2*2");
        });

        try (RecordSet records = new ExcelDataSource("Product", file).open()) {
            records.next();
            // Without the cached value this would read "=A2*2" and print the formula on the label.
            assertThat(records.current().text("Pair")).isEqualTo("20");
        }
    }

    @Test
    void aDateCellIsTypedAsADateAndRenderedAsExcelShowsIt() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Batches");
            row(sheet, 0, "Code", "Expiry");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("A1");
            Cell expiry = data.createCell(1);
            expiry.setCellValue(LocalDate.of(2026, 10, 26));
            expiry.setCellStyle(format(wb, "yyyy-mm-dd"));
        });
        ExcelDataSource source = new ExcelDataSource("Product", file);

        assertThat(source.fields())
                .filteredOn(field -> field.name().equals("Expiry"))
                .singleElement()
                .extracting(FieldDefinition::type)
                .isEqualTo(FieldType.DATE);
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Expiry")).isEqualTo("2026-10-26");
        }
    }

    @Test
    void aTextCellKeepsItsLeadingZerosAndStaysText() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Sku");
            row(sheet, 1, "0012");
        });
        ExcelDataSource source = new ExcelDataSource("Product", file);

        // The operator typed the zeros on purpose; the cell is text and so is the column.
        assertThat(source.fields())
                .singleElement()
                .extracting(FieldDefinition::type)
                .isEqualTo(FieldType.STRING);
        try (RecordSet records = source.open()) {
            records.next();
            assertThat(records.current().text("Sku")).isEqualTo("0012");
        }
    }

    @Test
    void aBooleanCellIsTypedAsABoolean() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Code", "Active");
            row(sheet, 1, "A1", Boolean.TRUE);
            row(sheet, 2, "A2", Boolean.FALSE);
        });
        ExcelDataSource source = new ExcelDataSource("Product", file);

        assertThat(source.fields())
                .filteredOn(field -> field.name().equals("Active"))
                .singleElement()
                .extracting(FieldDefinition::type)
                .isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void aSheetCanBeChosenByName() throws IOException {
        Path file = workbook(wb -> {
            row(wb.createSheet("Summary"), 0, "Total", 99);
            Sheet products = wb.createSheet("Products");
            row(products, 0, "Code");
            row(products, 1, "A1");
        });

        ExcelDataSource source = new ExcelDataSource("Product", file, ExcelOptions.DEFAULT.withSheetName("Products"));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code");
    }

    @Test
    void theFirstSheetIsUsedWhenNoneIsNamed() throws IOException {
        Path file = workbook(wb -> {
            row(wb.createSheet("First"), 0, "A");
            row(wb.createSheet("Second"), 0, "B");
        });

        assertThat(new ExcelDataSource("Product", file).fields())
                .extracting(FieldDefinition::name)
                .containsExactly("A");
    }

    @Test
    void namingASheetThatIsNotThereFailsClearly() throws IOException {
        Path file = workbook(wb -> row(wb.createSheet("Products"), 0, "Code"));

        assertThatThrownBy(
                        () -> new ExcelDataSource("Product", file, ExcelOptions.DEFAULT.withSheetName("Nope")).fields())
                .isInstanceOf(DataException.class)
                .hasMessageContaining("Nope");
    }

    @Test
    void aCellRangeLimitsWhatIsRead() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Report");
            row(sheet, 0, "Monthly report", null, null);
            row(sheet, 1, null, null, null);
            row(sheet, 2, "skip", "Code", "Qty");
            row(sheet, 3, "skip", "A1", 5);
            row(sheet, 4, "skip", "A2", 7);
        });

        ExcelDataSource source = new ExcelDataSource("Product", file, ExcelOptions.DEFAULT.withRange("B3:C5"));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Qty");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "Code")).containsExactly("A1", "A2");
        }
    }

    @Test
    void aBlankRowInsideTheDataIsSkippedRatherThanMergedAsAnEmptyLabel() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Code");
            row(sheet, 1, "A1");
            sheet.createRow(2);
            row(sheet, 3, "A2");
        });

        try (RecordSet records = new ExcelDataSource("Product", file).open()) {
            assertThat(column(records, "Code")).containsExactly("A1", "A2");
        }
    }

    @Test
    void withoutAHeaderRowColumnsArePositional() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "A1", "Bolt");
        });

        ExcelDataSource source = new ExcelDataSource("Product", file, ExcelOptions.DEFAULT.withHeaderRow(false));

        assertThat(source.fields()).extracting(FieldDefinition::name).containsExactly("Column1", "Column2");
        try (RecordSet records = source.open()) {
            assertThat(column(records, "Column1")).containsExactly("A1");
        }
    }

    @Test
    void sheetNamesAreAvailableForAnImportWizardToOffer() throws IOException {
        Path file = workbook(wb -> {
            wb.createSheet("Products");
            wb.createSheet("Prices");
        });

        assertThat(new ExcelDataSource("Product", file).sheetNames()).containsExactly("Products", "Prices");
    }

    @Test
    void eachOpenRereadsTheWorkbook() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Code");
            row(sheet, 1, "A1");
        });
        ExcelDataSource source = new ExcelDataSource("Product", file);

        try (RecordSet first = source.open()) {
            assertThat(column(first, "Code")).containsExactly("A1");
        }
        try (RecordSet second = source.open()) {
            assertThat(column(second, "Code")).containsExactly("A1");
        }
    }

    @Test
    void theWorkbookIsReleasedWhenTheCursorCloses() throws IOException {
        Path file = workbook(wb -> {
            Sheet sheet = wb.createSheet("Products");
            row(sheet, 0, "Code");
            row(sheet, 1, "A1");
        });

        RecordSet records = new ExcelDataSource("Product", file).open();
        records.next();
        records.close();

        // Closing twice must not try to release twice, and the handle must be gone: on Windows an
        // unreleased workbook would keep the file locked against the next import.
        records.close();
        assertThatThrownBy(records::next).isInstanceOf(IllegalStateException.class);
        assertThat(Files.deleteIfExists(file)).isTrue();
    }
}
