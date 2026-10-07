package com.rohit.labelbuilder.desktop.panels.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectorStateTest {

    private static RectangleElement rect(String id) {
        return RectangleElement.of(
                ElementProperties.of(id, id, "layer-1", new Bounds(0, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
    }

    @Test
    void noSelectionShowsTheEmptyState() {
        assertThat(InspectorState.of(List.of())).isInstanceOf(InspectorState.Empty.class);
    }

    @Test
    void oneElementDrivesTheFormFromThatElement() {
        LabelElement element = rect("a");

        InspectorState state = InspectorState.of(List.of(element));

        assertThat(state).isInstanceOf(InspectorState.Single.class);
        assertThat(((InspectorState.Single) state).element()).isSameAs(element);
    }

    @Test
    void severalElementsReportTheirCount() {
        InspectorState state = InspectorState.of(List.of(rect("a"), rect("b"), rect("c")));

        assertThat(state).isInstanceOf(InspectorState.Multiple.class);
        assertThat(((InspectorState.Multiple) state).count()).isEqualTo(3);
    }
}
