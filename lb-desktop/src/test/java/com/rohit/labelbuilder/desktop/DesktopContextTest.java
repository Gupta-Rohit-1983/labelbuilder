package com.rohit.labelbuilder.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.shell.BuildInfo;
import com.rohit.labelbuilder.desktop.shell.FxmlViewLoader;
import com.rohit.labelbuilder.desktop.shell.MainWindowController;
import com.rohit.labelbuilder.desktop.shell.PrimaryStageInitializer;
import com.rohit.labelbuilder.desktop.shell.WindowStatePreferences;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Proves the Spring side of the bootstrap without starting the JavaFX toolkit: the context loads,
 * shell beans exist, configuration binds, and FXML controllers are prototype-scoped.
 */
@SpringBootTest(classes = LabelBuilderDesktop.class)
class DesktopContextTest {

    static {
        // Redirect the app data tree to a throwaway temp dir so tests never touch the real profile.
        System.setProperty(
                "labelbuilder.home",
                System.getProperty("java.io.tmpdir") + "/labelbuilder-test-"
                        + ProcessHandle.current().pid());
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BuildInfo buildInfo;

    @Test
    void shellBeansArePresent() {
        assertThat(context.getBean(FxmlViewLoader.class)).isNotNull();
        assertThat(context.getBean(PrimaryStageInitializer.class)).isNotNull();
        assertThat(context.getBean(WindowStatePreferences.class)).isNotNull();
    }

    @Test
    void buildInfoBindsFromConfiguration() {
        assertThat(buildInfo.appName()).isEqualTo("LabelBuilder");
        assertThat(buildInfo.version()).isNotBlank();
    }

    @Test
    void fxmlControllersArePrototypeScoped() {
        // Each FXML load must receive a fresh controller instance.
        MainWindowController first = context.getBean(MainWindowController.class);
        MainWindowController second = context.getBean(MainWindowController.class);
        assertThat(first).isNotSameAs(second);
    }
}
