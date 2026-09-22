package com.rohit.labelbuilder.desktop.panels;

import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import org.springframework.stereotype.Component;

/**
 * The Object Explorer panel (Phase 8d): every element on the label, with selection kept in step with
 * the canvas in both directions.
 *
 * <p>Listed <b>front-to-back</b> — the topmost element first — which is the order people expect from
 * a layers/objects list even though the document stores the reverse (lbl-format.md §3).
 */
@Component
public class ObjectsPanel {

    private final DocumentSession session;

    public ObjectsPanel(DocumentSession session) {
        this.session = session;
    }

    /**
     * The document's elements in display order: topmost first. Pure, so the ordering rule is testable
     * without an FX toolkit.
     */
    public static List<LabelElement> displayOrder(LabelDocument document) {
        List<LabelElement> ordered = new ArrayList<>(document.elements());
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    /** The label shown for one element: its name, type, and any lock/hidden markers. */
    public static String describe(LabelElement element) {
        StringBuilder text = new StringBuilder(element.name());
        text.append("  ·  ").append(element.getClass().getSimpleName().replace("Element", ""));
        if (!element.visible()) {
            text.append("  (hidden)");
        }
        if (element.locked()) {
            text.append("  (locked)");
        }
        return text.toString();
    }

    /** A fresh Object Explorer view. FX thread only. */
    public Node create() {
        ListView<LabelElement> list = new ListView<>();
        list.getStyleClass().add("objects-panel");
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(LabelElement item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : describe(item));
            }
        });

        Sync sync = new Sync(list);
        session.documentProperty().addListener((o, a, b) -> sync.refresh());
        session.addSelectionListener(sync::pullSelection);
        list.getSelectionModel().getSelectedItems().addListener((ListChangeListener<LabelElement>)
                change -> sync.pushSelection());
        sync.refresh();
        return list;
    }

    /**
     * Keeps the list and the session's selection in step. The {@code updating} guard is what stops the
     * two directions from bouncing off each other — without it, echoing a selection back would clear
     * and re-apply it forever.
     */
    private final class Sync {

        private final ListView<LabelElement> list;
        private boolean updating;

        Sync(ListView<LabelElement> list) {
            this.list = list;
        }

        void refresh() {
            updating = true;
            try {
                list.getItems().setAll(displayOrder(session.document()));
            } finally {
                updating = false;
            }
            pullSelection();
        }

        /** Session → list. */
        void pullSelection() {
            if (updating) {
                return;
            }
            updating = true;
            try {
                list.getSelectionModel().clearSelection();
                for (int i = 0; i < list.getItems().size(); i++) {
                    if (session.isSelected(list.getItems().get(i).id())) {
                        list.getSelectionModel().select(i);
                    }
                }
            } finally {
                updating = false;
            }
        }

        /** List → session. */
        void pushSelection() {
            if (updating) {
                return;
            }
            updating = true;
            try {
                List<String> ids = list.getSelectionModel().getSelectedItems().stream()
                        .filter(Objects::nonNull)
                        .map(LabelElement::id)
                        .toList();
                session.clearSelection();
                session.addToSelection(ids);
            } finally {
                updating = false;
            }
        }
    }
}
