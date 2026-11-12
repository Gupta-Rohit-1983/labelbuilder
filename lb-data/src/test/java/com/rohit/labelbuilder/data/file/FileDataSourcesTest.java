package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDataSourcesTest {

    @TempDir
    Path folder;

    private Path write(String name, String content) throws IOException {
        Path file = folder.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void csvFilesGetADelimitedReader() throws IOException {
        DataSource source = FileDataSources.forFile("Product", write("p.csv", "Code,Name\nA1,Bolt\n"));

        assertThat(source).isInstanceOf(CsvDataSource.class);
        assertThat(source.name()).isEqualTo("Product");
    }

    @Test
    void aTabSeparatedFileIsSniffedRatherThanAssumedToBeCommaSeparated() throws IOException {
        // The extension says ".txt"; only the content says what splits it.
        DataSource source = FileDataSources.forFile("Product", write("p.txt", "Code\tName\nA1\tBolt\n"));

        assertThat(source).isInstanceOf(CsvDataSource.class);
        assertThat(((CsvDataSource) source).options().delimiter()).isEqualTo('\t');
    }

    @Test
    void workbooksGetTheExcelReader() throws IOException {
        Path file = folder.resolve("p.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Products").createRow(0).createCell(0).setCellValue("Code");
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        assertThat(FileDataSources.forFile("Product", file)).isInstanceOf(ExcelDataSource.class);
    }

    @Test
    void jsonAndXmlGetTheirOwnReaders() throws IOException {
        assertThat(FileDataSources.forFile("Product", write("p.json", "[]"))).isInstanceOf(JsonDataSource.class);
        assertThat(FileDataSources.forFile("Product", write("p.xml", "<a><b/><b/></a>")))
                .isInstanceOf(XmlDataSource.class);
    }

    @Test
    void theExtensionIsMatchedWithoutRegardToCase() throws IOException {
        assertThat(FileDataSources.forFile("Product", write("P.CSV", "Code\nA1\n")))
                .isInstanceOf(CsvDataSource.class);
    }

    @Test
    void anUnreadableExtensionSaysWhatItCanRead() throws IOException {
        Path file = write("p.pdf", "not data");

        assertThatThrownBy(() -> FileDataSources.forFile("Product", file))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("pdf")
                .hasMessageContaining("csv");
    }

    @Test
    void supportSoFarCanBeCheckedWithoutOpeningTheFile() {
        assertThat(FileDataSources.isSupported(Path.of("x", "p.csv"))).isTrue();
        assertThat(FileDataSources.isSupported(Path.of("p.XLSX"))).isTrue();
        assertThat(FileDataSources.isSupported(Path.of("p.pdf"))).isFalse();
        assertThat(FileDataSources.isSupported(Path.of("noextension"))).isFalse();
    }

    @Test
    void everySupportedExtensionActuallyResolvesToAReader() {
        // Keeps the advertised list and the dispatch from drifting apart.
        for (String extension : FileDataSources.supportedExtensions()) {
            assertThat(FileDataSources.isSupported(Path.of("p." + extension)))
                    .as("extension %s", extension)
                    .isTrue();
        }
    }
}
