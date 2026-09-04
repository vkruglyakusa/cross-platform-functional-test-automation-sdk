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
 * App home/landing screen, reached after onboarding completes. Ported verbatim from
 * {@code mobile.automation.uiActions.HomePage} in {@code 311_Mobile_Automation}.
 */
public class HomePage extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(HomePage.class.getName());

    public HomePage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.Button[@content-desc='Create Service Request']")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@label=\"Add Service Request\"]")
    WebElement createServiceRequestButton;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.view.View[@content-desc='Menu tab. 2 of 2. Double tap to activate']")
    WebElement menuTab;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.view.View[@content-desc='Today tab. 1 of 2. ']")
    WebElement homeTab;

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.TextView[@text=\"My Service Requests\"]")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeStaticText[contains(@label,'My Service Requests')]")
    WebElement myServiceRequest;

    public void clickCreateServiceRequestButton() {
        waitForElementPresent(createServiceRequestButton);
        createServiceRequestButton.click();
    }

    public void clickOnMyServiceRequestButton() {
        waitForElementPresent(myServiceRequest);
        myServiceRequest.click();
    }
}
