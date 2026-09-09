package com.test.automation.sdk.execution;

/**
 * The browser/user-mimic automation engine used to implement a session,
 * reserved as a third execution-selection dimension alongside {@link Platform}
 * and {@link RunMode} (Unified SDK Review Priority 5, section 11 of
 * docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md).
 *
 * <p>Today, {@link Platform#WEB} always implies {@link #SELENIUM} and
 * {@link Platform#ANDROID}/{@link Platform#IOS} always imply {@link #APPIUM},
 * so most callers never need to think about this dimension at all --
 * {@link ExecutionContext#forWeb(String, RunMode)}/
 * {@link ExecutionContext#forMobile(Platform, String, RunMode)} default it
 * automatically (see {@link SessionFactory#getAutomationTechnology()}).</p>
 *
 * <p>This enum exists so that, when a second Web (or user-mimic) technology
 * is introduced in the future -- e.g. Playwright -- the execution model can
 * disambiguate:</p>
 *
 * <pre>
 *   WEB + LOCAL + SELENIUM
 *   WEB + LOCAL + PLAYWRIGHT
 * </pre>
 *
 * <p>without redesigning {@code TestBase}, {@link ExecutionContext}, or
 * {@link SessionFactoryRegistry}. Introducing {@code PLAYWRIGHT}/
 * {@code USER_MIMIC} (or an actual Playwright {@code SessionFactory}) is
 * explicitly out of scope for Priority 5 -- only the seam is reserved.</p>
 */
public enum AutomationTechnology {

    /** Selenium WebDriver -- the only Web technology implemented today. */
    SELENIUM,

    /** Appium (java-client) -- the only Mobile technology implemented today. */
    APPIUM

    // Future: PLAYWRIGHT, USER_MIMIC -- add here + a matching SessionFactory
    // implementation per Platform when a concrete need arises. Do not
    // implement ahead of that need (see class Javadoc).
}
