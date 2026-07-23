package com.rohit.labelbuilder.desktop.dock;

import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * The docking area control: renders the current {@link DockState} (via {@link
 * DockStationBuilder}) and layers the interactions on top — drag-to-dock (5b), float to a utility
 * window, and auto-hide to edge button bars with a slide-over drawer (5c).
 *
 * <p>All state outcomes are pure transforms on {@link DockState}/{@link DockMoves}; this class
 * contains interaction wiring only. Gestures:
 *
 * <ul>
 *   <li>Drag a panel tab — a drop-zone overlay offers the four workspace sides; dropping on
 *       another group's header docks into that group.
 *   <li>Tab context menu — Float (own window; closing it re-docks) and Auto Hide (collapses to
 *       the edge bar; the edge button opens a drawer over the workspace; the drawer's Pin button
 *       re-docks).
 * </ul>
 */
public class DockStation extends StackPane {

    /** Dragboard format carrying the dragged panel id. */
    public static final DataFormat PANEL_ID_FORMAT = new DataFormat("application/x-labelbuilder-panel");

    private static final double ZONE_THICKNESS = 56;
    private static final double DRAWER_SIZE = 280;

    private final DockStationBuilder builder;
    private final DockPanelRegistry panels;
    private final Node center;
    private final ReadOnlyObjectWrapper<DockState> state = new ReadOnlyObjectWrapper<>();
    private final BorderPane frame = new BorderPane();
    private final VBox leftBar = sidebar();
    private final VBox rightBar = sidebar();
    private final AnchorPane overlay = new AnchorPane();
    private final Map<String, Stage> floatingStages = new HashMap<>();
    private Node drawer;

    public DockStation(DockStationBuilder builder, DockPanelRegistry panels, Node center, DockState initialState) {
        this.builder = builder;
        this.panels = panels;
        this.center = center;
        getStyleClass().add("dock-station");
        frame.setLeft(leftBar);
        frame.setRight(rightBar);
        buildOverlay();
        installStationDragHandlers();
        getChildren().setAll(frame, overlay);
        setState(initialState);
    }

    public DockState getState() {
        return state.get();
    }

    /** Applies new docking state and re-renders everything derived from it. */
    public final void setState(DockState newState) {
        state.set(newState);
        frame.setCenter(builder.build(newState.layout(), center, this::decorateTab, this::installGroupDropTarget));
        rebuildSidebars(newState);
        syncFloatingStages(newState);
        closeDrawer();
        overlay.setVisible(false);
    }

    /** Observable state — 5d persists from here. */
    public ReadOnlyObjectProperty<DockState> stateProperty() {
        return state.getReadOnlyProperty();
    }

    // ---- tabs: drag source + float/auto-hide menu ---------------------------------------

    private void decorateTab(Tab tab, String panelId) {
        Label handle = new Label(tab.getText());
        tab.setText("");
        tab.setGraphic(handle);
        handle.setOnDragDetected(event -> {
            Dragboard dragboard = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(PANEL_ID_FORMAT, panelId);
            content.putString(panelId);
            dragboard.setContent(content);
            event.consume();
        });
        handle.setOnDragDone(event -> overlay.setVisible(false));

        MenuItem floatItem = new MenuItem("Float");
        floatItem.setOnAction(e -> setState(getState().floatPanel(panelId)));
        MenuItem autoHideItem = new MenuItem("Auto Hide");
        autoHideItem.setOnAction(e -> setState(getState().autoHidePanel(panelId)));
        tab.setContextMenu(new ContextMenu(floatItem, autoHideItem));
    }

    // ---- drop targets -------------------------------------------------------------------

