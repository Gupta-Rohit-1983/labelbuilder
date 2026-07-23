package com.rohit.labelbuilder.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.shell.BuildInfo;
import com.rohit.labelbuilder.desktop.shell.FxmlViewLoader;
import com.rohit.labelbuilder.desktop.shell.MainWindowController;
import com.rohit.labelbuilder.desktop.shell.PrimaryStageInitializer;
import com.rohit.labelbuilder.desktop.shell.ShellActions;
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
        assertThat(context.getBean(com.rohit.labelbuilder.desktop.shell.RibbonStatePreferences.class))
                .isNotNull();
    }

    @Test
    void ribbonBeansArePresent() {
        assertThat(context.getBean(com.rohit.labelbuilder.desktop.ribbon.RibbonBuilder.class))
                .isNotNull();
        assertThat(context.getBean(com.rohit.labelbuilder.desktop.ribbon.RibbonContexts.class))
                .isNotNull();
        assertThat(context.getBean(com.rohit.labelbuilder.desktop.shell.ShellRibbon.class))
                .isNotNull();
    }

    @Test
    void shellActionsAreRegistered() {
        ActionRegistry registry = context.getBean(ActionRegistry.class);
        // A typo'd id in a menu declaration must fail here, at build time — not as a dead menu
        // item found by a user. ShellActions registers on @PostConstruct within this context.
        assertThat(context.getBean(ShellActions.class)).isNotNull();
        assertThat(registry.get(ShellActions.FILE_NEW)).isNotNull();
        assertThat(registry.get(ShellActions.HELP_ABOUT)).isNotNull();
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
