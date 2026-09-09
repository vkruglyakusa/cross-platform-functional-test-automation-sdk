package com.test.automation.sdk.tools.crawler.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * One discovered screen element (native or WebView-hosted) plus every locator
 * candidate considered for it. Mirrors the desktop SDK's {@code ElementCrawler.ElementInfo}.
 */
public class MobileElementInfo {

    private final String platform;
    private final String tagOrClassName;
    private final String text;
    private final List<MobileLocatorCandidate> candidates = new ArrayList<>();
    private boolean fromWebView;
    private String webViewContext;
    private String suggestedFieldName;
    private final List<String> visibleAfterSteps = new ArrayList<>();

    public MobileElementInfo(String platform, String tagOrClassName, String text) {
        this.platform = platform;
        this.tagOrClassName = tagOrClassName;
        this.text = text;
    }

    public String getPlatform() {
        return platform;
    }

    public String getTagOrClassName() {
        return tagOrClassName;
    }

    public String getText() {
        return text;
    }

    public List<MobileLocatorCandidate> getCandidates() {
        return candidates;
    }

    public void addCandidate(MobileLocatorCandidate candidate) {
        candidates.add(candidate);
    }

    public boolean isFromWebView() {
        return fromWebView;
    }

    public void setFromWebView(boolean fromWebView) {
        this.fromWebView = fromWebView;
    }

    public String getWebViewContext() {
        return webViewContext;
    }

    public void setWebViewContext(String webViewContext) {
        this.webViewContext = webViewContext;
    }

    public String getSuggestedFieldName() {
        return suggestedFieldName;
    }

    public void setSuggestedFieldName(String suggestedFieldName) {
        this.suggestedFieldName = suggestedFieldName;
    }

    /** First candidate in ladder order marked {@code UNIQUE}, or {@code null} if none resolved. */
    public MobileLocatorCandidate getBestUniqueCandidate() {
        for (MobileLocatorCandidate candidate : candidates) {
            if (candidate.isUnique()) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isResolved() {
        return getBestUniqueCandidate() != null;
    }

    /** Records that this element was also observed after a given crawl step (see {@link com.test.automation.sdk.tools.crawler.mobile.MobileDataDrivenCrawler}). */
    public void addVisibleAfterStep(String stepDescription) {
        if (stepDescription != null && !visibleAfterSteps.contains(stepDescription)) {
            visibleAfterSteps.add(stepDescription);
        }
    }

    public List<String> getVisibleAfterSteps() {
        return visibleAfterSteps;
    }

    /** Stable dedup/merge key for this element across screens -- tag + best identity value available. */
    public String mergeKey() {
        MobileLocatorCandidate best = getBestUniqueCandidate();
        String identity = best != null ? best.getStrategy() + "=" + best.getValue() : text;
        return tagOrClassName + "::" + identity;
    }
}
