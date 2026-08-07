package com.rohit.labelbuilder.desktop.canvas;

import org.springframework.stereotype.Component;

/**
 * Routes the shell's view commands (menu/ribbon Zoom actions) to the active {@link DesignCanvas}.
 *
 * <p>{@link com.rohit.labelbuilder.desktop.shell.ShellActions} registers its handlers once, at
 * startup, before any window exists — this indirection lets those singleton actions reach the
 * per-window canvas the main-window controller installs. Calls are no-ops until a canvas is
 * active, so an action fired with no window does nothing rather than failing.
 */
@Component
public class CanvasCommands {

    private DesignCanvas active;

    public void setActive(DesignCanvas canvas) {
        this.active = canvas;
    }

    public void zoomIn() {
        if (active != null) {
            active.zoomIn();
        }
    }

    public void zoomOut() {
        if (active != null) {
            active.zoomOut();
        }
    }

    public void zoomToFit() {
        if (active != null) {
            active.zoomToFit();
        }
    }
}
