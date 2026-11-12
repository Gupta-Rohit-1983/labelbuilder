package com.rohit.labelbuilder.data.file;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guesses the encoding, delimiter and quote character of a delimited file (FR-DS-03).
 *
 * <p>"CSV" names a family, not a format. A German ERP exports semicolons because the comma is its
 * decimal separator; a warehouse system exports tabs; Excel on Windows writes windows-1252 unless
 * asked otherwise, and writes a byte-order mark when it is. Asking the operator to know which is
 * asking them to guess, so the shape is read off a sample of the bytes.
 *
 * <p>Only the head of the file is examined, so sniffing a million-row export costs the same as
 * sniffing ten rows.
 */
public final class CsvSniffer {

    private static final int SNIFF_BYTES = 64 * 1024;
    private static final int SNIFF_LINES = 20;

    /** Tried in this order, so two candidates that score identically resolve to the conventional one. */
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};

    private static final char[] QUOTES = {'"', '\''};

    private CsvSniffer() {}

    /** Reads a sample of {@code file} and returns options describing it. */
    public static CsvOptions sniff(Path file) {
        byte[] head = TextFiles.head(file, SNIFF_BYTES);
        Charset charset = detectCharset(head, head.length == SNIFF_BYTES);
        List<String> lines = sampleLines(head, charset);
        char quote = detectQuote(lines);
        return CsvOptions.DEFAULT.withCharset(charset).withQuote(quote).withDelimiter(detectDelimiter(lines, quote));
    }

    /**
     * The encoding of a file's leading bytes.
     *
     * <p>A byte-order mark is decisive. Without one, UTF-8 is tested strictly: because most byte
     * sequences are <em>not</em> valid UTF-8, a clean decode is strong evidence, and a failure is
     * proof. The fallback is windows-1252 rather than UTF-8-with-replacement, since a legacy export
     * is overwhelmingly likelier than a corrupt UTF-8 one, and replacement characters would turn an
     * accented product name into mojibake on the label instead of failing loudly.
     *
     * @param truncated whether {@code head} was cut short by the sample limit, in which case it may
     *     end mid-character and the last few bytes may be ignored. When the sample <em>is</em> the
     *     file, no such allowance is made — otherwise a short file whose only bad byte happens to be
     *     near the end would pass as UTF-8 for lack of anything after it.
     */
    static Charset detectCharset(byte[] head, boolean truncated) {
        if (startsWith(head, 0xEF, 0xBB, 0xBF)) {
            return StandardCharsets.UTF_8;
        }
        if (startsWith(head, 0xFF, 0xFE)) {
            return StandardCharsets.UTF_16LE;
        }
        if (startsWith(head, 0xFE, 0xFF)) {
            return StandardCharsets.UTF_16BE;
        }
        return isValidUtf8(head, truncated) ? StandardCharsets.UTF_8 : legacyCharset();
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidUtf8(byte[] bytes, boolean truncated) {
        // A truncated sample is cut at a byte boundary, so it may end mid-character. A UTF-8 character
        // is at most four bytes, so if dropping up to three trailing bytes makes it decode, the file is
        // UTF-8 and only the sample was cut.
        if (bytes.length == 0) {
            return true; // an empty export is not a legacy-encoded one
        }
        int allowance = truncated ? 3 : 0;
        for (int dropped = 0; dropped <= allowance && dropped < bytes.length; dropped++) {
            if (decodesAsUtf8(bytes, bytes.length - dropped)) {
                return true;
            }
        }
        return false;
    }

    private static boolean decodesAsUtf8(byte[] bytes, int length) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static Charset legacyCharset() {
        return Charset.isSupported("windows-1252") ? Charset.forName("windows-1252") : StandardCharsets.ISO_8859_1;
    }

    private static List<String> sampleLines(byte[] head, Charset charset) {
        String text = new String(head, charset).replace(String.valueOf(TextFiles.BYTE_ORDER_MARK), "");
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r?\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
            if (lines.size() == SNIFF_LINES) {
                break;
            }
        }
        return lines;
    }

    /**
     * The quote character, recognised only where a quote can legally open a field.
     *
     * <p>Counting bare apostrophes would make {@code O'Brien} look like quoting and then swallow half
     * the file as one field, so a candidate only counts at the start of a line or straight after a
     * delimiter.
     */
    static char detectQuote(List<String> lines) {
        for (char candidate : QUOTES) {
            for (String line : lines) {
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == candidate && (i == 0 || isDelimiterCandidate(line.charAt(i - 1)))) {
                        return candidate;
                    }
                }
            }
        }
        return CsvOptions.DEFAULT.quote();
    }

    private static boolean isDelimiterCandidate(char c) {
        for (char delimiter : DELIMITERS) {
            if (c == delimiter) {
                return true;
            }
        }
        return false;
    }

    /**
     * The delimiter that splits the sample most consistently.
     *
     * <p>The test is agreement, not frequency: the right delimiter yields the same field count on
     * every row, while a character that merely happens to appear often — a comma inside quoted
     * addresses, say — yields a different count each time. Candidates that never produce more than one
     * field are rejected outright, so a single-column file keeps the default rather than picking up
     * whichever stray character appeared.
     */
    static char detectDelimiter(List<String> lines, char quote) {
        char best = CsvOptions.DEFAULT.delimiter();
        int bestAgreement = 0;
        int bestFields = 0;
        for (char candidate : DELIMITERS) {
            Map<Integer, Integer> countsByFields = new HashMap<>();
            for (String line : lines) {
                countsByFields.merge(countFields(line, candidate, quote), 1, Integer::sum);
            }
            for (Map.Entry<Integer, Integer> entry : countsByFields.entrySet()) {
                int fields = entry.getKey();
                int agreement = entry.getValue();
                if (fields < 2) {
                    continue;
                }
                if (agreement > bestAgreement || (agreement == bestAgreement && fields > bestFields)) {
                    best = candidate;
                    bestAgreement = agreement;
                    bestFields = fields;
                }
            }
        }
        return best;
    }

    /** How many fields {@code line} would split into, ignoring delimiters inside quotes. */
    static int countFields(String line, char delimiter, char quote) {
        int fields = 1;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == quote) {
                quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                fields++;
            }
        }
        return fields;
    }
}
