package com.rohit.labelbuilder.desktop.ribbon;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.GroupSpec;
import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.ItemSpec;
import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.Size;
import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.TabSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure-data spec behaviour — fully headless. */
class RibbonSpecTest {

    private static final TabSpec HOME = new TabSpec("Home", List.of(new GroupSpec("G", List.of(ItemSpec.small("a")))));
    private static final TabSpec BARCODE_TOOLS = new TabSpec(
            "Barcode Tools", "selection.barcode", List.of(new GroupSpec("G", List.of(ItemSpec.small("b")))));
    private static final RibbonSpec SPEC = new RibbonSpec(List.of(HOME, BARCODE_TOOLS));

    @Test
    void contextualTabsHiddenWithoutTheirKey() {
        assertThat(SPEC.visibleTabs(Set.of())).containsExactly(HOME);
    }

    @Test
    void contextualTabsAppearInSpecOrderWhenTheirKeyIsActive() {
        assertThat(SPEC.visibleTabs(Set.of("selection.barcode"))).containsExactly(HOME, BARCODE_TOOLS);
    }

    @Test
    void unrelatedKeysChangeNothing() {
        assertThat(SPEC.visibleTabs(Set.of("selection.text"))).containsExactly(HOME);
    }

    @Test
    void splitFactoryCarriesPrimaryAndMenuIds() {
        RibbonSpec.SplitItem item = ItemSpec.splitSmall("file.save", "file.saveAs");

        assertThat(item.size()).isEqualTo(Size.SMALL);
        assertThat(item.actionIds()).containsExactly("file.save", "file.saveAs");
    }

    @Test
    void galleryFactoryCarriesOptionsAndColumns() {
        RibbonSpec.GalleryItem item = ItemSpec.gallery("Styles", 3, "s.one", "s.two");

        assertThat(item.columns()).isEqualTo(3);
        assertThat(item.actionIds()).containsExactly("s.one", "s.two");
    }

    @Test
    void plainItemReportsItsSingleActionId() {
        assertThat(ItemSpec.large("file.print").actionIds()).containsExactly("file.print");
    }
}
