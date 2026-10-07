package com.rohit.labelbuilder.desktop.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.edit.EditCommands;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

/** Multi-element property edits through a live session, including undo (Phase 9d). */
class MultiSelectEditTest {

    private final DocumentSession session = new DocumentSession();

    private static ElementProperties props(String id, double x) {
        return ElementProperties.of(id, id, "layer-1", new Bounds(x, 0, 10, 10));
    }

    private void addRect(String id, double x) {
        session.execute(new AddElementCommand(RectangleElement.of(props(id, x), Stroke.solid(0.2), Fill.none())));
    }

    private void addText(String id, double x) {
        session.execute(new AddElementCommand(TextElement.of(props(id, x), "Hi", FontSpec.of("Arial", 10))));
    }

    private double xOf(String id) {
        return session.document().findElement(id).orElseThrow().bounds().xMm();
    }

    private void set(String key, Object value) {
        EditCommands.setProperty(session.selectedElements(), key, value, "Change " + key)
                .ifPresent(session::execute);
    }

    @Test
    void anEditAppliesToEverySelectedElement() {
        addRect("a", 0);
        addRect("b", 20);
        session.select("a");
        session.toggleSelection("b");

        set("x", 50.0);

        assertThat(xOf("a")).isEqualTo(50);
        assertThat(xOf("b")).isEqualTo(50);
    }

    @Test
    void theWholeMultiEditIsASingleUndoStep() {
        addRect("a", 0);
        addRect("b", 20);
        session.select("a");
        session.toggleSelection("b");

        set("x", 50.0);
        session.undo();

        assertThat(xOf("a")).isZero();
        assertThat(xOf("b")).isEqualTo(20);
    }

    @Test
    void elementsAlreadyHoldingTheValueAreNotRecorded() {
        addRect("a", 5);
        addRect("b", 20);
        session.select("a");
        session.toggleSelection("b");

        set("x", 5.0); // only "b" actually changes

        assertThat(xOf("b")).isEqualTo(5);
        session.undo();
        assertThat(xOf("b")).isEqualTo(20);
        assertThat(xOf("a")).isEqualTo(5); // never touched, so undo left it alone
    }

    @Test
    void anEditThatChangesNothingDoesNotReachTheUndoStack() {
        addRect("a", 5);
        session.select("a");

        set("x", 5.0);

        // The only undoable step is still the add.
        session.undo();
        assertThat(session.document().elements()).isEmpty();
    }

    @Test
    void mixedTypesCanBeEditedThroughTheirCommonProperties() {
        addRect("a", 0);
        addText("b", 20);
        session.select("a");
        session.toggleSelection("b");

        set("visible", false);

        assertThat(session.document().elements()).allMatch(e -> !e.visible());
    }

    @Test
    void aPropertyMissingFromAnElementIsSkippedRatherThanFailing() {
        addRect("a", 0);
        addText("b", 20);
        session.select("a");
        session.toggleSelection("b");

        // "value" exists only on the text element; the rectangle must be left alone, not blow up.
        set("value", "Changed");

        assertThat(session.document().findElement("b").orElseThrow())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(TextElement.class))
                .extracting(TextElement::value)
                .isEqualTo("Changed");
        assertThat(session.document().findElement("a").orElseThrow()).isInstanceOf(RectangleElement.class);
    }

    @Test
    void selectingASingleElementStillProducesAMergeableEdit() {
        addRect("a", 0);
        session.select("a");

        // Two edits of the same property on one element coalesce, as in the single-select inspector.
        set("x", 10.0);
        set("x", 30.0);

        assertThat(xOf("a")).isEqualTo(30);
        session.undo();
        assertThat(xOf("a")).isZero();
    }

    @Test
    void selectionSurvivesAMultiEdit() {
        addRect("a", 0);
        addRect("b", 20);
        session.select("a");
        session.toggleSelection("b");

        set("x", 50.0);

        assertThat(session.selectedIds()).containsExactlyInAnyOrder("a", "b");
        assertThat(session.selectedElements()).extracting(LabelElement::id).containsExactlyInAnyOrder("a", "b");
    }
}
