package com.test.automation.sdk.mobile.execution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import com.browserstack.BrowserStackSdk;

/**
 * Runs against BrowserStack App Automate via the official BrowserStack Java SDK. This
 * strategy deliberately sets NO app/device/automation-engine capabilities itself -- the
 * BrowserStack Java SDK javaagent (attached via the "browserstack" Maven profile's
 * surefire {@code -javaagent} argLine) transparently intercepts the local Appium session
 * request and reroutes it to App Automate using {@code browserstack.yml} (app id, device
 * matrix, debug/video/network-log options, etc.). This class only has to connect to the
 * same local Appium URL and verify afterwards that the javaagent actually took over the
 * session, so misconfiguration (missing javaagent/browserstack.yml) fails fast with a
 * clear error instead of silently running against a local Appium server by accident.
 */
final class BrowserStackExecutionStrategy implements MobileExecutionStrategy {

    private static final Logger log = LogManager.getLogger(BrowserStackExecutionStrategy.class.getName());

    @Override
    public ExecutionTarget getTarget() {
        return ExecutionTarget.BROWSERSTACK;
    }

    @Override
    public AppiumDriver createDriver(MobileSessionRequest request) {
        // Verified before creating the driver (matching the prior inline behavior) so
        // misconfiguration fails fast rather than after an unwanted session is opened.
        verifyBrowserStackSdkActive();

        AppiumDriver driver;
        switch (request.getMobileOS().toLowerCase()) {
            case "android":
                driver = new AndroidDriver(MobileExecutionStrategySupport.localAppiumUrl(), new UiAutomator2Options());
                break;
            case "ios":
                driver = new IOSDriver(MobileExecutionStrategySupport.localAppiumUrl(), new XCUITestOptions());
                break;
            default:
                throw new IllegalArgumentException("Unsupported mobile OS: " + request.getMobileOS()
                        + " (expected \"android\" or \"ios\")");
        }
        log.info("BrowserStack driver session started: {}", driver.getSessionId());
        return driver;
    }

    /**
     * Confirms the BrowserStack Java SDK javaagent actually rerouted the session, rather
     * than silently falling back to a real local Appium server. Mirrors the check
     * previously inlined in {@code MobileTestBase.isRunningInCloud()}.
     */
    private void verifyBrowserStackSdkActive() {
        try {
            if (BrowserStackSdk.getCurrentPlatform().isEmpty()) {
                throw new IllegalStateException("BrowserStack SDK reports no active platform");
            }
        } catch (Exception e) {
            log.error("Execution target is BROWSERSTACK but the BrowserStack SDK javaagent/config is not "
                    + "wired up correctly -- check pom.xml (-javaagent) and browserstack.yml.", e);
            throw new IllegalStateException(
                    "BrowserStack session could not be confirmed; see log for details.", e);
        }
    }
}
