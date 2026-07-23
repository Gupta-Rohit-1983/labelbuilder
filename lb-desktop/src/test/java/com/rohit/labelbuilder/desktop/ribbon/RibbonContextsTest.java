package com.rohit.labelbuilder.desktop.ribbon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.SetChangeListener;
import org.junit.jupiter.api.Test;

/** javafx.collections needs no toolkit — fully headless. */
class RibbonContextsTest {

    private final RibbonContexts contexts = new RibbonContexts();

    @Test
    void activateAndDeactivateMaintainTheSet() {
        contexts.activate("selection.barcode");
        assertThat(contexts.active()).containsExactly("selection.barcode");

        contexts.activate("selection.barcode"); // idempotent
        assertThat(contexts.active()).hasSize(1);

        contexts.deactivate("selection.barcode");
        assertThat(contexts.active()).isEmpty();
    }

    @Test
    void listenersFireOnChange() {
        List<String> events = new ArrayList<>();
        contexts.active().addListener((SetChangeListener<String>) change ->
                events.add(change.wasAdded() ? "+" + change.getElementAdded() : "-" + change.getElementRemoved()));

        contexts.activate("selection.text");
        contexts.deactivate("selection.text");

        assertThat(events).containsExactly("+selection.text", "-selection.text");
    }

    @Test
    void exposedViewRejectsDirectMutation() {
        assertThatThrownBy(() -> contexts.active().add("rogue")).isInstanceOf(UnsupportedOperationException.class);
    }
}
