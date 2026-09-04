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
 * Terms-of-use acceptance screen. Ported verbatim from
 * {@code mobile.automation.uiActions.TermsOfUse} in {@code 311_Mobile_Automation}.
 */
public class TermsOfUsePage extends MobileTestBase {

    public static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(TermsOfUsePage.class.getName());

    public TermsOfUsePage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    @CacheLookup
    @AndroidFindBy(xpath = "//android.widget.TextView[@text=\"Accept\"]")
    @iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@label='Accept']")
    WebElement acceptOne;

    @CacheLookup
    @AndroidFindBy(xpath = "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View[2]/android.widget.Button")
    WebElement readTermsOfUse;

    public void clickOnAcceptButton() {
        log.info("In about to click accept button on " + acceptOne);
        waitForElementPresent(acceptOne);
        sleep(2);
        acceptOne.click();
        log.info("accept button clicked");
    }

    public void clickOnReadTermsOfUseButton() {
        waitForElementPresent(readTermsOfUse);
        readTermsOfUse.click();
    }
}
