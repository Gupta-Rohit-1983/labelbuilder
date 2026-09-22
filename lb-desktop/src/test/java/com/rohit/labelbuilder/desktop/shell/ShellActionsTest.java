package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.canvas.CanvasCommands;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.desktop.document.EditActions;
import com.rohit.labelbuilder.desktop.document.ElementClipboard;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the registered action set headlessly (no FX toolkit, no Spring context). */
class ShellActionsTest {

    private ActionRegistry registry;
    private StatusBus status;
    private DocumentSession session;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        status = new StatusBus();
        session = new DocumentSession();
        ElementClipboard clipboard = new ElementClipboard();
        new ShellActions(
                        registry,
                        status,
                        new BuildInfo("LabelBuilder", "test"),
                        new CanvasCommands(),
                        session,
                        new EditActions(session, clipboard),
                        clipboard)
                .registerAll();
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
    void undoRedoAreDisabledOnAFreshDocument() {
        assertThat(registry.get(ShellActions.EDIT_UNDO).enabledProperty().get()).isFalse();
        assertThat(registry.get(ShellActions.EDIT_REDO).enabledProperty().get()).isFalse();
    }

    @Test
    void undoRedoTrackTheCommandStackAndDriveTheDocument() {
        session.execute(new AddElementCommand(rectangle()));

        assertThat(registry.get(ShellActions.EDIT_UNDO).enabledProperty().get()).isTrue();
        assertThat(registry.get(ShellActions.EDIT_REDO).enabledProperty().get()).isFalse();

        registry.get(ShellActions.EDIT_UNDO).run();

        assertThat(session.document().elements()).isEmpty();
        assertThat(registry.get(ShellActions.EDIT_UNDO).enabledProperty().get()).isFalse();
        assertThat(registry.get(ShellActions.EDIT_REDO).enabledProperty().get()).isTrue();

        registry.get(ShellActions.EDIT_REDO).run();

        assertThat(session.document().elements()).hasSize(1);
    }

    @Test
    void insertActionsAreRegisteredForEveryCreationTool() {
        for (String id : List.of(
                ShellActions.INSERT_TEXT,
                ShellActions.INSERT_RECTANGLE,
                ShellActions.INSERT_ELLIPSE,
                ShellActions.INSERT_LINE,
                ShellActions.INSERT_IMAGE,
                ShellActions.INSERT_BARCODE)) {
            assertThatCode(() -> registry.get(id)).as("insert action '%s'", id).doesNotThrowAnyException();
            assertThat(registry.get(id).enabledProperty().get()).isTrue();
        }
    }

    private static RectangleElement rectangle() {
        return RectangleElement.of(
                ElementProperties.of("r1", "r1", "layer-1", new Bounds(0, 0, 10, 10)), Stroke.solid(0.2), Fill.none());
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
