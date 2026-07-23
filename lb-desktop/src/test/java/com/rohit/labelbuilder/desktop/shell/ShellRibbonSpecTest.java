package com.rohit.labelbuilder.desktop.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rohit.labelbuilder.desktop.action.ActionRegistry;
import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the ribbon's structure headlessly — the spec is pure data, so no FX toolkit is
 * needed. Rendering itself is covered by the real UI (TestFX, Phase 19).
 */
class ShellRibbonSpecTest {

    @Test
    void everyRibbonActionIdResolvesInTheRegistry() {
        ActionRegistry registry = new ActionRegistry();
        new ShellActions(registry, new StatusBus(), new BuildInfo("LabelBuilder", "test")).registerAll();

        for (RibbonSpec.TabSpec tab : ShellRibbon.SPEC.tabs()) {
            for (RibbonSpec.GroupSpec group : tab.groups()) {
                for (RibbonSpec.ItemSpec item : group.items()) {
                    for (String actionId : item.actionIds()) {
                        assertThatCode(() -> registry.get(actionId))
                                .as("tab '%s' group '%s' action '%s'", tab.title(), group.title(), actionId)
                                .doesNotThrowAnyException();
                    }
                }
            }
        }
    }

    @Test
    void everyQuickAccessActionIdResolvesInTheRegistry() {
        ActionRegistry registry = new ActionRegistry();
        new ShellActions(registry, new StatusBus(), new BuildInfo("LabelBuilder", "test")).registerAll();

        assertThat(ShellRibbon.QUICK_ACCESS).isNotEmpty().doesNotHaveDuplicates();
        for (String id : ShellRibbon.QUICK_ACCESS) {
            assertThatCode(() -> registry.get(id))
                    .as("quick-access action '%s'", id)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void tabTitlesAreUniqueAndNonBlank() {
        List<String> titles =
                ShellRibbon.SPEC.tabs().stream().map(RibbonSpec.TabSpec::title).toList();

        assertThat(titles).doesNotHaveDuplicates().allSatisfy(title -> assertThat(title)
                .isNotBlank());
    }

    @Test
    void noActionAppearsTwiceWithinOneTab() {
        for (RibbonSpec.TabSpec tab : ShellRibbon.SPEC.tabs()) {
            List<String> ids = tab.groups().stream()
                    .flatMap(group -> group.items().stream())
                    .flatMap(item -> item.actionIds().stream())
                    .toList();

            assertThat(ids).as("tab '%s'", tab.title()).doesNotHaveDuplicates();
        }
    }
}
