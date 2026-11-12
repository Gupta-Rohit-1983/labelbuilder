package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.DataException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opening text files the way a data importer has to, rather than the way the JDK defaults to. */
final class TextFiles {

    /** The character a UTF-8 byte-order mark decodes to. */
    static final char BYTE_ORDER_MARK = '﻿';

    private TextFiles() {}

    /**
     * A reader over {@code file} with any leading byte-order mark consumed.
     *
     * <p>The JDK's UTF-16 charsets eat their own BOM; the UTF-8 charset does not, and hands the mark
     * over as an invisible first character. Left in place it becomes part of the first header cell, so
     * the column reads as {@code Code} on screen while {@code ${Product.Code}} silently resolves to
     * nothing — exactly the kind of fault that gets diagnosed as "the data binding is broken". Excel
     * writes that BOM on every "CSV UTF-8" export, so this is the common case, not the exotic one.
     */
    static Reader reader(Path file, Charset charset) {
        InputStream in = null;
        try {
            in = Files.newInputStream(file);
            PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(in, charset)), 1);
            int first = reader.read();
            if (first != -1 && first != BYTE_ORDER_MARK) {
                reader.unread(first);
            }
            return reader;
        } catch (IOException e) {
            closeQuietly(in);
            throw new DataException("cannot read " + file, e);
        } catch (RuntimeException e) {
            closeQuietly(in);
            throw e;
        }
    }

    /** The first {@code maxBytes} of a file, for sniffing its encoding and shape. */
    static byte[] head(Path file, int maxBytes) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new DataException("cannot read " + file, e);
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException suppressed) {
            // the original failure is what the caller needs to see
        }
    }
}
