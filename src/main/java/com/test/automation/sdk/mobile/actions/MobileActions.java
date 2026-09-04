package com.test.automation.sdk.mobile.actions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.appium.java_client.AppiumDriver;

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
}
