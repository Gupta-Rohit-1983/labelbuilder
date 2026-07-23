package com.rohit.labelbuilder.desktop.dock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;

/** Round-trip and defensiveness of the persistence codec — fully headless. */
class DockStateCodecTest {

    private static final Set<String> VALID = Set.of("p.toolbox", "p.props", "p.objects", "p.layers");

    @Test
    void roundTripsTheDefaultShapedLayout() {
        DockState original = DockState.of(DockLayout.centerOnly()
                .dockToSide("p.toolbox", Side.LEFT)
                .dockToSide("p.props", Side.RIGHT)
                .dockBeside("p.objects", "p.props")
                .withSelected("p.props"));

        DockState decoded =
                DockStateCodec.decode(DockStateCodec.encode(original), VALID).orElseThrow();

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripsAutoHiddenAndFloatingPanels() {
        DockState original = DockState.of(DockLayout.centerOnly()
                        .dockToSide("p.toolbox", Side.LEFT)
                        .dockToSide("p.props", Side.RIGHT))
                .autoHidePanel("p.toolbox")
                .floatPanel("p.props");

        DockState decoded =
                DockStateCodec.decode(DockStateCodec.encode(original), VALID).orElseThrow();

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.autoHidden()).containsEntry("p.toolbox", Side.LEFT);
        assertThat(decoded.floating()).containsExactly("p.props");
    }

    @Test
    void roundTripsDeeplyNestedSplits() {
        DockState original = DockState.of(DockLayout.centerOnly()
                .dockToSide("p.toolbox", Side.LEFT)
                .dockToSide("p.props", Side.RIGHT)
                .dockToSide("p.layers", Side.BOTTOM)
                .dockToSide("p.objects", Side.TOP));

        DockState decoded =
                DockStateCodec.decode(DockStateCodec.encode(original), VALID).orElseThrow();

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void garbageDecodesToEmpty() {
        assertThat(DockStateCodec.decode("nonsense", VALID)).isEmpty();
        assertThat(DockStateCodec.decode("", VALID)).isEmpty();
        assertThat(DockStateCodec.decode("v1|S[H;;]||", VALID)).isEmpty();
        assertThat(DockStateCodec.decode("v2|C||", VALID)).isEmpty(); // future version: discard
    }

    @Test
    void unknownPanelIdsInvalidateThePersistedState() {
        String encoded =
                DockStateCodec.encode(DockState.of(DockLayout.centerOnly().dockToSide("p.removed-in-v2", Side.LEFT)));

        assertThat(DockStateCodec.decode(encoded, VALID)).isEmpty();
    }

    @Test
    void structurallyInvalidStateDecodesToEmptyNotThrow() {
        // Panel present both in the tree and the floating set.
        String twoPlaces = "v1|S[H;0.25;{G[p.props;p.props]}{C}]||p.props";

        assertThat(DockStateCodec.decode(twoPlaces, VALID)).isEmpty();
    }

    @Test
    void withSelectedSurvivesTheRoundTrip() {
        DockLayout layout = DockLayout.centerOnly()
                .dockToSide("p.props", Side.RIGHT)
                .dockBeside("p.objects", "p.props")
                .withSelected("p.props");
        assertThat(layout.groupOf("p.props").orElseThrow().selectedId()).isEqualTo("p.props");

        DockState decoded = DockStateCodec.decode(DockStateCodec.encode(DockState.of(layout)), VALID)
                .orElseThrow();

        assertThat(decoded.layout().groupOf("p.props").orElseThrow().selectedId())
                .isEqualTo("p.props");
    }

    @Test
    void encodedFormIsSingleLine() {
        String encoded =
                DockStateCodec.encode(DockState.of(DockLayout.centerOnly().dockToSide("p.toolbox", Side.LEFT)));

        assertThat(encoded).doesNotContain("\n").startsWith("v1|");
    }
}
