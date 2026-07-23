package com.rohit.labelbuilder.desktop.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DockPanelRegistryTest {

    private final DockPanelRegistry registry = new DockPanelRegistry();

    // The content factory is never invoked headlessly, so a null-returning supplier is fine here.
    private static DockPanel panel(String id) {
        return new DockPanel(id, "Title of " + id, () -> null);
    }

    @Test
    void registersAndRetrieves() {
        registry.register(panel("p.properties"));

        assertThat(registry.get("p.properties").title()).isEqualTo("Title of p.properties");
        assertThat(registry.ids()).containsExactly("p.properties");
    }

    @Test
    void unknownIdFailsFast() {
        assertThatThrownBy(() -> registry.get("p.ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p.ghost");
    }

    @Test
    void duplicateRegistrationFailsFast() {
        registry.register(panel("p.twice"));

        assertThatThrownBy(() -> registry.register(panel("p.twice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p.twice");
    }
}
