package com.rohit.labelbuilder.data.bind;

import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.meta.ElementSchemas;
import com.rohit.labelbuilder.model.meta.PropertyDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges one record per data source into a document, substituting every {@code ${Source.Field}} in a
 * bindable property (FR-DS-02, lbl-format.md §4.2).
 *
 * <p>Pure: the template goes in, a resolved copy comes out, and nothing is mutated. A 10 000-label run
 * calls this once per record against the same immutable template, so a failure on row 4 000 cannot
 * have corrupted the template for row 4 001 — the snapshot discipline the document model has had since
 * Phase 7.
 *
 * <p><b>No per-element-type code.</b> Which properties carry printed content is declared by
 * {@link PropertyDescriptor#bindable()} in the Phase 7b metadata, so this class asks the schema and
 * never switches on an element class. Adding a bindable property to a future element type needs no
 * change here at all — which is the whole point of having built that metadata layer.
 *
 * <p>The merged document is handed to the renderer or the printer driver already resolved. A driver
 * therefore never needs to know what a data source is, which keeps the print module independent of
 * this one and its golden-file tests free of data fixtures.
 */
public final class DocumentBinder {

    private DocumentBinder() {}

    /**
     * Resolves {@code document} against one record per source.
     *
     * @param records the record to merge, by data source name; a source absent from the map is
     *     reported as an error rather than silently blanking the fields that read from it
     */
    public static BindingResult bind(LabelDocument document, Map<String, DataRecord> records) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(records, "records");
        List<BindingProblem> problems = new ArrayList<>();
        List<LabelElement> bound = new ArrayList<>(document.elements().size());
        for (LabelElement element : document.elements()) {
            bound.add(bindElement(element, records, problems));
        }
        return new BindingResult(document.withElements(bound), problems);
    }

    /**
     * Every field every bindable property of {@code document} reads, without needing any data.
     *
     * <p>What the designer lists as "fields used by this label", and what tells the print engine which
     * sources it has to open before a run.
     */
    public static List<FieldReference> referencesOf(LabelDocument document) {
        Set<FieldReference> references = new LinkedHashSet<>();
        collectReferences(document.elements(), references);
        return List.copyOf(references);
    }

    /** The data source names a document reads from, in first-seen order. */
    public static List<String> sourceNamesOf(LabelDocument document) {
        return referencesOf(document).stream()
                .map(FieldReference::sourceName)
                .distinct()
                .toList();
    }

    private static void collectReferences(List<LabelElement> elements, Set<FieldReference> into) {
        for (LabelElement element : elements) {
            for (PropertyDescriptor descriptor : bindableProperties(element)) {
                into.addAll(
                        ValueTemplate.parse((String) descriptor.read(element)).references());
            }
            if (element instanceof GroupElement group) {
                collectReferences(group.children(), into);
            }
        }
    }

    private static LabelElement bindElement(
            LabelElement element, Map<String, DataRecord> records, List<BindingProblem> problems) {
        LabelElement bound = element;
        for (PropertyDescriptor descriptor : bindableProperties(element)) {
            String value = (String) descriptor.read(bound);
            ValueTemplate template = ValueTemplate.parse(value);
            if (template.isLiteral()) {
                // The common case by far — a fixed caption — and worth not rewriting the element for.
                continue;
            }
            report(element, descriptor, template, records, problems);
            bound = descriptor.write(bound, template.resolve(records));
        }
        if (bound instanceof GroupElement group) {
            List<LabelElement> children = new ArrayList<>(group.children().size());
            for (LabelElement child : group.children()) {
                children.add(bindElement(child, records, problems));
            }
            bound = group.withChildren(children);
        }
        return bound;
    }

    /**
     * The writable properties of {@code element} that carry printed content.
     *
     * <p>A read-only descriptor is skipped rather than attempted: {@code write} would throw, and a
     * read-only property is a view of something else anyway.
     */
    private static List<PropertyDescriptor> bindableProperties(LabelElement element) {
        return ElementSchemas.schemaFor(element).properties().stream()
                .filter(PropertyDescriptor::bindable)
                .filter(descriptor -> !descriptor.readOnly())
                .toList();
    }

    private static void report(
            LabelElement element,
            PropertyDescriptor descriptor,
            ValueTemplate template,
            Map<String, DataRecord> records,
            List<BindingProblem> problems) {
        for (FieldReference reference : template.references()) {
            DataRecord record = records.get(reference.sourceName());
            if (record == null) {
                problems.add(BindingProblem.error(
                        element.id(),
                        descriptor.key(),
                        "no data source named '" + reference.sourceName() + "' was supplied for "
                                + reference.placeholder()));
            } else if (!record.has(reference.fieldName())) {
                // Not blanked: an empty barcode scans as nothing, and a label that prints a visible
                // placeholder is the one somebody notices before the run leaves the building.
                problems.add(BindingProblem.error(
                        element.id(),
                        descriptor.key(),
                        "'" + reference.sourceName() + "' has no field '" + reference.fieldName() + "'; it has "
                                + record.fieldNames()));
            }
        }
        for (String deferred : template.deferredExpressions()) {
            problems.add(BindingProblem.warning(
                    element.id(),
                    descriptor.key(),
                    "'${" + deferred + "}' is not a field reference and is left unresolved;"
                            + " expressions are evaluated by the Phase 12 engine"));
        }
    }
}
