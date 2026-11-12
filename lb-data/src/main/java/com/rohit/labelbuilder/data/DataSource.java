package com.rohit.labelbuilder.data;

import java.util.List;
import java.util.Map;

/**
 * A source of rows to merge onto labels (architecture §5.3): manual prompts, CSV, Excel, JDBC, REST.
 *
 * <p>A source is a <b>factory for cursors</b>, not a container of data. {@link #open} may be called
 * more than once — a print run re-reads the data rather than holding it — and each call yields a
 * fresh {@link RecordSet} the caller owns and must close.
 *
 * <p>Sources never carry credentials. A template stores which configuration to use, and the secret is
 * resolved from the client store or the server at open time (risk R-08, lbl-format.md §5).
 */
public interface DataSource extends AutoCloseable {

    /** The name the template binds to, e.g. {@code Product} in {@code ${Product.Code}}. */
    String name();

    /**
     * The field schema. Available without opening the source where possible, so a template can be
     * designed against a cached schema while the source itself is unreachable.
     */
    List<FieldDefinition> fields();

    /**
     * Open a cursor over the rows.
     *
     * @param parameters values for any declared query parameters or prompts; unknown keys are ignored
     * @throws DataException if the source cannot be reached or the parameters are unusable
     */
    RecordSet open(Map<String, Object> parameters);

    /** Open with no parameters. */
    default RecordSet open() {
        return open(Map.of());
    }

    /** Releases anything the source itself holds, such as a pooled connection. */
    @Override
    default void close() {
        // sources are usually stateless; those that are not override this
    }
}
