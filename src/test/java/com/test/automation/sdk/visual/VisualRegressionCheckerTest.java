package com.test.automation.sdk.visual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VisualRegressionChecker")
class VisualRegressionCheckerTest {

    private static byte[] pngOf(int width, int height, int rgb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("first check for a checkpoint creates the baseline and reports a match")
    void firstCheckCreatesBaseline(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, false);

        VisualComparisonResult result = checker.check(pngOf(4, 4, 0xFF112233), "checkpoint-a");

        assertTrue(result.isBaselineCreated());
        assertTrue(result.isMatched());
        assertTrue(Files.exists(baselineDir.resolve("checkpoint-a.png")));
    }

    @Test
    @DisplayName("subsequent check against an identical screenshot matches without a diff image")
    void identicalScreenshotMatches(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, false);
        byte[] png = pngOf(6, 6, 0xFFAABBCC);

        checker.check(png, "checkpoint-b");
        VisualComparisonResult result = checker.check(png, "checkpoint-b");

        assertFalse(result.isBaselineCreated());
        assertTrue(result.isMatched());
        assertNull(result.getDiffPath());
        assertTrue(Files.exists(result.getActualPath()));
    }

    @Test
    @DisplayName("a screenshot that differs beyond the threshold fails and writes a diff image")
    void differingScreenshotFailsAndWritesDiff(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, false);

        checker.check(pngOf(6, 6, 0xFF000000), "checkpoint-c");
        VisualComparisonResult result = checker.check(pngOf(6, 6, 0xFFFFFFFF), "checkpoint-c");

        assertFalse(result.isBaselineCreated());
        assertFalse(result.isMatched());
        assertEquals(100.0, result.getMismatchPercentage());
        assertNotNull(result.getDiffPath());
        assertTrue(Files.exists(result.getDiffPath()));
    }

    @Test
    @DisplayName("updateBaselines=true always overwrites the baseline and reports a match")
    void updateBaselinesOverwritesExistingBaseline(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, true);

        checker.check(pngOf(4, 4, 0xFF000000), "checkpoint-d");
        VisualComparisonResult result = checker.check(pngOf(4, 4, 0xFFFFFFFF), "checkpoint-d");

        assertTrue(result.isBaselineCreated());
        assertTrue(result.isMatched());
    }

    @Test
    @DisplayName("a differently-sized screenshot is reported as not matched with a clear detail message")
    void dimensionMismatchIsReportedAsFailure(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, false);

        checker.check(pngOf(10, 10, 0xFF112233), "checkpoint-e");
        VisualComparisonResult result = checker.check(pngOf(20, 10, 0xFF112233), "checkpoint-e");

        assertFalse(result.isMatched());
        assertNotNull(result.getDetail());
        assertTrue(result.getDetail().toLowerCase().contains("dimension"));
    }

    @Test
    @DisplayName("checkpoint names are sanitized to safe filesystem characters")
    void checkpointNamesAreSanitized(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");
        Path outputDir = tempDir.resolve("output");
        VisualRegressionChecker checker = new VisualRegressionChecker(baselineDir, outputDir, 0.1, 5, false);

        checker.check(pngOf(3, 3, 0xFF000000), "Login Page / Home!!");

        assertTrue(Files.exists(baselineDir.resolve("Login_Page___Home__.png")));
    }
}
