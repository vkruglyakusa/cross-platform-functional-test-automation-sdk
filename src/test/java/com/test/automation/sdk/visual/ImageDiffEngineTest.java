package com.test.automation.sdk.visual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ImageDiffEngine")
class ImageDiffEngineTest {

    private static BufferedImage solidColor(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    @Test
    @DisplayName("identical images report zero mismatch")
    void identicalImagesReportZeroMismatch() {
        BufferedImage baseline = solidColor(10, 10, 0xFF112233);
        BufferedImage actual = solidColor(10, 10, 0xFF112233);

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 0);

        assertTrue(result.isComparable());
        assertEquals(0.0, result.getMismatchPercentage());
        assertEquals(0, result.getMismatchedPixelCount());
        assertNotNull(result.getDiffImage());
    }

    @Test
    @DisplayName("completely different images report 100 percent mismatch")
    void completelyDifferentImagesReportFullMismatch() {
        BufferedImage baseline = solidColor(4, 4, 0xFF000000);
        BufferedImage actual = solidColor(4, 4, 0xFFFFFFFF);

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 0);

        assertTrue(result.isComparable());
        assertEquals(100.0, result.getMismatchPercentage());
        assertEquals(16, result.getMismatchedPixelCount());
    }

    @Test
    @DisplayName("small color deltas within tolerance are not flagged as mismatches")
    void withinToleranceDeltasAreIgnored() {
        BufferedImage baseline = solidColor(5, 5, 0xFF808080);
        BufferedImage actual = solidColor(5, 5, 0xFF858585); // +5 per channel

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 10);

        assertEquals(0.0, result.getMismatchPercentage());
    }

    @Test
    @DisplayName("color deltas beyond tolerance are flagged as mismatches")
    void beyondToleranceDeltasAreFlagged() {
        BufferedImage baseline = solidColor(5, 5, 0xFF808080);
        BufferedImage actual = solidColor(5, 5, 0xFFA0A0A0); // +32 per channel

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 10);

        assertEquals(100.0, result.getMismatchPercentage());
    }

    @Test
    @DisplayName("dimension mismatch is reported as not comparable with no diff image")
    void dimensionMismatchIsNotComparable() {
        BufferedImage baseline = solidColor(10, 10, 0xFFFFFFFF);
        BufferedImage actual = solidColor(20, 10, 0xFFFFFFFF);

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 0);

        assertFalse(result.isComparable());
        assertEquals(100.0, result.getMismatchPercentage());
        assertNull(result.getDiffImage());
        assertNotNull(result.getIncomparableReason());
    }

    @Test
    @DisplayName("partial mismatch reports the correct percentage")
    void partialMismatchReportsCorrectPercentage() {
        BufferedImage baseline = solidColor(10, 10, 0xFF000000);
        BufferedImage actual = solidColor(10, 10, 0xFF000000);
        // change 10 of the 100 pixels
        for (int x = 0; x < 10; x++) {
            actual.setRGB(x, 0, 0xFFFFFFFF);
        }

        ImageDiffEngine.DiffResult result = ImageDiffEngine.compare(baseline, actual, 0);

        assertEquals(10.0, result.getMismatchPercentage());
        assertEquals(10, result.getMismatchedPixelCount());
    }
}
