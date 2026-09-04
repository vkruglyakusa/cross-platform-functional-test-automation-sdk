package com.test.automation.sdk.mobile.sample;

import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;

import com.test.automation.sdk.mobile.actions.MobileActions;

/**
 * Sample mobile page object demonstrating the SDK's page-object convention:
 * {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} + {@code PageFactory} +
 * {@code AppiumFieldDecorator}, mirroring the desktop SDK's {@code @FindBy} +
 * {@code PageFactory} convention but for mobile-specific locator strategies.
 *
 * This class exists only to validate the SDK skeleton compiles against a
 * realistic usage pattern; it is not a real consumer page object.
 */
public class SampleHomePage {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(SampleHomePage.class.getName());

    private final AppiumDriver driver;

    @AndroidFindBy(accessibility = "Create Service Request")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@label='Add Service Request']")
    private WebElement createRequestButton;

    public SampleHomePage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    public void clickCreateRequestButton() {
        log.info("Clicking Create Service Request button");
        MobileActions.tap(driver, createRequestButton);
    }
}
