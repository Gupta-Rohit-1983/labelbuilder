package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.core.edit.EditCommands;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.desktop.shell.StatusBus;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.meta.ElementSchema;
import com.rohit.labelbuilder.model.meta.ElementSchemas;
import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import com.rohit.labelbuilder.model.meta.SharedProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

/**
 * The Property Inspector (Phase 9a): edits the selected element's properties, generated entirely from
 * its {@link ElementSchema}.
 *
 * <p>There is no per-element-type code here. The form is built from the schema's descriptors grouped
 * by category, and each edit is issued as a {@link SetPropertyCommand}, so every change is undoable
 * and — because that command merges per element and property — typing in a field collapses into one
 * undo step rather than one per keystroke.
 *
 * <p>Multi-selection editing arrives in 9d; richer editors (colour, font, spinners) in 9b.
 */
@Component
public class PropertyInspectorPanel {

    /** Categories that open collapsed — rarely-touched settings that would otherwise cost scrolling. */
    private static final Set<String> COLLAPSED_BY_DEFAULT = Set.of("Advanced", "Human-readable text");

    private final DocumentSession session;
    private final StatusBus status;

    public PropertyInspectorPanel(DocumentSession session, StatusBus status) {
        this.session = session;
        this.status = status;
    }

    /** A fresh inspector view. FX thread only. */
    public Node create() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getStyleClass().add("property-inspector");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        Form form = new Form(content);
        session.addSelectionListener(form::sync);
        session.documentProperty().addListener((o, a, b) -> form.sync());
        form.sync();
        return scroll;
    }

    /**
     * Holds the rendered form and decides, on every change, whether to rebuild it or merely refresh
     * its values. Rebuilding only when the <em>subject</em> changes is what keeps focus and caret
     * position intact while typing — a rebuild on every document change would fight the user.
     */
    private final class Form {

        private final VBox content;
        private final Map<String, Row> rows = new LinkedHashMap<>();
        private String currentSubject = "";

        Form(VBox content) {
            this.content = content;
        }

        /** One property's label and control; the label carries the "(mixed)" marker. */
        private record Row(PropertyDescriptor descriptor, Label label, PropertyEditor editor) {}

        void sync() {
            List<LabelElement> selection = session.selectedElements();
            if (InspectorState.of(selection) instanceof InspectorState.Empty) {
                showMessage("Select an element to edit its properties.");
                return;
            }
            String subject = subjectOf(selection);
            if (subject.equals(currentSubject)) {
                refreshValues(selection);
            } else {
                build(selection, subject);
            }
        }

        /**
         * Identifies what the form is showing. A rebuild happens only when this changes, so editing a
         * value — which changes the document but not the subject — leaves focus and caret alone.
         */
        private String subjectOf(List<LabelElement> selection) {
            StringBuilder key = new StringBuilder();
            for (LabelElement element : selection) {
                key.append(element.id())
                        .append(':')
                        .append(element.getClass().getSimpleName())
                        .append(',');
            }
            return key.toString();
        }

        private void showMessage(String message) {
            currentSubject = "";
            rows.clear();
            Label label = new Label(message);
            label.setWrapText(true);
            label.getStyleClass().add("inspector-hint");
            content.getChildren().setAll(label);
        }

        /** Push current values into editors the user is not interacting with. */
        private void refreshValues(List<LabelElement> selection) {
            for (Row row : rows.values()) {
                if (row.editor().isEditing()) {
                    continue;
                }
                apply(row, selection);
            }
        }

        /** Show the selection's agreed value, or the mixed state, for one row. */
        private void apply(Row row, List<LabelElement> selection) {
            SharedProperties.CommonValue common =
                    SharedProperties.commonValue(selection, row.descriptor().key());
            if (common.uniform()) {
                row.label().setText(row.descriptor().displayName());
                row.editor().showValue(common.value());
            } else {
                row.label().setText(row.descriptor().displayName() + " (mixed)");
                row.editor().showMixed();
            }
        }

        private void build(List<LabelElement> selection, String subject) {
            currentSubject = subject;
            rows.clear();
            content.getChildren().clear();

            if (selection.size() > 1) {
                Label header = new Label(selection.size() + " elements selected — editing their shared properties");
                header.setWrapText(true);
                header.getStyleClass().add("inspector-hint");
                content.getChildren().add(header);
            }

            // Only properties every selected element exposes; for one element that is its whole schema.
            for (Map.Entry<String, List<PropertyDescriptor>> group :
                    byCategory(SharedProperties.of(selection)).entrySet()) {
                content.getChildren().add(categoryPage(selection, group.getKey(), group.getValue()));
            }
            refreshValues(selection);
        }

        /** Group shared descriptors by category, keeping first-seen order. */
        private Map<String, List<PropertyDescriptor>> byCategory(List<PropertyDescriptor> descriptors) {
            Map<String, List<PropertyDescriptor>> grouped = new LinkedHashMap<>();
            for (PropertyDescriptor descriptor : descriptors) {
                grouped.computeIfAbsent(descriptor.category(), c -> new ArrayList<>())
                        .add(descriptor);
            }
            return grouped;
        }

        /**
         * One collapsible page per category. Everything starts open except {@link #COLLAPSED_BY_DEFAULT}
         * categories, which are the ones most people never touch — showing them expanded would bury the
         * properties that matter behind scrolling.
         */
        private Node categoryPage(List<LabelElement> selection, String title, List<PropertyDescriptor> descriptors) {
            TitledPane pane = new TitledPane(title, categoryGrid(selection, descriptors));
            pane.setExpanded(!COLLAPSED_BY_DEFAULT.contains(title));
            pane.setAnimated(false); // instant, so rebuilding on selection doesn't visibly flap
            pane.getStyleClass().add("inspector-category");
            return pane;
        }

        private Node categoryGrid(List<LabelElement> selection, List<PropertyDescriptor> descriptors) {
            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(4);
            ColumnConstraints labels = new ColumnConstraints();
            labels.setMinWidth(90);
            ColumnConstraints fields = new ColumnConstraints();
            fields.setHgrow(Priority.ALWAYS);
            fields.setFillWidth(true);
            grid.getColumnConstraints().addAll(labels, fields);

            LabelElement first = selection.getFirst();
            ElementSchema firstSchema = ElementSchemas.schemaFor(first);
            int row = 0;
            for (PropertyDescriptor descriptor : descriptors) {
                PropertyEditor editor = PropertyEditors.create(
                        descriptor,
                        firstSchema.get(first, descriptor.key()),
                        value -> commit(descriptor, value),
                        status::post);
                Label label = new Label(descriptor.displayName());
                rows.put(descriptor.key(), new Row(descriptor, label, editor));
                grid.add(label, 0, row);
                grid.add(editor.node(), 1, row);
                row++;
            }
            return grid;
        }

        /**
         * Apply an edit to the whole selection as one undo step. Elements already holding the value are
         * skipped, so resolving a mixed field records only the elements it actually changed.
         */
        private void commit(PropertyDescriptor descriptor, Object value) {
            List<LabelElement> targets = session.selectedElements();
            if (targets.isEmpty()) {
                return;
            }
            try {
                EditCommands.setProperty(targets, descriptor.key(), value, "Change " + descriptor.displayName())
                        .ifPresent(session::execute);
            } catch (RuntimeException e) {
                // The model rejected it (e.g. a negative size slipping past the advisory range).
                status.post(descriptor.displayName() + ": " + e.getMessage());
                sync();
            }
        }
    }
}
