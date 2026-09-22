package com.rohit.labelbuilder.desktop.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.edit.Align;
import com.rohit.labelbuilder.core.edit.Distribute;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

/** The arrange operations against a live session — undo behaviour included. */
class EditActionsTest {

    private final DocumentSession session = new DocumentSession();
    private final ElementClipboard clipboard = new ElementClipboard();
    private final EditActions edit = new EditActions(session, clipboard);

    private static RectangleElement rect(String id, double x, double y) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(x, y, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private void add(String id, double x, double y) {
        session.execute(new AddElementCommand(rect(id, x, y)));
    }

    private double xOf(String id) {
        return session.document().findElement(id).orElseThrow().bounds().xMm();
    }

    @Test
    void alignMovesTheSelectionAndIsOneUndoStep() {
        add("a", 10, 0);
        add("b", 40, 0);
        session.select("a");
        session.toggleSelection("b");

        edit.align(Align.LEFT);

        assertThat(xOf("b")).isEqualTo(10);

        session.undo();
        assertThat(xOf("b")).isEqualTo(40); // the whole align reverses at once
    }

    @Test
    void alignDoesNothingWithASingleSelection() {
        add("a", 10, 0);
        session.select("a");

        edit.align(Align.RIGHT);

        // No dead entry on the stack: the only undoable step is still the add.
        session.undo();
        assertThat(session.document().elements()).isEmpty();
    }

    @Test
    void distributeSpacesTheMiddleElement() {
        add("a", 0, 0);
        add("b", 12, 0);
        add("c", 100, 0);
        session.select("a");
        session.toggleSelection("b");
        session.toggleSelection("c");

        edit.distribute(Distribute.HORIZONTALLY);

        assertThat(session.document().findElement("b").orElseThrow().bounds().centerXMm())
                .isEqualTo(55);
    }

    @Test
    void nudgeMovesTheSelection() {
        add("a", 10, 10);
        session.select("a");

        edit.nudge(1, -1);

        assertThat(session.document().findElement("a").orElseThrow().bounds()).isEqualTo(new Bounds(11, 9, 10, 10));
    }

    @Test
    void repeatedNudgesOnOneElementCollapseIntoASingleUndoStep() {
        add("a", 10, 10);
        session.select("a");

        edit.nudge(1, 0);
        edit.nudge(1, 0);
        edit.nudge(1, 0);

        assertThat(xOf("a")).isEqualTo(13);

        session.undo(); // one undo unwinds the whole run of nudges
        assertThat(xOf("a")).isEqualTo(10);
    }

    @Test
    void nudgeWithNoSelectionIsANoOp() {
        add("a", 10, 10);

        edit.nudge(5, 5);

        assertThat(xOf("a")).isEqualTo(10);
    }

    @Test
    void duplicateAddsOffsetCopiesAndSelectsThem() {
        add("a", 10, 10);
        session.select("a");

        edit.duplicate();

        assertThat(session.document().elements()).hasSize(2);
        // The copy is selected, not the original, so the next drag moves the new one.
        assertThat(session.selectedIds()).hasSize(1).doesNotContain("a");

        LabelElement copy = session.selectedElements().getFirst();
        assertThat(copy.bounds()).isEqualTo(new Bounds(12, 12, 10, 10));
    }

    @Test
    void duplicateIsUndoable() {
        add("a", 10, 10);
        session.select("a");

        edit.duplicate();
        session.undo();

        assertThat(session.document().elements()).extracting(LabelElement::id).containsExactly("a");
    }

    @Test
    void duplicateWithNoSelectionDoesNothing() {
        add("a", 10, 10);

        edit.duplicate();

        assertThat(session.document().elements()).hasSize(1);
    }

    @Test
    void lockedElementsAreNotNudged() {
        session.execute(new AddElementCommand(rect("a", 10, 10).withLocked(true)));
        session.select("a");

        edit.nudge(5, 5);

        assertThat(xOf("a")).isEqualTo(10);
    }

    @Test
    void selectionCountDrivesArrangeEnablement() {
        add("a", 0, 0);
        add("b", 10, 0);

        assertThat(session.selectionCountProperty().get()).isZero();

        session.select("a");
        assertThat(session.selectionCountProperty().get()).isEqualTo(1);

        session.toggleSelection("b");
        assertThat(session.selectionCountProperty().get()).isEqualTo(2);

        session.clearSelection();
        assertThat(session.selectionCountProperty().get()).isZero();
    }
}
