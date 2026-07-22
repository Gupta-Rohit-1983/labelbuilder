package com.rohit.labelbuilder.desktop.shell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Loads FXML views with controllers supplied by the Spring context.
 *
 * <p>This is the single place {@link FXMLLoader} is configured; all views must be loaded through
 * it so controllers are always Spring beans (injectable, interceptable) and never plain
 * reflection-instantiated objects.
 */
@Component
public class FxmlViewLoader {

    private final ApplicationContext context;

    public FxmlViewLoader(ApplicationContext context) {
        this.context = context;
    }

    /**
     * @param resourcePath classpath location, e.g. {@code /fxml/main-window.fxml}
     * @return the root node of the loaded view
     */
    public Parent load(String resourcePath) {
        URL url = getClass().getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("FXML resource not found on classpath: " + resourcePath);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(context::getBean);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML view: " + resourcePath, e);
        }
    }
}
