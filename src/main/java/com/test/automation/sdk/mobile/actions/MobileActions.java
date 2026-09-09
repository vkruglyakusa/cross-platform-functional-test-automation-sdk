package com.test.automation.sdk.mobile.actions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.remote.SupportsContextSwitching;

/**
 * Common mobile gesture/action helpers, layered on top of the official
 * {@code io.appium:java-client} typed API. Falls back to the W3C "Execute Method"
 * extension ({@code executeScript("mobile: ...")}) only for gestures without a
 * typed equivalent -- see docs/proposals/mobile-automation-strategy.md section 3.
 *
 * Mirrors the desktop SDK's convention of wrapping raw Selenium/Appium calls behind
 * a stable helper API that page objects call into (compare to TestBase's
 * waitForElementPresent/clearAndType helpers in functional-test-automation-sdk).
 */
public final class MobileActions {

    private static final int DEFAULT_WAIT_SECONDS = 30;

    private MobileActions() {}

    public static void waitForElementPresent(AppiumDriver driver, WebElement element) {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_SECONDS))
                .until(ExpectedConditions.visibilityOf(element));
    }

    public static void waitForElementPresent(AppiumDriver driver, WebElement element, int timeoutSeconds) {
        new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.visibilityOf(element));
    }

    public static void tap(AppiumDriver driver, WebElement element) {
        waitForElementPresent(driver, element);
        element.click();
    }

    /** Taps at raw screen coordinates, for cases with no element to click (e.g. canvas/map widgets). */
    public static void tap(AppiumDriver driver, int x, int y) {
        Map<String, Object> params = new HashMap<>();
        params.put("x", x);
        params.put("y", y);
        driver.executeScript("mobile: tap", params);
    }

    public static void longPress(AppiumDriver driver, WebElement element, Duration duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("elementId", ((org.openqa.selenium.remote.RemoteWebElement) element).getId());
        params.put("duration", duration.toMillis());
        driver.executeScript("mobile: longClickGesture", params);
    }

    public static void swipe(AppiumDriver driver, int startX, int startY, int endX, int endY, Duration duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("startX", startX);
        params.put("startY", startY);
        params.put("endX", endX);
        params.put("endY", endY);
        params.put("duration", duration.toMillis());
        driver.executeScript("mobile: swipeGesture", params);
    }

    public static void scrollToElement(AppiumDriver driver, String strategy, String selector) {
        Map<String, Object> params = new HashMap<>();
        params.put("strategy", strategy); // e.g. "accessibility id", "-android uiautomator"
        params.put("selector", selector);
        driver.executeScript("mobile: scroll", params);
    }

    public static void hideKeyboard(AppiumDriver driver) {
        driver.executeScript("mobile: hideKeyboard");
    }

    // ------------------------------------------------------------------
    // Context switching (NATIVE_APP <-> WEBVIEW_*)
    //
    // Promoted from the proven pattern in
    // MobileElementCrawler.crawlWebViewsIfPresent() (WebView detection during
    // crawling) into general-purpose, public helpers so page objects/tests can
    // switch context directly -- see docs/proposals/sdk-structure-cleanup-
    // assessment-updated-v4.md, "Priority & Sequencing Adjustments" item 4.
    // ------------------------------------------------------------------

    private static final String NATIVE_APP_CONTEXT = "NATIVE_APP";
    private static final String WEBVIEW_CONTEXT_PREFIX = "WEBVIEW";

    private static SupportsContextSwitching requireContextSwitchingDriver(AppiumDriver driver) {
        if (!(driver instanceof SupportsContextSwitching)) {
            throw new IllegalStateException(
                    "Driver does not support context switching (not a SupportsContextSwitching instance): "
                            + driver.getClass().getName());
        }
        return (SupportsContextSwitching) driver;
    }

    /** All context handles currently reported by the driver, e.g. {@code ["NATIVE_APP", "WEBVIEW_com.example.app"]}. */
    public static Set<String> getAvailableContexts(AppiumDriver driver) {
        return requireContextSwitchingDriver(driver).getContextHandles();
    }

    /** The context the driver is currently switched to. */
    public static String getCurrentContext(AppiumDriver driver) {
        return requireContextSwitchingDriver(driver).getContext();
    }

    /** True when the driver's current context is a WebView (name starts with {@code "WEBVIEW"}). */
    public static boolean isInWebViewContext(AppiumDriver driver) {
        String current = getCurrentContext(driver);
        return current != null && current.startsWith(WEBVIEW_CONTEXT_PREFIX);
    }

    /** Switches the driver back to the native context. Safe to call even if already native. */
    public static void switchToNativeContext(AppiumDriver driver) {
        requireContextSwitchingDriver(driver).context(NATIVE_APP_CONTEXT);
    }

    /** Switches the driver to the exact context name given (as returned by {@link #getAvailableContexts(AppiumDriver)}). */
    public static void switchToContext(AppiumDriver driver, String contextName) {
        requireContextSwitchingDriver(driver).context(contextName);
    }

    /**
     * Switches to the first available WebView context.
     *
     * @throws IllegalStateException if no WebView context is currently present
     *         (e.g. the hybrid screen hasn't finished loading its embedded web content yet)
     */
    public static void switchToWebViewContext(AppiumDriver driver) {
        SupportsContextSwitching contextDriver = requireContextSwitchingDriver(driver);
        for (String context : contextDriver.getContextHandles()) {
            if (context != null && context.startsWith(WEBVIEW_CONTEXT_PREFIX)) {
                contextDriver.context(context);
                return;
            }
        }
        throw new IllegalStateException("No WEBVIEW context is currently available. Available contexts: "
                + contextDriver.getContextHandles());
    }

    /**
     * Switches to the WebView context whose name contains {@code nameContains}
     * (e.g. a specific package name), for apps with more than one active WebView.
     *
     * @throws IllegalStateException if no matching WebView context is currently present
     */
    public static void switchToWebViewContext(AppiumDriver driver, String nameContains) {
        SupportsContextSwitching contextDriver = requireContextSwitchingDriver(driver);
        for (String context : contextDriver.getContextHandles()) {
            if (context != null && context.startsWith(WEBVIEW_CONTEXT_PREFIX) && context.contains(nameContains)) {
                contextDriver.context(context);
                return;
            }
        }
        throw new IllegalStateException("No WEBVIEW context containing [" + nameContains
                + "] is currently available. Available contexts: " + contextDriver.getContextHandles());
    }
}
