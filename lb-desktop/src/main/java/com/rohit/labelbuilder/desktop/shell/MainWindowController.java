package com.rohit.labelbuilder.desktop.shell;

import static com.rohit.labelbuilder.desktop.action.ActionRegistry.SEPARATOR;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_COPY;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_CUT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_PASTE;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_REDO;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.EDIT_UNDO;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_EXIT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_NEW;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_OPEN;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_PRINT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_SAVE;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.FILE_SAVE_AS;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.HELP_ABOUT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_FIT;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_IN;
import static com.rohit.labelbuilder.desktop.shell.ShellActions.VIEW_ZOOM_OUT;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.canvas.CanvasCommands;
import com.rohit.labelbuilder.desktop.canvas.CanvasView;
import com.rohit.labelbuilder.desktop.canvas.DesignCanvas;
import com.rohit.labelbuilder.desktop.dock.DockPanelRegistry;
import com.rohit.labelbuilder.desktop.dock.DockState;
import com.rohit.labelbuilder.desktop.dock.DockStatePreferences;
import com.rohit.labelbuilder.desktop.dock.DockStation;
import com.rohit.labelbuilder.desktop.dock.DockStationBuilder;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller of the main window shell.
 *
 * <p>A Spring bean (created via {@link FxmlViewLoader}'s controller factory), so services inject
 * through the constructor. Prototype-scoped: every FXML load must get a fresh controller — FXML
 * controllers hold per-view node references and are never shareable singletons.
 *
 * <p>The FXML supplies layout only; the menus are generated here from the {@link ActionRegistry}
 * and the ribbon from {@link ShellRibbon}'s spec, so this class decides <em>which</em> commands
 * appear <em>where</em> while the registry owns what they say and do.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainWindowController {

    private final ActionRegistry actions;
    private final StatusBus statusBus;
    private final ShellRibbon ribbon;
    private final RibbonStatePreferences ribbonState;
    private final DockStationBuilder dockBuilder;
    private final DockPanelRegistry dockPanels;
    private final DockStatePreferences dockState;
    private final CanvasCommands canvasCommands;

    @FXML
    private VBox topBox;

    @FXML
    private MenuBar menuBar;

    @FXML
    private HBox quickAccessBar;

    @FXML
    private StackPane dockingArea;

    @FXML
    private Label statusMessage;

    @FXML
    private Label cursorPositionLabel;

    @FXML
    private Label zoomLabel;

    @FXML
    private Label environmentLabel;

    public MainWindowController(
            ActionRegistry actions,
            StatusBus statusBus,
            ShellRibbon ribbon,
            RibbonStatePreferences ribbonState,
            DockStationBuilder dockBuilder,
            DockPanelRegistry dockPanels,
            DockStatePreferences dockState,
            CanvasCommands canvasCommands) {
        this.actions = actions;
        this.statusBus = statusBus;
        this.ribbon = ribbon;
        this.ribbonState = ribbonState;
        this.dockBuilder = dockBuilder;
        this.dockPanels = dockPanels;
        this.dockState = dockState;
        this.canvasCommands = canvasCommands;
    }

    @FXML
    private void initialize() {
        menuBar.getMenus()
                .setAll(
                        actions.createMenu(
                                "_File",
                                FILE_NEW,
                                FILE_OPEN,
                                SEPARATOR,
                                FILE_SAVE,
                                FILE_SAVE_AS,
                                SEPARATOR,
                                FILE_PRINT,
                                SEPARATOR,
                                FILE_EXIT),
                        actions.createMenu("_Edit", EDIT_UNDO, EDIT_REDO, SEPARATOR, EDIT_CUT, EDIT_COPY, EDIT_PASTE),
                        actions.createMenu("_View", VIEW_ZOOM_IN, VIEW_ZOOM_OUT, VIEW_ZOOM_FIT),
                        actions.createMenu("_Help", HELP_ABOUT));
        ribbon.quickAccessActionIds().forEach(id -> {
            var button = actions.createToolBarButton(id);
            button.getStyleClass().add("qat-button");
            quickAccessBar.getChildren().add(button);
        });

        var ribbonPane = ribbon.create();
        ribbonState.bind(ribbonPane);
        topBox.getChildren().add(ribbonPane);

        // The design canvas (framed with rulers by CanvasView) occupies the docking Center slot;
        // the View menu/ribbon zoom actions route to the canvas via CanvasCommands.
        DesignCanvas canvas = new DesignCanvas();
        CanvasView canvasView = new CanvasView(canvas);
        canvasCommands.setActive(canvas);

        // Restore the persisted workspace (default: StandardPanels.defaultLayout()); persist every
        // docking change.
        DockState initial = dockState.load(DockState.of(StandardPanels.defaultLayout()), dockPanels.ids());
        DockStation station = new DockStation(dockBuilder, dockPanels, canvasView, initial);
        station.stateProperty().addListener((obs, old, current) -> dockState.save(current));
        dockingArea.getChildren().setAll(station);

        statusMessage.textProperty().bind(statusBus.messageProperty());
        cursorPositionLabel
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            Point2D p = canvas.pointerMmProperty().get();
                            return p == null ? "—" : String.format("%.1f, %.1f mm", p.getX(), p.getY());
                        },
                        canvas.pointerMmProperty()));
        zoomLabel
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> Math.round(canvas.viewportProperty().get().zoom() * 100) + "%",
                        canvas.viewportProperty()));
        environmentLabel.setText("Java %s · JavaFX %s"
                .formatted(System.getProperty("java.version"), System.getProperty("javafx.version")));
    }
}
