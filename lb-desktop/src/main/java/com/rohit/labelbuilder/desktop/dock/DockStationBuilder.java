package com.rohit.labelbuilder.desktop.dock;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link DockLayout} into nested {@code SplitPane}/{@code TabPane} controls. The single
 * place the layout tree becomes scene graph; interaction layers ({@link DockStation}) hook in via
 * the decorators without this class knowing anything about drag-and-drop.
 *
 * <p>Must run on the FX thread. Rebuild-on-change is deliberate: layouts change rarely (user
 * docking gestures), and rebuilding from the immutable model is simpler and less bug-prone than
 * incremental scene surgery.
 */
@Component
public class DockStationBuilder {

    /** Hook invoked for every panel tab created — {@link DockStation} installs drag sources here. */
    @FunctionalInterface
    public interface TabDecorator {
        void decorate(Tab tab, String panelId);
    }

    /** Hook invoked for every tab group created — {@link DockStation} installs drop targets here. */
    @FunctionalInterface
    public interface GroupDecorator {
        void decorate(TabPane tabPane, DockLayout.DockNode.Group group);
    }

    private static final TabDecorator NO_TAB_DECORATION = (tab, panelId) -> {};
    private static final GroupDecorator NO_GROUP_DECORATION = (tabPane, group) -> {};

    private final DockPanelRegistry panels;

    public DockStationBuilder(DockPanelRegistry panels) {
        this.panels = panels;
    }

    /**
     * @param center the node to place in the layout's {@link DockLayout.DockNode.Center} slot
     */
    public Node build(DockLayout layout, Node center) {
        return build(layout, center, NO_TAB_DECORATION, NO_GROUP_DECORATION);
    }

    public Node build(DockLayout layout, Node center, TabDecorator tabDecorator, GroupDecorator groupDecorator) {
        return render(layout.root(), center, tabDecorator, groupDecorator);
    }

    private Node render(
            DockLayout.DockNode node, Node center, TabDecorator tabDecorator, GroupDecorator groupDecorator) {
        return switch (node) {
            case DockLayout.DockNode.Center c -> center;
            case DockLayout.DockNode.Group group -> tabGroup(group, tabDecorator, groupDecorator);
            case DockLayout.DockNode.Split split -> splitPane(split, center, tabDecorator, groupDecorator);
        };
    }

    private TabPane tabGroup(
            DockLayout.DockNode.Group group, TabDecorator tabDecorator, GroupDecorator groupDecorator) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("dock-group");
        for (String panelId : group.panelIds()) {
            DockPanel panel = panels.get(panelId);
            Tab tab = new Tab(panel.title(), panel.content().get());
            tab.setClosable(false); // closing arrives with float/auto-hide in 5c
            tabDecorator.decorate(tab, panelId);
            tabPane.getTabs().add(tab);
            if (panelId.equals(group.selectedId())) {
                tabPane.getSelectionModel().select(tab);
            }
        }
        groupDecorator.decorate(tabPane, group);
        return tabPane;
    }

    private SplitPane splitPane(
            DockLayout.DockNode.Split split, Node center, TabDecorator tabDecorator, GroupDecorator groupDecorator) {
        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("dock-split");
        splitPane.setOrientation(split.orientation());
        for (DockLayout.DockNode child : split.children()) {
            Node rendered = render(child, center, tabDecorator, groupDecorator);
            splitPane.getItems().add(rendered);
            // Panel strips keep their size when the window resizes; the center absorbs it.
            if (!(child instanceof DockLayout.DockNode.Center)) {
                SplitPane.setResizableWithParent(rendered, false);
            }
        }
        splitPane.setDividerPositions(
                split.dividers().stream().mapToDouble(Double::doubleValue).toArray());
        return splitPane;
    }
}
