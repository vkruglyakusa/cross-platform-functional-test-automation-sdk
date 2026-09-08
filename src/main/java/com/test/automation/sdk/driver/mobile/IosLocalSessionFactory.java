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
 * {@link SessionFactory} for {@link Platform#IOS} + {@link RunMode#LOCAL}.
 * See {@link AndroidLocalSessionFactory} for the delegation rationale.
 */
public final class IosLocalSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.IOS;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.LOCAL;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        return MobileExecutionStrategyFactory.forTarget(ExecutionTarget.LOCAL)
                .createDriver(new MobileSessionRequest("ios", context.getDeviceName()));
    }
}
