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
 * Final "Notifications" onboarding screen (finish button). Ported verbatim from
 * {@code mobile.automation.uiActions.Notofications} in {@code 311_Mobile_Automation}
 * (renamed to fix the original class name's typo).
 */
public class NotificationsPage extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(NotificationsPage.class.getName());

    public NotificationsPage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@name=\"Finish\"]")
    WebElement finishButton;

    public void clickOnFinishButton() {
        waitForElementPresent(finishButton);
        finishButton.click();
    }
}
