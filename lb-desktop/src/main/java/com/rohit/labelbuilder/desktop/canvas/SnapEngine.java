package com.rohit.labelbuilder.desktop.canvas;

/**
 * Snapping along a single axis: combines grid snapping (rounds to the nearest grid line whenever
 * enabled) with guide snapping (snaps to a guide only when the cursor is within a threshold, and
 * takes priority over the grid when strictly closer). Pure functions — fully headless-testable;
 * {@link DesignCanvas} applies it per axis with a pixel-derived threshold.
 */
public final class SnapEngine {

    private SnapEngine() {}

    /**
     * @param value the raw model coordinate (mm)
     * @param settings grid configuration
     * @param guidePositions competing guide positions on this axis (mm)
     * @param thresholdMm how near a guide must be to capture the value
     * @return the snapped coordinate, or {@code value} if nothing applies
     */
    public static double snap(double value, GridSettings settings, double[] guidePositions, double thresholdMm) {
        double best = value;
        double bestDist = Double.MAX_VALUE;
        boolean found = false;

        if (settings.snapToGrid()) {
            double gridLine = settings.snap(value);
            best = gridLine;
            bestDist = Math.abs(gridLine - value);
            found = true;
        }
        for (double guide : guidePositions) {
            double dist = Math.abs(guide - value);
            if (dist <= thresholdMm && dist < bestDist) {
                best = guide;
                bestDist = dist;
                found = true;
            }
        }
        return found ? best : value;
    }
}
