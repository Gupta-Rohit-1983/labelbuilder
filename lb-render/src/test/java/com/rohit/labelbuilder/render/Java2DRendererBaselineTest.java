package com.rohit.labelbuilder.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * PNG-diff regression: the reference renderer's output for a fixed vector scene must match a
 * committed baseline image (architecture §2 — the baseline locks the reference so later renderer
 * changes are caught). A small tolerance absorbs any anti-aliasing jitter; a real change to shapes
 * or colours moves far more pixels than that.
 *
 * <p>If the baseline is missing (first run after adding/altering the scene), it is written to the
 * test resources and the test fails asking for a re-run — a deliberate, reviewable bootstrap.
 */
class Java2DRendererBaselineTest {

    private static final String BASELINE_RESOURCE = "/render/baseline-basic.png";
    private static final Path BASELINE_SOURCE = Path.of("src", "test", "resources", "render", "baseline-basic.png");

    @Test
    void referenceOutputMatchesCommittedBaseline() throws IOException {
        BufferedImage actual = new Java2DRenderer().render(SampleScenes.basicVector(), SampleScenes.BASELINE_DPI);

        BufferedImage baseline = loadBaseline();
        if (baseline == null) {
            Files.createDirectories(BASELINE_SOURCE.getParent());
            ImageIO.write(actual, "png", BASELINE_SOURCE.toFile());
            fail("Baseline image was missing; generated it at " + BASELINE_SOURCE.toAbsolutePath()
                    + ". Review it and re-run the tests.");
            return;
        }

        ImageComparator.Diff diff = ImageComparator.compare(baseline, actual);
        assertThat(diff.fractionDiffering())
                .as("fraction of differing pixels vs baseline")
                .isLessThan(0.01);
        assertThat(diff.meanChannelDelta())
                .as("mean per-channel delta vs baseline")
                .isLessThan(3.0);
    }

    private static BufferedImage loadBaseline() throws IOException {
        try (InputStream in = Java2DRendererBaselineTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
            return in == null ? null : ImageIO.read(in);
        }
    }
}
