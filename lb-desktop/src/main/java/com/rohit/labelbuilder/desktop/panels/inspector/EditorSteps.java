package com.rohit.labelbuilder.desktop.panels.inspector;

import com.rohit.labelbuilder.model.meta.PropertyDescriptor;

/**
 * Chooses a spinner's step and bounds from a {@link PropertyDescriptor} (Phase 9b). Pure, so the
 * rules are testable and live in one place rather than being guessed per editor.
 *
 * <p>The step is the increment a single click should produce. Getting it wrong is quietly annoying:
 * stepping opacity by 0.5 jumps from transparent to opaque, and stepping a millimetre by 1 makes fine
 * positioning impossible — so the step is derived from what the property actually measures.
 */
final class EditorSteps {

    /** Spinner factories need finite bounds; the model's real limits are its own constructors' job. */
    private static final double WIDE_MIN = -100_000;

    private static final double WIDE_MAX = 100_000;

    private EditorSteps() {}

    static double stepFor(PropertyDescriptor descriptor) {
        if (descriptor.kind() == com.rohit.labelbuilder.model.meta.PropertyKind.INTEGER) {
            return 1;
        }
        // A 0..1 ratio (opacity, line spacing factors) needs a much finer step than a millimetre.
        if (descriptor.max().orElse(Double.MAX_VALUE) <= 1.0) {
            return 0.05;
        }
        // Degrees step by whole units; millimetres by half, which suits a 1 mm default grid.
        return "rotation".equals(descriptor.key()) ? 1 : 0.5;
    }

    static double minFor(PropertyDescriptor descriptor) {
        return descriptor.min().orElse(WIDE_MIN);
    }

    static double maxFor(PropertyDescriptor descriptor) {
        return descriptor.max().orElse(WIDE_MAX);
    }
}
