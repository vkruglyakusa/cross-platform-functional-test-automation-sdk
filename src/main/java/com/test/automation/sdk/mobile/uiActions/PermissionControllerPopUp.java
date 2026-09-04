package com.test.automation.sdk.mobile.uiActions;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.CacheLookup;
import org.openqa.selenium.support.PageFactory;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;

/**
 * Android's system "Allow app to access X" permission dialog (e.g. location,
 * notifications). Ported verbatim (locators unchanged -- already proven in
 * production) from {@code mobile.automation.uiActions.PermissionControllerPopUp} in
 * {@code 311_Mobile_Automation}. iOS presents an equivalent native alert but is not
 * driven through this class; iOS permission handling is out of scope until a pilot
 * app requiring it is identified (see strategy doc §8a, app-instrumentation risk).
 */
public class PermissionControllerPopUp extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(PermissionControllerPopUp.class.getName());

    public PermissionControllerPopUp(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.LinearLayout[@resource-id='com.android.permissioncontroller:id/grant_dialog']")
    WebElement permissionDialog;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button[@resource-id='com.android.permissioncontroller:id/permission_allow_button']")
    WebElement allowButton;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button[@resource-id='com.android.permissioncontroller:id/permission_deny_button']")
    WebElement dontAllow;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button[@resource-id='com.android.permissioncontroller:id/permission_allow_one_time_button']")
    public WebElement onlyThisTime;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button[@resource-id='com.android.permissioncontroller:id/permission_allow_foreground_only_button']")
    public WebElement whileUsingApp;

    public enum PermissionBtn {
        ALLOW, DENY, ONLY_THIS_TIME, WHILE_USING_APP
    }

    public boolean isPermissionDialogDisplayed() {
        try {
            waitForElementPresent(permissionDialog, 10);
        } catch (Exception e) {
            log.info("Permission dialog not displayed. No action for accept/deny permission.");
            return false;
        }
        return true;
    }

    public void clickButtonOnPermissionDialog(String permissionRequest, PermissionBtn button) {
        log.info("Trying to click button for permission request: {}", permissionRequest);

        if (!isPermissionDialogDisplayed()) {
            return;
        }

        WebElement btnElement = getPermissionBtnElement(button);
        log.info("------In about to click on [ {} ] Button ------", button);
        try {
            elementClick(null, btnElement);
            log.info("------Successfully clicked on [ {} ] Button ------", button);
        } catch (Exception e) {
            clickOnElement(btnElement);
        }
    }

    private WebElement getPermissionBtnElement(PermissionBtn button) {
        switch (button) {
            case ALLOW:
                return allowButton;
            case DENY:
                return dontAllow;
            case ONLY_THIS_TIME:
                return onlyThisTime;
            case WHILE_USING_APP:
                return whileUsingApp;
        }
        log.error("Unexpected error. No button element found for [ {} ]. Return null.", button);
        return null;
    }
}
