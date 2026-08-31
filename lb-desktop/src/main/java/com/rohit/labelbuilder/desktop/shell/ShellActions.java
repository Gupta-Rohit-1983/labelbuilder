package com.rohit.labelbuilder.desktop.shell;

import static com.rohit.labelbuilder.desktop.action.AppAction.action;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.canvas.CanvasCommands;
import jakarta.annotation.PostConstruct;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;

/**
 * Defines and registers the shell's standard actions. Handlers are placeholders (posting to the
 * status bar) until their real implementations land — file ops in Phase 14, undo/redo in Phase 8,
 * print in 13, zoom in 6a. Enablement is honest: the CommandStack engine now exists in lb-core
 * (Phase 7c), but undo/redo stay disabled until the editor is backed by a live document in Phase 8,
 * which is when these actions get wired to a stack.
 *
 * <p>Cut/copy/paste carry no accelerators deliberately: scene-wide Ctrl+X/C/V would shadow
 * {@code TextInputControl}'s built-in clipboard handling. They get focus-aware routing when the
 * ActionRegistry learns about edit contexts (Phase 7c).
 */
@Component
public class ShellActions {

    public static final String FILE_NEW = "file.new";
    public static final String FILE_OPEN = "file.open";
    public static final String FILE_SAVE = "file.save";
    public static final String FILE_SAVE_AS = "file.saveAs";
    public static final String FILE_PRINT = "file.print";
    public static final String FILE_EXIT = "file.exit";
    public static final String EDIT_UNDO = "edit.undo";
    public static final String EDIT_REDO = "edit.redo";
    public static final String EDIT_CUT = "edit.cut";
    public static final String EDIT_COPY = "edit.copy";
    public static final String EDIT_PASTE = "edit.paste";
    public static final String VIEW_ZOOM_IN = "view.zoomIn";
    public static final String VIEW_ZOOM_OUT = "view.zoomOut";
    public static final String VIEW_ZOOM_FIT = "view.zoomFit";
    public static final String HELP_ABOUT = "help.about";

    private final ActionRegistry registry;
    private final StatusBus status;
    private final BuildInfo buildInfo;
    private final CanvasCommands canvas;

    public ShellActions(ActionRegistry registry, StatusBus status, BuildInfo buildInfo, CanvasCommands canvas) {
        this.registry = registry;
        this.status = status;
        this.buildInfo = buildInfo;
        this.canvas = canvas;
    }

    @PostConstruct
    void registerAll() {
        registry.register(action(FILE_NEW)
                .text("_New Label…")
                .longText("Create a new label")
                .accelerator("Shortcut+N")
                .onAction(() -> status.post("New label — not yet implemented (Phase 14)"))
                .build());
        registry.register(action(FILE_OPEN)
                .text("_Open…")
                .longText("Open a label file")
                .accelerator("Shortcut+O")
                .onAction(() -> status.post("Open — not yet implemented (Phase 14)"))
                .build());
        registry.register(action(FILE_SAVE)
                .text("_Save")
                .longText("Save the current label")
                .accelerator("Shortcut+S")
                .onAction(() -> status.post("Save — not yet implemented (Phase 14)"))
                .build());
        registry.register(action(FILE_SAVE_AS)
                .text("Save _As…")
                .longText("Save the current label under a new name")
                .accelerator("Shortcut+Shift+S")
                .onAction(() -> status.post("Save As — not yet implemented (Phase 14)"))
                .build());
        registry.register(action(FILE_PRINT)
                .text("_Print…")
                .longText("Print the current label")
                .accelerator("Shortcut+P")
                .onAction(() -> status.post("Print — not yet implemented (Phase 13)"))
                .build());
        registry.register(action(FILE_EXIT)
                .text("E_xit")
                .longText("Exit LabelBuilder")
                .onAction(ShellActions::requestClose)
                .build());

        registry.register(action(EDIT_UNDO)
                .text("_Undo")
                .accelerator("Shortcut+Z")
                .enabled(false) // wired to a CommandStack when the editor gets a live document — Phase 8
                .onAction(() -> status.post("Undo"))
                .build());
        registry.register(action(EDIT_REDO)
                .text("_Redo")
                .accelerator("Shortcut+Y")
                .enabled(false) // wired to a CommandStack when the editor gets a live document — Phase 8
                .onAction(() -> status.post("Redo"))
                .build());
        registry.register(action(EDIT_CUT)
                .text("Cu_t")
                .onAction(() -> status.post("Cut — not yet implemented (Phase 8)"))
                .build());
        registry.register(action(EDIT_COPY)
                .text("_Copy")
                .onAction(() -> status.post("Copy — not yet implemented (Phase 8)"))
                .build());
        registry.register(action(EDIT_PASTE)
                .text("_Paste")
                .onAction(() -> status.post("Paste — not yet implemented (Phase 8)"))
                .build());

        registry.register(action(VIEW_ZOOM_IN)
                .text("Zoom _In")
                .accelerator("Shortcut+Equals")
                .onAction(canvas::zoomIn)
                .build());
        registry.register(action(VIEW_ZOOM_OUT)
                .text("Zoom _Out")
                .accelerator("Shortcut+Minus")
                .onAction(canvas::zoomOut)
                .build());
        registry.register(action(VIEW_ZOOM_FIT)
                .text("_Fit to Window")
                .accelerator("Shortcut+Digit0")
                .onAction(canvas::zoomToFit)
                .build());

        registry.register(action(HELP_ABOUT)
                .text("_About LabelBuilder")
                .onAction(this::showAbout)
                .build());
    }

    /**
     * Fires a close <em>request</em> on the showing window (never {@code Stage.close()}) so the
     * same path runs as the window-manager close button: onCloseRequest veto (Phase 14), then
     * onHiding state save, then context shutdown.
     */
    private static void requestClose() {
        Window.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .ifPresent(window -> window.fireEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST)));
    }

    private void showAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About " + buildInfo.appName());
        about.setHeaderText(buildInfo.appName() + " " + buildInfo.version());
        about.setContentText("Label design and printing suite.\n\nRunning on Java %s, JavaFX %s."
                .formatted(System.getProperty("java.version"), System.getProperty("javafx.version")));
        Window.getWindows().stream().filter(Window::isShowing).findFirst().ifPresent(about::initOwner);
        about.showAndWait();
    }
}
