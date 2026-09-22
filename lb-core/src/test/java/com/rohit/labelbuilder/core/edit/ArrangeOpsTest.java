package com.rohit.labelbuilder.core.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ArrangeOpsTest {

    private static final double EPS = 1e-9;

    private static RectangleElement rect(String id, double x, double y, double w, double h) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(x, y, w, h)), Stroke.solid(0.2), Fill.none());
    }

    /** Deterministic ids for duplication assertions. */
    private static Supplier<String> ids() {
        AtomicInteger n = new AtomicInteger();
        return () -> "copy-" + n.incrementAndGet();
    }

    @Test
    void boundingBoxSpansEveryElement() {
        Bounds box = ArrangeOps.boundingBox(List.of(rect("a", 10, 10, 10, 10), rect("b", 30, 5, 10, 20)));

        assertThat(box).isEqualTo(new Bounds(10, 5, 30, 20));
    }

    @Test
    void alignLeftMovesEveryElementToTheSelectionsLeftEdge() {
        List<LabelElement> elements = List.of(rect("a", 10, 0, 10, 10), rect("b", 30, 20, 10, 10));

        Map<String, Bounds> changes = ArrangeOps.align(elements, Align.LEFT);

        // "a" already sits on the left edge, so only "b" changes.
        assertThat(changes).containsOnlyKeys("b");
        assertThat(changes.get("b").xMm()).isCloseTo(10, within(EPS));
        assertThat(changes.get("b").yMm()).isCloseTo(20, within(EPS)); // y untouched
    }

    @Test
    void alignCenterCentresOnTheSelectionsCentre() {
        List<LabelElement> elements = List.of(rect("a", 0, 0, 10, 10), rect("b", 30, 0, 20, 10));
        // selection spans x 0..50, centre 25

        Map<String, Bounds> changes = ArrangeOps.align(elements, Align.CENTER);

        assertThat(changes.get("a").centerXMm()).isCloseTo(25, within(EPS));
        assertThat(changes.get("b").centerXMm()).isCloseTo(25, within(EPS));
    }

    @Test
    void alignBottomUsesEachElementsOwnHeight() {
        List<LabelElement> elements = List.of(rect("a", 0, 0, 10, 10), rect("b", 20, 0, 10, 30));
        // selection spans y 0..30

        Map<String, Bounds> changes = ArrangeOps.align(elements, Align.BOTTOM);

        assertThat(changes.get("a").bottomMm()).isCloseTo(30, within(EPS));
        assertThat(changes.get("a").yMm()).isCloseTo(20, within(EPS));
    }

    @Test
    void aligningFewerThanTwoElementsIsANoOp() {
        assertThat(ArrangeOps.align(List.of(rect("a", 0, 0, 10, 10)), Align.LEFT))
                .isEmpty();
        assertThat(ArrangeOps.align(List.of(), Align.LEFT)).isEmpty();
    }

    @Test
    void lockedElementsAreLeftAlone() {
        LabelElement locked = rect("b", 30, 0, 10, 10).withLocked(true);
        List<LabelElement> elements = List.of(rect("a", 10, 0, 10, 10), locked);

        // Only one movable element remains, so there is nothing to align against.
        assertThat(ArrangeOps.align(elements, Align.LEFT)).isEmpty();
    }

    @Test
    void distributeSpacesTheMiddleElementsEvenlyAndHoldsTheEnds() {
        List<LabelElement> elements =
                List.of(rect("a", 0, 0, 10, 10), rect("b", 12, 0, 10, 10), rect("c", 100, 0, 10, 10));
        // centres: a=5, c=105 → the middle centre must land at 55

        Map<String, Bounds> changes = ArrangeOps.distribute(elements, Distribute.HORIZONTALLY);

        assertThat(changes).containsOnlyKeys("b"); // ends stay put
        assertThat(changes.get("b").centerXMm()).isCloseTo(55, within(EPS));
    }

    @Test
    void distributeIsStableWhenAppliedTwice() {
        List<LabelElement> elements =
                List.of(rect("a", 0, 0, 10, 10), rect("b", 12, 0, 10, 10), rect("c", 100, 0, 10, 10));

        Map<String, Bounds> first = ArrangeOps.distribute(elements, Distribute.HORIZONTALLY);
        List<LabelElement> spaced =
                List.of(rect("a", 0, 0, 10, 10), rect("b", first.get("b").xMm(), 0, 10, 10), rect("c", 100, 0, 10, 10));

        assertThat(ArrangeOps.distribute(spaced, Distribute.HORIZONTALLY)).isEmpty();
    }

    @Test
    void distributeNeedsThreeElements() {
        List<LabelElement> two = List.of(rect("a", 0, 0, 10, 10), rect("b", 50, 0, 10, 10));

        assertThat(ArrangeOps.distribute(two, Distribute.HORIZONTALLY)).isEmpty();
    }

    @Test
    void distributeVerticallyUsesTheVerticalCentres() {
        List<LabelElement> elements =
                List.of(rect("a", 0, 0, 10, 10), rect("b", 0, 5, 10, 10), rect("c", 0, 100, 10, 10));

        Map<String, Bounds> changes = ArrangeOps.distribute(elements, Distribute.VERTICALLY);

        assertThat(changes.get("b").centerYMm()).isCloseTo(55, within(EPS));
    }

    @Test
    void nudgeTranslatesEveryElement() {
        Map<String, Bounds> changes =
                ArrangeOps.nudge(List.of(rect("a", 10, 10, 5, 5), rect("b", 20, 20, 5, 5)), 1, -2);

        assertThat(changes.get("a")).isEqualTo(new Bounds(11, 8, 5, 5));
        assertThat(changes.get("b")).isEqualTo(new Bounds(21, 18, 5, 5));
    }

    @Test
    void aZeroNudgeChangesNothing() {
        assertThat(ArrangeOps.nudge(List.of(rect("a", 0, 0, 5, 5)), 0, 0)).isEmpty();
    }

    @Test
    void duplicateOffsetsTheCopiesAndGivesThemFreshIds() {
        List<LabelElement> copies = ArrangeOps.duplicate(List.of(rect("a", 10, 10, 5, 5)), 2, 2, ids());

        assertThat(copies).hasSize(1);
        assertThat(copies.getFirst().id()).isEqualTo("copy-1");
        assertThat(copies.getFirst().bounds()).isEqualTo(new Bounds(12, 12, 5, 5));
    }

    @Test
    void duplicatingAGroupReIdsItsChildrenToo() {
        GroupElement group = new GroupElement(
                ElementProperties.of("g", "g", "layer-1", new Bounds(0, 0, 20, 20)),
                List.of(rect("child", 1, 1, 5, 5)));

        GroupElement copy =
                (GroupElement) ArrangeOps.duplicate(List.of(group), 2, 2, ids()).getFirst();

        assertThat(copy.id()).isNotEqualTo("g");
        assertThat(copy.children().getFirst().id()).isNotEqualTo("child");
        assertThat(copy.children().getFirst().bounds()).isEqualTo(new Bounds(3, 3, 5, 5));
    }
}
