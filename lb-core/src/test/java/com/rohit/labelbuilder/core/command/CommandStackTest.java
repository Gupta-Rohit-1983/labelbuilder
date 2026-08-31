package com.rohit.labelbuilder.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

class CommandStackTest {

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(0, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private final LabelDocument doc =
            LabelDocument.blank("d", "Untitled", Stock.of(100, 50)).addElement(rect("r1"));
    private final CommandStack stack = new CommandStack(doc);

    @Test
    void freshStackHasNothingToUndoOrRedo() {
        assertThat(stack.current()).isSameAs(doc);
        assertThat(stack.canUndo()).isFalse();
        assertThat(stack.canRedo()).isFalse();
        assertThat(stack.undoLabel()).isEmpty();
    }

    @Test
    void executeAdvancesStateAndEnablesUndo() {
        stack.execute(new AddElementCommand(rect("r2")));

        assertThat(stack.current().elements()).hasSize(2);
        assertThat(stack.canUndo()).isTrue();
        assertThat(stack.canRedo()).isFalse();
        assertThat(stack.undoLabel()).contains("Add Rectangle");
    }

    @Test
    void undoThenRedoRoundTrips() {
        LabelDocument after = stack.execute(new AddElementCommand(rect("r2")));

        assertThat(stack.undo()).isSameAs(doc);
        assertThat(stack.canRedo()).isTrue();
        assertThat(stack.redoLabel()).contains("Add Rectangle");

        assertThat(stack.redo()).isEqualTo(after);
    }

    @Test
    void executingAfterUndoTruncatesTheRedoTail() {
        stack.execute(new AddElementCommand(rect("r2")));
        stack.undo();

        stack.execute(new AddElementCommand(rect("r3")));

        assertThat(stack.canRedo()).isFalse();
        assertThat(stack.current().findElement("r3")).isPresent();
        assertThat(stack.current().findElement("r2")).isEmpty();
    }

    @Test
    void samePropertyEditsMergeIntoOneUndoStep() {
        stack.execute(new SetPropertyCommand("r1", "x", 5.0));
        stack.execute(new SetPropertyCommand("r1", "x", 9.0));

        assertThat(stack.undoDepth()).isEqualTo(1); // coalesced
        assertThat(stack.current().findElement("r1").orElseThrow().bounds().xMm())
                .isEqualTo(9.0);

        stack.undo(); // single undo reverts the whole run
        assertThat(stack.current().findElement("r1").orElseThrow().bounds().xMm())
                .isEqualTo(0.0);
    }

    @Test
    void differentPropertiesDoNotMerge() {
        stack.execute(new SetPropertyCommand("r1", "x", 5.0));
        stack.execute(new SetPropertyCommand("r1", "y", 7.0));

        assertThat(stack.undoDepth()).isEqualTo(2);
    }

    @Test
    void capacityDropsOldestUndoableSteps() {
        CommandStack small = new CommandStack(doc, 2);
        small.execute(new SetPropertyCommand("r1", "x", 1.0));
        small.execute(new SetPropertyCommand("r1", "y", 2.0));
        small.execute(new SetPropertyCommand("r1", "width", 3.0));

        assertThat(small.undoDepth()).isEqualTo(2); // capped
        // Unwinding both available steps cannot reach x==1 again (that step fell off).
        small.undo();
        small.undo();
        assertThat(small.canUndo()).isFalse();
        assertThat(small.current().findElement("r1").orElseThrow().bounds().xMm())
                .isEqualTo(1.0);
    }

    @Test
    void listenersFireOnEveryStateChange() {
        int[] count = {0};
        stack.addChangeListener(() -> count[0]++);

        stack.execute(new AddElementCommand(rect("r2")));
        stack.undo();
        stack.redo();

        assertThat(count[0]).isEqualTo(3);
    }

    @Test
    void aThrowingCommandLeavesTheStackUntouched() {
        int[] count = {0};
        stack.addChangeListener(() -> count[0]++);

        assertThatThrownBy(() -> stack.execute(new SetPropertyCommand("ghost", "x", 1.0)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stack.current()).isSameAs(doc);
        assertThat(stack.canUndo()).isFalse();
        assertThat(count[0]).isZero(); // no change event on failure
    }
}
