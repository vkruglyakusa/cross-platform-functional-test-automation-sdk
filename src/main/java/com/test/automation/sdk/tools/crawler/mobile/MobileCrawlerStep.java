package com.test.automation.sdk.tools.crawler.mobile;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * One user-interaction step in a multi-screen crawl, mirroring the desktop SDK's
 * {@code CrawlerStep} semantic factories so the two crawlers feel familiar to the
 * same engineers. Used by {@link MobileDataDrivenCrawler} to drive the app from
 * screen to screen while crawling each screen along the way.
 */
public final class MobileCrawlerStep {

    /** What kind of interaction this step performs. */
    public enum Action { TAP, TYPE, SWIPE }

    /** Which attribute to locate the target element by. */
    public enum By { RESOURCE_ID, ACCESSIBILITY_ID, TEXT, XPATH }

    /** Swipe direction, used only when {@link #action} is {@link Action#SWIPE}. */
    public enum SwipeDirection { UP, DOWN }

    private final Action action;
    private final By by;
    private final String selector;
    private final String inputValue;
    private final SwipeDirection swipeDirection;
    private String description = "";

    private MobileCrawlerStep(Action action, By by, String selector, String inputValue) {
        this(action, by, selector, inputValue, null);
    }

    private MobileCrawlerStep(Action action, By by, String selector, String inputValue, SwipeDirection swipeDirection) {
        this.action = action;
        this.by = by;
        this.selector = selector;
        this.inputValue = inputValue;
        this.swipeDirection = swipeDirection;
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

    /** Full-screen swipe-down (content moves up), used to reveal further items in a long/lazy-loaded scrollable list. */
    public static MobileCrawlerStep swipeDown() {
        return new MobileCrawlerStep(Action.SWIPE, null, null, null, SwipeDirection.DOWN);
    }

    /** Full-screen swipe-up (content moves down). */
    public static MobileCrawlerStep swipeUp() {
        return new MobileCrawlerStep(Action.SWIPE, null, null, null, SwipeDirection.UP);
    }

    public SwipeDirection getSwipeDirection() {
        return swipeDirection;
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
