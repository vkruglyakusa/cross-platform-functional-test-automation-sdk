package com.test.automation.sdk.mobile.crawler.manual;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.remote.SupportsContextSwitching;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.accessibility.AccessibilityChecker;

/**
 * Validates whether the desktop SDK's axe-core-based {@code AccessibilityChecker} is
 * usable against mobile targets, and if so, under which conditions.
 *
 * <p>{@code AccessibilityChecker} is entirely DOM/JavaScript based (axe-core injected via
 * {@code JavascriptExecutor}, scanned via {@code AxeBuilder().analyze(driver)}). A native
 * Android/iOS screen (Appium {@code NATIVE_APP} context) has no DOM at all, so axe-core has
 * nothing to inject into or scan -- this harness proves that empirically rather than just by
 * reading the source. A hybrid WebView context, however, is a real Chrome-backed DOM once
 * Appium has switched into it (same context-switch this SDK's own
 * {@code MobileElementCrawler#crawlWebViewsIfPresent()} performs), so the hypothesis is that
 * {@code AccessibilityChecker} should work there unmodified.
 *
 * <p>Uses the same {@code io.appium.android.apis} "ApiDemos" WebView1 activity as
 * {@link HybridWebViewCrawlerValidation}, and the same ChromeDriver-pinning workaround
 * required by this emulator's system WebView build.
 */
public final class MobileAccessibilityValidation {

    private MobileAccessibilityValidation() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("accessibility.checking.enabled", "true");
        System.setProperty("accessibility.fail.on.violation", "false");

        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setAppPackage("io.appium.android.apis");
        options.setAppActivity(".view.WebView1");
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(120));
        options.setCapability("appium:chromedriverAutodownload", true);
        options.setCapability("appium:chromedriverDisableBuildCheck", true);
        options.setCapability("appium:chromedriverExecutable",
                "C:\\Users\\vkruglyak\\Downloads\\chromedriver133\\chromedriver-win64\\chromedriver.exe");

        AndroidDriver driver = new AndroidDriver(new java.net.URI("http://127.0.0.1:4723/").toURL(), options);
        SupportsContextSwitching contextDriver = driver;

        try {
            sleep(3000);

            System.out.println("========================================================");
            System.out.println("ATTEMPT 1: AccessibilityChecker against NATIVE_APP context");
            System.out.println("========================================================");
            try {
                int nativeViolations = AccessibilityChecker.check((WebDriver) driver, "ApiDemos-Native-WebView1Screen");
                System.out.println("[UNEXPECTED] check() returned normally with " + nativeViolations + " violations "
                        + "-- expected an exception or a 0-result/injection-failure outcome against a non-DOM context.");
            } catch (Exception e) {
                System.out.println("[EXPECTED FAILURE] AccessibilityChecker threw against NATIVE_APP context:");
                System.out.println("  " + e.getClass().getName() + ": " + e.getMessage());
            }

            System.out.println();
            System.out.println("========================================================");
            System.out.println("ATTEMPT 2: AccessibilityChecker against WEBVIEW context");
            System.out.println("========================================================");
            String webviewContext = null;
            for (String ctx : contextDriver.getContextHandles()) {
                if (ctx != null && ctx.startsWith("WEBVIEW")) {
                    webviewContext = ctx;
                    break;
                }
            }
            if (webviewContext == null) {
                System.out.println("[FAIL] No WEBVIEW context found -- cannot validate.");
            } else {
                contextDriver.context(webviewContext);
                try {
                    int webViolations = AccessibilityChecker.check((WebDriver) driver, "ApiDemos-WebView1-DOM");
                    System.out.println("[PASS] check() completed against WEBVIEW context with "
                            + webViolations + " violation(s) reported.");
                } catch (Exception e) {
                    System.out.println("[FAIL] AccessibilityChecker threw against WEBVIEW context:");
                    e.printStackTrace(System.out);
                } finally {
                    contextDriver.context("NATIVE_APP");
                }
            }
            System.out.println("========================================================");
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
