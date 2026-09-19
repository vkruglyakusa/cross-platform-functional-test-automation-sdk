package com.test.automation.sdk.visual;

/**
 * Outcome of a single {@link VisualRegressionChecker#check(byte[], String)} call.
 */
public final class VisualComparisonResult {

    private final String checkpointName;
    private final boolean baselineCreated;
    private final boolean matched;
    private final double mismatchPercentage;
    private final String detail;
    private final java.nio.file.Path baselinePath;
    private final java.nio.file.Path actualPath;
    private final java.nio.file.Path diffPath;

    VisualComparisonResult(String checkpointName, boolean baselineCreated, boolean matched,
                            double mismatchPercentage, String detail,
                            java.nio.file.Path baselinePath, java.nio.file.Path actualPath,
                            java.nio.file.Path diffPath) {
        this.checkpointName = checkpointName;
        this.baselineCreated = baselineCreated;
        this.matched = matched;
        this.mismatchPercentage = mismatchPercentage;
        this.detail = detail;
        this.baselinePath = baselinePath;
        this.actualPath = actualPath;
        this.diffPath = diffPath;
    }

    public String getCheckpointName() {
        return checkpointName;
    }

    /** {@code true} when no baseline previously existed and this run's screenshot was saved as the new baseline. */
    public boolean isBaselineCreated() {
        return baselineCreated;
    }

    /** {@code true} when the screenshot matched the baseline within the configured tolerance (or a baseline was just created). */
    public boolean isMatched() {
        return matched;
    }

    /** Percentage (0-100) of pixels that differed from the baseline. {@code 0.0} when {@link #isBaselineCreated()}. */
    public double getMismatchPercentage() {
        return mismatchPercentage;
    }

    /** Human-readable summary, e.g. a dimension-mismatch reason or the mismatch percentage. */
    public String getDetail() {
        return detail;
    }

    public java.nio.file.Path getBaselinePath() {
        return baselinePath;
    }

    public java.nio.file.Path getActualPath() {
        return actualPath;
    }

    /** {@code null} when matched, when a baseline was just created, or when dimensions differed. */
    public java.nio.file.Path getDiffPath() {
        return diffPath;
    }
}
