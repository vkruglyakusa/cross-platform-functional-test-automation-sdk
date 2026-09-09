package com.test.automation.sdk.utility;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.web.CrawlerStep}. This facade keeps the
 * legacy {@code com.test.automation.sdk.utility.CrawlerStep} FQN source-compatible.
 */
@Deprecated
public class CrawlerStep {

    public enum Action { SELECT, TYPE, CLICK, WAIT, CLEAR }

    public enum LocatorMode {
        XPATH,
        BY_LABEL,
        BY_PLACEHOLDER,
        BY_TEXT,
        BY_ARIA_LABEL,
        BY_FORM_CONTROL_NAME
    }

    private final com.test.automation.sdk.tools.crawler.web.CrawlerStep delegate;

    CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep delegate) {
        this.delegate = delegate;
    }

    public static CrawlerStep select(String xpath, String visibleText) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.select(xpath, visibleText));
    }

    public static CrawlerStep type(String xpath, String value) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.type(xpath, value));
    }

    public static CrawlerStep click(String xpath) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.click(xpath));
    }

    public static CrawlerStep clear(String xpath) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.clear(xpath));
    }

    public static CrawlerStep wait(int millis) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.wait(millis));
    }

    public static CrawlerStep selectByLabel(String labelText, String optionValue) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.selectByLabel(labelText, optionValue));
    }

    public static CrawlerStep typeByPlaceholder(String placeholder, String value) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.typeByPlaceholder(placeholder, value));
    }

    public static CrawlerStep typeByFormControlName(String formControlName, String value) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.typeByFormControlName(formControlName, value));
    }

    public static CrawlerStep clickByText(String visibleText) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.clickByText(visibleText));
    }

    public static CrawlerStep clickByAriaLabel(String ariaLabel) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.clickByAriaLabel(ariaLabel));
    }

    public static CrawlerStep selectByAriaLabel(String ariaLabel, String optionValue) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.selectByAriaLabel(ariaLabel, optionValue));
    }

    public static CrawlerStep selectByFormControlName(String formControlName, String optionValue) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.selectByFormControlName(formControlName, optionValue));
    }

    public static CrawlerStep clickByLabel(String labelText) {
        return new CrawlerStep(com.test.automation.sdk.tools.crawler.web.CrawlerStep.clickByLabel(labelText));
    }

    public CrawlerStep describe(String description) {
        delegate.describe(description);
        return this;
    }

    public Action getAction() {
        return Action.valueOf(delegate.getAction().name());
    }

    public LocatorMode getLocatorMode() {
        return LocatorMode.valueOf(delegate.getLocatorMode().name());
    }

    public String getLocator() {
        return delegate.getLocator();
    }

    public String getValue() {
        return delegate.getValue();
    }

    public String getDescription() {
        return delegate.getDescription();
    }

    public boolean isSemantic() {
        return delegate.isSemantic();
    }

    public String label() {
        return delegate.label();
    }

    com.test.automation.sdk.tools.crawler.web.CrawlerStep unwrap() {
        return delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
