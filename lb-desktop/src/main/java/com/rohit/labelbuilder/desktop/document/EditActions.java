package com.rohit.labelbuilder.desktop.document;

import com.rohit.labelbuilder.core.edit.Align;
import com.rohit.labelbuilder.core.edit.Distribute;
import com.rohit.labelbuilder.core.edit.EditCommands;
import com.rohit.labelbuilder.core.edit.ElementFactory;
import com.rohit.labelbuilder.core.edit.ZOrder;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The arrange operations (Phase 8b) applied to the current selection: align, distribute, nudge and
 * duplicate. A thin binding layer — the geometry lives in {@code ArrangeOps} and the undo wrapping in
 * {@code EditCommands}; this only supplies the selection and pushes the result through the session.
 *
 * <p>Every operation is a no-op when the selection is too small for it to mean anything, so nothing
 * dead reaches the undo stack.
 */
@Component
public class EditActions {

    /** Offset applied to duplicates so the copy is visibly on top of, not hidden behind, its source. */
    private static final double DUPLICATE_OFFSET_MM = 2;

    private final DocumentSession session;
    private final ElementClipboard clipboard;

    public EditActions(DocumentSession session, ElementClipboard clipboard) {
        this.session = session;
        this.clipboard = clipboard;
    }

    public void align(Align align) {
        EditCommands.align(selection(), align).ifPresent(session::execute);
    }

    public void distribute(Distribute axis) {
        EditCommands.distribute(selection(), axis).ifPresent(session::execute);
    }

    /** Move the selection by a delta in millimetres (the arrow-key nudge). */
    public void nudge(double dxMm, double dyMm) {
        EditCommands.nudge(selection(), dxMm, dyMm).ifPresent(session::execute);
    }

    /** Duplicate the selection, then select the copies so the next drag moves them. */
    public void duplicate() {
        List<LabelElement> copies = new ArrayList<>();
        EditCommands.duplicate(selection(), DUPLICATE_OFFSET_MM, DUPLICATE_OFFSET_MM, ElementFactory::newId, copies)
                .ifPresent(command -> {
                    session.execute(command);
                    session.clearSelection();
                    session.addToSelection(copies.stream().map(LabelElement::id).toList());
                });
    }

    // ---- clipboard and structure (8c) ---------------------------------------------------

    /** Copy the selection to the clipboard, leaving the document untouched. */
    public void copy() {
        if (!selection().isEmpty()) {
            clipboard.set(selection());
        }
    }

    /** Copy the selection, then delete it — one undo step for the removal. */
    public void cut() {
        List<LabelElement> selected = selection();
        if (selected.isEmpty()) {
            return;
        }
        clipboard.set(selected);
        EditCommands.delete(selected).ifPresent(session::execute);
    }

    /** Paste the clipboard as new elements, offset, and select them. */
    public void paste() {
        List<LabelElement> pasted = new ArrayList<>();
        EditCommands.paste(
                        clipboard.contents(), DUPLICATE_OFFSET_MM, DUPLICATE_OFFSET_MM, ElementFactory::newId, pasted)
                .ifPresent(command -> {
                    session.execute(command);
                    session.clearSelection();
                    session.addToSelection(pasted.stream().map(LabelElement::id).toList());
                });
    }

    public void delete() {
        EditCommands.delete(selection()).ifPresent(session::execute);
    }

    /** Group the selection and select the new group. */
    public void group() {
        String groupId = ElementFactory.newId();
        EditCommands.group(selection(), () -> groupId).ifPresent(command -> {
            session.execute(command);
            session.select(groupId);
        });
    }

    /** Dissolve the selected groups and select the freed children. */
    public void ungroup() {
        List<String> freed = selection().stream()
                .filter(GroupElement.class::isInstance)
                .map(GroupElement.class::cast)
                .flatMap(group -> group.children().stream())
                .map(LabelElement::id)
                .toList();
        EditCommands.ungroup(selection()).ifPresent(command -> {
            session.execute(command);
            session.clearSelection();
            session.addToSelection(freed);
        });
    }

    public void reorder(ZOrder move) {
        EditCommands.reorder(selection(), move).ifPresent(session::execute);
    }

    private List<LabelElement> selection() {
        return session.selectedElements();
    }
}
