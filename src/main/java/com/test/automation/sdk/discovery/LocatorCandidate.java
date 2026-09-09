package com.test.automation.sdk.discovery;

/**
 * One locator candidate for a discovered element, normalized from either the
 * desktop web crawler's free-form composite-key strategy labels
 * ({@code utility.ElementCrawler.ElementInfo.allXpaths}) or the mobile crawler's
 * typed {@code mobile.crawler.MobileLocatorCandidate} -- see Structure Cleanup
 * Phase 6 (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md).
 *
 * This is a pure normalization/reporting contract: it does not replace either
 * crawler's own richer, platform-specific candidate representation, and it does
 * not change either crawler's uniqueness-detection algorithm (Guardrails #15/#16
 * -- keep mature crawler algorithms platform-specific, normalize outputs only).
 */
public final class LocatorCandidate {

    /** Uniqueness/safety marker, normalized across both crawlers' labeling conventions. */
    public enum Marker {
        /** Exactly 1 match -- safe to use. */
        UNIQUE,
        /** More than 1 match -- do not use. */
        NOT_UNIQUE,
        /** Matches a known auto-generated/list-recycled identifier pattern -- do not use even if unique today. */
        DYNAMIC,
        /** 0 matches -- element may be conditional, or the page/screen changed since discovery. */
        STALE,
        /** Position-based fallback -- do not use unless absolutely last resort. */
        STRUCTURAL,
        /** Informational candidate that doesn't map to one of the standard markers above (e.g. a detected sibling-group locator). */
        OTHER
    }

    private final String strategyLabel;
    private final String value;
    private final Marker marker;
    private final int matchCount;

    public LocatorCandidate(String strategyLabel, String value, Marker marker, int matchCount) {
        this.strategyLabel = strategyLabel;
        this.value = value;
        this.marker = marker;
        this.matchCount = matchCount;
    }

    public String getStrategyLabel() {
        return strategyLabel;
    }

    public String getValue() {
        return value;
    }

    public Marker getMarker() {
        return marker;
    }

    /** Number of elements this candidate matched, when known; -1 when not tracked by the source crawler. */
    public int getMatchCount() {
        return matchCount;
    }

    public boolean isUnique() {
        return marker == Marker.UNIQUE;
    }

    public String toReportLine() {
        String detail = matchCount >= 0 ? " (" + matchCount + " found)" : "";
        return strategyLabel + " = \"" + value + "\" -> " + marker + detail;
    }

    @Override
    public String toString() {
        return toReportLine();
    }
}
