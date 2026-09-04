package com.test.automation.sdk.mobile.crawler;

/**
 * One candidate locator for a single screen element, plus how it was verified and
 * whether it is safe to use.
 *
 * Mirrors the desktop SDK's {@code ElementCrawler} uniqueness-marker convention
 * ({@code UNIQUE [x]} / {@code NOT UNIQUE} / {@code DYNAMIC} / {@code STRUCTURAL})
 * so the reports and generated page objects feel familiar to engineers who already
 * use the web crawler -- see docs/proposals/mobile-automation-strategy.md section 8a.
 */
public class MobileLocatorCandidate {

    /** Mobile locator strategy ladder (see strategy doc section 8a "Mobile locator priority ladder"). */
    public enum Strategy {
        /** Android {@code resource-id}. Highest priority on Android. */
        RESOURCE_ID,
        /** Android {@code content-desc} / iOS {@code name} -- accessibility id, highest priority on iOS. */
        ACCESSIBILITY_ID,
        /** Exact visible text (Android {@code text}). */
        TEXT,
        /** {@code -android uiautomator} UiSelector DSL -- compound conditions only. */
        UIAUTOMATOR,
        /** {@code -ios predicate string} -- exact attribute match, e.g. {@code label == 'X'}. */
        PREDICATE_STRING,
        /** {@code -ios class chain} -- structural, compound conditions only. */
        CLASS_CHAIN,
        /** Raw XPath -- last resort, mirrors the desktop ladder's "never positional" rule. */
        XPATH
    }

    /** How uniqueness for this candidate was determined. */
    public enum Verification {
        /** Counted against the single in-memory page-source snapshot -- zero remote calls. */
        STATIC,
        /** Confirmed with one live {@code driver.findElements(...)} round trip. */
        REMOTE
    }

    /** Same semantics as the desktop crawler's report markers. */
    public enum Marker {
        /** Exactly 1 match -- safe to use. */
        UNIQUE,
        /** More than 1 match -- do not use. */
        NOT_UNIQUE,
        /** Matches a known auto-generated/list-recycled identifier pattern -- do not use even if unique today. */
        DYNAMIC,
        /** Position-based fallback (e.g. tag+index XPath) -- do not use unless absolutely last resort. */
        STRUCTURAL
    }

    private final Strategy strategy;
    private final String value;
    private Verification verification = Verification.STATIC;
    private Marker marker;
    private int matchCount;

    public MobileLocatorCandidate(Strategy strategy, String value) {
        this.strategy = strategy;
        this.value = value;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public String getValue() {
        return value;
    }

    public Verification getVerification() {
        return verification;
    }

    public void setVerification(Verification verification) {
        this.verification = verification;
    }

    public Marker getMarker() {
        return marker;
    }

    public void setMarker(Marker marker) {
        this.marker = marker;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public boolean isUnique() {
        return marker == Marker.UNIQUE;
    }

    /** Report-line format, e.g. {@code RESOURCE_ID="com.app:id/submit" -> UNIQUE [x] (statically verified)}. */
    public String toReportLine() {
        String markerText;
        switch (marker) {
            case UNIQUE:
                markerText = "UNIQUE [x]";
                break;
            case NOT_UNIQUE:
                markerText = "NOT UNIQUE (" + matchCount + " found)";
                break;
            case DYNAMIC:
                markerText = "DYNAMIC";
                break;
            case STRUCTURAL:
            default:
                markerText = "STRUCTURAL";
                break;
        }
        String verifiedText = verification == Verification.REMOTE ? "remote-verified" : "statically verified";
        return strategy + "=\"" + value + "\" -> " + markerText + " (" + verifiedText + ")";
    }

    @Override
    public String toString() {
        return toReportLine();
    }
}
