package com.test.automation.sdk.visual;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Screenshot-baseline visual regression engine.
 *
 * <p>Zero-config visual regression: the first time a given
 * {@code checkpointName} is checked, the current screenshot is saved as the
 * accepted baseline and the check reports a match (nothing to compare
 * against yet). Every subsequent check compares the new screenshot against
 * that stored baseline using {@link ImageDiffEngine}, saves the actual
 * screenshot and (on mismatch) a red-highlighted diff image next to it, and
 * reports whether the mismatch percentage stayed within the configured
 * tolerance.</p>
 *
 * <p>Baselines are intentionally kept as plain PNG files in the configured
 * {@code baselineDirectory} (default {@code src/test/resources/visual-baselines},
 * i.e. inside the consumer project's source tree) so they can be committed to
 * version control and reviewed like any other test asset -- approving a
 * legitimate UI change is just a normal file diff/commit, no external service
 * required.</p>
 */
public class VisualRegressionChecker {

    private final Path baselineDirectory;
    private final Path outputDirectory;
    private final double mismatchThresholdPercent;
    private final int pixelColorTolerance;
    private final boolean updateBaselines;

    public VisualRegressionChecker(Path baselineDirectory, Path outputDirectory,
                                    double mismatchThresholdPercent, int pixelColorTolerance,
                                    boolean updateBaselines) {
        this.baselineDirectory = baselineDirectory;
        this.outputDirectory = outputDirectory;
        this.mismatchThresholdPercent = mismatchThresholdPercent;
        this.pixelColorTolerance = pixelColorTolerance;
        this.updateBaselines = updateBaselines;
    }

    /**
     * Compares a raw PNG screenshot against the stored baseline for
     * {@code checkpointName}, creating that baseline if it does not exist yet
     * (or when re-baselining is requested via {@code updateBaselines}).
     */
    public VisualComparisonResult check(byte[] screenshotPng, String checkpointName) {
        String safeName = sanitize(checkpointName);
        Path baselineFile = baselineDirectory.resolve(safeName + ".png");

        try {
            BufferedImage actual = readImage(screenshotPng);

            if (updateBaselines || !Files.exists(baselineFile)) {
                Files.createDirectories(baselineDirectory);
                Files.write(baselineFile, screenshotPng);
                return new VisualComparisonResult(checkpointName, true, true, 0.0,
                        "Baseline created/updated -- nothing to compare against yet", baselineFile, baselineFile, null);
            }

            BufferedImage baseline = ImageIO.read(baselineFile.toFile());
            if (baseline == null) {
                return new VisualComparisonResult(checkpointName, false, false, 100.0,
                        "Existing baseline file could not be read as an image: " + baselineFile, baselineFile, null, null);
            }

            Path actualFile = outputDirectory.resolve(safeName).resolve("actual.png");
            Files.createDirectories(actualFile.getParent());
            Files.write(actualFile, screenshotPng);

            ImageDiffEngine.DiffResult diff = ImageDiffEngine.compare(baseline, actual, pixelColorTolerance);
            if (!diff.isComparable()) {
                return new VisualComparisonResult(checkpointName, false, false, diff.getMismatchPercentage(),
                        diff.getIncomparableReason(), baselineFile, actualFile, null);
            }

            boolean matched = diff.getMismatchPercentage() <= mismatchThresholdPercent;
            Path diffFile = null;
            if (!matched) {
                diffFile = outputDirectory.resolve(safeName).resolve("diff.png");
                ImageIO.write(diff.getDiffImage(), "png", diffFile.toFile());
            }

            String detail = String.format("mismatch=%.3f%% (threshold=%.3f%%, %d pixel(s) differ)",
                    diff.getMismatchPercentage(), mismatchThresholdPercent, diff.getMismatchedPixelCount());
            return new VisualComparisonResult(checkpointName, false, matched, diff.getMismatchPercentage(),
                    detail, baselineFile, actualFile, diffFile);
        } catch (IOException e) {
            return new VisualComparisonResult(checkpointName, false, false, 100.0,
                    "Visual comparison failed due to an I/O error: " + e.getMessage(), baselineFile, null, null);
        }
    }

    private static BufferedImage readImage(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) {
            throw new IOException("Captured screenshot bytes could not be decoded as an image");
        }
        return image;
    }

    private static String sanitize(String checkpointName) {
        return checkpointName.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    /** Convenience factory reading every setting from {@link com.test.automation.sdk.config.ConfigurationManager}. */
    public static VisualRegressionChecker fromConfiguration() {
        com.test.automation.sdk.config.ConfigurationManager.VisualRegressionConfig config =
                com.test.automation.sdk.config.ConfigurationManager.getVisualRegressionConfig();
        return new VisualRegressionChecker(
                Paths.get(config.baselineDirectory()),
                Paths.get(config.outputDirectory()),
                config.mismatchThresholdPercent(),
                config.pixelColorTolerance(),
                config.updateBaselines());
    }
}
