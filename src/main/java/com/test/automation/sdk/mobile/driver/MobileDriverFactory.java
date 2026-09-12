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
import com.test.automation.sdk.config.ConfigurationManager;
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
        RunMode runMode = RunMode.resolve();
        if (runMode == RunMode.BROWSERSTACK) {
            return getBrowserStackDriver(mobileOS, deviceName);
        }
        if (runMode == RunMode.REMOTE_APPIUM) {
            return getRemoteAppiumDriver(mobileOS, deviceName);
        }
        return getLocalDriver(mobileOS, deviceName);
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
        switch (normalizeMobileOs(mobileOS)) {
            case "android":
                return createLocalAndroidDriver(deviceName);
            case "ios":
                return createLocalIosDriver(deviceName);
            default:
                throw unsupportedMobileOs(mobileOS);
        }
    }

    /** Connects directly to a user-managed remote Appium server. */
    public static AppiumDriver getRemoteAppiumDriver(String mobileOS, String deviceName) {
        URL serverUrl = appiumUrl(true);
        switch (normalizeMobileOs(mobileOS)) {
            case "android":
                return new AndroidDriver(serverUrl, androidOptions(deviceName, true));
            case "ios":
                return new IOSDriver(serverUrl, iosOptions(deviceName, true));
            default:
                throw unsupportedMobileOs(mobileOS);
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
        switch (normalizeMobileOs(mobileOS)) {
            case "android":
                driver = new AndroidDriver(appiumUrl(false), new UiAutomator2Options());
                break;
            case "ios":
                driver = new IOSDriver(appiumUrl(false), new XCUITestOptions());
                break;
            default:
                throw unsupportedMobileOs(mobileOS);
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

        AppiumDriver driver = new AndroidDriver(appiumUrl(false), androidOptions(deviceName, false));
        log.info("Local Android driver session started: {}", driver.getSessionId());
        return driver;
    }

    private static AppiumDriver createLocalIosDriver(String deviceName) {
        log.info("Initializing local iOS driver (device: {})", deviceName);

        AppiumDriver driver = new IOSDriver(appiumUrl(false), iosOptions(deviceName, false));
        log.info("Local iOS driver session started: {}", driver.getSessionId());
        return driver;
    }

    private static String resolveAppPath(String configuredPath) {
        File f = new File(configuredPath);
        return f.isAbsolute() ? f.getAbsolutePath()
                : new File(System.getProperty("user.dir"), configuredPath).getAbsolutePath();
    }

    static UiAutomator2Options androidOptions(String deviceName, boolean remote) {
        UiAutomator2Options options = new UiAutomator2Options();
        String appPath = mobileProperty("mobile.android.appPath", "android.appPath");
        if (appPath != null && !appPath.trim().isEmpty()) {
            options.setCapability("app", remote ? appPath : resolveAppPath(appPath));
        }
        options.setCapability("deviceName", requireDeviceName(deviceName));
        options.setCapability("automationName", MobileConfigReader.get("android.automationName", "UiAutomator2"));
        options.setCapability("platformName", "Android");
        return options;
    }

    static XCUITestOptions iosOptions(String deviceName, boolean remote) {
        XCUITestOptions options = new XCUITestOptions();
        String appPath = mobileProperty("mobile.ios.appPath", "ios.appPath");
        if (appPath != null && !appPath.trim().isEmpty()) {
            options.setCapability("app", remote ? appPath : resolveAppPath(appPath));
        }
        options.setCapability("deviceName", requireDeviceName(deviceName));
        options.setCapability("automationName", MobileConfigReader.get("ios.automationName", "XCUITest"));
        options.setCapability("platformName", "iOS");
        return options;
    }

    private static String mobileProperty(String studioKey, String sdkKey) {
        String studioValue = System.getProperty(studioKey);
        return studioValue != null && !studioValue.trim().isEmpty()
                ? studioValue.trim() : MobileConfigReader.get(sdkKey, null);
    }

    private static String requireDeviceName(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceName must not be blank");
        }
        return deviceName.trim();
    }

    private static String normalizeMobileOs(String mobileOS) {
        return mobileOS == null ? "" : mobileOS.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static IllegalArgumentException unsupportedMobileOs(String mobileOS) {
        return new IllegalArgumentException("Unsupported mobile OS: " + mobileOS
                + " (expected \"android\" or \"ios\")");
    }

    /** Resolves and validates the local or user-managed remote Appium endpoint. */
    static URL appiumUrl(boolean remote) {
        String property = remote ? "mobile.appium.url" : "appium.localUrl";
        String urlString = MobileConfigReader.get(
                property, remote ? "" : "http://127.0.0.1:4723/");
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalStateException("Missing required remote Appium setting: " + property);
        }
        try {
            URI uri = new URI(urlString.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException("Appium URL must be an absolute HTTP(S) URL with a host: " + urlString);
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException(property + " must not contain credentials; use environment variables");
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException(property + " must not contain a query or fragment");
            }
            if (remote && "http".equalsIgnoreCase(scheme)
                    && !ConfigurationManager.resolveBoolean("mobile.appium.allowInsecureHttp", false)) {
                throw new IllegalStateException(property
                        + " uses HTTP; set mobile.appium.allowInsecureHttp=true only for a trusted Appium server");
            }
            return uri.toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Invalid " + property + ": " + urlString, e);
        }
    }
}
