package com.rohit.labelbuilder.desktop.panels;

import com.rohit.labelbuilder.core.command.AddLayerCommand;
import com.rohit.labelbuilder.core.command.MoveToLayerCommand;
import com.rohit.labelbuilder.core.command.UpdateLayerCommand;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

/**
 * The Layers panel (Phase 8d): one row per layer with visibility and lock toggles, a button to add a
 * layer, and a per-row button that moves the current selection onto that layer.
 *
 * <p>Every change goes through a command, so toggling a layer's visibility is undoable like any other
 * edit. Rows are rebuilt wholesale whenever the document changes — with a handful of layers that is
 * far simpler, and less bug-prone, than reconciling recycled list cells against changing state.
 */
@Component
public class LayersPanel {

    private final DocumentSession session;

    public LayersPanel(DocumentSession session) {
        this.session = session;
    }

    /**
     * The next free {@code layer-N} id for a document. Pure, so the numbering rule is testable without
     * an FX toolkit; it skips ids already taken rather than counting layers, so deleting and re-adding
     * cannot produce a duplicate.
     */
    public static String nextLayerId(LabelDocument document) {
        List<String> taken = document.layers().stream().map(Layer::id).toList();
        int n = 1;
        while (taken.contains("layer-" + n)) {
            n++;
        }
        return "layer-" + n;
    }

    /** A fresh Layers view. FX thread only. */
    public Node create() {
        VBox rows = new VBox(2);
        rows.setPadding(new Insets(8));
        rows.getStyleClass().add("layers-panel");

        Runnable rebuild = () -> populate(rows);
        session.documentProperty().addListener((o, a, b) -> rebuild.run());
        session.addSelectionListener(rebuild);
        rebuild.run();

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private void populate(VBox rows) {
        rows.getChildren().clear();
        LabelDocument document = session.document();
        for (Layer layer : document.layers()) {
            rows.getChildren().add(layerRow(layer));
        }

        Button addLayer = new Button("Add Layer");
        addLayer.setMaxWidth(Double.MAX_VALUE);
        addLayer.setOnAction(e -> {
            String id = nextLayerId(session.document());
            session.execute(new AddLayerCommand(Layer.of(id, "Layer " + id.replace("layer-", ""))));
        });
        rows.getChildren().add(new Label(" "));
        rows.getChildren().add(addLayer);
    }

    private Node layerRow(Layer layer) {
        CheckBox visible = new CheckBox();
        visible.setSelected(layer.visible());
        visible.setTooltip(new Tooltip("Show this layer"));
        visible.setOnAction(e ->
                session.execute(new UpdateLayerCommand(layer.withVisible(visible.isSelected()), "Layer Visibility")));

        CheckBox locked = new CheckBox();
        locked.setSelected(layer.locked());
        locked.setTooltip(new Tooltip("Lock this layer"));
        locked.setOnAction(
                e -> session.execute(new UpdateLayerCommand(layer.withLocked(locked.isSelected()), "Layer Lock")));

        Label name = new Label(layer.name());
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);

        Button moveHere = new Button("←");
        moveHere.setTooltip(new Tooltip("Move the selected elements to this layer"));
        moveHere.setDisable(session.selectionSize() == 0);
        moveHere.setOnAction(
                e -> session.execute(new MoveToLayerCommand(List.copyOf(session.selectedIds()), layer.id())));

        HBox row = new HBox(6, visible, locked, name, moveHere);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
