package com.test.automation.sdk.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.discovery.DiscoveredElement;
import com.test.automation.sdk.discovery.DiscoveryResult;
import com.test.automation.sdk.discovery.LocatorCandidate;
import com.test.automation.sdk.discovery.LocatorCandidate.Marker;

class WebCrawlerRunnerTest {
    private static final String[] KEYS = {
            "crawler.className", "crawler.url", "crawler.resultFile",
            "crawler.mode", "crawler.browser", "crawler.viewport"
    };

    @AfterEach
    void clearProperties() {
        for (String key : KEYS) System.clearProperty(key);
    }

    @Test
    void rejectsMissingRequiredProperties() {
        validProperties();
        System.clearProperty("crawler.className");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.url");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.resultFile");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.clearProperty("crawler.mode");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
    }

    @Test
    void rejectsUnsupportedModeAndBrowser() {
        validProperties();
        System.setProperty("crawler.mode", "DESKTOP");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
        validProperties();
        System.setProperty("crawler.browser", "safari");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
    }

    @Test
    void rejectsReservedJavaTypeName() {
        validProperties();
        System.setProperty("crawler.className", "class");
        assertThrows(IllegalArgumentException.class, WebCrawlerRunner.Config::fromSystemProperties);
    }

    @Test
    void normalizesModeAndBrowserCase() {
        validProperties();
        System.setProperty("crawler.mode", "mobile_web");
        System.setProperty("crawler.browser", "FIREFOX");
        WebCrawlerRunner.Config config = WebCrawlerRunner.Config.fromSystemProperties();
        assertEquals("MOBILE_WEB", config.mode);
        assertEquals("firefox", config.browser);
    }

    @Test
    void defaultsBrowserAndViewport() {
        validProperties();
        System.clearProperty("crawler.browser");
        System.clearProperty("crawler.viewport");
        WebCrawlerRunner.Config config = WebCrawlerRunner.Config.fromSystemProperties();
        assertEquals("chrome", config.browser);
        assertEquals("phone", config.viewport);
    }

    @Test
    void completedResultCountsEmptyDiscovery() {
        DiscoveryResult discovery = new DiscoveryResult("web", "https://example.com", Collections.emptyList());
        Map<String, Object> result = WebCrawlerRunner.completedResult("WEB", discovery, Collections.emptyMap());
        assertEquals("COMPLETED", result.get("status"));
        assertEquals("WEB", result.get("mode"));
        assertEquals(0, result.get("elements"));
        assertEquals(0, result.get("resolvedElements"));
        assertEquals(0, result.get("unresolvedElements"));
        assertEquals("https://example.com", result.get("source"));
    }

    @Test
    void completedResultCountsResolvedAndUnresolvedElements() {
        LocatorCandidate unique = new LocatorCandidate("id", "submit", Marker.UNIQUE, 1);
        DiscoveredElement resolved = new DiscoveredElement("web", "button", "Submit", List.of(unique), null);
        DiscoveredElement unresolved = new DiscoveredElement("web", "input", "Email", Collections.emptyList(), null);
        DiscoveryResult discovery = new DiscoveryResult(
                "web", "https://example.com/login", List.of(resolved, unresolved));
        Map<String, String> artifacts = Map.of("pageObject", "LoginPage.java");
        Map<String, Object> result = WebCrawlerRunner.completedResult("WEB", discovery, artifacts);
        assertEquals(2, result.get("elements"));
        assertEquals(1, result.get("resolvedElements"));
        assertEquals(1, result.get("unresolvedElements"));
        assertEquals(artifacts, result.get("artifacts"));
    }

    @Test
    void webModeDoesNotResizeViewport() throws Exception {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        invokeApplyViewport(driver, "WEB", "phone");
        verify(driver.manage().window(), never()).setSize(org.mockito.ArgumentMatchers.any(Dimension.class));
    }

    @Test
    void mobileWebAppliesPhoneAndTabletViewports() throws Exception {
        WebDriver phoneDriver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        WebDriver tabletDriver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        invokeApplyViewport(phoneDriver, "MOBILE_WEB", "phone");
        invokeApplyViewport(tabletDriver, "MOBILE_WEB", "tablet");
        verify(phoneDriver.manage().window()).setSize(new Dimension(390, 844));
        verify(tabletDriver.manage().window()).setSize(new Dimension(1024, 1366));
    }

    @Test
    void mobileWebRejectsUnsupportedViewport() throws Exception {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> invokeApplyViewport(driver, "MOBILE_WEB", "desktop"));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static void invokeApplyViewport(WebDriver driver, String mode, String viewport) throws Exception {
        Method method = WebCrawlerRunner.class.getDeclaredMethod(
                "applyViewport", WebDriver.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(null, driver, mode, viewport);
    }

    private static void validProperties() {
        System.setProperty("crawler.className", "LoginPage");
        System.setProperty("crawler.url", "https://example.com/login");
        System.setProperty("crawler.resultFile", "target/discovery-result.json");
        System.setProperty("crawler.mode", "WEB");
        System.setProperty("crawler.browser", "chrome");
        System.setProperty("crawler.viewport", "phone");
    }
}
