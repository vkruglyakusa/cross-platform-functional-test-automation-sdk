package com.test.automation.sdk.mobile.testbase;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import io.appium.java_client.AppiumDriver;

import com.browserstack.BrowserStackSdk;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.mobile.actions.MobileActions;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;
import com.test.automation.sdk.session.AutomationSessionFactory;
import com.test.automation.sdk.testbase.TestBase;

/**
 * Base class for mobile TestNG test classes.
 *
 * As of Phase 4 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md), this now {@code extends}
 * the shared {@link TestBase} instead of duplicating its own parallel
 * lifecycle -- both platforms share the same {@code driver} field (an
 * {@link AppiumDriver} IS-A {@code WebDriver}), the same Extent/Allure
 * reporting hooks, and the same TestNG teardown ({@link TestBase#afterClass()}).
 *
 * Two of {@link TestBase}'s TestNG configuration methods are web-specific and
 * are overridden here to keep mobile test classes from ever attempting to
 * launch a Selenium browser session:
 * <ul>
 *   <li>{@link TestBase#setUp(String, String)} -- overridden as a no-op;
 *       mobile session creation happens in {@link #setUpDriver} instead.</li>
 *   <li>{@link TestBase#beforeMethod(Method)} -- overridden so a retry
 *       re-acquires the {@link AppiumDriver} session (via the same
 *       {@code ExecutionContext -> AutomationSessionFactory} path as
 *       {@link #setUpDriver}) instead of the inherited web
 *       {@code initialization(...)} path.</li>
 * </ul>
 * {@link TestBase#afterClass()} (quits {@code driver}/{@code automationSession},
 * flushes Extent) is inherited unchanged -- it is already driver-type-agnostic.
 *
 * As of Priority 1 of the Unified SDK Implementation Review
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 7), {@link #setUpDriver}/the retry path in {@link #beforeMethod}
 * acquire the Appium session through {@code ExecutionContext ->
 * AutomationSessionFactory -> DriverManager -> SessionFactoryRegistry} --
 * the same technology-neutral lifecycle {@link TestBase#initialization}
 * uses for Web -- rather than calling {@link MobileDriverFactory} directly.
 * {@link MobileDriverFactory} is unaffected and remains the real
 * capability-building implementation the Android/iOS
 * {@code SessionFactory}s delegate to.
 *
 * Generalized from {@code mobile.automation.testBase.TestBase} in the proven
 * {@code 311_Mobile_Automation} project (see docs/proposals/mobile-automation-strategy.md,
 * sections 3a and 6a). Handles the BrowserStack-vs-local switch via
 * {@link #isRunningInCloud()} and standard TestNG driver lifecycle. Excel-driven
 * {@code @DataProvider} support, reporting hooks, etc. reuse the desktop SDK's
 * shared utilities (Excel_Reader, YamlConfigReader) rather than re-implementing them
 * here -- see the functional-test-automation-sdk dependency in pom.xml.
 */
public class MobileTestBase extends TestBase {

    public static final Logger log = LogManager.getLogger(MobileTestBase.class.getName());

    protected static String mobileOsName;
    protected static String deviceName = "";

    /**
     * True when the resolved {@link RunMode} is {@link RunMode#BROWSERSTACK}
     * (see {@link RunMode#resolve()} for the {@code -Drun.mode}/legacy
     * {@code -Dmobile.execution.target}/{@code -DtestInBrowserstack} resolution order)
     * AND the BrowserStack Java SDK javaagent confirms an active platform. Mirrors
     * {@code TestBase.isTestInBrowserstack()} from the 311 prior art.
     */
    public static boolean isRunningInCloud() {
        if (RunMode.resolve() != RunMode.BROWSERSTACK) {
            return false;
        }
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
        return true;
    }

    /** Current platform name ("android"/"ios"), from BrowserStack when in the cloud, else from the -Dmobile.os system property. */
    public static String getCurrentPlatformOS() {
        if (isRunningInCloud()) {
            return BrowserStackSdk.getCurrentPlatform().get("platformName").toString();
        }
        if (mobileOsName == null || mobileOsName.isEmpty()) {
            throw new IllegalStateException("Mobile OS is not set. Pass -Dmobile.os=android|ios "
                    + "or a TestNG <parameter name=\"mobileOS\"> when running locally.");
        }
        return mobileOsName;
    }

    /**
     * Overrides the inherited web {@link TestBase#setUp(String, String)} as a no-op --
     * mobile test classes must never attempt to launch a Selenium browser session.
     * Appium session creation happens separately in {@link #setUpDriver}.
     */
    @Override
    @Parameters({"environment", "browserName"})
    @BeforeClass
    protected void setUp(@Optional("") String environment, @Optional("") String browserName) throws IOException {
        // Intentionally empty -- see class Javadoc.
    }

    @Parameters({"mobileOS", "deviceName"})
    @BeforeClass(alwaysRun = true)
    public void setUpDriver(@Optional("android") String mobileOS, @Optional("") String device) {
        mobileOsName = mobileOS;
        deviceName = device;
        ExecutionContext context = ExecutionContext.forMobile(resolvePlatform(mobileOS), device, RunMode.resolve());
        automationSession = AutomationSessionFactory.create(context);
        driver = automationSession.unwrap(AppiumDriver.class);
    }

