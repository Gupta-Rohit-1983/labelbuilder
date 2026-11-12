package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.AbstractRecordSet;
import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * An {@code .xlsx}/{@code .xls} {@link DataSource} over a sheet and optional cell range (FR-DS-04).
 *
 * <p><b>Cells are read as the operator last saw them.</b> A cell formatted {@code 0.00} holding 1.5
 * yields {@code "1.50"}, because a price on a label with the trailing zero dropped is a defect
 * somebody has to explain to a customer. The number format is applied exactly as Excel applies it,
 * and the declared {@link FieldType} comes from the cell's own type rather than from guessing at text.
 *
 * <p><b>Formulas are read from their cached results, not recalculated.</b> The workbook already stores
 * what Excel computed, so using it matches the screen the operator approved and cannot fail on a
 * function this library has not implemented — recalculating could do neither. The cost is that a file
 * written by a generator that stores no cached values reads as empty in those cells.
 *
 * <p><b>Known limit.</b> The workbook is opened read-only but is still parsed into memory by POI,
 * because the format is a ZIP of XML with a shared string table rather than a line-oriented stream.
 * The cursor hands out one row at a time and never materialises a row list, so the {@code RecordSet}
 * contract holds, but a very large workbook is bounded by the file rather than by the cursor. A true
 * SAX-streaming reader for {@code .xlsx} belongs with the rest of the large-batch work in Phase 18
 * (FR-DS-02); CSV is the answer in the meantime, and the import wizard should say so.
 */
public final class ExcelDataSource implements DataSource {

    private final String name;
    private final Path file;
    private final ExcelOptions options;
    private volatile List<FieldDefinition> fields;

    public ExcelDataSource(String name, Path file) {
        this(name, file, ExcelOptions.DEFAULT);
    }

