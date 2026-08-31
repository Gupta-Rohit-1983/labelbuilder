package com.rohit.labelbuilder.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

/** The concrete document commands (each a pure transform, exercised without a stack). */
class DocumentCommandsTest {

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(1, 2, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private final LabelDocument doc =
            LabelDocument.blank("d", "Untitled", Stock.of(100, 50)).addElement(rect("r1"));

    @Test
    void addElementAppends() {
        LabelDocument result = new AddElementCommand(rect("r2")).apply(doc);
        assertThat(result.elements()).extracting(LabelElement::id).containsExactly("r1", "r2");
    }

    @Test
    void removeElementDrops() {
        assertThat(new RemoveElementCommand("r1").apply(doc).elements()).isEmpty();
    }

    @Test
    void moveElementTranslatesAndIsNotMergeable() {
        MoveElementCommand move = new MoveElementCommand("r1", 5, -1);

        LabelDocument result = move.apply(doc);

        assertThat(result.findElement("r1").orElseThrow().bounds()).isEqualTo(new Bounds(6, 1, 10, 10));
        assertThat(move.mergeKey()).isNull();
    }

    @Test
    void moveUnknownElementThrows() {
        assertThatThrownBy(() -> new MoveElementCommand("ghost", 1, 1).apply(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setPropertyUsesTheElementSchemaAndMergesByElementAndKey() {
        SetPropertyCommand cmd = new SetPropertyCommand("r1", "width", 40.0);

        LabelDocument result = cmd.apply(doc);

        assertThat(result.findElement("r1").orElseThrow().bounds().widthMm()).isEqualTo(40.0);
        assertThat(cmd.mergeKey()).isEqualTo("set:r1:width");
    }

    @Test
    void compositeAppliesChildrenInOrderAsOneTransform() {
        CompositeCommand tx =
                CompositeCommand.of("Add two", new AddElementCommand(rect("r2")), new AddElementCommand(rect("r3")));

        LabelDocument result = tx.apply(doc);

        assertThat(result.elements()).extracting(LabelElement::id).containsExactly("r1", "r2", "r3");
        assertThat(tx.label()).isEqualTo("Add two");
    }

    @Test
    void compositeIsAtomicThroughTheStackWhenAChildFails() {
        CommandStack stack = new CommandStack(doc);
        CompositeCommand tx = CompositeCommand.of(
                "Add then fail", new AddElementCommand(rect("r2")), new MoveElementCommand("ghost", 1, 1));

        assertThatThrownBy(() -> stack.execute(tx)).isInstanceOf(IllegalArgumentException.class);

        // Nothing committed: r2 was never added because the transaction as a whole was rejected.
        assertThat(stack.current()).isSameAs(doc);
    }

    @Test
    void emptyTransactionIsRejected() {
        assertThatThrownBy(() -> CompositeCommand.of("empty")).isInstanceOf(IllegalArgumentException.class);
    }
}
