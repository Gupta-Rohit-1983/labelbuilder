package com.rohit.labelbuilder.data.file;

import java.util.Objects;
import java.util.Optional;

/**
 * How to read rows out of an XML document.
 *
 * @param rowElement the local name of the element that repeats, one per row; empty to detect it
 * @param sampleRows how many rows to read when working out the field list
 */
public record XmlOptions(Optional<String> rowElement, int sampleRows) {

    public static final XmlOptions DEFAULT = new XmlOptions(Optional.empty(), TypeInference.DEFAULT_SAMPLE_ROWS);

    public XmlOptions {
        Objects.requireNonNull(rowElement, "rowElement");
        if (rowElement.isPresent() && rowElement.get().isBlank()) {
            throw new IllegalArgumentException("rowElement must not be blank");
        }
        if (sampleRows < 1) {
            throw new IllegalArgumentException("sampleRows must be at least 1, was " + sampleRows);
        }
    }

    public XmlOptions withRowElement(String rowElement) {
        return new XmlOptions(Optional.of(rowElement), sampleRows);
    }

    public XmlOptions withSampleRows(int sampleRows) {
        return new XmlOptions(rowElement, sampleRows);
    }
}
