package com.test.automation.sdk.mobile.crawler.manual;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import com.test.automation.sdk.mobile.crawler.MobileCrawlerReportWriter;
import com.test.automation.sdk.mobile.crawler.MobileElementCrawler;
import com.test.automation.sdk.mobile.crawler.MobileElementInfo;
import com.test.automation.sdk.mobile.crawler.MobileScreenSnapshot;

/**
 * Manual validation harness -- NOT a JUnit/TestNG test. Run directly with {@code java}
 * (see the session notes / README for the exact command) against a local Android
 * emulator with the NYC 311 app (`gov.nyc.doitt.ThreeOneOne`) already installed and
 * a local Appium server already running on {@code http://127.0.0.1:4723}.
 *
 * <p>Purpose: prove {@link MobileElementCrawler} produces usable, unique locators
 * against a real (not synthetic) React Native app across several real screens/flows,
 * complementing the driver-free unit tests in {@code MobileElementCrawlerTest}.</p>
 */
public final class LocalEmulatorCrawlerValidation {

    private LocalEmulatorCrawlerValidation() {}

    public static void main(String[] args) throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setAppPackage("gov.nyc.doitt.ThreeOneOne");
        options.setAppActivity(".MainActivity");
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(120));

        AndroidDriver driver = new AndroidDriver(new java.net.URI("http://127.0.0.1:4723/").toURL(), options);
        MobileElementCrawler crawler = new MobileElementCrawler(driver);
        int passed = 0;
        int attempted = 0;

        try {
            driver.activateApp("gov.nyc.doitt.ThreeOneOne"); // guard against a backgrounded app from a prior run
            sleep(3000); // let the app finish its initial render

            // ---- Case 1: Home screen ----
            attempted++;
            MobileScreenSnapshot home = crawlAndReport(crawler, "01_Home");
            if (home != null) {
                passed++;
                // Locate "Create Service Request" via the crawler's own discovered candidates
                // (mirrors HomePage.java's @content-desc='Create Service Request').
                WebElement createBtn = findByTextOrDesc(driver, home, "Create Service Request");
                if (createBtn != null) {
                    createBtn.click();
                    sleep(2000);
                } else {
                    System.out.println("[WARN] Could not find 'Create Service Request' button -- "
                            + "subsequent cases may fail.");
                }
            }

            // ---- Case 2: New Service Request type list ----
            attempted++;
            MobileScreenSnapshot srList = waitForScreenToPopulate(crawler, 4, 8000);
            String srListReport = MobileCrawlerReportWriter.write("02_NewServiceRequestList", srList);
            logSnapshot("02_NewServiceRequestList", srList, srListReport);
            if (srList.getNativeElements().size() > 3) {
                passed++;
                WebElement noiseOption = findByTextOrDesc(driver, srList, "Noise");
                if (noiseOption != null) {
                    noiseOption.click();
                    sleep(2000);
                    grantLocationPermissionIfPresent(driver);
                    sleep(1500);
                } else {
                    System.out.println("[WARN] Could not find 'Noise' category -- case 3 may fail.");
                }
            } else {
                System.out.println("[WARN] New Service Request list never populated beyond the header.");
            }

            // ---- Case 3: Noise complaint form ----
            attempted++;
            MobileScreenSnapshot noiseForm = waitForScreenToPopulate(crawler, 4, 8000);
            String noiseFormReport = MobileCrawlerReportWriter.write("03_NoiseForm", noiseForm);
            logSnapshot("03_NoiseForm", noiseForm, noiseFormReport);
            if (noiseForm.getNativeElements().size() > 3) {
                passed++;
            }

            // ---- Case 4: back to the New Service Request list, then Home, then My Service Requests ----
            attempted++;
            WebElement inAppBack = findByTextOrDesc(driver, noiseForm, "Back");
            if (inAppBack != null) {
                inAppBack.click();
                sleep(1500);
            }
            MobileScreenSnapshot afterFirstBack = crawler.crawlCurrentScreen();
            WebElement inAppBack2 = findByTextOrDesc(driver, afterFirstBack, "Back");
            if (inAppBack2 != null) {
                inAppBack2.click();
                sleep(1500);
            }
            MobileScreenSnapshot backAtHome = crawler.crawlCurrentScreen();
            WebElement myRequestsTab = findByTextOrDesc(driver, backAtHome, "My Service Requests");
            if (myRequestsTab == null) {
                myRequestsTab = findByTextOrDesc(driver, backAtHome, "Today");
            }
            if (myRequestsTab != null) {
                myRequestsTab.click();
                sleep(2000);
            }
            MobileScreenSnapshot myRequests = crawlAndReport(crawler, "04_MyServiceRequests");
            if (myRequests != null && myRequests.getNativeElements().stream()
                    .noneMatch(e -> "com.google.android.apps.nexuslauncher".equals(launcherPackageOf(e)))) {
                passed++;
            } else {
                System.out.println("[WARN] Ended up on the OS launcher instead of an app screen -- "
                        + "back-navigation overshot the app.");
            }

            System.out.println();
            System.out.println("========================================================");
            System.out.println("RESULT: " + passed + "/" + attempted + " cases successfully crawled.");
            System.out.println("========================================================");
        } finally {
            driver.quit();
        }
    }

    /** Re-crawls until the screen has more than {@code minElements} elements or the timeout elapses. */
    private static MobileScreenSnapshot waitForScreenToPopulate(MobileElementCrawler crawler, int minElements,
            long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        MobileScreenSnapshot last;
        do {
            last = crawler.crawlCurrentScreen();
            if (last.getNativeElements().size() > minElements) {
                return last;
            }
            sleep(500);
        } while (System.currentTimeMillis() < deadline);
        return last;
    }

    private static String launcherPackageOf(MobileElementInfo info) {
        return info.getCandidates().stream()
                .map(c -> c.getValue())
                .filter(v -> v != null && v.contains("nexuslauncher"))
                .findFirst()
                .map(v -> "com.google.android.apps.nexuslauncher")
                .orElse(null);
    }

    private static void logSnapshot(String screenName, MobileScreenSnapshot snapshot, String reportPath) {
        List<MobileElementInfo> elements = snapshot.getNativeElements();
        long uniqueCount = elements.stream().filter(MobileElementInfo::isResolved).count();
        System.out.println("[OK] " + screenName + ": " + elements.size() + " elements discovered, "
                + uniqueCount + " resolved to a UNIQUE candidate. Report: " + reportPath);
        for (MobileElementInfo info : elements) {
            if (info.isResolved()) {
                System.out.println("     - " + info.getTagOrClassName() + " text=\"" + info.getText()
                        + "\" -> " + info.getBestUniqueCandidate().getStrategy() + "="
                        + info.getBestUniqueCandidate().getValue());
            }
        }
    }

    private static MobileScreenSnapshot crawlAndReport(MobileElementCrawler crawler, String screenName) {
        try {
            MobileScreenSnapshot snapshot = crawler.crawlCurrentScreen();
            String reportPath = MobileCrawlerReportWriter.write(screenName, snapshot);
            List<MobileElementInfo> elements = snapshot.getNativeElements();
            long uniqueCount = elements.stream().filter(MobileElementInfo::isResolved).count();
            System.out.println("[OK] " + screenName + ": " + elements.size() + " elements discovered, "
                    + uniqueCount + " resolved to a UNIQUE candidate. Report: " + reportPath);
            for (MobileElementInfo info : elements) {
                if (info.isResolved()) {
                    System.out.println("     - " + info.getTagOrClassName() + " text=\"" + info.getText()
                            + "\" -> " + info.getBestUniqueCandidate().getStrategy() + "="
                            + info.getBestUniqueCandidate().getValue());
                }
            }
            return snapshot;
        } catch (Exception e) {
            System.out.println("[FAIL] " + screenName + ": " + e);
            return null;
        }
    }

    /** Finds a clickable element whose crawled text/content-desc contains the given needle. */
    private static WebElement findByTextOrDesc(AndroidDriver driver, MobileScreenSnapshot snapshot, String needle) {
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            if (info.getText() != null && info.getText().toLowerCase().contains(needle.toLowerCase())
                    && info.isResolved()) {
                try {
                    By by = toSeleniumBy(info);
                    return driver.findElement(by);
                } catch (Exception ignored) {
                    // fall through to the generic xpath-by-text search below
                }
            }
        }
        // Fallback: direct text/content-desc search, independent of the crawler's own candidates.
        try {
            return driver.findElement(By.xpath("//*[contains(@text,'" + needle + "') or contains(@content-desc,'"
                    + needle + "')]"));
        } catch (Exception e) {
            return null;
        }
    }

    private static By toSeleniumBy(MobileElementInfo info) {
        switch (info.getBestUniqueCandidate().getStrategy()) {
            case RESOURCE_ID:
                return AppiumBy.id(info.getBestUniqueCandidate().getValue());
            case ACCESSIBILITY_ID:
                return AppiumBy.accessibilityId(info.getBestUniqueCandidate().getValue());
            default:
                return By.xpath(info.getBestUniqueCandidate().getValue().startsWith("//")
                        ? info.getBestUniqueCandidate().getValue()
                        : "//*[@text='" + info.getBestUniqueCandidate().getValue() + "']");
        }
    }

    private static void grantLocationPermissionIfPresent(AndroidDriver driver) {
        try {
            WebElement allowButton = driver.findElement(By.id(
                    "com.android.permissioncontroller:id/permission_allow_foreground_only_button"));
            allowButton.click();
        } catch (Exception ignored) {
            // no permission dialog appeared -- nothing to do
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
