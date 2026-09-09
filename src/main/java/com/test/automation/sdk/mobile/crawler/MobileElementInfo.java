package com.test.automation.sdk.mobile.crawler;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo}. This wrapper preserves
 * the legacy source-level API while delegating to the new package.
 */
@Deprecated
public class MobileElementInfo {

    private final com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo delegate;

    public MobileElementInfo(String platform, String tagOrClassName, String text) {
        this(new com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo(platform, tagOrClassName, text));
    }

    MobileElementInfo(com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo delegate) {
        this.delegate = delegate;
    }

    public String getPlatform() {
        return delegate.getPlatform();
    }

    public String getTagOrClassName() {
        return delegate.getTagOrClassName();
    }

    public String getText() {
        return delegate.getText();
    }

    public List<MobileLocatorCandidate> getCandidates() {
        List<MobileLocatorCandidate> converted = new ArrayList<MobileLocatorCandidate>();
        for (com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate candidate : delegate.getCandidates()) {
            converted.add(new MobileLocatorCandidate(candidate));
        }
        return converted;
    }

    public void addCandidate(MobileLocatorCandidate candidate) {
        delegate.addCandidate(candidate.unwrap());
    }

    public boolean isFromWebView() {
        return delegate.isFromWebView();
    }

    public void setFromWebView(boolean fromWebView) {
        delegate.setFromWebView(fromWebView);
    }

    public String getWebViewContext() {
        return delegate.getWebViewContext();
    }

    public void setWebViewContext(String webViewContext) {
        delegate.setWebViewContext(webViewContext);
    }

    public String getSuggestedFieldName() {
        return delegate.getSuggestedFieldName();
    }

    public void setSuggestedFieldName(String suggestedFieldName) {
        delegate.setSuggestedFieldName(suggestedFieldName);
    }

    public MobileLocatorCandidate getBestUniqueCandidate() {
        com.test.automation.sdk.tools.crawler.mobile.MobileLocatorCandidate candidate = delegate.getBestUniqueCandidate();
        return candidate == null ? null : new MobileLocatorCandidate(candidate);
    }

    public boolean isResolved() {
        return delegate.isResolved();
    }

    public void addVisibleAfterStep(String stepDescription) {
        delegate.addVisibleAfterStep(stepDescription);
    }

    public List<String> getVisibleAfterSteps() {
        return delegate.getVisibleAfterSteps();
    }

    public String mergeKey() {
        return delegate.mergeKey();
    }

    com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo unwrap() {
        return delegate;
    }
}
