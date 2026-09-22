package com.rohit.labelbuilder.desktop.panels;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.core.command.AddLayerCommand;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import org.junit.jupiter.api.Test;

/**
 * The panels' decision logic, extracted as pure functions so it is testable without an FX toolkit.
 * The views themselves are exercised by the UI suite (TestFX, Phase 19).
 */
class PanelLogicTest {

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(0, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private static LabelDocument documentWith(String... ids) {
        LabelDocument doc = LabelDocument.blank("d", "T", Stock.of(100, 50));
        for (String id : ids) {
            doc = doc.addElement(rect(id));
        }
        return doc;
    }

    @Test
    void objectsAreListedTopmostFirst() {
        // The document stores back-to-front; the panel shows the reverse.
        LabelDocument doc = documentWith("a", "b", "c");

        assertThat(ObjectsPanel.displayOrder(doc)).extracting(LabelElement::id).containsExactly("c", "b", "a");
    }

    @Test
    void displayOrderOfAnEmptyDocumentIsEmpty() {
        assertThat(ObjectsPanel.displayOrder(documentWith())).isEmpty();
    }

    @Test
    void elementDescriptionShowsNameAndType() {
        assertThat(ObjectsPanel.describe(rect("a"))).contains("a").contains("Rectangle");
    }

    @Test
    void hiddenAndLockedElementsAreMarked() {
        assertThat(ObjectsPanel.describe(rect("a").withVisible(false))).contains("hidden");
        assertThat(ObjectsPanel.describe(rect("a").withLocked(true))).contains("locked");
    }

    @Test
    void nextLayerIdFollowsTheExistingOnes() {
        LabelDocument doc = documentWith("a"); // blank() starts with layer-1

        assertThat(LayersPanel.nextLayerId(doc)).isEqualTo("layer-2");
    }

    @Test
    void nextLayerIdSkipsIdsAlreadyTakenRatherThanCounting() {
        LabelDocument doc = new AddLayerCommand(Layer.of("layer-2", "Two")).apply(documentWith("a"));
        doc = new AddLayerCommand(Layer.of("layer-4", "Four")).apply(doc);

        // Three layers exist, but "layer-3" is free — counting would have collided with "layer-4".
        assertThat(LayersPanel.nextLayerId(doc)).isEqualTo("layer-3");
    }
}
