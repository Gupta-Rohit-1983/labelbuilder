package com.rohit.labelbuilder.render;

import java.awt.image.BufferedImage;

/**
 * Pixel-level image comparison for the PNG-diff regression suite. Reports how many pixels differ
 * and by how much, so tests can assert an exact match or allow a small tolerance (anti-aliased
 * edges can differ by a channel step or two even between deterministic renders).
 */
public final class ImageComparator {

    private ImageComparator() {}

    /** Outcome of comparing two equally-sized images. */
    public record Diff(long differingPixels, long totalPixels, int maxChannelDelta, double meanChannelDelta) {

        /** Fraction of pixels that differ in any channel (0..1). */
        public double fractionDiffering() {
            return totalPixels == 0 ? 0 : (double) differingPixels / totalPixels;
        }

        public boolean isIdentical() {
            return differingPixels == 0;
        }
    }

    /**
     * @throws IllegalArgumentException if the images differ in size (a size mismatch is a bug in
     *     the renderer, not a diff to tolerate)
     */
    public static Diff compare(BufferedImage expected, BufferedImage actual) {
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            throw new IllegalArgumentException("Image sizes differ: %dx%d vs %dx%d"
                    .formatted(expected.getWidth(), expected.getHeight(), actual.getWidth(), actual.getHeight()));
        }
        long differing = 0;
        long totalDelta = 0;
        int maxDelta = 0;
        long channels = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int e = expected.getRGB(x, y);
                int a = actual.getRGB(x, y);
                if (e == a) {
                    channels += 4;
                    continue;
                }
                differing++;
                for (int shift = 0; shift < 32; shift += 8) {
                    int delta = Math.abs(((e >> shift) & 0xFF) - ((a >> shift) & 0xFF));
                    totalDelta += delta;
                    maxDelta = Math.max(maxDelta, delta);
                    channels++;
                }
            }
        }
        long totalPixels = (long) expected.getWidth() * expected.getHeight();
        double mean = channels == 0 ? 0 : (double) totalDelta / channels;
        return new Diff(differing, totalPixels, maxDelta, mean);
    }
}
