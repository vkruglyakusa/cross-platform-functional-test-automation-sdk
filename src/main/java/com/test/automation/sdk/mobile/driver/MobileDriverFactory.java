package com.test.automation.sdk.mobile.driver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import com.browserstack.BrowserStackSdk;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.mobile.config.MobileConfigReader;

/**
 * Creates {@link AppiumDriver} instances for Android or iOS, local or
 * BrowserStack.
 *
 * As of Phase 1 of the unified SDK structure cleanup
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v2.md), this
 * class owns the real per-target driver-creation logic directly (local
 * Appium capabilities vs. BrowserStack SDK/javaagent rerouting) -- the
 * previously-parallel {@code mobile.execution.MobileExecutionStrategy}
 * hierarchy (a second, mobile-only session-selection mechanism duplicating
 * {@link com.test.automation.sdk.execution.SessionFactoryRegistry}) has been
 * retired. {@link RunMode#resolve()} is now the single place local-vs-cloud
 * is decided, mirroring how {@code testbase.WebDriverFactory} already owns
 * the equivalent web logic.
 *
 * <p>{@code driver.mobile.AndroidLocalSessionFactory} /
 * {@code AndroidBrowserStackSessionFactory} / {@code IosLocalSessionFactory} /
 * {@code IosBrowserStackSessionFactory} delegate to {@link #getLocalDriver}
 * / {@link #getBrowserStackDriver} directly (no longer through a separate
 * strategy layer), so {@link com.test.automation.sdk.execution.SessionFactoryRegistry}
 * and this class now agree on exactly one implementation per (platform,
 * runMode) pair. This class's public method signatures are intentionally
 * unchanged so existing callers (e.g. {@code MobileTestBase}) do not need to
 * change.
 */
public final class MobileDriverFactory {

    private static final Logger log = LogManager.getLogger(MobileDriverFactory.class.getName());

    private MobileDriverFactory() {}

    /**
     * @param mobileOS   "android" or "ios" (case-insensitive)
     * @param deviceName local device/emulator name; ignored when running in the cloud
     *                   (the cloud device matrix comes from browserstack.yml instead)
     */
    public static AppiumDriver getDriver(String mobileOS, String deviceName) {
        return RunMode.resolve() == RunMode.BROWSERSTACK
                ? getBrowserStackDriver(mobileOS, deviceName)
                : getLocalDriver(mobileOS, deviceName);
    }

    /**
     * Runs against a genuinely local Appium server (default
     * {@code http://127.0.0.1:4723/}), with a real device/emulator/simulator
     * attached. Sets the app path / device name / automation engine
     * capabilities itself, since there is no cloud provider config
     * (browserstack.yml, etc.) supplying them.
     *
     * Generalized from the proven driver-factory pattern in the
     * {@code 311_Mobile_Automation} project (see
     * docs/proposals/mobile-automation-strategy.md, sections 3a and 6a).
     */
    public static AppiumDriver getLocalDriver(String mobileOS, String deviceName) {
        switch (mobileOS.toLowerCase()) {
            case "android":
                return createLocalAndroidDriver(deviceName);
            case "ios":
                return createLocalIosDriver(deviceName);
            default:
                throw new IllegalArgumentException("Unsupported mobile OS: " + mobileOS
                        + " (expected \"android\" or \"ios\")");
        }
    }

    /**
     * Runs against BrowserStack App Automate via the official BrowserStack
     * Java SDK. Deliberately sets NO app/device/automation-engine
     * capabilities itself -- the BrowserStack Java SDK javaagent (attached
     * via the "browserstack" Maven profile's surefire {@code -javaagent}
     * argLine) transparently intercepts the local Appium session request and
     * reroutes it to App Automate using {@code browserstack.yml} (app id,
     * device matrix, debug/video/network-log options, etc.). This method
     * only has to connect to the same local Appium URL and verify afterwards
     * that the javaagent actually took over the session, so misconfiguration
     * (missing javaagent/browserstack.yml) fails fast with a clear error
     * instead of silently running against a local Appium server by accident.
     */
    public static AppiumDriver getBrowserStackDriver(String mobileOS, String deviceName) {
        // Verified before creating the driver (matching the prior inline behavior) so
        // misconfiguration fails fast rather than after an unwanted session is opened.
        verifyBrowserStackSdkActive();

        AppiumDriver driver;
        switch (mobileOS.toLowerCase()) {
            case "android":
                driver = new AndroidDriver(localAppiumUrl(), new UiAutomator2Options());
                break;
            case "ios":
                driver = new IOSDriver(localAppiumUrl(), new XCUITestOptions());
                break;
            default:
                throw new IllegalArgumentException("Unsupported mobile OS: " + mobileOS
                        + " (expected \"android\" or \"ios\")");
        }
        log.info("BrowserStack driver session started: {}", driver.getSessionId());
        return driver;
    }

    /**
     * Confirms the BrowserStack Java SDK javaagent actually rerouted the
     * session, rather than silently falling back to a real local Appium
     * server. Mirrors the check used by {@code MobileTestBase.isRunningInCloud()}.
     */
    static void verifyBrowserStackSdkActive() {
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

    private static AppiumDriver createLocalAndroidDriver(String deviceName) {
        log.info("Initializing local Android driver (device: {})", deviceName);

        UiAutomator2Options options = new UiAutomator2Options();
        String appPath = MobileConfigReader.get("android.appPath", null);
        if (appPath != null) {
            options.setCapability("app", resolveAppPath(appPath));
        }
        options.setCapability("deviceName", deviceName);
        options.setCapability("automationName",
                MobileConfigReader.get("android.automationName", "UiAutomator2"));
        options.setCapability("platformName", "Android");

        AppiumDriver driver = new AndroidDriver(localAppiumUrl(), options);
        log.info("Local Android driver session started: {}", driver.getSessionId());
        return driver;
    }

    private static AppiumDriver createLocalIosDriver(String deviceName) {
        log.info("Initializing local iOS driver (device: {})", deviceName);

        XCUITestOptions options = new XCUITestOptions();
        String appPath = MobileConfigReader.get("ios.appPath", null);
        if (appPath != null) {
            options.setCapability("app", resolveAppPath(appPath));
        }
        options.setCapability("deviceName", deviceName);
        options.setCapability("automationName",
                MobileConfigReader.get("ios.automationName", "XCUITest"));
        options.setCapability("platformName", "iOS");

        AppiumDriver driver = new IOSDriver(localAppiumUrl(), options);
        log.info("Local iOS driver session started: {}", driver.getSessionId());
        return driver;
    }

    private static String resolveAppPath(String configuredPath) {
        File f = new File(configuredPath);
        return f.isAbsolute() ? f.getAbsolutePath()
                : new File(System.getProperty("user.dir"), configuredPath).getAbsolutePath();
    }

    /**
     * Every driver (local or cloud) connects to this same local Appium URL.
     * For {@link RunMode#BROWSERSTACK}, the BrowserStack Java SDK javaagent
     * transparently intercepts this call and reroutes the session to App
     * Automate using browserstack.yml -- callers do not need to know or care
     * that this happens.
     */
    private static URL localAppiumUrl() {
        String urlString = MobileConfigReader.get("appium.localUrl", "http://127.0.0.1:4723/");
        try {
            return new URI(urlString).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Invalid appium.localUrl in mobile-config.yaml: " + urlString, e);
        }
    }
}
