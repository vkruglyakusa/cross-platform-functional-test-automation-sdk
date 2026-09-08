package com.test.automation.sdk.driver.mobile;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.mobile.execution.ExecutionTarget;
import com.test.automation.sdk.mobile.execution.MobileExecutionStrategyFactory;
import com.test.automation.sdk.mobile.execution.MobileSessionRequest;

/**
 * {@link SessionFactory} for {@link Platform#ANDROID} + {@link RunMode#BROWSERSTACK}.
 * Delegates to the existing, already-tested
 * {@code mobile.execution.BrowserStackExecutionStrategy}. See
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
        return MobileExecutionStrategyFactory.forTarget(ExecutionTarget.BROWSERSTACK)
                .createDriver(new MobileSessionRequest("android", context.getDeviceName()));
    }
}
