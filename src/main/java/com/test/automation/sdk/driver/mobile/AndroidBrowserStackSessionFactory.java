package com.test.automation.sdk.driver.mobile;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;

/**
 * {@link SessionFactory} for {@link Platform#ANDROID} + {@link RunMode#BROWSERSTACK}.
 * Delegates to the existing, already-tested
 * {@link MobileDriverFactory#getBrowserStackDriver(String, String)}. See
 * {@link AndroidLocalSessionFactory} for the delegation rationale.
 */
public final class AndroidBrowserStackSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.ANDROID;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.BROWSERSTACK;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        return MobileDriverFactory.getBrowserStackDriver("android", context.getDeviceName());
    }
}
