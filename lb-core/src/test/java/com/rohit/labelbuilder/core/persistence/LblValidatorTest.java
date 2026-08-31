package com.rohit.labelbuilder.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.Stroke;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LblValidatorTest {

    @Test
    void wellFormedPackagePasses() {
        assertThatCode(() -> LblValidator.validate(Fixtures.richPackage())).doesNotThrowAnyException();
    }

    @Test
    void elementOnAnUnknownLayerIsRejected() {
        RectangleElement onGhostLayer = RectangleElement.of(
                ElementProperties.of("r1", "r1", "ghost-layer", new Bounds(0, 0, 5, 5)),
                Stroke.solid(0.2),
                Fill.none());
        LabelDocument doc = LabelDocument.blank("d", "T", Stock.of(50, 50)).addElement(onGhostLayer);

        assertThatThrownBy(() -> LblValidator.validate(LabelPackage.of(doc)))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("unknown layer");
    }

    @Test
    void imageReferencingAMissingAssetIsRejected() {
        ImageElement image =
                ImageElement.of(ElementProperties.of("i1", "i1", "layer-1", new Bounds(0, 0, 10, 10)), "assets/x.png");
        LabelDocument doc = LabelDocument.blank("d", "T", Stock.of(50, 50)).addElement(image);

        assertThatThrownBy(() -> LblValidator.validate(LabelPackage.of(doc)))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("missing asset");
    }

    @Test
    void orphanAssetIsRejected() {
        LabelDocument doc = LabelDocument.blank("d", "T", Stock.of(50, 50));
        LabelPackage pkg = new LabelPackage(doc, Map.of("assets/unused.png", new byte[] {1, 2, 3}), null);

        assertThatThrownBy(() -> LblValidator.validate(pkg))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("orphan asset");
    }

    @Test
    void credentialShapedBytesAreFlagged() {
        assertThatThrownBy(() -> LblValidator.assertNoCredentialLeak("pwd=hunter2".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(LabelFileException.class);
        assertThatThrownBy(() -> LblValidator.assertNoCredentialLeak("PASSWORD: x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(LabelFileException.class);
    }

    @Test
    void aCleanDocumentHasNoCredentials() {
        // scan the plaintext document.json (the archive itself is deflated) — invariant §8.5
        byte[] documentJson = DocumentJson.writeBytes(Fixtures.richDocument());

        assertThatCode(() -> LblValidator.assertNoCredentialLeak(documentJson)).doesNotThrowAnyException();
        assertThat(documentJson).isNotEmpty();
    }
}
