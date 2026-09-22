package com.rohit.labelbuilder.desktop.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.edit.ZOrder;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cut/copy/paste, delete, grouping and restacking through a live session (Phase 8c). */
class ClipboardAndStructureTest {

    private final DocumentSession session = new DocumentSession();
    private final ElementClipboard clipboard = new ElementClipboard();
    private final EditActions edit = new EditActions(session, clipboard);

    private static RectangleElement rect(String id, double x) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(x, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private void add(String... ids) {
        double x = 0;
        for (String id : ids) {
            session.execute(new AddElementCommand(rect(id, x)));
            x += 20;
        }
    }

    private List<String> documentIds() {
        return session.document().elements().stream().map(LabelElement::id).toList();
    }

    // ---- clipboard ----

    @Test
    void copyLeavesTheDocumentAloneAndFillsTheClipboard() {
        add("a");
        session.select("a");

        edit.copy();

        assertThat(clipboard.isEmpty()).isFalse();
        assertThat(session.document().elements()).hasSize(1);
        assertThat(clipboard.hasContentProperty().get()).isTrue();
    }

    @Test
    void cutCopiesThenRemoves() {
        add("a", "b");
        session.select("a");

        edit.cut();

        assertThat(documentIds()).containsExactly("b");
        assertThat(clipboard.contents()).extracting(LabelElement::id).containsExactly("a");
    }

    @Test
    void pasteAddsOffsetCopiesWithFreshIdsAndSelectsThem() {
        add("a");
        session.select("a");
        edit.copy();

        edit.paste();

        assertThat(session.document().elements()).hasSize(2);
        assertThat(session.selectedIds()).hasSize(1).doesNotContain("a");
        assertThat(session.selectedElements().getFirst().bounds()).isEqualTo(new Bounds(2, 2, 10, 10));
    }

    @Test
    void pasteCanBeRepeatedAndNeverReusesAnId() {
        add("a");
        session.select("a");
        edit.copy();

        edit.paste();
        edit.paste();

        assertThat(documentIds()).doesNotHaveDuplicates().hasSize(3);
    }

    @Test
    void pasteWithAnEmptyClipboardDoesNothing() {
        add("a");

        edit.paste();

        assertThat(session.document().elements()).hasSize(1);
    }

    @Test
    void cutThenPasteIsUndoableInTwoSteps() {
        add("a");
        session.select("a");

        edit.cut();
        edit.paste();

        assertThat(session.document().elements()).hasSize(1);

        session.undo(); // undo the paste
        assertThat(session.document().elements()).isEmpty();

        session.undo(); // undo the cut
        assertThat(documentIds()).containsExactly("a");
    }

    // ---- delete ----

    @Test
    void deleteRemovesTheWholeSelectionInOneStep() {
        add("a", "b", "c");
        session.select("a");
        session.toggleSelection("c");

        edit.delete();

        assertThat(documentIds()).containsExactly("b");

        session.undo();
        assertThat(documentIds()).containsExactly("a", "b", "c");
    }

    @Test
    void deleteClearsTheSelectionOfVanishedElements() {
        add("a");
        session.select("a");

        edit.delete();

        assertThat(session.selectedIds()).isEmpty();
    }

    // ---- grouping ----

    @Test
    void groupCollapsesTheSelectionAndSelectsTheGroup() {
        add("a", "b");
        session.select("a");
        session.toggleSelection("b");

        edit.group();

        assertThat(session.document().elements()).hasSize(1);
        assertThat(session.document().elements().getFirst()).isInstanceOf(GroupElement.class);
        assertThat(session.selectionSize()).isEqualTo(1);
    }

    @Test
    void groupNeedsTwoElements() {
        add("a");
        session.select("a");

        edit.group();

        assertThat(session.document().elements().getFirst()).isInstanceOf(RectangleElement.class);
    }

    @Test
    void ungroupRestoresTheChildrenAndSelectsThem() {
        add("a", "b");
        session.select("a");
        session.toggleSelection("b");
        edit.group();

        edit.ungroup();

        assertThat(documentIds()).containsExactly("a", "b");
        assertThat(session.selectedIds()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void ungroupIgnoresANonGroupSelection() {
        add("a");
        session.select("a");

        edit.ungroup();

        assertThat(documentIds()).containsExactly("a");
    }

    // ---- z-order ----

    @Test
    void bringToFrontRestacksTheSelection() {
        add("a", "b", "c");
        session.select("a");

        edit.reorder(ZOrder.BRING_TO_FRONT);

        assertThat(documentIds()).containsExactly("b", "c", "a");
    }

    @Test
    void reorderIsUndoable() {
        add("a", "b");
        session.select("a");

        edit.reorder(ZOrder.BRING_TO_FRONT);
        session.undo();

        assertThat(documentIds()).containsExactly("a", "b");
    }

    @Test
    void reorderWithNoSelectionDoesNothing() {
        add("a", "b");

        edit.reorder(ZOrder.SEND_TO_BACK);

        assertThat(documentIds()).containsExactly("a", "b");
    }
}
