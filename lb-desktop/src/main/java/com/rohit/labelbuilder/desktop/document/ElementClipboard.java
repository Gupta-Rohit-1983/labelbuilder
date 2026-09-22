package com.rohit.labelbuilder.desktop.document;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.springframework.stereotype.Component;

/**
 * Holds cut or copied elements for pasting (Phase 8c).
 *
 * <p>This is an <b>in-application</b> clipboard: it keeps the model objects themselves, so a paste is
 * exact and needs no serialisation round-trip. Pasting between two running copies of LabelBuilder
 * would need the elements on the system clipboard as {@code .lbl} JSON; that waits for file
 * management (Phase 14), which is where the fragment format belongs.
 *
 * <p>Contents are immutable elements, so holding them is safe even as the document they came from
 * keeps changing — a copied element cannot be mutated out from under the clipboard.
 */
@Component
public class ElementClipboard {

    private final ReadOnlyBooleanWrapper hasContent = new ReadOnlyBooleanWrapper(false);
    private List<LabelElement> contents = List.of();

    /** Replace the clipboard contents. */
    public void set(List<LabelElement> elements) {
        contents = List.copyOf(elements);
        hasContent.set(!contents.isEmpty());
    }

    /** The current contents, in document order; empty when nothing has been copied. */
    public List<LabelElement> contents() {
        return contents;
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /** Observable, so Paste can grey out until there is something to paste. */
    public ReadOnlyBooleanProperty hasContentProperty() {
        return hasContent.getReadOnlyProperty();
    }
}
