package com.test.automation.sdk.mobile.crawler.manual;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import com.test.automation.sdk.mobile.crawler.MobileElementCrawler;
import com.test.automation.sdk.mobile.crawler.MobileScreenSnapshot;
import com.test.automation.sdk.utility.ElementCrawler;

/**
 * Hybrid/WebView crawler validation harness (not a test) -- launches the Appium
 * project's own open-source sample app ({@code io.appium.android.apis}, "ApiDemos"),
 * navigates directly to its WebView1 activity (a real embedded Android WebView showing
 * local HTML), and confirms {@code MobileElementCrawler#crawlCurrentScreen()} detects
 * the active WEBVIEW context and delegates it to the desktop SDK's {@code ElementCrawler}
 * for real DOM-based element discovery -- proving the hybrid (native + WebView) crawling
 * path documented in MOBILE-USER-GUIDE.md actually works end-to-end, not just in theory.
 */
public final class HybridWebViewCrawlerValidation {

    private HybridWebViewCrawlerValidation() {}

    public static void main(String[] args) throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setAppPackage("io.appium.android.apis");
        options.setAppActivity(".view.WebView1");
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(120));
        // Emulator system WebView/Chrome build often has no matching bundled chromedriver;
        // let Appium's chromedriver-autodownload fetch a compatible one on demand.
        options.setCapability("appium:chromedriverAutodownload", true);
        // System WebView build (151.x) is newer than any published chromedriver; disable the
        // strict build-number match and pin an explicit close-version chromedriver binary
        // so Appium can still drive the WebView context despite the version mismatch.
        options.setCapability("appium:chromedriverDisableBuildCheck", true);
        options.setCapability("appium:chromedriverExecutable",
                "C:\\Users\\vkruglyak\\Downloads\\chromedriver133\\chromedriver-win64\\chromedriver.exe");

        AndroidDriver driver = new AndroidDriver(new java.net.URI("http://127.0.0.1:4723/").toURL(), options);
        MobileElementCrawler crawler = new MobileElementCrawler(driver);

        try {
            sleep(3000);
            System.out.println("Current contexts: " + driver.getContextHandles());
            System.out.println("Current context: " + driver.getContext());

            MobileScreenSnapshot snapshot = crawler.crawlCurrentScreen();

            System.out.println();
            System.out.println("========================================================");
            System.out.println("HYBRID CRAWL VALIDATION");
            System.out.println("Native elements found: " + snapshot.getNativeElements().size());
            Map<String, List<ElementCrawler.ElementInfo>> webViewElements = snapshot.getWebViewElements();
            System.out.println("WebView contexts detected: " + webViewElements.size());
            for (Map.Entry<String, List<ElementCrawler.ElementInfo>> entry : webViewElements.entrySet()) {
                System.out.println("  Context [" + entry.getKey() + "]: " + entry.getValue().size()
                        + " DOM elements discovered via desktop ElementCrawler delegation");
                for (ElementCrawler.ElementInfo info : entry.getValue()) {
                    System.out.println("     - " + info.toString());
                }
            }
            System.out.println("Context after crawl (should be restored to NATIVE_APP): " + driver.getContext());
            System.out.println("========================================================");

            if (webViewElements.isEmpty()) {
                System.out.println("[FAIL] No WebView context was detected/delegated.");
            } else {
                System.out.println("[PASS] Hybrid WebView delegation confirmed working end-to-end.");
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
