package com.test.automation.sdk.mobile.sample;

import org.testng.annotations.Test;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;

/**
 * Sample smoke test demonstrating the MobileTestBase lifecycle and page-object
 * usage pattern. Not a real consumer test -- exists to validate the SDK skeleton
 * compiles and wires together correctly (driver factory -> test base -> page object
 * -> actions).
 *
 * A real consumer project would extend MobileTestBase the same way and add
 * @DataProvider-backed, Excel-driven @Test methods per the desktop SDK convention.
 */
public class SampleSmokeTest extends MobileTestBase {

    @Test(enabled = false, description = "Illustrative only -- requires a running Appium server or BrowserStack session")
    public void sampleTest() {
        SampleHomePage homePage = new SampleHomePage(driver);
        homePage.clickCreateRequestButton();
    }
}
