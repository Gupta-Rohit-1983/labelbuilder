package com.rohit.labelbuilder.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ImageComparatorTest {

    private static BufferedImage filled(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    @Test
    void identicalImagesReportNoDifference() {
        BufferedImage a = filled(4, 4, 0xFFFFFFFF);
        BufferedImage b = filled(4, 4, 0xFFFFFFFF);

        ImageComparator.Diff diff = ImageComparator.compare(a, b);

        assertThat(diff.isIdentical()).isTrue();
        assertThat(diff.differingPixels()).isZero();
        assertThat(diff.fractionDiffering()).isZero();
        assertThat(diff.totalPixels()).isEqualTo(16);
    }

    @Test
    void oneChangedPixelIsCountedWithItsDelta() {
        BufferedImage a = filled(2, 2, 0xFF000000);
        BufferedImage b = filled(2, 2, 0xFF000000);
        b.setRGB(0, 0, 0xFF0000FF); // blue channel 0 -> 255

        ImageComparator.Diff diff = ImageComparator.compare(a, b);

        assertThat(diff.differingPixels()).isEqualTo(1);
        assertThat(diff.maxChannelDelta()).isEqualTo(255);
        assertThat(diff.fractionDiffering()).isEqualTo(0.25);
    }

    @Test
    void mismatchedSizesAreRejected() {
        assertThatThrownBy(() -> ImageComparator.compare(filled(2, 2, 0), filled(3, 2, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
