package com.test.automation.sdk.mobile.uiActions;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.support.PageFactory;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;
import com.test.automation.sdk.mobile.uiActions.PermissionControllerPopUp.PermissionBtn;

/**
 * Orchestrates the app's first-run onboarding flow (data policy -> terms of use ->
 * OS permission dialog -> notifications intro -> home screen), then starts a new
 * Service Request. Ported from {@code mobile.automation.uiActions.NavigationUtility}
 * in {@code 311_Mobile_Automation} -- this is the entry point exercised by the
 * pilot's first test (see {@code regression_suite.xml} -> {@code EndToEndSRCreation.TestSRCreation}).
 *
 * NOTE: the downstream {@code NewServiceRequestPage}/{@code Form} abstraction (dynamic,
 * schema-driven SR form filling + CRM API validation) that the 311 test continues into
 * after this method is a substantially larger, separate porting effort and is
 * intentionally not included yet -- see the mobile SDK's todo tracking for the next
 * incremental step.
 */
public class NavigationUtility extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(NavigationUtility.class.getName());

    public NavigationUtility(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    /**
     * Runs the one-time onboarding flow and lands on the "new service request" entry
     * point. Mirrors {@code NavigationUtility.navigateToNewServiceRequestPage()}.
     */
    public void navigateToNewServiceRequestPage() throws Exception {
        UserDataPolicyPage userDataPolicyPage = new UserDataPolicyPage(driver);
        if (!verifyIfDeviceIphone()) {
            try {
                userDataPolicyPage.clickOnAcceptButton();
            } catch (Exception e) {
                log.info("Accept data terms button is not present");
            }
        }

        TermsOfUsePage termsOfUsePage = new TermsOfUsePage(driver);
        termsOfUsePage.clickOnAcceptButton();

        PermissionControllerPopUp permissionControllerPopUp = new PermissionControllerPopUp(driver);
        sleep(2);
        permissionControllerPopUp.clickButtonOnPermissionDialog("Notification", PermissionBtn.ALLOW);

        NotificationsPage notificationsPage = new NotificationsPage(driver);
        notificationsPage.clickOnFinishButton();

        HomePage homePage = new HomePage(driver);
        homePage.clickCreateServiceRequestButton();
    }
}
