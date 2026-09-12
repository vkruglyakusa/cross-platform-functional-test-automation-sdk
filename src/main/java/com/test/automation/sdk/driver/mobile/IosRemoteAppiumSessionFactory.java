package com.test.automation.sdk.driver.mobile;

import org.openqa.selenium.WebDriver;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.ProviderId;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;

/** Session factory for iOS on a user-managed remote Appium server. */
public final class IosRemoteAppiumSessionFactory implements SessionFactory {
    @Override public Platform getPlatform() { return Platform.IOS; }
    @Override public RunMode getRunMode() { return RunMode.REMOTE; }
    @Override public ProviderId getProviderId() { return new ProviderId("custom"); }
    @Override public WebDriver createDriver(ExecutionContext context) {
        return MobileDriverFactory.getRemoteAppiumDriver("ios", context.getDeviceName());
    }
}
