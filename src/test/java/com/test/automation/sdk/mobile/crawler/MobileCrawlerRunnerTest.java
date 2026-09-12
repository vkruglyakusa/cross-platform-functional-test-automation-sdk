package com.test.automation.sdk.mobile.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate.Marker;
import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate.Strategy;
import com.test.automation.sdk.utility.ElementCrawler;

class MobileCrawlerRunnerTest {
    private static final String[] KEYS = {
            "crawler.className", "crawler.screenName", "crawler.mobileOS",
            "crawler.deviceName", "crawler.resultFile"
    };

    @AfterEach
    void clearProperties() {
        for (String key : KEYS) System.clearProperty(key);
    }

    @Test
    void rejectsMissingRequiredProperties() {
        validProperties();
        System.clearProperty("crawler.className");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.screenName");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.mobileOS");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.deviceName");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.resultFile");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
    }

    @Test
    void rejectsUnsupportedMobileOSAndReservedClassName() {
        validProperties();
        System.setProperty("crawler.mobileOS", "windows");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.setProperty("crawler.className", "class");
        assertThrows(IllegalArgumentException.class, MobileCrawlerRunner.Config::fromSystemProperties);
    }

    @Test
    void normalizesAndroidAndIosCase() {
        validProperties();
        System.setProperty("crawler.mobileOS", "ANDROID");
        assertEquals("android", MobileCrawlerRunner.Config.fromSystemProperties().mobileOS);
        System.setProperty("crawler.mobileOS", "iOS");
        assertEquals("ios", MobileCrawlerRunner.Config.fromSystemProperties().mobileOS);
    }

    @Test
    void completedResultCountsEmptySnapshot() {
        MobileCrawlerRunner.Config config = config();
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot(
                "android", "<hierarchy/>", null, Collections.emptyList(), Collections.emptyMap());

        Map<String, Object> result = MobileCrawlerRunner.completedResult(config, snapshot, Collections.emptyMap());

        assertEquals("COMPLETED", result.get("status"));
        assertEquals("NATIVE_MOBILE", result.get("mode"));
        assertEquals("android", result.get("platform"));
        assertEquals("login-screen", result.get("screen"));
        assertEquals(0, result.get("nativeElements"));
        assertEquals(0, result.get("resolvedNativeElements"));
        assertEquals(0, result.get("unresolvedNativeElements"));
        assertEquals(0, result.get("webViewElements"));
    }

    @Test
    void completedResultCountsResolvedUnresolvedAndWebViewElements() {
        MobileElementInfo resolved = new MobileElementInfo("android", "Button", "Submit");
        MobileLocatorCandidate candidate = new MobileLocatorCandidate(Strategy.RESOURCE_ID, "app:id/submit");
        candidate.setMarker(Marker.UNIQUE);
        candidate.setMatchCount(1);
        resolved.addCandidate(candidate);
        MobileElementInfo unresolved = new MobileElementInfo("android", "EditText", "Email");
        Map<String, List<ElementCrawler.ElementInfo>> webViews = Map.of(
                "WEBVIEW_app", List.of(new ElementCrawler.ElementInfo(), new ElementCrawler.ElementInfo()));
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot(
                "android", "<hierarchy/>", new byte[] {1, 2},
                List.of(resolved, unresolved), webViews);
        Map<String, String> artifacts = Map.of("screenshot", "screen.png");

        Map<String, Object> result = MobileCrawlerRunner.completedResult(config(), snapshot, artifacts);

        assertEquals(2, result.get("nativeElements"));
        assertEquals(1, result.get("resolvedNativeElements"));
        assertEquals(1, result.get("unresolvedNativeElements"));
        assertEquals(2, result.get("webViewElements"));
        assertEquals(artifacts, result.get("artifacts"));
    }

    @Test
    void completedResultHandlesNullElementCollections() {
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot(
                "ios", "<AppiumAUT/>", null, null, null);

        Map<String, Object> result = MobileCrawlerRunner.completedResult(config(), snapshot, Collections.emptyMap());

        assertEquals(0, result.get("nativeElements"));
        assertEquals(0, result.get("resolvedNativeElements"));
        assertEquals(0, result.get("unresolvedNativeElements"));
        assertEquals(0, result.get("webViewElements"));
    }

    private static MobileCrawlerRunner.Config config() {
        validProperties();
        return MobileCrawlerRunner.Config.fromSystemProperties();
    }

    private static void validProperties() {
        System.setProperty("crawler.className", "LoginScreen");
        System.setProperty("crawler.screenName", "login-screen");
        System.setProperty("crawler.mobileOS", "android");
        System.setProperty("crawler.deviceName", "emulator-5554");
        System.setProperty("crawler.resultFile", "target/snapshot-result.json");
    }
}
