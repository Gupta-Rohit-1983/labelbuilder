package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.scene.input.KeyCombination;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Static integrity checks on {@code main-window.fxml} that run without the JavaFX toolkit: every
 * accelerator string must parse, and every {@code onAction} handler must exist on the controller.
 * Catches the classic FXML failure mode — a typo that only explodes at runtime on first click.
 */
class MainWindowFxmlTest {

    private static final String FXML = "/fxml/main-window.fxml";

    @Test
    void allAcceleratorsParse() throws Exception {
        List<String> accelerators = attributeValues("accelerator");

        assertThat(accelerators).isNotEmpty();
        for (String accelerator : accelerators) {
            assertThatCode(() -> KeyCombination.valueOf(accelerator))
                    .as("accelerator '%s'", accelerator)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void allActionHandlersExistOnController() throws Exception {
        List<String> handlers = attributeValues("onAction");
        List<String> controllerMethods = Arrays.stream(MainWindowController.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(handlers).isNotEmpty();
        for (String handler : handlers) {
            assertThat(handler).startsWith("#");
            assertThat(controllerMethods)
                    .as("controller method for %s", handler)
                    .contains(handler.substring(1));
        }
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
