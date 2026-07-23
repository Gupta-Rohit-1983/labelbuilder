package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Static integrity checks on {@code main-window.fxml} that run without the JavaFX toolkit.
 *
 * <p>Since Phase 4a the FXML is layout-only: all behaviour (handlers, accelerators, texts) comes
 * from the ActionRegistry. These tests enforce that the FXML never grows inline wiring again —
 * an {@code onAction} here would bypass the registry and split a command's definition across two
 * places.
 */
class MainWindowFxmlTest {

    private static final String FXML = "/fxml/main-window.fxml";

    @Test
    void fxmlCarriesNoInlineBehaviour() throws Exception {
        assertThat(attributeValues("onAction")).isEmpty();
        assertThat(attributeValues("accelerator")).isEmpty();
    }

    @Test
    void registryTargetsExist() throws Exception {
        // The controller populates the menu bar and quick-access toolbar from the ActionRegistry
        // and appends the ribbon into topBox.
        List<String> ids = attributeValues("fx:id");

        assertThat(ids).contains("topBox", "menuBar", "quickAccessBar", "statusMessage");
    }

    private List<String> attributeValues(String attribute) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The FXML is trusted local content; disable external entity resolution anyway.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc;
        try (InputStream in = getClass().getResourceAsStream(FXML)) {
            assertThat(in).as("resource %s", FXML).isNotNull();
            doc = factory.newDocumentBuilder().parse(in);
        }
        List<String> values = new ArrayList<>();
        collect(doc.getDocumentElement(), attribute, values);
        return values;
    }

    private void collect(Element element, String attribute, List<String> values) {
        if (element.hasAttribute(attribute)) {
            values.add(element.getAttribute(attribute));
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                collect(childElement, attribute, values);
            }
        }
    }
}
