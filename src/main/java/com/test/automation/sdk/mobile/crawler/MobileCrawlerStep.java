package com.test.automation.sdk.mobile.crawler;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep}. This wrapper preserves
 * the legacy static-factory API for existing consumers.
 */
@Deprecated
public final class MobileCrawlerStep {

    public enum Action { TAP, TYPE }

    public enum By { RESOURCE_ID, ACCESSIBILITY_ID, TEXT, XPATH }

    private final com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep delegate;

    private MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep delegate) {
        this.delegate = delegate;
    }

    public static MobileCrawlerStep tapByResourceId(String resourceId) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.tapByResourceId(resourceId));
    }

    public static MobileCrawlerStep tapByAccessibilityId(String accessibilityId) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.tapByAccessibilityId(accessibilityId));
    }

    public static MobileCrawlerStep tapByText(String text) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.tapByText(text));
    }

    public static MobileCrawlerStep tapByXpath(String xpath) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.tapByXpath(xpath));
    }

    public static MobileCrawlerStep typeByResourceId(String resourceId, String value) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.typeByResourceId(resourceId, value));
    }

    public static MobileCrawlerStep typeByAccessibilityId(String accessibilityId, String value) {
        return new MobileCrawlerStep(com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep.typeByAccessibilityId(accessibilityId, value));
    }

    public MobileCrawlerStep describe(String description) {
        delegate.describe(description);
        return this;
    }

    public Action getAction() {
        return Action.valueOf(delegate.getAction().name());
    }

    public By getBy() {
        return By.valueOf(delegate.getBy().name());
    }

    public String getSelector() {
        return delegate.getSelector();
    }

    public String getInputValue() {
        return delegate.getInputValue();
    }

    public String getDescription() {
        return delegate.getDescription();
    }

    com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep unwrap() {
        return delegate;
    }
}
