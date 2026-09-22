package com.rohit.labelbuilder.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.core.edit.ZOrder;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Grouping, z-order and layer commands (Phase 8c). */
class StructureCommandsTest {

    private static RectangleElement rect(String id, double x, double y) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(x, y, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    private LabelDocument documentWith(String... ids) {
        LabelDocument doc = LabelDocument.blank("d", "T", Stock.of(200, 100));
        double x = 0;
        for (String id : ids) {
            doc = doc.addElement(rect(id, x, 0));
            x += 20;
        }
        return doc;
    }

    private static List<String> idsOf(LabelDocument doc) {
        return doc.elements().stream().map(LabelElement::id).toList();
    }

    // ---- grouping ----

    @Test
    void groupReplacesMembersAndTakesTheTopmostSlot() {
        LabelDocument doc = documentWith("a", "b", "c");

        LabelDocument grouped = GroupCommand.of(List.of("a", "c"), "g").apply(doc);

        // "c" was topmost of the two, so the group sits where "c" was: after "b".
        assertThat(idsOf(grouped)).containsExactly("b", "g");
        GroupElement group = (GroupElement) grouped.findElement("g").orElseThrow();
        assertThat(group.children()).extracting(LabelElement::id).containsExactly("a", "c");
    }

    @Test
    void groupBoundsSpanItsMembers() {
        LabelDocument doc = documentWith("a", "b", "c");

        GroupElement group = (GroupElement) GroupCommand.of(List.of("a", "c"), "g")
                .apply(doc)
                .findElement("g")
                .orElseThrow();

        // "a" at x=0 w=10, "c" at x=40 w=10 → 0..50
        assertThat(group.bounds()).isEqualTo(new Bounds(0, 0, 50, 10));
    }

    @Test
    void groupingNeedsTwoExistingElements() {
        LabelDocument doc = documentWith("a", "b");

        assertThatThrownBy(() -> GroupCommand.of(List.of("a", "ghost"), "g").apply(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ungroupSplicesChildrenBackAtTheGroupsPosition() {
        LabelDocument doc = documentWith("a", "b", "c");
        // Grouping the outer two puts the group in "c"'s slot, above "b": [b, g]
        LabelDocument grouped = GroupCommand.of(List.of("a", "c"), "g").apply(doc);
        assertThat(idsOf(grouped)).containsExactly("b", "g");

        LabelDocument ungrouped = new UngroupCommand("g").apply(grouped);

        // The children land where the group sat, so "a" is now above "b" rather than below it.
        assertThat(idsOf(ungrouped)).containsExactly("b", "a", "c");
    }

    @Test
    void groupingAdjacentElementsThenUngroupingRestoresTheOriginalOrder() {
        LabelDocument doc = documentWith("a", "b", "c");

        LabelDocument round = new UngroupCommand("g")
                .apply(GroupCommand.of(List.of("a", "b"), "g").apply(doc));

        // "a" and "b" were already adjacent and below "c", which is exactly where the group sat.
        assertThat(idsOf(round)).containsExactly("a", "b", "c");
    }

    @Test
    void ungroupingANonGroupIsRejected() {
        LabelDocument doc = documentWith("a");

        assertThatThrownBy(() -> new UngroupCommand("a").apply(doc)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- z-order ----

    @Test
    void bringToFrontMovesSelectionToTheEndKeepingRelativeOrder() {
        LabelDocument doc = documentWith("a", "b", "c", "d");

        LabelDocument result = new ReorderCommand(List.of("a", "c"), ZOrder.BRING_TO_FRONT).apply(doc);

        assertThat(idsOf(result)).containsExactly("b", "d", "a", "c");
    }

    @Test
    void sendToBackMovesSelectionToTheStart() {
        LabelDocument doc = documentWith("a", "b", "c", "d");

        LabelDocument result = new ReorderCommand(List.of("b", "d"), ZOrder.SEND_TO_BACK).apply(doc);

        assertThat(idsOf(result)).containsExactly("b", "d", "a", "c");
    }

    @Test
    void bringForwardStepsOverTheNextUnselectedElement() {
        LabelDocument doc = documentWith("a", "b", "c");

        LabelDocument result = new ReorderCommand(List.of("a"), ZOrder.BRING_FORWARD).apply(doc);

        assertThat(idsOf(result)).containsExactly("b", "a", "c");
    }

    @Test
    void sendBackwardStepsUnderThePreviousUnselectedElement() {
        LabelDocument doc = documentWith("a", "b", "c");

        LabelDocument result = new ReorderCommand(List.of("c"), ZOrder.SEND_BACKWARD).apply(doc);

        assertThat(idsOf(result)).containsExactly("a", "c", "b");
    }

    @Test
    void bringForwardKeepsAMultiSelectionTogetherWithoutLeapfrogging() {
        LabelDocument doc = documentWith("a", "b", "c", "d");

        LabelDocument result = new ReorderCommand(List.of("a", "b"), ZOrder.BRING_FORWARD).apply(doc);

        assertThat(idsOf(result)).containsExactly("c", "a", "b", "d");
    }

    @Test
    void bringForwardAtTheTopIsANoOp() {
        LabelDocument doc = documentWith("a", "b");

        LabelDocument result = new ReorderCommand(List.of("b"), ZOrder.BRING_FORWARD).apply(doc);

        assertThat(idsOf(result)).containsExactly("a", "b");
    }

    // ---- layers ----

    @Test
    void addLayerAppendsAndRejectsDuplicateIds() {
        LabelDocument doc = documentWith("a");

        LabelDocument result = new AddLayerCommand(Layer.of("layer-2", "Overlay")).apply(doc);

        assertThat(result.layers()).extracting(Layer::id).containsExactly("layer-1", "layer-2");
        assertThatThrownBy(() -> new AddLayerCommand(Layer.of("layer-1", "Dup")).apply(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateLayerTogglesVisibilityInPlace() {
        LabelDocument doc = documentWith("a");

        LabelDocument result = UpdateLayerCommand.of(doc.layers().getFirst().withVisible(false))
                .apply(doc);

        assertThat(result.layers().getFirst().visible()).isFalse();
        assertThat(result.layers()).hasSize(1);
    }

    @Test
    void updatingAMissingLayerIsRejected() {
        LabelDocument doc = documentWith("a");

        assertThatThrownBy(() -> UpdateLayerCommand.of(Layer.of("ghost", "X")).apply(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveToLayerReassignsWithoutChangingZOrder() {
        LabelDocument doc = new AddLayerCommand(Layer.of("layer-2", "Overlay")).apply(documentWith("a", "b"));

        LabelDocument result = new MoveToLayerCommand(List.of("a"), "layer-2").apply(doc);

        assertThat(result.findElement("a").orElseThrow().layerId()).isEqualTo("layer-2");
        assertThat(result.findElement("b").orElseThrow().layerId()).isEqualTo("layer-1");
        assertThat(idsOf(result)).containsExactly("a", "b");
    }

    @Test
    void movingToAMissingLayerIsRejected() {
        LabelDocument doc = documentWith("a");

        assertThatThrownBy(() -> new MoveToLayerCommand(List.of("a"), "ghost").apply(doc))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