    /** Maps the TestNG {@code mobileOS} parameter ("android"/"ios") to {@link Platform}. */
    private static Platform resolvePlatform(String mobileOS) {
        if ("ios".equalsIgnoreCase(mobileOS) || "iphone".equalsIgnoreCase(mobileOS)) {
            return Platform.IOS;
        }
        return Platform.ANDROID;
    }

    /**
     * Overrides the inherited web {@link TestBase#beforeMethod(Method)} so a detected
     * retry re-creates the {@link AppiumDriver} session (via {@link MobileDriverFactory}),
     * instead of the inherited web {@code initialization(...)} path (which would NPE / try
     * to launch a browser -- {@code browser}/{@code baseURL} are never set for mobile tests).
     */
    @Override
    @BeforeMethod
    protected void beforeMethod(Method result) throws IOException {
        testRetryCount++;
        if (testRetryCount > 1 && result.isAnnotationPresent(Test.class)) {
            log.info("Retry count: " + (testRetryCount - 1));
            if (automationSession != null) {
                try {
                    automationSession.quit();
                } catch (Exception e) {
                    log.warn("Error quitting mobile driver before retry", e);
                }
            }
            ExecutionContext context = ExecutionContext.forMobile(resolvePlatform(mobileOsName), deviceName, RunMode.resolve());
            automationSession = AutomationSessionFactory.create(context);
            driver = automationSession.unwrap(AppiumDriver.class);
        }
    }

    /**
     * Casts the shared {@code driver} field to {@link AppiumDriver} for the
     * mobile-only helpers below. Safe: this field is only ever assigned an
     * {@code AppiumDriver} instance in this class ({@link #setUpDriver}/retry path).
     */
    private AppiumDriver requireAppiumDriver() {
        return (AppiumDriver) driver;
    }

    /**
     * True when the current platform (BrowserStack-reported, or local -DmobileOS) is iOS.
     * Ported from {@code TestBase.verifyIfDeviceIphone()} in {@code 311_Mobile_Automation}
     * -- used by page objects/flows that branch on Android vs. iOS (e.g. the onboarding
     * flow's user-data-policy screen, which only appears on Android).
     */
    protected boolean verifyIfDeviceIphone() {
        String os = getCurrentPlatformOS();
        return "ios".equalsIgnoreCase(os) || "iphone".equalsIgnoreCase(os);
    }

    /**
     * {@code TestBase.waitForElementPresent(WebElement[, int])} is inherited unchanged
     * from the parent -- it waits on the {@code WebElement} itself via
     * {@code ExpectedConditions}, which works identically for Appium-backed elements
     * (an {@link AppiumDriver} IS-A {@code WebDriver}), so no mobile-specific override
     * is needed here.
     */

    /**
     * Primary click helper: waits for the element, then clicks, logging the action under
     * {@code key} for traceability. Mirrors {@code TestBase.elementClick(key, element)} from
     * the 311 prior art. Throws on failure so callers can fall back to {@link #clickOnElement}.
     */
    protected void elementClick(String key, WebElement element) {
        waitForElementPresent(element);
        log.info("Clicking element{}", key != null ? " for [" + key + "]" : "");
        element.click();
    }

    /**
     * Fallback click strategy for elements that resist a plain {@code WebElement.click()}
     * (e.g. flaky/overlapping native views). Mirrors {@code TestBase.clickOnElement} from the
     * 311 prior art, which falls back to a coordinate/gesture-based tap.
     */
    protected void clickOnElement(WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            log.warn("Direct click failed, falling back to gesture tap: {}", e.getMessage());
            MobileActions.tap(requireAppiumDriver(), element);
        }
    }

    /** Simple sleep helper for the rare cases where a stability wait isn't yet available (see strategy doc §8a). */
    protected void sleep(int seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Switches the driver's context back to native (from a WebView) so subsequent
     * {@code @FindBy}/native-locator interactions resolve against the native view
     * hierarchy again. Delegates to {@link MobileActions#switchToNativeContext(AppiumDriver)}.
     */
    protected void mobileSwitchToNative() {
        MobileActions.switchToNativeContext(requireAppiumDriver());
    }

    /**
     * Switches the driver's context to the first available WebView, for hybrid
     * screens that embed web content inside the native app. Delegates to
     * {@link MobileActions#switchToWebViewContext(AppiumDriver)}.
     *
     * @throws IllegalStateException if no WebView context is currently present
     */
    protected void mobileSwitchToWebView() {
        MobileActions.switchToWebViewContext(requireAppiumDriver());
    }

    /**
     * Switches the driver's context to the WebView whose name contains
     * {@code nameContains}, for apps with more than one active WebView.
     * Delegates to {@link MobileActions#switchToWebViewContext(AppiumDriver, String)}.
     *
     * @throws IllegalStateException if no matching WebView context is currently present
     */
    protected void mobileSwitchToWebView(String nameContains) {
        MobileActions.switchToWebViewContext(requireAppiumDriver(), nameContains);
    }

    /** True when the driver's current context is a WebView (not native). */
    protected boolean isInWebViewContext() {
        return MobileActions.isInWebViewContext(requireAppiumDriver());
    }

    /** All context handles currently reported by the driver, e.g. {@code ["NATIVE_APP", "WEBVIEW_com.example.app"]}. */
    protected java.util.Set<String> getAvailableContexts() {
        return MobileActions.getAvailableContexts(requireAppiumDriver());
    }
}
