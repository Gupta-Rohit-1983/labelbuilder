package com.rohit.labelbuilder.desktop.document;

import com.rohit.labelbuilder.core.command.Command;
import com.rohit.labelbuilder.core.command.CommandStack;
import com.rohit.labelbuilder.desktop.canvas.SelectionModel;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.springframework.stereotype.Component;

/**
 * The editor's live document: the current {@link LabelDocument}, its {@link CommandStack}, and the
 * selection. Everything that edits the label goes through {@link #execute(Command)}, so undo/redo is
 * automatic and no code path can mutate the document behind the history's back.
 *
 * <p>The document is exposed as an observable property; the canvas repaints from it rather than
 * holding its own copy. Selection is by <b>element id</b>, not by element instance — elements are
 * immutable and get replaced on every edit, so an instance-based selection would go stale after the
 * first drag.
 *
 * <p>One document per application for now; multi-document tabs arrive with file management
 * (Phase 14).
 */
@Component
public class DocumentSession {

    /** Default new-label stock until the New Label dialog lands (Phase 14). */
    private static final double DEFAULT_WIDTH_MM = 100;

    private static final double DEFAULT_HEIGHT_MM = 60;

    private final ReadOnlyObjectWrapper<LabelDocument> document = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper selectionCount = new ReadOnlyIntegerWrapper(0);
    private final SelectionModel<String> selection = new SelectionModel<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();

    private CommandStack stack;

    public DocumentSession() {
        open(LabelDocument.blank("doc-1", "Untitled", Stock.of(DEFAULT_WIDTH_MM, DEFAULT_HEIGHT_MM)));
    }

    /** Replace the open document, resetting history and selection. */
    public final void open(LabelDocument newDocument) {
        stack = new CommandStack(newDocument);
        stack.addChangeListener(this::syncFromStack);
        document.set(newDocument);
        selection.clear();
        fireSelectionChanged();
        syncFlags();
    }

    // ---- document ----------------------------------------------------------------------

    public ReadOnlyObjectProperty<LabelDocument> documentProperty() {
        return document.getReadOnlyProperty();
    }

    public LabelDocument document() {
        return document.get();
    }

    /** The id of the layer new elements are placed on (the first layer for now). */
    public String activeLayerId() {
        return document().layers().getFirst().id();
    }

    // ---- history -----------------------------------------------------------------------

    /** Apply an edit through the history. */
    public void execute(Command command) {
        stack.execute(command);
    }

    public void undo() {
        stack.undo();
    }

    public void redo() {
        stack.redo();
    }

    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    // ---- selection (by element id) ------------------------------------------------------

    public Set<String> selectedIds() {
        return selection.selected();
    }

    /** The element whose resize/rotate handles are shown, or {@code null}. */
    public String primarySelectedId() {
        return selection.primary();
    }

    public boolean isSelected(String elementId) {
        return selection.isSelected(elementId);
    }

    public int selectionSize() {
        return selection.size();
    }

    /**
     * How many elements are selected, observable — arrange actions bind their enablement to this
     * (align needs two, distribute needs three).
     */
    public ReadOnlyIntegerProperty selectionCountProperty() {
        return selectionCount.getReadOnlyProperty();
    }

    /** The selected elements, resolved against the current document (stale ids are skipped). */
    public List<LabelElement> selectedElements() {
        List<LabelElement> out = new ArrayList<>();
        for (String id : selection.selected()) {
            document().findElement(id).ifPresent(out::add);
        }
        return out;
    }

    public void select(String elementId) {
        selection.replaceWith(elementId);
        fireSelectionChanged();
    }

    public void toggleSelection(String elementId) {
        selection.toggle(elementId);
        fireSelectionChanged();
    }

    public void addToSelection(Collection<String> elementIds) {
        selection.addAll(elementIds);
        fireSelectionChanged();
    }

    public void clearSelection() {
        selection.clear();
        fireSelectionChanged();
    }

    /** Notified whenever the selection changes (the canvas repaints; the inspector reloads). */
    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    // ---- internals ---------------------------------------------------------------------

    private void syncFromStack() {
        document.set(stack.current());
        syncFlags();
        pruneSelection();
    }

    private void syncFlags() {
        canUndo.set(stack.canUndo());
        canRedo.set(stack.canRedo());
    }

    /** Drop ids that no longer exist — e.g. after undoing an add, or deleting a selected element. */
    private void pruneSelection() {
        List<String> stale = selection.selected().stream()
                .filter(id -> document().findElement(id).isEmpty())
                .toList();
        if (!stale.isEmpty()) {
            List<String> keep = selection.selected().stream()
                    .filter(id -> !stale.contains(id))
                    .toList();
            selection.clear();
            selection.addAll(keep);
            fireSelectionChanged();
        }
    }

    private void fireSelectionChanged() {
        selectionCount.set(selection.size());
        for (Runnable listener : selectionListeners) {
            listener.run();
        }
    }
}
