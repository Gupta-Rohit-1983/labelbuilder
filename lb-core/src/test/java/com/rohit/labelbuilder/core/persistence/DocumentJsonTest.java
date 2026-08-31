package com.rohit.labelbuilder.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentJsonTest {

    private final LabelDocument doc = Fixtures.richDocument();

    @Test
    void roundTripIsDeepEqual() {
        LabelDocument back = DocumentJson.readBytes(DocumentJson.writeBytes(doc));
        assertThat(back).isEqualTo(doc); // record deep-equals (invariant §8.2)
    }

    @Test
    void writeIsByteStableAcrossASaveLoadCycle() {
        byte[] first = DocumentJson.writeBytes(doc);
        byte[] second = DocumentJson.writeBytes(DocumentJson.readBytes(first));
        assertThat(second).isEqualTo(first); // invariant §8.1
    }

    @Test
    void documentJsonCarriesNoTimestamps() {
        String json = new String(DocumentJson.writeBytes(doc), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("created").doesNotContain("modified");
    }

    @Test
    void everyElementTypeSurvivesTheRoundTrip() {
        LabelDocument back = DocumentJson.readBytes(DocumentJson.writeBytes(doc));
        assertThat(back.elements())
                .extracting(e -> e.getClass().getSimpleName())
                .containsExactly(
                        "TextElement",
                        "RectangleElement",
                        "EllipseElement",
                        "LineElement",
                        "ImageElement",
                        "BarcodeElement",
                        "GroupElement");
    }

    @Test
    void groupChildrenAreNestedNotFlattened() {
        LabelDocument back = DocumentJson.readBytes(DocumentJson.writeBytes(doc));
        GroupElement group = (GroupElement) back.findElement("grp").orElseThrow();
        assertThat(group.children()).extracting(e -> e.id()).containsExactly("g-child");
    }

    @Test
    void enumsAndColoursMapByNameAndHex() {
        BarcodeElement barcode = (BarcodeElement) doc.findElement("b1").orElseThrow();
        String json = new String(DocumentJson.writeBytes(doc), StandardCharsets.UTF_8);

        assertThat(json).contains("\"symbology\":\"CODE_128\"");
        assertThat(json).contains("#F0F0F0"); // rectangle fill colour, opaque form
        assertThat(barcode.symbology().name()).isEqualTo("CODE_128");
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = DocumentJson.toNode(doc);
        node.put("futureFieldFromANewerVersion", 42); // forward tolerance (§7)

        LabelDocument back = DocumentJson.readBytes(mapper.writeValueAsBytes(node));

        assertThat(back).isEqualTo(doc);
    }

    @Test
    void unknownElementTypeIsRejected() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = DocumentJson.toNode(doc);
        ((ObjectNode) node.get("elements").get(0)).put("type", "hologram");

        assertThatThrownBy(() -> DocumentJson.readBytes(mapper.writeValueAsBytes(node)))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("hologram");
    }
}
