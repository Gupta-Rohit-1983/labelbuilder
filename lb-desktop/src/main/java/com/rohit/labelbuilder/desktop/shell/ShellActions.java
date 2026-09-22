package com.rohit.labelbuilder.desktop.shell;

import static com.rohit.labelbuilder.desktop.action.AppAction.action;

import com.rohit.labelbuilder.core.edit.Align;
import com.rohit.labelbuilder.core.edit.Distribute;
import com.rohit.labelbuilder.core.edit.ElementKind;
import com.rohit.labelbuilder.core.edit.ZOrder;
import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.canvas.CanvasCommands;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.desktop.document.EditActions;
import com.rohit.labelbuilder.desktop.document.ElementClipboard;
import jakarta.annotation.PostConstruct;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;

/**
 * Defines and registers the shell's standard actions. Handlers are placeholders (posting to the
 * status bar) until their real implementations land — file ops in Phase 14, print in 13.
 *
 * <p>Undo/redo are live as of Phase 8a: they drive the {@link DocumentSession}'s command stack and
 * their enablement is <em>bound</em> to it, so they grey out exactly when there is nothing to undo or
 * redo. The insert actions arm a creation tool on the canvas; the next click or drag places the
 * element (and every placement is itself undoable).
 *
 * <p>Cut/copy/paste still carry no accelerators here, deliberately: a scene-wide Ctrl+X/C/V would
 * shadow {@code TextInputControl}'s built-in clipboard handling. Instead the design canvas binds
 * those keys itself (Phase 8c), so they act on elements only while the canvas has focus and keep
 * their normal meaning inside any text field.
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
    public static final String INSERT_TEXT = "insert.text";
    public static final String INSERT_RECTANGLE = "insert.rectangle";
    public static final String INSERT_ELLIPSE = "insert.ellipse";
    public static final String INSERT_LINE = "insert.line";
    public static final String INSERT_IMAGE = "insert.image";
    public static final String INSERT_BARCODE = "insert.barcode";
    public static final String EDIT_DUPLICATE = "edit.duplicate";
    public static final String ARRANGE_ALIGN_LEFT = "arrange.alignLeft";
    public static final String ARRANGE_ALIGN_CENTER = "arrange.alignCenter";
    public static final String ARRANGE_ALIGN_RIGHT = "arrange.alignRight";
    public static final String ARRANGE_ALIGN_TOP = "arrange.alignTop";
    public static final String ARRANGE_ALIGN_MIDDLE = "arrange.alignMiddle";
    public static final String ARRANGE_ALIGN_BOTTOM = "arrange.alignBottom";
    public static final String ARRANGE_DISTRIBUTE_H = "arrange.distributeHorizontally";
    public static final String ARRANGE_DISTRIBUTE_V = "arrange.distributeVertically";
    public static final String EDIT_DELETE = "edit.delete";
    public static final String EDIT_GROUP = "edit.group";
    public static final String EDIT_UNGROUP = "edit.ungroup";
    public static final String ARRANGE_BRING_TO_FRONT = "arrange.bringToFront";
    public static final String ARRANGE_BRING_FORWARD = "arrange.bringForward";
    public static final String ARRANGE_SEND_BACKWARD = "arrange.sendBackward";
    public static final String ARRANGE_SEND_TO_BACK = "arrange.sendToBack";
    public static final String VIEW_ZOOM_IN = "view.zoomIn";
    public static final String VIEW_ZOOM_OUT = "view.zoomOut";
    public static final String VIEW_ZOOM_FIT = "view.zoomFit";
    public static final String HELP_ABOUT = "help.about";

    private final ActionRegistry registry;
    private final StatusBus status;
    private final BuildInfo buildInfo;
    private final CanvasCommands canvas;
    private final DocumentSession session;
    private final EditActions edit;
    private final ElementClipboard clipboard;

    public ShellActions(
            ActionRegistry registry,
            StatusBus status,
            BuildInfo buildInfo,
            CanvasCommands canvas,
            DocumentSession session,
            EditActions edit,
            ElementClipboard clipboard) {
        this.registry = registry;
        this.status = status;
        this.buildInfo = buildInfo;
        this.canvas = canvas;
        this.session = session;
        this.edit = edit;
        this.clipboard = clipboard;
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
                .longText("Undo the last change")
                .accelerator("Shortcut+Z")
                .onAction(session::undo)
                .build());
        registry.register(action(EDIT_REDO)
                .text("_Redo")
                .longText("Redo the last undone change")
                .accelerator("Shortcut+Y")
                .onAction(session::redo)
                .build());
        // Bound, not set: enablement now tracks the command stack for the life of the app.
        registry.get(EDIT_UNDO).enabledProperty().bind(session.canUndoProperty());
        registry.get(EDIT_REDO).enabledProperty().bind(session.canRedoProperty());
        registry.register(action(EDIT_CUT)
                .text("Cu_t")
                .longText("Cut the selected elements")
                .onAction(edit::cut)
                .build());
        registry.register(action(EDIT_COPY)
                .text("_Copy")
                .longText("Copy the selected elements")
                .onAction(edit::copy)
                .build());
        registry.register(action(EDIT_PASTE)
                .text("_Paste")
                .longText("Paste elements from the clipboard")
                .onAction(edit::paste)
                .build());
        registry.register(action(EDIT_DELETE)
                .text("_Delete")
                .longText("Delete the selected elements")
                .onAction(edit::delete)
                .build());
        registry.register(action(EDIT_GROUP)
                .text("_Group")
                .longText("Group the selected elements")
                .accelerator("Shortcut+G")
                .onAction(edit::group)
                .build());
        registry.register(action(EDIT_UNGROUP)
                .text("_Ungroup")
                .longText("Dissolve the selected groups")
                .accelerator("Shortcut+Shift+G")
                .onAction(edit::ungroup)
                .build());
        bindToSelection(EDIT_CUT, 1);
        bindToSelection(EDIT_COPY, 1);
        bindToSelection(EDIT_DELETE, 1);
        bindToSelection(EDIT_GROUP, 2);
        bindToSelection(EDIT_UNGROUP, 1);
        registry.get(EDIT_PASTE).enabledProperty().bind(clipboard.hasContentProperty());

        registerReorder(ARRANGE_BRING_TO_FRONT, "Bring to _Front", ZOrder.BRING_TO_FRONT);
        registerReorder(ARRANGE_BRING_FORWARD, "Bring F_orward", ZOrder.BRING_FORWARD);
        registerReorder(ARRANGE_SEND_BACKWARD, "Send Back_ward", ZOrder.SEND_BACKWARD);
        registerReorder(ARRANGE_SEND_TO_BACK, "Send to _Back", ZOrder.SEND_TO_BACK);

        registry.register(action(EDIT_DUPLICATE)
                .text("_Duplicate")
                .longText("Duplicate the selected elements")
                .accelerator("Shortcut+D")
                .onAction(edit::duplicate)
                .build());
        registry.get(EDIT_DUPLICATE)
                .enabledProperty()
                .bind(session.selectionCountProperty().greaterThanOrEqualTo(1));

        registerAlign(ARRANGE_ALIGN_LEFT, "Align _Left", Align.LEFT);
        registerAlign(ARRANGE_ALIGN_CENTER, "Align _Center", Align.CENTER);
        registerAlign(ARRANGE_ALIGN_RIGHT, "Align _Right", Align.RIGHT);
        registerAlign(ARRANGE_ALIGN_TOP, "Align _Top", Align.TOP);
        registerAlign(ARRANGE_ALIGN_MIDDLE, "Align _Middle", Align.MIDDLE);
        registerAlign(ARRANGE_ALIGN_BOTTOM, "Align _Bottom", Align.BOTTOM);
        registerDistribute(ARRANGE_DISTRIBUTE_H, "Distribute _Horizontally", Distribute.HORIZONTALLY);
        registerDistribute(ARRANGE_DISTRIBUTE_V, "Distribute _Vertically", Distribute.VERTICALLY);

        registerInsert(INSERT_TEXT, "_Text", ElementKind.TEXT);
        registerInsert(INSERT_RECTANGLE, "_Rectangle", ElementKind.RECTANGLE);
        registerInsert(INSERT_ELLIPSE, "_Ellipse", ElementKind.ELLIPSE);
        registerInsert(INSERT_LINE, "_Line", ElementKind.LINE);
        registerInsert(INSERT_IMAGE, "_Image", ElementKind.IMAGE);
        registerInsert(INSERT_BARCODE, "_Barcode", ElementKind.BARCODE);

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

    /** Enables the action only once at least {@code minimum} elements are selected. */
    private void bindToSelection(String id, int minimum) {
        registry.get(id).enabledProperty().bind(session.selectionCountProperty().greaterThanOrEqualTo(minimum));
    }

    /** Registers a z-order action; restacking needs something selected. */
    private void registerReorder(String id, String text, ZOrder move) {
        registry.register(action(id)
                .text(text)
                .longText(move.displayName())
                .onAction(() -> edit.reorder(move))
                .build());
        bindToSelection(id, 1);
    }

    /** Aligning is meaningless below two elements, so these enable only on a multi-selection. */
    private void registerAlign(String id, String text, Align align) {
        registry.register(action(id)
                .text(text)
                .longText(align.displayName() + " within the selection")
                .onAction(() -> edit.align(align))
                .build());
        registry.get(id).enabledProperty().bind(session.selectionCountProperty().greaterThanOrEqualTo(2));
    }

    /** Distributing needs three: with two there is nothing between the ends to space out. */
    private void registerDistribute(String id, String text, Distribute axis) {
        registry.register(action(id)
                .text(text)
                .longText(axis.displayName() + " (needs three or more elements)")
                .onAction(() -> edit.distribute(axis))
                .build());
        registry.get(id).enabledProperty().bind(session.selectionCountProperty().greaterThanOrEqualTo(3));
    }

    /** Registers an insert action that arms the matching creation tool on the canvas. */
    private void registerInsert(String id, String text, ElementKind kind) {
        registry.register(action(id)
                .text(text)
                .longText("Insert " + kind.displayName().toLowerCase(java.util.Locale.ROOT)
                        + " — click or drag on the label to place it")
                .onAction(() -> canvas.setTool(kind))
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
