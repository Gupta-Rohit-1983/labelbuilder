package com.rohit.labelbuilder.desktop.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.command.SetPropertyCommand;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

/**
 * The editor session, exercised headlessly — JavaFX <em>properties</em> need no toolkit, only
 * controls do, so this runs without starting FX.
 */
class DocumentSessionTest {

    private final DocumentSession session = new DocumentSession();

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(1, 2, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    @Test
    void startsOnABlankDocumentWithNoHistory() {
        assertThat(session.document().elements()).isEmpty();
        assertThat(session.canUndoProperty().get()).isFalse();
        assertThat(session.canRedoProperty().get()).isFalse();
        assertThat(session.activeLayerId()).isEqualTo("layer-1");
    }

    @Test
    void executePublishesTheNewDocumentAndEnablesUndo() {
        session.execute(new AddElementCommand(rect("a")));

        assertThat(session.document().elements()).extracting(LabelElement::id).containsExactly("a");
        assertThat(session.canUndoProperty().get()).isTrue();
    }

    @Test
    void documentPropertyNotifiesObservers() {
        int[] changes = {0};
        session.documentProperty().addListener((o, a, b) -> changes[0]++);

        session.execute(new AddElementCommand(rect("a")));
        session.undo();

        assertThat(changes[0]).isEqualTo(2);
    }

    @Test
    void undoAndRedoMoveTheDocumentBackAndForward() {
        session.execute(new AddElementCommand(rect("a")));

        session.undo();
        assertThat(session.document().elements()).isEmpty();
        assertThat(session.canRedoProperty().get()).isTrue();

        session.redo();
        assertThat(session.document().elements()).hasSize(1);
    }

    @Test
    void selectionIsByIdAndSurvivesAnEditThatReplacesTheElement() {
        session.execute(new AddElementCommand(rect("a")));
        session.select("a");

        // Editing replaces the immutable element instance; an id-based selection still holds.
        session.execute(new SetPropertyCommand("a", "x", 40.0));

        assertThat(session.isSelected("a")).isTrue();
        assertThat(session.selectedElements()).hasSize(1);
        assertThat(session.selectedElements().getFirst().bounds().xMm()).isEqualTo(40.0);
    }

    @Test
    void selectionListenersFireOnChange() {
        int[] changes = {0};
        session.addSelectionListener(() -> changes[0]++);

        session.select("a");
        session.clearSelection();

        assertThat(changes[0]).isEqualTo(2);
    }

    @Test
    void toggleAddsAndRemoves() {
        session.execute(new AddElementCommand(rect("a")));
        session.toggleSelection("a");
        assertThat(session.isSelected("a")).isTrue();

        session.toggleSelection("a");
        assertThat(session.isSelected("a")).isFalse();
    }

    @Test
    void undoingAnAddDropsTheNowMissingElementFromTheSelection() {
        session.execute(new AddElementCommand(rect("a")));
        session.select("a");

        session.undo();

        assertThat(session.selectedIds()).isEmpty();
        assertThat(session.selectionSize()).isZero();
    }

    @Test
    void openResetsHistoryAndSelection() {
        session.execute(new AddElementCommand(rect("a")));
        session.select("a");

        session.open(session.document());

        assertThat(session.canUndoProperty().get()).isFalse();
        assertThat(session.selectedIds()).isEmpty();
    }
}
