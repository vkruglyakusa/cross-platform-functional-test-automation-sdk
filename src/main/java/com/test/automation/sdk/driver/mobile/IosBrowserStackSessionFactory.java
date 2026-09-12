package com.test.automation.sdk.driver.mobile;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.ProviderId;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;

/**
 * BrowserStack provider factory for iOS Appium remote execution.
 * See {@link AndroidBrowserStackSessionFactory} for the delegation rationale.
 */
public final class IosBrowserStackSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.IOS;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.REMOTE;
    }

    @Override
    public ProviderId getProviderId() {
        return new ProviderId("browserstack");
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        return MobileDriverFactory.getBrowserStackDriver("ios", context.getDeviceName());
    }
}
