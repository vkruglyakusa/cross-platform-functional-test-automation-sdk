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
 * "New Service Request" screen, reached after tapping "Create Service Request" on
 * {@link HomePage}. Minimal port of {@code mobile.automation.uiActions.NewServiceRequestPage}
 * from {@code 311_Mobile_Automation} -- only the page-banner element used to confirm the
 * screen has loaded is included here. The dynamic per-category form-filling machinery
 * ({@code getForm()} + the {@code forms.*Form} classes) is a separate, larger follow-up
 * porting effort, not needed for this smoke-level check.
 */
public class NewServiceRequestPage extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(NewServiceRequestPage.class.getName());

    public NewServiceRequestPage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.TextView[@text='New Service Request']")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeStaticText[@name=\"New Service Request\"]")
    WebElement pageBanner;

    public void verifyPageBannerDisplayed() {
        waitForElementPresent(pageBanner);
    }
}
