package com.rohit.labelbuilder.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelDocumentTest {

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(0, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private final LabelDocument blank = LabelDocument.blank("doc-1", "Untitled", Stock.of(100, 50));

    @Test
    void blankHasOneLayerAndNoElements() {
        assertThat(blank.layers()).extracting(Layer::id).containsExactly("layer-1");
        assertThat(blank.elements()).isEmpty();
        assertThat(blank.stock().widthMm()).isEqualTo(100);
    }

    @Test
    void addElementAppendsAtTopOfZOrder() {
        LabelDocument doc = blank.addElement(rect("a")).addElement(rect("b"));

        // index is z-order, back-to-front: "a" behind "b"
        assertThat(doc.elements()).extracting(LabelElement::id).containsExactly("a", "b");
    }

    @Test
    void findElementLocatesById() {
        LabelDocument doc = blank.addElement(rect("a"));

        assertThat(doc.findElement("a")).isPresent();
        assertThat(doc.findElement("missing")).isEmpty();
    }

    @Test
    void removeElementDropsItAndIsANoOpWhenAbsent() {
        LabelDocument doc = blank.addElement(rect("a")).addElement(rect("b"));

        assertThat(doc.removeElement("a").elements())
                .extracting(LabelElement::id)
                .containsExactly("b");
        assertThat(doc.removeElement("nope").elements()).hasSize(2);
    }

    @Test
    void replaceElementKeepsZOrderPosition() {
        LabelDocument doc = blank.addElement(rect("a")).addElement(rect("b"));
        RectangleElement moved =
                (RectangleElement) doc.findElement("a").orElseThrow().movedBy(5, 5);

        LabelDocument replaced = doc.replaceElement(moved);

        assertThat(replaced.elements()).extracting(LabelElement::id).containsExactly("a", "b");
        assertThat(replaced.findElement("a").orElseThrow().bounds()).isEqualTo(new Bounds(5, 5, 10, 10));
    }

    @Test
    void replaceUnknownElementIsRejected() {
        assertThatThrownBy(() -> blank.replaceElement(rect("ghost"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editsDoNotMutateTheOriginalDocument() {
        blank.addElement(rect("a")).withName("Changed");

        assertThat(blank.elements()).isEmpty();
        assertThat(blank.name()).isEqualTo("Untitled");
    }

    @Test
    void elementsListIsUnmodifiable() {
        LabelDocument doc = blank.addElement(rect("a"));
        assertThatThrownBy(() -> doc.elements().add(rect("b"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorDefensivelyCopiesElements() {
        List<LabelElement> mutable = new java.util.ArrayList<>();
        mutable.add(rect("a"));
        LabelDocument doc = blank.withElements(mutable);

        mutable.add(rect("b")); // must not leak into the document

        assertThat(doc.elements()).hasSize(1);
    }
}
