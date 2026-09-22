package com.rohit.labelbuilder.desktop.shell;

import static com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.ItemSpec.large;
import static com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.ItemSpec.small;
import static com.rohit.labelbuilder.desktop.ribbon.RibbonSpec.ItemSpec.splitSmall;

import com.rohit.labelbuilder.desktop.ribbon.RibbonBuilder;
import com.rohit.labelbuilder.desktop.ribbon.RibbonContexts;
import com.rohit.labelbuilder.desktop.ribbon.RibbonSpec;
import java.util.List;
import javafx.scene.control.TabPane;
import org.springframework.stereotype.Component;

/**
 * The shell's ribbon definition. Only actions that exist today appear; groups grow as their
 * features land (galleries get content in Phase 8; contextual tabs appear once the canvas
 * selection activates context keys in Phase 6/8). Structure lives here as a constant
 * {@link RibbonSpec} so tests can validate it headlessly against the registry.
 */
@Component
public class ShellRibbon {

    /**
     * The quick-access toolbar: always-visible one-click commands, shown beside the menu bar.
     * User-customizable ordering is a later refinement (Phase 17 preferences).
     */
    static final List<String> QUICK_ACCESS =
            List.of(ShellActions.FILE_NEW, ShellActions.FILE_OPEN, ShellActions.FILE_SAVE, ShellActions.FILE_PRINT);

    static final RibbonSpec SPEC = new RibbonSpec(List.of(
            new RibbonSpec.TabSpec(
                    "Home",
                    List.of(
                            new RibbonSpec.GroupSpec(
                                    "Label",
                                    List.of(
                                            large(ShellActions.FILE_NEW),
                                            small(ShellActions.FILE_OPEN),
                                            // Split button: Save primary, Save As in the dropdown.
                                            splitSmall(ShellActions.FILE_SAVE, ShellActions.FILE_SAVE_AS))),
                            new RibbonSpec.GroupSpec(
                                    "Clipboard",
                                    List.of(
                                            large(ShellActions.EDIT_PASTE),
                                            small(ShellActions.EDIT_CUT),
                                            small(ShellActions.EDIT_COPY))),
                            new RibbonSpec.GroupSpec(
                                    "History", List.of(small(ShellActions.EDIT_UNDO), small(ShellActions.EDIT_REDO))),
                            // Creation tools (8a): each arms the canvas for one placement.
                            new RibbonSpec.GroupSpec(
                                    "Insert",
                                    List.of(
                                            large(ShellActions.INSERT_TEXT),
                                            small(ShellActions.INSERT_RECTANGLE),
                                            small(ShellActions.INSERT_ELLIPSE),
                                            small(ShellActions.INSERT_LINE),
                                            small(ShellActions.INSERT_IMAGE),
                                            small(ShellActions.INSERT_BARCODE))),
                            // Arrange (8b): enablement is selection-driven, so these grey out
                            // until enough elements are selected for the operation to mean anything.
                            new RibbonSpec.GroupSpec(
                                    "Arrange",
                                    List.of(
                                            large(ShellActions.EDIT_DUPLICATE),
                                            small(ShellActions.EDIT_GROUP),
                                            small(ShellActions.EDIT_UNGROUP),
                                            small(ShellActions.ARRANGE_BRING_TO_FRONT),
                                            small(ShellActions.ARRANGE_BRING_FORWARD),
                                            small(ShellActions.ARRANGE_SEND_BACKWARD),
                                            small(ShellActions.ARRANGE_SEND_TO_BACK),
                                            small(ShellActions.ARRANGE_ALIGN_LEFT),
                                            small(ShellActions.ARRANGE_ALIGN_CENTER),
                                            small(ShellActions.ARRANGE_ALIGN_RIGHT),
                                            small(ShellActions.ARRANGE_ALIGN_TOP),
                                            small(ShellActions.ARRANGE_ALIGN_MIDDLE),
                                            small(ShellActions.ARRANGE_ALIGN_BOTTOM),
                                            small(ShellActions.ARRANGE_DISTRIBUTE_H),
                                            small(ShellActions.ARRANGE_DISTRIBUTE_V))),
                            new RibbonSpec.GroupSpec("Output", List.of(large(ShellActions.FILE_PRINT))))),
            new RibbonSpec.TabSpec(
                    "View",
                    List.of(new RibbonSpec.GroupSpec(
                            "Zoom",
                            List.of(
                                    small(ShellActions.VIEW_ZOOM_IN),
                                    small(ShellActions.VIEW_ZOOM_OUT),
                                    small(ShellActions.VIEW_ZOOM_FIT)))))));

    private final RibbonBuilder builder;
    private final RibbonContexts contexts;

    public ShellRibbon(RibbonBuilder builder, RibbonContexts contexts) {
        this.builder = builder;
        this.contexts = contexts;
    }

    /** Builds a fresh ribbon control for a window. FX thread only. */
    public TabPane create() {
        return builder.build(SPEC, contexts);
    }

    /** Action ids for the quick-access toolbar, in display order. */
    public List<String> quickAccessActionIds() {
        return QUICK_ACCESS;
    }
}
