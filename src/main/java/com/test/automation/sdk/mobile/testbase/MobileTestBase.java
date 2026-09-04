package com.test.automation.sdk.mobile.testbase;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import io.appium.java_client.AppiumDriver;

import com.browserstack.BrowserStackSdk;
import com.test.automation.sdk.mobile.actions.MobileActions;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;
import com.test.automation.sdk.mobile.execution.ExecutionTarget;

/**
 * Base class for mobile TestNG test classes.
 *
 * Generalized from {@code mobile.automation.testBase.TestBase} in the proven
 * {@code 311_Mobile_Automation} project (see docs/proposals/mobile-automation-strategy.md,
 * sections 3a and 6a). Handles the BrowserStack-vs-local switch via
 * {@link #isRunningInCloud()} and standard TestNG driver lifecycle. Excel-driven
 * {@code @DataProvider} support, reporting hooks, etc. reuse the desktop SDK's
 * shared utilities (Excel_Reader, YamlConfigReader) rather than re-implementing them
 * here -- see the functional-test-automation-sdk dependency in pom.xml.
 */
public class MobileTestBase {

    public static final Logger log = LogManager.getLogger(MobileTestBase.class.getName());

    protected static String mobileOsName;
    protected static String deviceName = "";
    protected AppiumDriver driver;

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

    @Parameters({"mobileOS", "deviceName"})
    @BeforeClass(alwaysRun = true)
    public void setUpDriver(@Optional("android") String mobileOS, @Optional("") String device) {
        mobileOsName = mobileOS;
        deviceName = device;
        driver = MobileDriverFactory.getDriver(mobileOS, device);
    }

    @AfterClass(alwaysRun = true)
    public void tearDownDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Error quitting mobile driver", e);
            }
        }
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

    /** Waits for an element to be visible before any interaction. Mirrors {@code TestBase.waitForElementPresent}. */
    protected void waitForElementPresent(WebElement element) {
        MobileActions.waitForElementPresent(driver, element);
    }

    /** Waits for an element to be visible, with a caller-specified timeout in seconds. */
    protected void waitForElementPresent(WebElement element, int timeoutSeconds) {
        MobileActions.waitForElementPresent(driver, element, timeoutSeconds);
    }

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
            MobileActions.tap(driver, element);
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
