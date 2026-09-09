package com.test.automation.sdk.mobile.crawler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.appium.java_client.AppiumDriver;

@DisplayName("Legacy mobile tooling compatibility facades")
class MobileToolingCompatibilityFacadeTest {

    private static final String ANDROID_SOURCE =
            "<hierarchy>"
            + "<android.widget.FrameLayout>"
            + "  <android.widget.EditText resource-id='com.app:id/username' text='' clickable='true'/>"
            + "  <android.widget.Button resource-id='com.app:id/loginBtn' text='Log In' clickable='true'/>"
            + "</android.widget.FrameLayout>"
            + "</hierarchy>";

    @Test
    @DisplayName("legacy MobileElementCrawler analyzePageSource returns wrapped legacy model types")
    void mobileElementCrawlerAnalyzePageSourceReturnsLegacyTypes() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE, "android");
        MobileElementInfo loginButton = elements.stream().filter(e -> "Log In".equals(e.getText())).findFirst().orElse(null);

        assertEquals(2, elements.size());
        assertTrue(loginButton.isResolved());
        assertEquals(MobileLocatorCandidate.Strategy.RESOURCE_ID, loginButton.getBestUniqueCandidate().getStrategy());
    }

    @Test
    @DisplayName("legacy MobileCrawlerReportWriter and MobilePageObjectGenerator accept the legacy snapshot facade")
    void legacyStaticFacadesAcceptLegacySnapshot() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE, "android");
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot("android", ANDROID_SOURCE, new byte[0], elements,
                Collections.<String, List<com.test.automation.sdk.utility.ElementCrawler.ElementInfo>>emptyMap());

        String report = MobileCrawlerReportWriter.render("LoginScreen", snapshot);
        String pageObject = MobilePageObjectGenerator.generate("PoletopLoginPage", snapshot);

        assertTrue(report.contains("LoginScreen"));
        assertTrue(pageObject.contains("PoletopLoginPage"));
        assertTrue(pageObject.contains("@AndroidFindBy"));
    }

    @Test
    @DisplayName("legacy MobileCrawlerStep and MobileDataDrivenCrawler fluent APIs remain facade-typed")
    void mobileCrawlerFluentApisRemainFacadeTyped() {
        MobileCrawlerStep step = MobileCrawlerStep.tapByText("Continue").describe("Step A");
        MobileDataDrivenCrawler crawler = new MobileDataDrivenCrawler(mock(AppiumDriver.class));

        assertEquals(MobileCrawlerStep.Action.TAP, step.getAction());
        assertEquals(MobileCrawlerStep.By.TEXT, step.getBy());
        assertSame(crawler, crawler.setStateDeduplication(false));
    }

    @Test
    @DisplayName("legacy MobileElementDiscoveryAdapter static conversion accepts legacy snapshot facade")
    void mobileElementDiscoveryAdapterStaticConversionAcceptsLegacySnapshot() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE, "android");
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot("android", ANDROID_SOURCE, new byte[0], elements,
                Collections.<String, List<com.test.automation.sdk.utility.ElementCrawler.ElementInfo>>emptyMap());

        assertEquals(2, MobileElementDiscoveryAdapter.toDiscoveryResult(snapshot).resolvedCount());
    }
}
