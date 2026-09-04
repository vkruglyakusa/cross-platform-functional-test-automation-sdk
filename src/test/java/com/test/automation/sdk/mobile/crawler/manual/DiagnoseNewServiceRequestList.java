package com.test.automation.sdk.mobile.crawler.manual;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

/**
 * One-off diagnostic (not a test) -- navigates to the New Service Request list and
 * dumps the raw page source at several intervals so we can see whether it is a
 * loading spinner, an error state, or genuinely static/empty content.
 */
public final class DiagnoseNewServiceRequestList {

    private DiagnoseNewServiceRequestList() {}

    public static void main(String[] args) throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setAppPackage("gov.nyc.doitt.ThreeOneOne");
        options.setAppActivity(".MainActivity");
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(120));

        AndroidDriver driver = new AndroidDriver(new java.net.URI("http://127.0.0.1:4723/").toURL(), options);
        try {
            driver.activateApp("gov.nyc.doitt.ThreeOneOne");
            sleep(3000);

            WebElement createBtn = driver.findElement(
                    By.xpath("//*[contains(@content-desc,'Create Service Request')]"));
            createBtn.click();
            System.out.println("Tapped Create Service Request.");

            for (int i = 0; i < 6; i++) {
                sleep(3000);
                String src = driver.getPageSource();
                System.out.println("==== t+" + ((i + 1) * 3) + "s page source (" + src.length() + " chars) ====");
                System.out.println(src);
                System.out.println("==== end ====");
            }
        } finally {
            driver.quit();
        }
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
