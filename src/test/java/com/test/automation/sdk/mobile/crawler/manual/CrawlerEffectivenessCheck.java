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
import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate;
import com.test.automation.sdk.mobile.crawler.MobileScreenSnapshot;

/**
 * Effectiveness check (not a test) -- crawls the screens that are reachable WITHOUT
 * the server-side forced-update gate (Home, Menu/SubMenu, My Service Requests, Lookup)
 * and reports how many elements the crawler resolves to a UNIQUE, stable candidate,
 * specifically contrasting against the pilot repo's own hand-written locators, e.g.
 * {@code mobile.automation.uiActions.SubMenuPage} which uses the fragile/obfuscated
 * structural xpath {@code //w2.c2/android.view.View/android.view.View}.
 */
public final class CrawlerEffectivenessCheck {

    private CrawlerEffectivenessCheck() {}

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

        int totalElements = 0;
        int totalUnique = 0;
        int totalFragileAvoided = 0;

        try {
            driver.activateApp("gov.nyc.doitt.ThreeOneOne");
            sleep(3000);

            MobileScreenSnapshot home = crawlAndReport(crawler, "eff_01_Home");
            totalElements += home.getNativeElements().size();
            totalUnique += countUnique(home);

            WebElement menuTab = findByTextOrDesc(driver, home, "Menu");
            if (menuTab != null) {
                menuTab.click();
                sleep(2000);
                MobileScreenSnapshot menu = crawlAndReport(crawler, "eff_02_Menu");
                totalElements += menu.getNativeElements().size();
                totalUnique += countUnique(menu);
                totalFragileAvoided += countAvoidedFragileXpaths(menu);
            } else {
                System.out.println("[WARN] Could not find Menu tab.");
            }

            // Relaunch to get back to a clean Home (RN tab state can be sticky).
            driver.activateApp("gov.nyc.doitt.ThreeOneOne");
            sleep(2000);
            MobileScreenSnapshot home2 = crawler.crawlCurrentScreen();

            WebElement myRequests = findByTextOrDesc(driver, home2, "My Service Requests");
            if (myRequests != null) {
                myRequests.click();
                sleep(2000);
                MobileScreenSnapshot myRequestsScreen = crawlAndReport(crawler, "eff_03_MyServiceRequests");
                totalElements += myRequestsScreen.getNativeElements().size();
                totalUnique += countUnique(myRequestsScreen);

                WebElement lookup = findByTextOrDesc(driver, myRequestsScreen, "Lookup");
                if (lookup != null) {
                    lookup.click();
                    sleep(2000);
                    MobileScreenSnapshot lookupScreen = crawlAndReport(crawler, "eff_04_Lookup");
                    totalElements += lookupScreen.getNativeElements().size();
                    totalUnique += countUnique(lookupScreen);
                } else {
                    System.out.println("[WARN] Could not find Lookup button.");
                }
            } else {
                System.out.println("[WARN] Could not find My Service Requests entry point.");
            }

            System.out.println();
            System.out.println("========================================================");
            System.out.println("CRAWLER EFFECTIVENESS SUMMARY");
            System.out.println("Elements discovered : " + totalElements);
            System.out.println("Resolved to UNIQUE   : " + totalUnique
                    + " (" + pct(totalUnique, totalElements) + "%)");
            System.out.println("Fragile/structural candidates seen but correctly NOT chosen as best: "
                    + totalFragileAvoided);
            System.out.println("========================================================");
        } finally {
            driver.quit();
        }
    }

    private static int countUnique(MobileScreenSnapshot snapshot) {
        return (int) snapshot.getNativeElements().stream().filter(MobileElementInfo::isResolved).count();
    }

    /**
     * Counts elements where the crawler generated a structural/index-based xpath candidate
     * (e.g. positional {@code /android.view.View[2]}) among its options, but its BEST/chosen
     * candidate was a non-structural strategy instead -- i.e. the crawler had the "easy"
     * fragile locator available and deliberately preferred a better one.
     */
    private static int countAvoidedFragileXpaths(MobileScreenSnapshot snapshot) {
        int count = 0;
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            boolean hasStructuralCandidate = info.getCandidates().stream()
                    .anyMatch(c -> c.getValue() != null && c.getValue().matches(".*\\[[0-9]+\\].*"));
            boolean bestIsNonStructural = info.isResolved()
                    && !info.getBestUniqueCandidate().getValue().matches(".*\\[[0-9]+\\].*");
            if (hasStructuralCandidate && bestIsNonStructural) {
                count++;
            }
        }
        return count;
    }

    private static String pct(int part, int whole) {
        if (whole == 0) {
            return "0";
        }
        return String.format("%.1f", (part * 100.0) / whole);
    }

    private static MobileScreenSnapshot crawlAndReport(MobileElementCrawler crawler, String screenName) {
        MobileScreenSnapshot snapshot = crawler.crawlCurrentScreen();
        String reportPath = MobileCrawlerReportWriter.write(screenName, snapshot);
        List<MobileElementInfo> elements = snapshot.getNativeElements();
        long uniqueCount = elements.stream().filter(MobileElementInfo::isResolved).count();
        System.out.println("[OK] " + screenName + ": " + elements.size() + " elements discovered, "
                + uniqueCount + " resolved to a UNIQUE candidate. Report: " + reportPath);
        for (MobileElementInfo info : elements) {
            if (info.isResolved()) {
                MobileLocatorCandidate best = info.getBestUniqueCandidate();
                System.out.println("     - " + info.getTagOrClassName() + " text=\"" + info.getText()
                        + "\" -> " + best.getStrategy() + "=" + best.getValue());
            } else {
                System.out.println("     ! UNRESOLVED: " + info.getTagOrClassName() + " text=\"" + info.getText() + "\"");
            }
        }
        return snapshot;
    }

    private static WebElement findByTextOrDesc(AndroidDriver driver, MobileScreenSnapshot snapshot, String needle) {
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            if (info.getText() != null && info.getText().toLowerCase().contains(needle.toLowerCase())
                    && info.isResolved()) {
                try {
                    return driver.findElement(toSeleniumBy(info));
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        try {
            return driver.findElement(By.xpath("//*[contains(@text,'" + needle + "') or contains(@content-desc,'"
                    + needle + "')]"));
        } catch (Exception e) {
            return null;
        }
    }

    private static By toSeleniumBy(MobileElementInfo info) {
        MobileLocatorCandidate best = info.getBestUniqueCandidate();
        switch (best.getStrategy()) {
            case RESOURCE_ID:
                return AppiumBy.id(best.getValue());
            case ACCESSIBILITY_ID:
                return AppiumBy.accessibilityId(best.getValue());
            default:
                return By.xpath(best.getValue().startsWith("//") ? best.getValue()
                        : "//*[@text='" + best.getValue() + "']");
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