    public ExcelDataSource(String name, Path file, ExcelOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.file = Objects.requireNonNull(file, "file");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public String name() {
        return name;
    }

    public Path file() {
        return file;
    }

    public ExcelOptions options() {
        return options;
    }

    /** The names of the sheets in the workbook, for an import wizard to offer. */
    public List<String> sheetNames() {
        try (Workbook workbook = open(file)) {
            List<String> names = new ArrayList<>(workbook.getNumberOfSheets());
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetAt(i).getSheetName());
            }
            return names;
        } catch (IOException e) {
            throw new DataException("cannot read " + file, e);
        }
    }

    @Override
    public List<FieldDefinition> fields() {
        List<FieldDefinition> cached = fields;
        if (cached == null) {
            cached = scanSchema();
            fields = cached;
        }
        return cached;
    }

    @Override
    public RecordSet open(Map<String, Object> parameters) {
        return new ExcelCursor(fields(), file, options);
    }

    private List<FieldDefinition> scanSchema() {
        try (Workbook workbook = open(file)) {
            Sheet sheet = sheet(workbook, options);
            DataFormatter formatter = new DataFormatter();
            CellRange range = options.range();
            int lastRow = range.effectiveLastRow(sheet.getLastRowNum());

            List<String> headerCells = List.of();
            List<List<CellValue>> sample = new ArrayList<>();
            boolean headerPending = options.headerRow();
            for (int rowIndex = range.firstRow(); rowIndex <= lastRow; rowIndex++) {
                List<CellValue> cells = readRow(sheet.getRow(rowIndex), range, formatter);
                if (isBlank(cells)) {
                    continue;
                }
                if (headerPending) {
                    headerPending = false;
                    headerCells = cells.stream().map(CellValue::text).toList();
                    continue;
                }
                sample.add(cells);
                if (sample.size() >= options.sampleRows()) {
                    break;
                }
            }

            int width = headerCells.size();
            for (List<CellValue> row : sample) {
                width = Math.max(width, row.size());
            }
            if (width == 0) {
                return List.of();
            }

            List<String> padded = new ArrayList<>(headerCells);
            while (padded.size() < width) {
                padded.add(null);
            }
            List<String> names =
                    options.headerRow() ? FieldNames.headerNames(padded) : FieldNames.positionalNames(width);

            List<ColumnScan> columns = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                columns.add(new ColumnScan());
            }
            for (List<CellValue> row : sample) {
                for (int i = 0; i < width; i++) {
                    if (i < row.size()) {
                        columns.get(i).add(row.get(i).text(), row.get(i).declaredType());
                    } else {
                        columns.get(i).addMissing();
                    }
                }
            }

            List<FieldDefinition> definitions = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                definitions.add(columns.get(i).toField(names.get(i)));
            }
            return definitions;
        } catch (IOException e) {
            throw new DataException("cannot read " + file, e);
        }
    }

    private static Workbook open(Path file) {
        try {
            return WorkbookFactory.create(file.toFile(), null, true);
        } catch (EncryptedDocumentException e) {
            throw new DataException(file + " is password-protected", e);
        } catch (IOException e) {
            throw new DataException("cannot read " + file, e);
        }
    }

    private static Sheet sheet(Workbook workbook, ExcelOptions options) {
        if (options.sheetName().isPresent()) {
            String wanted = options.sheetName().get();
            Sheet sheet = workbook.getSheet(wanted);
            if (sheet == null) {
                throw new DataException("no sheet named '" + wanted + "' in the workbook");
            }
            return sheet;
        }
        if (options.sheetIndex() >= workbook.getNumberOfSheets()) {
            throw new DataException("the workbook has only " + workbook.getNumberOfSheets()
                    + " sheet(s), so there is no sheet " + (options.sheetIndex() + 1));
        }
        return workbook.getSheetAt(options.sheetIndex());
    }

    /** The cells of one row inside the range, left-padded to the range's first column. */
    private static List<CellValue> readRow(Row row, CellRange range, DataFormatter formatter) {
        if (row == null) {
            return List.of();
        }
        int lastColumn = range.lastColumn().isPresent()
                ? range.lastColumn().getAsInt()
                : Math.max(row.getLastCellNum() - 1, range.firstColumn() - 1);
        List<CellValue> cells = new ArrayList<>();
        for (int column = range.firstColumn(); column <= lastColumn; column++) {
            cells.add(readCell(row.getCell(column), formatter));
        }
        return cells;
    }

    private static CellValue readCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return CellValue.EMPTY;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> numeric(cell, formatter);
            case STRING -> {
                String text = cell.getStringCellValue();
                // A text cell is text on purpose: "0012" is an article number the operator typed with
                // its zeros, so only a date is allowed to override STRING here.
                yield new CellValue(text, TypeInference.temporalType(text).orElse(FieldType.STRING));
            }
            case BOOLEAN -> new CellValue(cell.getBooleanCellValue() ? "TRUE" : "FALSE", FieldType.BOOLEAN);
            case ERROR -> new CellValue(errorText(cell), null);
            default -> CellValue.EMPTY;
        };
    }

    private static CellValue numeric(Cell cell, DataFormatter formatter) {
        double value = cell.getNumericCellValue();
        CellStyle style = cell.getCellStyle();
        String pattern = style.getDataFormatString() == null ? "General" : style.getDataFormatString();
        String text = formatter.formatRawCellContents(value, style.getDataFormat(), pattern);
        if (DateUtil.isValidExcelDate(value) && DateUtil.isADateFormat(style.getDataFormat(), pattern)) {
            // Excel stores a date as days since 1900 and a time as the fraction of a day, so the serial
            // number itself says which of the three temporal types the cell is.
            FieldType temporal =
                    value < 1.0 ? FieldType.TIME : (value == Math.floor(value) ? FieldType.DATE : FieldType.TIMESTAMP);
            return new CellValue(text, temporal);
        }
        return new CellValue(text, TypeInference.inferType(List.of(text)));
    }

    private static String errorText(Cell cell) {
        try {
            return FormulaError.forInt(cell.getErrorCellValue()).getString();
        } catch (IllegalArgumentException e) {
            return "#ERROR!";
        }
    }

    private static boolean isBlank(List<CellValue> cells) {
        return cells.stream()
                .allMatch(cell -> cell.text() == null || cell.text().isBlank());
    }

    /** One cell: the text Excel shows, and the type the cell itself declares. */
    private record CellValue(String text, FieldType declaredType) {
        static final CellValue EMPTY = new CellValue("", null);
    }

    /** Walks the rows of one sheet, holding the workbook open until closed. */
    private static final class ExcelCursor extends AbstractRecordSet {

        private final Path file;
        private final List<String> names;
        private final Workbook workbook;
        private final Sheet sheet;
        private final CellRange range;
        private final DataFormatter formatter = new DataFormatter();
        private final int lastRow;
        private int rowIndex;
        private boolean headerPending;

        ExcelCursor(List<FieldDefinition> fields, Path file, ExcelOptions options) {
            super(fields);
            this.file = file;
            this.names = fields.stream().map(FieldDefinition::name).toList();
            this.workbook = ExcelDataSource.open(file);
            try {
                this.sheet = sheet(workbook, options);
                this.range = options.range();
                this.lastRow = range.effectiveLastRow(sheet.getLastRowNum());
                this.rowIndex = range.firstRow();
                this.headerPending = options.headerRow();
            } catch (RuntimeException e) {
                release();
                throw e;
            }
        }

        @Override
        protected DataRecord advance() {
            while (rowIndex <= lastRow) {
                List<CellValue> cells = readRow(sheet.getRow(rowIndex++), range, formatter);
                if (isBlank(cells)) {
                    // A spacer row inside the data is skipped rather than merged as an empty label.
                    continue;
                }
                if (headerPending) {
                    headerPending = false;
                    continue;
                }
                return toRecord(cells);
            }
            return null;
        }

        private DataRecord toRecord(List<CellValue> cells) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) {
                values.put(names.get(i), i < cells.size() ? cells.get(i).text() : null);
            }
            for (int i = names.size(); i < cells.size(); i++) {
                values.put(
                        FieldNames.unique(values, FieldNames.column(i)),
                        cells.get(i).text());
            }
            return new MapRecord(values);
        }

        @Override
        protected void release() {
            try {
                workbook.close();
            } catch (IOException e) {
                throw new DataException("cannot close " + file, e);
            }
        }
    }
}
