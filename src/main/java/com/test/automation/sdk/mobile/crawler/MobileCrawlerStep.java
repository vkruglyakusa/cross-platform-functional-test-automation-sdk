package com.test.automation.sdk.mobile.crawler;

/**
 * One user-interaction step in a multi-screen crawl, mirroring the desktop SDK's
 * {@code CrawlerStep} semantic factories so the two crawlers feel familiar to the
 * same engineers. Used by {@link MobileDataDrivenCrawler} to drive the app from
 * screen to screen while crawling each screen along the way.
 */
public final class MobileCrawlerStep {

    /** What kind of interaction this step performs. */
    public enum Action { TAP, TYPE }

    /** Which attribute to locate the target element by. */
    public enum By { RESOURCE_ID, ACCESSIBILITY_ID, TEXT, XPATH }

    private final Action action;
    private final By by;
    private final String selector;
    private final String inputValue;
    private String description = "";

    private MobileCrawlerStep(Action action, By by, String selector, String inputValue) {
        this.action = action;
        this.by = by;
        this.selector = selector;
        this.inputValue = inputValue;
    }

    public static MobileCrawlerStep tapByResourceId(String resourceId) {
        return new MobileCrawlerStep(Action.TAP, By.RESOURCE_ID, resourceId, null);
    }

    public static MobileCrawlerStep tapByAccessibilityId(String accessibilityId) {
        return new MobileCrawlerStep(Action.TAP, By.ACCESSIBILITY_ID, accessibilityId, null);
    }

    public static MobileCrawlerStep tapByText(String text) {
        return new MobileCrawlerStep(Action.TAP, By.TEXT, text, null);
    }

    public static MobileCrawlerStep tapByXpath(String xpath) {
        return new MobileCrawlerStep(Action.TAP, By.XPATH, xpath, null);
    }

    public static MobileCrawlerStep typeByResourceId(String resourceId, String value) {
        return new MobileCrawlerStep(Action.TYPE, By.RESOURCE_ID, resourceId, value);
    }

    public static MobileCrawlerStep typeByAccessibilityId(String accessibilityId, String value) {
        return new MobileCrawlerStep(Action.TYPE, By.ACCESSIBILITY_ID, accessibilityId, value);
    }

    public MobileCrawlerStep describe(String description) {
        this.description = description;
        return this;
    }

    public Action getAction() {
        return action;
    }

    public By getBy() {
        return by;
    }

    public String getSelector() {
        return selector;
    }

    public String getInputValue() {
        return inputValue;
    }

    public String getDescription() {
        return description;
    }
}
