package com.rohit.labelbuilder.data.file;

import com.rohit.labelbuilder.data.AbstractRecordSet;
import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * An XML {@link DataSource} over a repeating element.
 *
 * <p>Read with StAX, pulling one event at a time, so the document is never built as a DOM — the same
 * streaming guarantee as the other file sources (risk R-06).
 *
 * <p>Within a row, attributes and child elements both become fields, and nesting flattens with dotted
 * names: {@code <item sku="A"><price><net>1.50</net></price></item>} yields {@code sku} and
 * {@code price.net}. A repeated child becomes {@code tag}, {@code tag_2}, … rather than overwriting,
 * because an element appearing twice is data, not a mistake to resolve by discarding half of it.
 *
 * <p><b>DTDs and external entities are refused.</b> An XML file naming a data source is often supplied
 * by whoever is sending the order, and the default StAX configuration would happily resolve an entity
 * pointing at {@code file:///etc/passwd} or an internal URL — the XXE attack. Since a label feed has
 * no legitimate need for either, both are switched off rather than filtered.
 */
public final class XmlDataSource implements DataSource {

    /** Far enough into the document to see a repetition, bounded so detection cannot read a whole feed. */
    private static final int DETECTION_EVENT_LIMIT = 10_000;

    private final String name;
    private final Path file;
    private final XmlOptions options;
    private volatile List<FieldDefinition> fields;
    private volatile String resolvedRowElement;

    public XmlDataSource(String name, Path file) {
        this(name, file, XmlOptions.DEFAULT);
    }

    public XmlDataSource(String name, Path file, XmlOptions options) {
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

    public XmlOptions options() {
        return options;
    }

    /** The element treated as a row, whether configured or detected. */
    public String rowElement() {
        String cached = resolvedRowElement;
        if (cached == null) {
            cached = options.rowElement().orElseGet(() -> detectRowElement(file));
            resolvedRowElement = cached;
        }
        return cached;
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
        return new XmlCursor(fields(), file, rowElement());
    }

    private List<FieldDefinition> scanSchema() {
        Map<String, ColumnScan> columns = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        int rows = 0;
        try (XmlRowReader reader = new XmlRowReader(file, rowElement())) {
            Map<String, String> row;
            while (rows < options.sampleRows() && (row = reader.nextRow()) != null) {
                rows++;
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    // No declared type: XML carries text, so the type has to be inferred from it.
                    columns.computeIfAbsent(entry.getKey(), f -> new ColumnScan())
                            .add(entry.getValue(), null);
                    occurrences.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        int sampled = rows;
        columns.forEach((field, scan) -> {
            if (occurrences.getOrDefault(field, 0) < sampled) {
                scan.addMissing();
            }
        });
        List<FieldDefinition> definitions = new ArrayList<>(columns.size());
        columns.forEach((field, scan) -> definitions.add(scan.toField(field)));
        return definitions;
    }

    /**
     * Finds the element that repeats, for a file whose shape nobody has described.
     *
     * <p>The shallowest element name that occurs more than once at its depth wins: in
     * {@code <orders><order>…</order><order>…</order></orders>} that is {@code order}, and in an
     * RSS-shaped {@code <rss><channel><item/><item/></channel></rss>} it is {@code item} rather than
     * the single {@code channel} wrapping it. A document with nothing repeating at all falls back to
     * the children of the root, so a one-row file reads as one row rather than as an error.
     *
     * <p>Detection cannot settle a genuinely ambiguous document. A file holding one order with three
     * lines has only {@code line} repeating, so that is what it returns — which is a reasonable
     * reading of it, just not the only one. That is why {@link XmlOptions#withRowElement} exists; the
     * import wizard should show what was detected and let it be overridden.
     */
    static String detectRowElement(Path file) {
        Map<Integer, Map<String, Integer>> countsByDepth = new TreeMap<>();
        try (XmlStream stream = XmlStream.open(file)) {
            XMLStreamReader reader = stream.reader();
            int depth = -1;
            int events = 0;
            while (reader.hasNext() && events++ < DETECTION_EVENT_LIMIT) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    countsByDepth
                            .computeIfAbsent(depth, d -> new LinkedHashMap<>())
                            .merge(reader.getLocalName(), 1, Integer::sum);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        } catch (XMLStreamException e) {
            throw new DataException("cannot read " + file + ": " + e.getMessage(), e);
        }

        for (Map<String, Integer> atDepth : countsByDepth.values()) {
            for (Map.Entry<String, Integer> entry : atDepth.entrySet()) {
                if (entry.getValue() > 1) {
                    return entry.getKey();
                }
            }
        }
        Map<String, Integer> rootChildren = countsByDepth.get(1);
        if (rootChildren != null && !rootChildren.isEmpty()) {
            return rootChildren.keySet().iterator().next();
        }
        throw new DataException("cannot tell which element is a row in " + file + "; name one explicitly");
    }

    /** A configured StAX reader over a file, closing both the reader and the stream. */
    private record XmlStream(XMLStreamReader reader, InputStream in, Path file) implements AutoCloseable {

        static XmlStream open(Path file) {
            InputStream in = null;
            try {
                in = Files.newInputStream(file);
                return new XmlStream(factory().createXMLStreamReader(in), in, file);
            } catch (IOException e) {
                closeQuietly(in);
                throw new DataException("cannot read " + file, e);
            } catch (XMLStreamException e) {
                closeQuietly(in);
                throw new DataException("cannot read " + file + ": " + e.getMessage(), e);
            }
        }

        private static XMLInputFactory factory() {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            // Coalescing so a value split across a CDATA boundary arrives as one string rather than two
            // events, which would otherwise truncate the field to its first fragment.
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            return factory;
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new DataException("cannot close " + file, e);
            } finally {
                closeQuietly(in);
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

    /**
     * Pulls one row at a time out of the repeating element.
     *
     * <p>Shared by the schema scan and the cursor so "what counts as a field" is decided once.
     */
    private static final class XmlRowReader implements AutoCloseable {

        private final Path file;
        private final String rowElement;
        private final XmlStream stream;
        private boolean done;

        XmlRowReader(Path file, String rowElement) {
            this.file = file;
            this.rowElement = rowElement;
            this.stream = XmlStream.open(file);
        }

        Map<String, String> nextRow() {
            if (done) {
                return null;
            }
            try {
                XMLStreamReader reader = stream.reader();
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT
                            && reader.getLocalName().equals(rowElement)) {
                        return readRow(reader);
                    }
                }
                done = true;
                return null;
            } catch (XMLStreamException e) {
                throw new DataException("cannot read " + file + ": " + e.getMessage(), e);
            }
        }

        /** Reads from the row element's start tag to its matching end tag. */
        private Map<String, String> readRow(XMLStreamReader reader) throws XMLStreamException {
            Map<String, String> values = new LinkedHashMap<>();
            putAttributes(reader, values, "");

            Deque<String> path = new ArrayDeque<>();
            // Parallel to path: whether that element has had a child, so a container's whitespace is
            // not mistaken for a value.
            Deque<Boolean> hasChild = new ArrayDeque<>();
            StringBuilder text = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        if (!hasChild.isEmpty()) {
                            hasChild.removeLast();
                            hasChild.addLast(Boolean.TRUE);
                        }
                        path.addLast(reader.getLocalName());
                        hasChild.addLast(Boolean.FALSE);
                        text.setLength(0);
                        putAttributes(reader, values, String.join(".", path));
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (path.isEmpty()) {
                            return values; // the row element's own end tag
                        }
                        boolean container = Boolean.TRUE.equals(hasChild.removeLast());
                        if (!container) {
                            values.put(
                                    FieldNames.unique(values, String.join(".", path)),
                                    text.toString().trim());
                        }
                        path.removeLast();
                        text.setLength(0);
                    }
                    default -> {
                        // comments, processing instructions and whitespace carry no fields
                    }
                }
            }
            done = true;
            return values;
        }

        private static void putAttributes(XMLStreamReader reader, Map<String, String> values, String prefix) {
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                String name = prefix.isEmpty()
                        ? reader.getAttributeLocalName(i)
                        : prefix + "." + reader.getAttributeLocalName(i);
                values.put(FieldNames.unique(values, name), reader.getAttributeValue(i));
            }
        }

        @Override
        public void close() {
            stream.close();
        }
    }

    /** Streams rows, padding each one out to the schema the source published. */
    private static final class XmlCursor extends AbstractRecordSet {

        private final List<String> names;
        private final XmlRowReader reader;

        XmlCursor(List<FieldDefinition> fields, Path file, String rowElement) {
            super(fields);
            this.names = fields.stream().map(FieldDefinition::name).toList();
            this.reader = new XmlRowReader(file, rowElement);
        }

        @Override
        protected DataRecord advance() {
            Map<String, String> row = reader.nextRow();
            if (row == null) {
                return null;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String field : names) {
                values.put(field, row.get(field));
            }
            row.forEach((field, value) -> {
                if (!values.containsKey(field)) {
                    // A field that appeared after the sample ended: carried, not dropped.
                    values.put(field, value);
                }
            });
            return new MapRecord(values);
        }

        @Override
        protected void release() {
            reader.close();
        }
    }
}
