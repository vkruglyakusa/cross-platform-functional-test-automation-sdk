package com.test.automation.sdk.execution;

import org.openqa.selenium.WebDriver;

/**
 * A pluggable session factory for creating driver sessions for one
 * ({@link Platform}, {@link RunMode}) combination. Generalizes
 * {@code com.test.automation.sdk.mobile.execution.MobileExecutionStrategy}
 * (previously mobile-only, {@code AppiumDriver}-typed) so the same pattern
 * covers web as well -- {@code io.appium.java_client.AppiumDriver} already
 * {@code implements WebDriver}, so a single return type works for both.
 *
 * Tests and page objects never see this interface directly -- they only call
 * {@code TestBase}/{@code DriverManager}, which resolve and delegate to the
 * factory matching the current {@link ExecutionContext}. Adding a new
 * platform or provider means adding one new {@link Platform}/{@link RunMode}
 * value plus one new implementation of this interface, with no changes to
 * test code.
 */
public interface SessionFactory {

    /** Which {@link Platform} this factory creates sessions for. */
    Platform getPlatform();

    /** Which {@link RunMode} this factory creates sessions for. */
    RunMode getRunMode();

    /**
     * Which {@link AutomationTechnology} this factory implements. Defaults to
     * the platform-implied value ({@link AutomationTechnology#SELENIUM} for
     * {@link Platform#WEB}, {@link AutomationTechnology#APPIUM} for
     * {@link Platform#ANDROID}/{@link Platform#IOS}) so every existing
     * {@link SessionFactory} implementation keeps compiling/behaving
     * unchanged (Unified SDK Review Priority 5, section 11). Override only
     * when a platform gains more than one coexisting technology (e.g.
     * {@code WEB + PLAYWRIGHT} alongside {@code WEB + SELENIUM}).
     */
    default AutomationTechnology getAutomationTechnology() {
        return getPlatform() == Platform.WEB ? AutomationTechnology.SELENIUM : AutomationTechnology.APPIUM;
    }

    /** Creates and returns a ready-to-use driver session for the given context. */
    WebDriver createDriver(ExecutionContext context);

    /** Convenience derived from {@link #getRunMode()}; true for any non-local (cloud) target. */
    default boolean isRemote() {
        return getRunMode() != RunMode.LOCAL;
    }
}
