package com.rohit.labelbuilder.desktop;

import javafx.application.Application;

/**
 * Process entry point.
 *
 * <p>Delegates immediately to {@link FxApplication} so that JavaFX owns the process lifecycle and
 * Spring is started inside {@link FxApplication#init()} — see that class for the bootstrap
 * sequence. Kept as a separate class (rather than making the {@code Application} subclass the main
 * class) so the app can also be launched from an executable jar where the main class must not
 * extend {@code Application}.
 */
public final class LabelBuilderApp {

    private LabelBuilderApp() {}

    public static void main(String[] args) {
        Application.launch(FxApplication.class, args);
    }
}
