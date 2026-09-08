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
 * {@link SessionFactory} for {@link Platform#ANDROID} + {@link RunMode#LOCAL}.
 *
 * Deliberately delegates to the existing, already-tested
 * {@code mobile.execution.LocalExecutionStrategy} (via the public
 * {@link MobileExecutionStrategyFactory#forTarget(ExecutionTarget)} entry
 * point) rather than duplicating its Appium capability-building logic -- see
 * {@code docs/proposals/unified-sdk-architect-review.md} Phase 2, which calls
 * for driver ACQUISITION to be unified without rewriting the working mobile
 * execution-strategy code underneath it.
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
        return MobileExecutionStrategyFactory.forTarget(ExecutionTarget.LOCAL)
                .createDriver(new MobileSessionRequest("android", context.getDeviceName()));
    }
}
