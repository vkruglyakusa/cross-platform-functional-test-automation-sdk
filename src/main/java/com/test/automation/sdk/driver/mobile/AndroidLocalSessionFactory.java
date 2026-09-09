package com.test.automation.sdk.driver.mobile;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;

/**
 * {@link SessionFactory} for {@link Platform#ANDROID} + {@link RunMode#LOCAL}.
 *
 * Deliberately delegates to the existing, already-tested
 * {@link MobileDriverFactory#getLocalDriver(String, String)} rather than
 * duplicating its Appium capability-building logic -- see
 * {@code docs/proposals/sdk-structure-cleanup-assessment-updated-v2.md}
 * Phase 1, which retired the previously-parallel
 * {@code mobile.execution.MobileExecutionStrategy} hierarchy in favor of
 * {@link com.test.automation.sdk.execution.SessionFactoryRegistry} as the
 * single session-selection mechanism.
 */
public final class AndroidLocalSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.ANDROID;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.LOCAL;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        return MobileDriverFactory.getLocalDriver("android", context.getDeviceName());
    }
}
