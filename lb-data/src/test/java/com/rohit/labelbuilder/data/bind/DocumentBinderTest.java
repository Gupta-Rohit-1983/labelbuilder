package com.rohit.labelbuilder.data.bind;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.AutoFit;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.ImageFit;
import com.rohit.labelbuilder.model.style.Monochrome;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Symbology;
import com.rohit.labelbuilder.model.style.TextAlign;
import com.rohit.labelbuilder.model.style.VerticalAlign;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentBinderTest {

    private static final Map<String, DataRecord> RECORDS =
            Map.of("Product", MapRecord.of("Code", "A1", "Name", "Bolt", "Image", "flags/gb.png"));

    private static ElementProperties props(String id) {
        return ElementProperties.of(id, id, "layer-1", Bounds.of(1, 1, 40, 10));
    }

    private static TextElement text(String id, String value) {
        return new TextElement(
                props(id),
                value,
                FontSpec.of("Arial", 10),
                RgbaColor.BLACK,
                TextAlign.LEFT,
                VerticalAlign.TOP,
                1.0,
                true,
                AutoFit.NONE,
                0.0);
    }

    private static BarcodeElement barcode(String id, String value) {
        return BarcodeElement.of(
                props(id),
                Symbology.CODE_128,
                value,
                0.25,
                12.0,
                2.5,
                CheckDigitMode.AUTO,
                Hri.below(FontSpec.of("Arial", 8)));
    }

    private static ImageElement image(String id, String assetPath) {
        return new ImageElement(props(id), assetPath, ImageFit.CONTAIN, Monochrome.disabled(), 1.0);
    }

    private static LabelDocument documentOf(LabelElement... elements) {
        return LabelDocument.blank("doc-1", "Label", Stock.of(100, 50)).withElements(List.of(elements));
    }

    private static String textValueOf(LabelDocument document, int index) {
        return ((TextElement) document.elements().get(index)).value();
    }

    @Test
    void aTextValueIsResolvedFromTheRecord() {
        BindingResult result = DocumentBinder.bind(documentOf(text("t1", "SKU: ${Product.Code}")), RECORDS);

        assertThat(textValueOf(result.document(), 0)).isEqualTo("SKU: A1");
        assertThat(result.hasProblems()).isFalse();
    }

    @Test
    void aBarcodeValueIsResolvedToo() {
        BindingResult result = DocumentBinder.bind(documentOf(barcode("b1", "${Product.Code}")), RECORDS);

        assertThat(((BarcodeElement) result.document().elements().get(0)).value())
                .isEqualTo("A1");
    }

    @Test
    void anImageCanPickItsAssetPerRecord() {
        // A country flag or a hazard symbol chosen by the data, not by the designer.
        BindingResult result = DocumentBinder.bind(documentOf(image("i1", "${Product.Image}")), RECORDS);

        assertThat(((ImageElement) result.document().elements().get(0)).assetPath())
                .isEqualTo("flags/gb.png");
    }

    @Test
    void theElementNameIsNotBoundBecauseItNeverPrints() {
        // It is design metadata. Substituting into it would be surprising and invisible.
        TextElement element = text("t1", "fixed").withProperties(props("t1").withName("${Product.Code}"));

        BindingResult result = DocumentBinder.bind(documentOf(element), RECORDS);

        assertThat(result.document().elements().get(0).name()).isEqualTo("${Product.Code}");
    }

    @Test
    void thePrintConditionIsNotBoundBecauseItIsEvaluatedNotSubstituted() {
        // Substituting a value into a condition would turn it into a constant.
        TextElement element =
                text("t1", "fixed").withProperties(props("t1").withPrintCondition("${Product.Code} = 'A1'"));

        BindingResult result = DocumentBinder.bind(documentOf(element), RECORDS);

        assertThat(result.document().elements().get(0).properties().printCondition())
                .isEqualTo("${Product.Code} = 'A1'");
    }

    @Test
    void theTemplateIsNotMutated() {
        // A failure on row 4 000 must not have corrupted the template for row 4 001.
        LabelDocument template = documentOf(text("t1", "${Product.Code}"));

        DocumentBinder.bind(template, RECORDS);

        assertThat(textValueOf(template, 0)).isEqualTo("${Product.Code}");
    }

    @Test
    void oneTemplateMergesManyRecords() {
        LabelDocument template = documentOf(text("t1", "${Product.Code}"));

        assertThat(textValueOf(
                        DocumentBinder.bind(template, Map.of("Product", MapRecord.of("Code", "A1")))
                                .document(),
                        0))
                .isEqualTo("A1");
        assertThat(textValueOf(
                        DocumentBinder.bind(template, Map.of("Product", MapRecord.of("Code", "A2")))
                                .document(),
                        0))
                .isEqualTo("A2");
    }

    @Test
    void elementsInsideAGroupAreBound() {
        GroupElement group = new GroupElement(props("g1"), List.of(text("t1", "${Product.Code}")));

        BindingResult result = DocumentBinder.bind(documentOf(group), RECORDS);

        GroupElement bound = (GroupElement) result.document().elements().get(0);
        assertThat(((TextElement) bound.children().get(0)).value()).isEqualTo("A1");
        assertThat(result.hasProblems()).isFalse();
    }

    @Test
    void aNestedGroupIsBoundAtEveryDepth() {
        GroupElement inner = new GroupElement(props("g2"), List.of(text("t1", "${Product.Name}")));
        GroupElement outer = new GroupElement(props("g1"), List.of(inner));

        BindingResult result = DocumentBinder.bind(documentOf(outer), RECORDS);

        GroupElement boundOuter = (GroupElement) result.document().elements().get(0);
        GroupElement boundInner = (GroupElement) boundOuter.children().get(0);
        assertThat(((TextElement) boundInner.children().get(0)).value()).isEqualTo("Bolt");
    }

    @Test
    void everythingElseAboutAnElementIsLeftAlone() {
        TextElement element = text("t1", "${Product.Code}");

        TextElement bound = (TextElement) DocumentBinder.bind(documentOf(element), RECORDS)
                .document()
                .elements()
                .get(0);

        assertThat(bound.font()).isEqualTo(element.font());
        assertThat(bound.align()).isEqualTo(element.align());
        assertThat(bound.properties().bounds()).isEqualTo(element.properties().bounds());
        assertThat(bound.id()).isEqualTo("t1");
    }

    // ---- problems are reported, not thrown ----

    @Test
    void anUnsuppliedSourceIsAnErrorNamingTheElement() {
        BindingResult result = DocumentBinder.bind(documentOf(text("t1", "${Stock.Qty}")), RECORDS);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).singleElement().satisfies(problem -> {
            assertThat(problem.elementId()).isEqualTo("t1");
            assertThat(problem.propertyKey()).isEqualTo("value");
            assertThat(problem.message()).contains("Stock");
        });
        // The document still comes back, with the placeholder visible for the designer to see.
        assertThat(textValueOf(result.document(), 0)).isEqualTo("${Stock.Qty}");
    }

    @Test
    void anUnknownFieldIsAnErrorThatSaysWhatIsAvailable() {
        BindingResult result = DocumentBinder.bind(documentOf(barcode("b1", "${Product.Nope}")), RECORDS);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors().get(0).message()).contains("Nope").contains("Code");
    }

    @Test
    void anErrorRatherThanABlankBecauseAnEmptyBarcodeScansAsNothing() {
        BindingResult result = DocumentBinder.bind(documentOf(barcode("b1", "${Product.Nope}")), RECORDS);

        assertThat(((BarcodeElement) result.document().elements().get(0)).value())
                .isEqualTo("${Product.Nope}");
    }

    @Test
    void aDeferredExpressionIsAWarningNotAnError() {
        // The label prints; part of it just has not merged yet.
        BindingResult result = DocumentBinder.bind(documentOf(text("t1", "${UPPER(Product.Code)}")), RECORDS);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.warnings()).singleElement().satisfies(problem -> assertThat(problem.message())
                .contains("Phase 12"));
    }

    @Test
    void aProblemInsideAGroupStillNamesTheChildElement() {
        GroupElement group = new GroupElement(props("g1"), List.of(text("t1", "${Nope.Field}")));

        BindingResult result = DocumentBinder.bind(documentOf(group), RECORDS);

        assertThat(result.errors())
                .singleElement()
                .extracting(BindingProblem::elementId)
                .isEqualTo("t1");
    }

    @Test
    void everyProblemIsReportedNotJustTheFirst() {
        BindingResult result = DocumentBinder.bind(
                documentOf(text("t1", "${Nope.A}"), text("t2", "${Product.Nope}"), text("t3", "${f(x)}")), RECORDS);

        assertThat(result.problems()).hasSize(3);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void aDocumentWithNoBindingsAtAllIsUnchangedAndClean() {
        LabelDocument template = documentOf(text("t1", "Fragile"), barcode("b1", "0123456789"));

        BindingResult result = DocumentBinder.bind(template, Map.of());

        assertThat(result.document()).isEqualTo(template);
        assertThat(result.hasProblems()).isFalse();
    }

    // ---- what the designer and the print engine need to know up front ----

    @Test
    void theFieldsADocumentReadsCanBeListedWithoutAnyData() {
        LabelDocument template = documentOf(
                text("t1", "${Product.Code} ${Product.Name}"),
                barcode("b1", "${Product.Code}"),
                new GroupElement(props("g1"), List.of(text("t2", "${Stock.Qty}"))));

        assertThat(DocumentBinder.referencesOf(template))
                .containsExactly(
                        new FieldReference("Product", "Code"),
                        new FieldReference("Product", "Name"),
                        new FieldReference("Stock", "Qty"));
    }

    @Test
    void theSourcesAPrintRunHasToOpenAreDerivedFromTheDocument() {
        LabelDocument template = documentOf(text("t1", "${Product.Code} ${Stock.Qty} ${Product.Name}"));

        assertThat(DocumentBinder.sourceNamesOf(template)).containsExactly("Product", "Stock");
    }

    @Test
    void aDocumentThatBindsNothingNeedsNoSources() {
        assertThat(DocumentBinder.sourceNamesOf(documentOf(text("t1", "Fragile"))))
                .isEmpty();
    }
}
