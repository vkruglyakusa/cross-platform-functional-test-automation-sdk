package com.test.automation.sdk.tools.crawler.mobile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.test.automation.sdk.discovery.DiscoveredElement;
import com.test.automation.sdk.discovery.DiscoveryResult;
import com.test.automation.sdk.discovery.LocatorCandidate;
import com.test.automation.sdk.discovery.LocatorCandidate.Marker;
import com.test.automation.sdk.tools.crawler.web.ElementCrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MobileElementDiscoveryAdapter}'s driver-free normalization
 * logic (Structure Cleanup Phase 6). Only the pure {@code toDiscoveryResult}/
 * {@code toDiscoveredElement} conversions are covered here -- {@code discoverCurrent()}
 * requires a live {@code AppiumDriver} session and is out of scope for unit tests
 * (mirrors {@code MobileElementCrawlerTest}'s driver-free scope note).
 */
@DisplayName("MobileElementDiscoveryAdapter - driver-free normalization")
class MobileElementDiscoveryAdapterTest {

    private MobileElementInfo nativeElementWithCandidates() {
        MobileElementInfo info = new MobileElementInfo("android", "android.widget.Button", "Log In");
        MobileLocatorCandidate unique = new MobileLocatorCandidate(
                MobileLocatorCandidate.Strategy.RESOURCE_ID, "com.app:id/loginBtn");
        unique.setMarker(MobileLocatorCandidate.Marker.UNIQUE);
        unique.setMatchCount(1);
        info.addCandidate(unique);

        MobileLocatorCandidate notUnique = new MobileLocatorCandidate(
                MobileLocatorCandidate.Strategy.TEXT, "Log In");
        notUnique.setMarker(MobileLocatorCandidate.Marker.NOT_UNIQUE);
        notUnique.setMatchCount(2);
        info.addCandidate(notUnique);
        return info;
    }

    @Test
    @DisplayName("toDiscoveredElement normalizes platform, tag and text from a native MobileElementInfo")
    void toDiscoveredElementNormalizesBasics() {
        DiscoveredElement element = MobileElementDiscoveryAdapter.toDiscoveredElement(nativeElementWithCandidates());

        assertEquals("android", element.getPlatform());
        assertEquals("android.widget.Button", element.getTagOrType());
        assertEquals("Log In", element.getText());
        assertEquals(2, element.getCandidates().size());
    }

    @Test
    @DisplayName("toDiscoveredElement maps MobileLocatorCandidate.Marker.UNIQUE to the common Marker.UNIQUE and resolves the element")
    void mapsUniqueMarker() {
        DiscoveredElement element = MobileElementDiscoveryAdapter.toDiscoveredElement(nativeElementWithCandidates());

        assertTrue(element.isResolved());
        LocatorCandidate best = element.getBestUniqueCandidate();
        assertNotNull(best);
        assertEquals(Marker.UNIQUE, best.getMarker());
        assertEquals("RESOURCE_ID", best.getStrategyLabel());
        assertEquals("com.app:id/loginBtn", best.getValue());
        assertEquals(1, best.getMatchCount());
    }

    @Test
    @DisplayName("toDiscoveredElement maps MobileLocatorCandidate.Marker.NOT_UNIQUE with its match count")
    void mapsNotUniqueMarker() {
        DiscoveredElement element = MobileElementDiscoveryAdapter.toDiscoveredElement(nativeElementWithCandidates());

        LocatorCandidate notUnique = element.getCandidates().stream()
                .filter(c -> c.getStrategyLabel().equals("TEXT"))
                .findFirst().orElseThrow();
        assertEquals(Marker.NOT_UNIQUE, notUnique.getMarker());
        assertEquals(2, notUnique.getMatchCount());
    }

    @Test
    @DisplayName("toDiscoveredElement.unwrap(MobileElementInfo.class) returns the original source object")
    void unwrapReturnsOriginalSource() {
        MobileElementInfo info = nativeElementWithCandidates();
        DiscoveredElement element = MobileElementDiscoveryAdapter.toDiscoveredElement(info);

        assertSame(info, element.unwrap(MobileElementInfo.class));
    }

    @Test
    @DisplayName("toDiscoveryResult combines native elements and delegated WebView elements into one normalized result")
    void toDiscoveryResultCombinesNativeAndWebView() {
        ElementCrawler.ElementInfo webInfo = new ElementCrawler.ElementInfo();
        webInfo.tag = "input";
        webInfo.allXpaths.put("BY ID  [" + ElementCrawler.LABEL_UNIQUE + "]", "//input[@id='q']");

        Map<String, List<ElementCrawler.ElementInfo>> webViewElements = new LinkedHashMap<>();
        webViewElements.put("WEBVIEW_com.example.app", List.of(webInfo));

        MobileScreenSnapshot snapshot = new MobileScreenSnapshot(
                "android", "<hierarchy/>", new byte[0],
                List.of(nativeElementWithCandidates()), webViewElements);

        DiscoveryResult result = MobileElementDiscoveryAdapter.toDiscoveryResult(snapshot);

        assertEquals("android", result.getPlatform());
        assertEquals(2, result.getElements().size(), "1 native + 1 delegated WebView element");
        assertEquals(2, result.resolvedCount(), "both the native and the WebView element resolve to UNIQUE");

        boolean hasWebElement = result.getElements().stream().anyMatch(e -> "web".equals(e.getPlatform()));
        assertTrue(hasWebElement, "WebView element must be normalized with platform=web (via WebElementDiscoveryAdapter)");
    }

    @Test
    @DisplayName("toDiscoveryResult handles a snapshot with no WebView content")
    void toDiscoveryResultHandlesNoWebViewContent() {
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot(
                "ios", "<hierarchy/>", new byte[0],
                List.of(nativeElementWithCandidates()), Collections.emptyMap());

        DiscoveryResult result = MobileElementDiscoveryAdapter.toDiscoveryResult(snapshot);

        assertEquals(1, result.getElements().size());
        assertFalse(snapshot.hasWebViewContent());
    }
}
