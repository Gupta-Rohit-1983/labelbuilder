package com.rohit.labelbuilder.data.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSnifferTest {

    @TempDir
    Path folder;

    private Path write(String name, String content, Charset charset) throws IOException {
        Path file = folder.resolve(name);
        Files.write(file, content.getBytes(charset));
        return file;
    }

    private Path write(String name, String content) throws IOException {
        return write(name, content, StandardCharsets.UTF_8);
    }

    @Test
    void detectsSemicolons() throws IOException {
        // A German export uses semicolons because the comma is its decimal separator.
        Path file = write("de.csv", "Code;Name;Preis\nA1;Schraube;1,50\nA2;Mutter;0,80\n");

        assertThat(CsvSniffer.sniff(file).delimiter()).isEqualTo(';');
    }

    @Test
    void detectsTabs() throws IOException {
        Path file = write("wms.txt", "Code\tQty\nA1\t5\nA2\t7\n");

        assertThat(CsvSniffer.sniff(file).delimiter()).isEqualTo('\t');
    }

    @Test
    void detectsPipes() throws IOException {
        Path file = write("feed.txt", "Code|Qty\nA1|5\nA2|7\n");

        assertThat(CsvSniffer.sniff(file).delimiter()).isEqualTo('|');
    }

    @Test
    void aSingleColumnFileKeepsTheDefaultRatherThanPickingUpStrayCharacters() throws IOException {
        Path file = write("codes.csv", "Code\nA-1\nA-2\nA-3\n");

        assertThat(CsvSniffer.sniff(file).delimiter()).isEqualTo(',');
    }

    @Test
    void consistencyBeatsFrequency() throws IOException {
        // The semicolons split every row into three; the commas inside the quoted addresses do not
        // split anything consistently, and a count-the-characters sniffer would pick them.
        Path file = write("mixed.csv", """
                Code;Name;Address
                A1;Bolt;"12 High Street, Leeds, West Yorkshire"
                A2;Nut;"4 Mill Lane, Bath"
                A3;Washer;"9 Dock Road, Hull, East Riding, HU1"
                """);

        assertThat(CsvSniffer.sniff(file).delimiter()).isEqualTo(';');
    }

    @Test
    void aByteOrderMarkIdentifiesUtf8() throws IOException {
        Path file = folder.resolve("bom.csv");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "Code,Name\nA1,Bolt\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);
        Files.write(file, bytes);

        CsvOptions options = CsvSniffer.sniff(file);

        assertThat(options.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(options.delimiter()).isEqualTo(',');
    }

    @Test
    void multiByteUtf8WithoutABomIsStillRecognised() throws IOException {
        Path file = write("accents.csv", "Code,Name\nA1,Clé à molette\n");

        assertThat(CsvSniffer.sniff(file).charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void bytesThatCannotBeUtf8FallBackToALegacyEncoding() throws IOException {
        // 0xE9 alone is "é" in windows-1252 and illegal in UTF-8. Decoding it as UTF-8 with
        // replacement would print a black diamond on the label instead of the accent.
        Path file = folder.resolve("legacy.csv");
        Files.write(file, new byte[] {'C', 'o', 'd', 'e', ',', 'N', 'o', 'm', '\n', 'A', '1', ',', (byte) 0xE9, '\n'});

        Charset detected = CsvSniffer.sniff(file).charset();

        assertThat(detected).isNotEqualTo(StandardCharsets.UTF_8);
        assertThat(new String(Files.readAllBytes(file), detected)).contains("é");
    }

    @Test
    void anApostropheInsideAWordIsNotAQuoteCharacter() throws IOException {
        Path file = write("names.csv", "Code,Name\nA1,O'Brien\nA2,D'Angelo\n");

        assertThat(CsvSniffer.sniff(file).quote()).isEqualTo('"');
    }

    @Test
    void singleQuotesAreDetectedWhenTheyOpenAField() throws IOException {
        Path file = write("quoted.csv", "Code,Name\n'A1','Bolt, long'\n'A2','Nut'\n");

        assertThat(CsvSniffer.sniff(file).quote()).isEqualTo('\'');
    }

    @Test
    void sniffingDoesNotChangeTheOtherDefaults() throws IOException {
        Path file = write("plain.csv", "Code,Name\nA1,Bolt\n");

        CsvOptions options = CsvSniffer.sniff(file);

        assertThat(options.headerRow()).isTrue();
        assertThat(options.sampleRows()).isEqualTo(TypeInference.DEFAULT_SAMPLE_ROWS);
    }

    @Test
    void countingFieldsIgnoresDelimitersInsideQuotes() {
        assertThat(CsvSniffer.countFields("a,b,c", ',', '"')).isEqualTo(3);
        assertThat(CsvSniffer.countFields("a,\"b,c\",d", ',', '"')).isEqualTo(3);
        assertThat(CsvSniffer.countFields("", ',', '"')).isEqualTo(1);
    }
}
