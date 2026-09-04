package com.test.automation.sdk.mobile.driver;

import io.appium.java_client.AppiumDriver;

import com.test.automation.sdk.mobile.execution.MobileExecutionStrategy;
import com.test.automation.sdk.mobile.execution.MobileExecutionStrategyFactory;
import com.test.automation.sdk.mobile.execution.MobileSessionRequest;

/**
 * Creates {@link AppiumDriver} instances for Android or iOS.
 *
 * Thin public-facing facade: the actual per-target driver-creation logic (local Appium
 * capabilities vs. BrowserStack SDK/javaagent rerouting) lives behind
 * {@link MobileExecutionStrategy} implementations, resolved by
 * {@link MobileExecutionStrategyFactory} from
 * {@link com.test.automation.sdk.mobile.execution.ExecutionTarget}. This class's public
 * method signature is intentionally unchanged so existing callers (e.g. {@code MobileTestBase})
 * do not need to change.
 */
public final class MobileDriverFactory {

    private MobileDriverFactory() {}

    /**
     * @param mobileOS   "android" or "ios" (case-insensitive)
     * @param deviceName local device/emulator name; ignored when running in the cloud
     *                   (the cloud device matrix comes from browserstack.yml instead)
     */
    public static AppiumDriver getDriver(String mobileOS, String deviceName) {
        MobileExecutionStrategy strategy = MobileExecutionStrategyFactory.resolve();
        return strategy.createDriver(new MobileSessionRequest(mobileOS, deviceName));
    }
}
