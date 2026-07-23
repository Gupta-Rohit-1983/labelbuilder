package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the registered action set headlessly (no FX toolkit, no Spring context). */
class ShellActionsTest {

    private ActionRegistry registry;
    private StatusBus status;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        status = new StatusBus();
        new ShellActions(registry, status, new BuildInfo("LabelBuilder", "test")).registerAll();
    }

    @Test
    void everyDeclaredActionIdIsRegistered() throws Exception {
        for (String id : declaredActionIds()) {
            assertThatCode(() -> registry.get(id)).as("action id '%s'", id).doesNotThrowAnyException();
        }
    }

    @Test
    void placeholderActionsPostToTheStatusBar() {
        registry.get(ShellActions.FILE_NEW).run();

        assertThat(status.messageProperty().get()).contains("not yet implemented");
    }

    @Test
    void undoRedoStartDisabledUntilCommandStackExists() {
        assertThat(registry.get(ShellActions.EDIT_UNDO).enabledProperty().get()).isFalse();
        assertThat(registry.get(ShellActions.EDIT_REDO).enabledProperty().get()).isFalse();

        // And their handlers must not fire while disabled.
        registry.get(ShellActions.EDIT_UNDO).run();
        assertThat(status.messageProperty().get()).isEqualTo("Ready");
    }

    @Test
    void clipboardActionsCarryNoAccelerators() {
        // Scene-wide Ctrl+X/C/V would shadow TextInputControl's clipboard handling; the routing
        // becomes focus-aware in Phase 7c. Until then these must stay accelerator-free.
        assertThat(registry.get(ShellActions.EDIT_CUT).accelerator()).isNull();
        assertThat(registry.get(ShellActions.EDIT_COPY).accelerator()).isNull();
        assertThat(registry.get(ShellActions.EDIT_PASTE).accelerator()).isNull();
    }

    /** All public static final String fields of {@link ShellActions} — the action id constants. */
    private static List<String> declaredActionIds() throws IllegalAccessException {
        List<String> ids = new java.util.ArrayList<>();
        for (Field field : ShellActions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class) {
                ids.add((String) field.get(null));
            }
        }
        return ids;
    }
}
