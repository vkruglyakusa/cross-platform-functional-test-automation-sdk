package com.test.automation.sdk.mobile.crawler;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate}. This facade keeps
 * the legacy {@code com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate} FQN usable.
 */
@Deprecated
public class MobileLocatorCandidate {

    public enum Strategy {
        RESOURCE_ID,
        ACCESSIBILITY_ID,
        TEXT,
        UIAUTOMATOR,
        PREDICATE_STRING,
        CLASS_CHAIN,
        XPATH
    }

    public enum Verification {
        STATIC,
        REMOTE
    }

    public enum Marker {
        UNIQUE,
        NOT_UNIQUE,
        DYNAMIC,
        STRUCTURAL
    }

    private final com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate delegate;

    public MobileLocatorCandidate(Strategy strategy, String value) {
        this(new com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate(
                com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate.Strategy.valueOf(strategy.name()),
                value));
    }

    MobileLocatorCandidate(com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate delegate) {
        this.delegate = delegate;
    }

    public Strategy getStrategy() {
        return Strategy.valueOf(delegate.getStrategy().name());
    }

    public String getValue() {
        return delegate.getValue();
    }

    public Verification getVerification() {
        return Verification.valueOf(delegate.getVerification().name());
    }

    public void setVerification(Verification verification) {
        delegate.setVerification(
                com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate.Verification.valueOf(
                        verification.name()));
    }

    public Marker getMarker() {
        return delegate.getMarker() == null ? null : Marker.valueOf(delegate.getMarker().name());
    }

    public void setMarker(Marker marker) {
        delegate.setMarker(com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate.Marker.valueOf(
                marker.name()));
    }

    public int getMatchCount() {
        return delegate.getMatchCount();
    }

    public void setMatchCount(int matchCount) {
        delegate.setMatchCount(matchCount);
    }

    public boolean isUnique() {
        return delegate.isUnique();
    }

    public String toReportLine() {
        return delegate.toReportLine();
    }

    com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate unwrap() {
        return delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
