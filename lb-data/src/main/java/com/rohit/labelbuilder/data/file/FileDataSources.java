package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the right file {@link DataSource} for a path.
 *
 * <p>One place decides which extension means which reader, so the import wizard, the automation
 * watcher (Phase 20) and a template reopening its source all agree. Delimited files are sniffed
 * rather than assumed, since {@link CsvSniffer} can tell a tab-separated file from a comma-separated
 * one whatever it is called.
 */
public final class FileDataSources {

    private static final List<String> DELIMITED = List.of("csv", "tsv", "tab", "txt");
    private static final List<String> WORKBOOK = List.of("xlsx", "xlsm", "xls");
    private static final List<String> JSON = List.of("json");
    private static final List<String> XML = List.of("xml");

    private FileDataSources() {}

    /** The extensions this factory recognises, lower-case and without the dot. */
    public static List<String> supportedExtensions() {
        return List.of("csv", "tsv", "tab", "txt", "xlsx", "xlsm", "xls", "json", "xml");
    }

    public static boolean isSupported(Path file) {
        return supportedExtensions().contains(extension(file));
    }

    /**
     * A source reading {@code file}, with its format taken from the extension and its details from the
     * file itself.
     *
     * @throws DataException if the extension is not one this build can read
     */
    public static DataSource forFile(String name, Path file) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(file, "file");
        String extension = extension(file);
        if (DELIMITED.contains(extension)) {
            return CsvDataSource.sniffing(name, file);
        }
        if (WORKBOOK.contains(extension)) {
            return new ExcelDataSource(name, file);
        }
        if (JSON.contains(extension)) {
            return new JsonDataSource(name, file);
        }
        if (XML.contains(extension)) {
            return new XmlDataSource(name, file);
        }
        throw new DataException("no reader for '" + extension + "' files (" + file + "); supported: "
                + String.join(", ", supportedExtensions()));
    }

    static String extension(Path file) {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
