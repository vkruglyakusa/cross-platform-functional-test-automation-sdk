package com.test.automation.sdk.mobile.uiActions;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.CacheLookup;
import org.openqa.selenium.support.PageFactory;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;

/**
 * Android-only "accept our data policy" onboarding screen. Ported verbatim from
 * {@code mobile.automation.uiActions.UserDataPolicy} in {@code 311_Mobile_Automation}.
 * Not shown on iOS -- see {@link com.test.automation.sdk.mobile.uiActions.NavigationUtility}.
 */
public class UserDataPolicyPage extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(UserDataPolicyPage.class.getName());

    public UserDataPolicyPage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@name='Accept']")
    WebElement accept;

    public void clickOnAcceptButton() {
        waitForElementPresent(accept);
        accept.click();
    }
}
