package com.test.automation.sdk.mobile.execution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import com.test.automation.sdk.mobile.config.MobileConfigReader;

/**
 * Runs against a genuinely local Appium server (default {@code http://127.0.0.1:4723/}),
 * with a real device/emulator/simulator attached. Sets the app path / device name /
 * automation engine capabilities itself, since there is no cloud provider config
 * (browserstack.yml, etc.) supplying them.
 *
 * Generalized from the proven driver-factory pattern in the {@code 311_Mobile_Automation}
 * project (see docs/proposals/mobile-automation-strategy.md, sections 3a and 6a).
 */
final class LocalExecutionStrategy implements MobileExecutionStrategy {

    private static final Logger log = LogManager.getLogger(LocalExecutionStrategy.class.getName());

    @Override
    public ExecutionTarget getTarget() {
        return ExecutionTarget.LOCAL;
    }

    @Override
    public AppiumDriver createDriver(MobileSessionRequest request) {
        switch (request.getMobileOS().toLowerCase()) {
            case "android":
                return createAndroidDriver(request.getDeviceName());
            case "ios":
                return createIosDriver(request.getDeviceName());
            default:
                throw new IllegalArgumentException("Unsupported mobile OS: " + request.getMobileOS()
                        + " (expected \"android\" or \"ios\")");
        }
    }

    private AppiumDriver createAndroidDriver(String deviceName) {
        log.info("Initializing local Android driver (device: {})", deviceName);

        UiAutomator2Options options = new UiAutomator2Options();
        String appPath = MobileConfigReader.get("android.appPath", null);
        if (appPath != null) {
            options.setCapability("app", MobileExecutionStrategySupport.resolveAppPath(appPath));
        }
        options.setCapability("deviceName", deviceName);
        options.setCapability("automationName",
                MobileConfigReader.get("android.automationName", "UiAutomator2"));
        options.setCapability("platformName", "Android");

        AppiumDriver driver = new AndroidDriver(MobileExecutionStrategySupport.localAppiumUrl(), options);
        log.info("Local Android driver session started: {}", driver.getSessionId());
        return driver;
    }

    private AppiumDriver createIosDriver(String deviceName) {
        log.info("Initializing local iOS driver (device: {})", deviceName);

        XCUITestOptions options = new XCUITestOptions();
        String appPath = MobileConfigReader.get("ios.appPath", null);
        if (appPath != null) {
            options.setCapability("app", MobileExecutionStrategySupport.resolveAppPath(appPath));
        }
        options.setCapability("deviceName", deviceName);
        options.setCapability("automationName",
                MobileConfigReader.get("ios.automationName", "XCUITest"));
        options.setCapability("platformName", "iOS");

        AppiumDriver driver = new IOSDriver(MobileExecutionStrategySupport.localAppiumUrl(), options);
        log.info("Local iOS driver session started: {}", driver.getSessionId());
        return driver;
    }
}
