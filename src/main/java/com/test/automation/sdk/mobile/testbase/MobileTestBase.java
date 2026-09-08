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
import com.test.automation.sdk.mobile.actions.MobileActions;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;
import com.test.automation.sdk.mobile.execution.ExecutionTarget;
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
 *       re-initializes the {@link AppiumDriver} session (via
 *       {@link MobileDriverFactory}) instead of the inherited web
 *       {@code initialization(...)} path.</li>
 * </ul>
 * {@link TestBase#afterClass()} (quits {@code driver}, flushes Extent) is
 * inherited unchanged -- it is already driver-type-agnostic.
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
     * True when the resolved {@link ExecutionTarget} is {@link ExecutionTarget#BROWSERSTACK}
     * (see {@link ExecutionTarget#resolve()} for the {@code -Dmobile.execution.target}/legacy
     * {@code -DtestInBrowserstack} resolution order) AND the BrowserStack Java SDK javaagent
     * confirms an active platform. Mirrors {@code TestBase.isTestInBrowserstack()} from the
     * 311 prior art.
     */
    public static boolean isRunningInCloud() {
        if (ExecutionTarget.resolve() != ExecutionTarget.BROWSERSTACK) {
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
        driver = MobileDriverFactory.getDriver(mobileOS, device);
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
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Error quitting mobile driver before retry", e);
                }
            }
            driver = MobileDriverFactory.getDriver(mobileOsName, deviceName);
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
}
