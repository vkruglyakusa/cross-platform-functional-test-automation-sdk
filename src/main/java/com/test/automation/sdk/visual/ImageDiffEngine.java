package com.test.automation.sdk.visual;

import java.awt.image.BufferedImage;

/**
 * Pure, dependency-free pixel-diff engine for {@link VisualRegressionChecker}.
 *
 * <p>Deliberately does not depend on any external image-comparison library
 * (e.g. OpenCV) so the SDK's visual regression feature works out of the box
 * with zero extra native dependencies -- consistent with the zero-config
 * philosophy already used by {@code com.test.automation.sdk.healing}. Each
 * pixel's RGB channels are compared with a small per-channel tolerance (to
 * absorb harmless anti-aliasing/compression noise), and a red-highlighted
 * diff image is produced for any run whose mismatch percentage is
 * non-negligible, so a human reviewer can see exactly what changed.</p>
 */
public final class ImageDiffEngine {

    private ImageDiffEngine() {
    }

    /**
     * Compares {@code baseline} against {@code actual} pixel-by-pixel.
     *
     * @param baseline previously accepted screenshot
     * @param actual screenshot captured by the current run
     * @param pixelColorTolerance maximum per-channel (0-255) delta still
     *                            considered "the same pixel" -- absorbs
     *                            harmless anti-aliasing/JPEG-style noise
     * @return a {@link DiffResult} describing the comparison, including a
     *         highlighted diff image when the images are the same size
     */
    public static DiffResult compare(BufferedImage baseline, BufferedImage actual, int pixelColorTolerance) {
        if (baseline.getWidth() != actual.getWidth() || baseline.getHeight() != actual.getHeight()) {
            return new DiffResult(false, 100.0, 0, null,
                    "Dimension mismatch: baseline=" + baseline.getWidth() + "x" + baseline.getHeight()
                            + " actual=" + actual.getWidth() + "x" + actual.getHeight());
        }

        int width = baseline.getWidth();
        int height = baseline.getHeight();
        long mismatchedPixels = 0;
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int baselineRgb = baseline.getRGB(x, y);
                int actualRgb = actual.getRGB(x, y);
                if (pixelsDiffer(baselineRgb, actualRgb, pixelColorTolerance)) {
                    mismatchedPixels++;
                    diff.setRGB(x, y, 0xFFFF0000); // opaque red highlight
                } else {
                    diff.setRGB(x, y, actualRgb);
                }
            }
        }

        long totalPixels = (long) width * height;
        double mismatchPercentage = totalPixels == 0 ? 0.0 : (mismatchedPixels * 100.0) / totalPixels;
        return new DiffResult(true, mismatchPercentage, mismatchedPixels, diff, null);
    }

    private static boolean pixelsDiffer(int rgb1, int rgb2, int tolerance) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) > tolerance
                || Math.abs(g1 - g2) > tolerance
                || Math.abs(b1 - b2) > tolerance;
    }

    /** Outcome of a single {@link #compare(BufferedImage, BufferedImage, int)} call. */
    public static final class DiffResult {
        private final boolean comparable;
        private final double mismatchPercentage;
        private final long mismatchedPixelCount;
        private final BufferedImage diffImage;
        private final String incomparableReason;

        DiffResult(boolean comparable, double mismatchPercentage, long mismatchedPixelCount,
                   BufferedImage diffImage, String incomparableReason) {
            this.comparable = comparable;
            this.mismatchPercentage = mismatchPercentage;
            this.mismatchedPixelCount = mismatchedPixelCount;
            this.diffImage = diffImage;
            this.incomparableReason = incomparableReason;
        }

        /** {@code false} when baseline/actual dimensions differ -- no diff image is produced in that case. */
        public boolean isComparable() {
            return comparable;
        }

        public double getMismatchPercentage() {
            return mismatchPercentage;
        }

        public long getMismatchedPixelCount() {
            return mismatchedPixelCount;
        }

        /** {@code null} when {@link #isComparable()} is {@code false}. */
        public BufferedImage getDiffImage() {
            return diffImage;
        }

        /** Human-readable reason the images could not be compared, or {@code null} when comparable. */
        public String getIncomparableReason() {
            return incomparableReason;
        }
    }
}