    private void installGroupDropTarget(TabPane tabPane, DockLayout.DockNode.Group group) {
        tabPane.setOnDragOver(event -> {
            String draggedId = draggedPanelId(event);
            if (draggedId != null && !group.panelIds().contains(draggedId)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        tabPane.setOnDragDropped(event -> {
            String draggedId = draggedPanelId(event);
            if (draggedId != null) {
                DockState current = getState();
                setState(new DockState(
                        DockMoves.intoGroupOf(
                                current.layout(), draggedId, group.panelIds().get(0)),
                        current.autoHidden(),
                        current.floating()));
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    private void installStationDragHandlers() {
        // Shows the zone overlay while any panel drag is over the station. Zones and groups
        // consume the events they accept; this bubbling handler only toggles visibility.
        addEventHandler(DragEvent.DRAG_OVER, event -> {
            if (event.getDragboard().hasContent(PANEL_ID_FORMAT)) {
                overlay.setVisible(true);
            }
        });
        addEventHandler(DragEvent.DRAG_EXITED, event -> overlay.setVisible(false));
    }

    // ---- floating windows ---------------------------------------------------------------

    private void syncFloatingStages(DockState newState) {
        // Close stages for panels that are no longer floating.
        floatingStages.entrySet().removeIf(entry -> {
            if (!newState.floating().contains(entry.getKey())) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
        // Open stages for newly floating panels.
        for (String panelId : newState.floating()) {
            floatingStages.computeIfAbsent(panelId, this::openFloatingStage);
        }
    }

    private Stage openFloatingStage(String panelId) {
        DockPanel panel = panels.get(panelId);
        Stage stage = new Stage(StageStyle.UTILITY);
        stage.setTitle(panel.title());
        if (getScene() != null) {
            stage.initOwner(getScene().getWindow());
        }
        StackPane root = new StackPane(panel.content().get());
        root.getStyleClass().add("dock-floating-content");
        stage.setScene(new Scene(root, 320, 420));
        // Closing a floating panel docks it back where it came from rather than losing it.
        stage.setOnCloseRequest(
                e -> setState(getState().dockBack(panelId, getState().homeSideOf(panelId))));
        stage.show();
        return stage;
    }

    // ---- auto-hide sidebars + drawer ----------------------------------------------------

    private static VBox sidebar() {
        VBox bar = new VBox();
        bar.getStyleClass().add("dock-sidebar");
        bar.setSpacing(4);
        return bar;
    }

    private void rebuildSidebars(DockState newState) {
        leftBar.getChildren().clear();
        rightBar.getChildren().clear();
        newState.autoHidden().forEach((panelId, side) -> {
            Button button = new Button(panels.get(panelId).title());
            button.getStyleClass().add("dock-sidebar-button");
            button.setFocusTraversable(false);
            button.setOnAction(e -> toggleDrawer(panelId, side));
            // TOP/BOTTOM auto-hide is rare with our layouts; those panels park on the right bar.
            (side == Side.LEFT ? leftBar : rightBar).getChildren().add(button);
        });
        leftBar.setVisible(!leftBar.getChildren().isEmpty());
        leftBar.setManaged(leftBar.isVisible());
        rightBar.setVisible(!rightBar.getChildren().isEmpty());
        rightBar.setManaged(rightBar.isVisible());
    }

    private void toggleDrawer(String panelId, Side side) {
        if (drawer != null && panelId.equals(drawer.getUserData())) {
            closeDrawer();
            return;
        }
        closeDrawer();
        DockPanel panel = panels.get(panelId);

        Button pin = new Button("Pin");
        pin.getStyleClass().add("dock-drawer-pin");
        pin.setFocusTraversable(false);
        pin.setOnAction(e -> setState(getState().dockBack(panelId, side)));

        Label title = new Label(panel.title());
        title.setMaxWidth(Double.MAX_VALUE);

        BorderPane header = new BorderPane(null, null, pin, null, title);
        header.getStyleClass().add("dock-drawer-header");

        BorderPane box = new BorderPane(panel.content().get());
        box.setTop(header);
        box.getStyleClass().add("dock-drawer");
        box.setMaxWidth(DRAWER_SIZE);
        box.setUserData(panelId);
        StackPane.setAlignment(box, side == Side.LEFT ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);

        drawer = box;
        getChildren().add(box);
    }

    private void closeDrawer() {
        if (drawer != null) {
            getChildren().remove(drawer);
            drawer = null;
        }
    }

    // ---- drop-zone overlay --------------------------------------------------------------

    private void buildOverlay() {
        // Only the zone bands catch events; the middle of the overlay lets drags fall through to
        // the tab groups underneath.
        overlay.setPickOnBounds(false);
        overlay.setVisible(false);
        overlay.getStyleClass().add("dock-overlay");

        Region left = zone(Side.LEFT);
        AnchorPane.setLeftAnchor(left, 0.0);
        AnchorPane.setTopAnchor(left, ZONE_THICKNESS);
        AnchorPane.setBottomAnchor(left, ZONE_THICKNESS);
        left.setPrefWidth(ZONE_THICKNESS);

        Region right = zone(Side.RIGHT);
        AnchorPane.setRightAnchor(right, 0.0);
        AnchorPane.setTopAnchor(right, ZONE_THICKNESS);
        AnchorPane.setBottomAnchor(right, ZONE_THICKNESS);
        right.setPrefWidth(ZONE_THICKNESS);

        Region top = zone(Side.TOP);
        AnchorPane.setTopAnchor(top, 0.0);
        AnchorPane.setLeftAnchor(top, 0.0);
        AnchorPane.setRightAnchor(top, 0.0);
        top.setPrefHeight(ZONE_THICKNESS);

        Region bottom = zone(Side.BOTTOM);
        AnchorPane.setBottomAnchor(bottom, 0.0);
        AnchorPane.setLeftAnchor(bottom, 0.0);
        AnchorPane.setRightAnchor(bottom, 0.0);
        bottom.setPrefHeight(ZONE_THICKNESS);

        overlay.getChildren().addAll(left, right, top, bottom);
    }

    private Region zone(Side side) {
        Region zone = new Region();
        zone.getStyleClass().add("dock-zone");
        zone.setOnDragOver(event -> {
            if (draggedPanelId(event) != null) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        zone.setOnDragEntered(event -> zone.getStyleClass().add("dock-zone-active"));
        zone.setOnDragExited(event -> zone.getStyleClass().remove("dock-zone-active"));
        zone.setOnDragDropped(event -> {
            String draggedId = draggedPanelId(event);
            if (draggedId != null) {
                DockState current = getState();
                setState(new DockState(
                        DockMoves.toSide(current.layout(), draggedId, side), current.autoHidden(), current.floating()));
                event.setDropCompleted(true);
                event.consume();
            }
        });
        return zone;
    }

    private static String draggedPanelId(DragEvent event) {
        Object content = event.getDragboard().getContent(PANEL_ID_FORMAT);
        return content instanceof String id ? id : null;
    }
}
