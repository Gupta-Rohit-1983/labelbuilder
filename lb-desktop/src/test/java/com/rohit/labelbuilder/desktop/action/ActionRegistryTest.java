package com.rohit.labelbuilder.desktop.action;

import static com.rohit.labelbuilder.desktop.action.AppAction.action;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Headless-safe: only {@link MenuItem}s are created ({@code MenuItem} is not a {@code Node}). */
class ActionRegistryTest {

    private ActionRegistry registry;
    private AtomicInteger invocations;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        invocations = new AtomicInteger();
        registry.register(action("test.hello")
                .text("_Hello")
                .longText("Says hello")
                .accelerator("Shortcut+H")
                .onAction(invocations::incrementAndGet)
                .build());
    }

    @Test
    void unknownIdFailsFast() {
        assertThatThrownBy(() -> registry.get("no.such.action"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no.such.action");
    }

    @Test
    void duplicateRegistrationFailsFast() {
        assertThatThrownBy(() -> registry.register(
                        action("test.hello").text("x").onAction(() -> {}).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.hello");
    }

    @Test
    void menuItemRendersTheAction() {
        MenuItem item = registry.createMenuItem("test.hello");

        assertThat(item.getText()).isEqualTo("_Hello");
        assertThat(item.getAccelerator()).isEqualTo(KeyCombination.valueOf("Shortcut+H"));
        assertThat(item.isDisable()).isFalse();

        item.getOnAction().handle(null);
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void enablementPropagatesLiveToRenderedControls() {
        MenuItem item = registry.createMenuItem("test.hello");

        registry.get("test.hello").setEnabled(false);

        assertThat(item.isDisable()).isTrue();
    }

    @Test
    void disabledActionDoesNotRun() {
        registry.get("test.hello").setEnabled(false);

        registry.get("test.hello").run();

        assertThat(invocations.get()).isZero();
    }

    @Test
    void createMenuBuildsItemsInDeclarationOrder() {
        // No SEPARATOR entries here: SeparatorMenuItem's static init requires the FX toolkit, so
        // the separator branch is exercised by the real UI (TestFX, Phase 19), not headlessly.
        registry.register(action("test.other").text("Other").onAction(() -> {}).build());

        Menu menu = registry.createMenu("_Test", "test.hello", "test.other");

        assertThat(menu.getText()).isEqualTo("_Test");
        assertThat(menu.getItems()).extracting(MenuItem::getText).containsExactly("_Hello", "Other");
    }
}
