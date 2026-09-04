package com.test.automation.sdk.mobile.samples.onboarding;

import org.testng.annotations.Test;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;
import com.test.automation.sdk.mobile.uiActions.NavigationUtility;
import com.test.automation.sdk.mobile.uiActions.NewServiceRequestPage;

/**
 * First real pilot test ported from {@code 311_Mobile_Automation}'s
 * {@code regression_suite.xml} -> {@code EndToEndSRCreation.TestSRCreation}, scoped to
 * just the onboarding + "start a new Service Request" portion of that flow (the
 * dynamic SR-form-filling + CRM API validation portion is a separate, larger
 * follow-up porting effort -- not included here).
 *
 * Mirrors production's own next real step after {@code navigateToNewServiceRequestPage()}
 * (which ends by tapping "Create Service Request" on the Home screen): production calls
 * {@code newServiceRequestPage.clickOnPageBanner()} next -- verified end-to-end against a
 * local Appium/emulator run with the real 311 app (see consumer-template's copy of this
 * test, {@code OnboardingAndHomeSmokeTest}, which is enabled and has been run successfully).
 *
 * Disabled by default here: requires either a running local Appium server + the app
 * binary declared in mobile-config.yaml ("local" Maven profile), or a live
 * BrowserStack session (default "browserstack" profile) with a valid
 * browserstack.yml. Enable once a device/session is available to validate against.
 */
public class OnboardingAndHomeSmokeTest extends MobileTestBase {

    @Test(enabled = false, description = "Ported from 311_Mobile_Automation onboarding flow -- "
            + "requires a running Appium server or BrowserStack session to execute")
    public void verifyOnboardingReachesNewServiceRequestScreen() throws Exception {
        NavigationUtility navigationUtility = new NavigationUtility(driver);
        navigationUtility.navigateToNewServiceRequestPage();

        // At this point the app should be past onboarding and have tapped "Create Service
        // Request" on the Home screen, landing on the New Service Request entry screen.
        // Further form-filling steps will be added once the dynamic Form/*Form machinery
        // is ported.
        NewServiceRequestPage newServiceRequestPage = new NewServiceRequestPage(driver);
        newServiceRequestPage.verifyPageBannerDisplayed();
    }
}
