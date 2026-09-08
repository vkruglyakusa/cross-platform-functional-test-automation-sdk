package com.test.automation.sdk.execution;

/**
 * The automation platform a test targets. This is the first of the two axes
 * (the other being {@link RunMode}) that the unified execution model
 * switches on -- see {@code docs/proposals/unified-sdk-architect-review.md}
 * section D/E.
 *
 * Generalizes the platform distinction that was previously implicit in
 * "which factory method did you call" ({@code WebDriverFactory.getWebDriver}
 * vs. {@code MobileDriverFactory.getDriver}).
 */
public enum Platform {

    /** Selenium WebDriver (Chrome/Firefox/Edge). */
    WEB,

    /** Appium AppiumDriver targeting an Android device/emulator. */
    ANDROID,

    /** Appium AppiumDriver targeting an iOS device/simulator. */
    IOS

    // Future: MOBILE_WEB (browser-only mobile web, no native app) can be added
    // here once a concrete need arises -- see roadmap FUTURE items.
}
