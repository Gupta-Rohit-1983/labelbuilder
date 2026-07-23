package com.rohit.labelbuilder.desktop.action;

import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.input.KeyCombination;

/**
 * A user-invokable command, defined once and rendered anywhere (menu item, toolbar button, later
 * ribbon button, context-menu entry) — FR-X-02.
 *
 * <p>Holds presentation (text, tooltip, accelerator), state (enablement, as a JavaFX property so
 * every rendered control stays in sync automatically) and behaviour (the handler). Controls are
 * <em>created from</em> actions by {@link ActionRegistry}; nothing should hand-wire an
 * {@code onAction} anywhere else.
 */
public final class AppAction {

    private final String id;
    private final String text;
    private final String longText;
    private final KeyCombination accelerator;
    private final Runnable handler;
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);

    private AppAction(Builder builder) {
        this.id = builder.id;
        this.text = Objects.requireNonNull(builder.text, "text required for action " + builder.id);
        this.longText = builder.longText;
        this.accelerator = builder.accelerator;
        this.handler = Objects.requireNonNull(builder.handler, "handler required for action " + builder.id);
        this.enabled.set(builder.enabled);
    }

    public static Builder action(String id) {
        return new Builder(id);
    }

    /** Runs the handler; a no-op while disabled (guards accelerator/programmatic invocation). */
    public void run() {
        if (enabled.get()) {
            handler.run();
        }
    }

    public String id() {
        return id;
    }

    /** Display text, with {@code _} marking the mnemonic (menu semantics). */
    public String text() {
        return text;
    }

    /** Tooltip / status-line description; may be null. */
    public String longText() {
        return longText;
    }

    /** May be null — not every action has a shortcut. */
    public KeyCombination accelerator() {
        return accelerator;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public static final class Builder {

        private final String id;
        private String text;
        private String longText;
        private KeyCombination accelerator;
        private Runnable handler;
        private boolean enabled = true;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "action id");
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder longText(String longText) {
            this.longText = longText;
            return this;
        }

        /** @param combination parsed via {@link KeyCombination#valueOf}, e.g. {@code "Shortcut+N"} */
        public Builder accelerator(String combination) {
            this.accelerator = KeyCombination.valueOf(combination);
            return this;
        }

        public Builder onAction(Runnable handler) {
            this.handler = handler;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AppAction build() {
            return new AppAction(this);
        }
    }
}
