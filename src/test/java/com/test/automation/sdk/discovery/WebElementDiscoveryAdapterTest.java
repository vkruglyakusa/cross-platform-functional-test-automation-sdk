package com.test.automation.sdk.discovery;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.test.automation.sdk.discovery.LocatorCandidate.Marker;
import com.test.automation.sdk.utility.ElementCrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WebElementDiscoveryAdapter}'s driver-free normalization
 * logic (Structure Cleanup Phase 6). Only the pure {@code toDiscoveryResult}/
 * {@code toDiscoveredElement} conversions are covered here -- {@code discoverCurrent()}
 * requires a live {@code WebDriver} session and is out of scope for unit tests
 * (mirrors {@code MobileElementCrawlerTest}'s driver-free scope note).
 *
 * <p>Moved from {@code utility.WebElementDiscoveryAdapterTest} in Phase 8
 * (Package Cleanup) to track the production class's new location.
 */
@DisplayName("WebElementDiscoveryAdapter - driver-free normalization")
class WebElementDiscoveryAdapterTest {

    private ElementCrawler.ElementInfo elementInfoWithXpaths() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.tag = "input";
        info.text = "";
        info.labelText = "Username";
        info.allXpaths.put("BY ID  [" + ElementCrawler.LABEL_DYNAMIC + " - auto-generated ID]", "//input[@id='mat-input-0']");
        info.allXpaths.put("BY FORMCONTROLNAME  [" + ElementCrawler.LABEL_UNIQUE + "]", "//input[@formcontrolname='username']");
        info.allXpaths.put("BY NAME  [" + ElementCrawler.LABEL_NOT_UNIQUE + " - 3 found]", "//input[@name='user']");
        info.allXpaths.put("BY ROLE  [" + ElementCrawler.LABEL_STALE + " - 0 elements]", "//input[@role='textbox']");
        info.allXpaths.put("STRUCTURAL [fallback - verify in DevTools]", "//div[3]/input[1]");
        info.allXpaths.put("BY ANCESTOR [@id='nav'] LIST [3 siblings]  [GROUP]", "//*[@id='nav']//input");
        info.xpath = "//input[@id='username']";
        return info;
    }

    @Test
    @DisplayName("toDiscoveredElement normalizes tag, text and platform")
    void toDiscoveredElementNormalizesBasics() {
        ElementCrawler.ElementInfo info = elementInfoWithXpaths();
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(info);

        assertEquals("web", element.getPlatform());
        assertEquals("input", element.getTagOrType());
        assertEquals("Username", element.getText(), "falls back to labelText when text is blank");
        assertEquals(6, element.getCandidates().size());
    }

    @Test
    @DisplayName("toDiscoveredElement maps UNIQUE composite key to Marker.UNIQUE and resolves the element")
    void mapsUniqueMarker() {
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(elementInfoWithXpaths());

        assertTrue(element.isResolved());
        LocatorCandidate best = element.getBestUniqueCandidate();
        assertNotNull(best);
        assertEquals(Marker.UNIQUE, best.getMarker());
        assertEquals("//input[@formcontrolname='username']", best.getValue());
        assertEquals("BY FORMCONTROLNAME", best.getStrategyLabel());
    }

    @Test
    @DisplayName("toDiscoveredElement maps NOT UNIQUE composite key with parsed match count")
    void mapsNotUniqueMarkerWithMatchCount() {
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(elementInfoWithXpaths());

        LocatorCandidate notUnique = findByStrategy(element, "BY NAME");
        assertEquals(Marker.NOT_UNIQUE, notUnique.getMarker());
        assertEquals(3, notUnique.getMatchCount());
    }

    @Test
    @DisplayName("toDiscoveredElement maps DYNAMIC and STALE composite keys")
    void mapsDynamicAndStaleMarkers() {
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(elementInfoWithXpaths());

        assertEquals(Marker.DYNAMIC, findByStrategy(element, "BY ID").getMarker());
        assertEquals(Marker.STALE, findByStrategy(element, "BY ROLE").getMarker());
    }

    @Test
    @DisplayName("toDiscoveredElement maps STRUCTURAL composite key")
    void mapsStructuralMarker() {
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(elementInfoWithXpaths());

        LocatorCandidate structural = findByStrategy(element, "STRUCTURAL [fallback - verify in DevTools]");
        assertEquals(Marker.STRUCTURAL, structural.getMarker());
    }

    @Test
    @DisplayName("toDiscoveredElement maps an unrecognized composite key (e.g. GROUP) to Marker.OTHER")
    void mapsUnrecognizedKeyToOther() {
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(elementInfoWithXpaths());

        LocatorCandidate group = findByStrategy(element, "BY ANCESTOR [@id='nav'] LIST [3 siblings]");
        assertEquals(Marker.OTHER, group.getMarker());
        assertEquals(-1, group.getMatchCount());
    }

    @Test
    @DisplayName("toDiscoveredElement.unwrap(ElementInfo.class) returns the original source object")
    void unwrapReturnsOriginalSource() {
        ElementCrawler.ElementInfo info = elementInfoWithXpaths();
        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(info);

        assertSame(info, element.unwrap(ElementCrawler.ElementInfo.class));
    }

    @Test
    @DisplayName("toDiscoveryResult wraps multiple elements with platform=web and the given source label")
    void toDiscoveryResultWrapsElements() {
        DiscoveryResult result = WebElementDiscoveryAdapter.toDiscoveryResult(
                "https://example.com/login", List.of(elementInfoWithXpaths()));

        assertEquals("web", result.getPlatform());
        assertEquals("https://example.com/login", result.getSourceLabel());
        assertEquals(1, result.getElements().size());
        assertEquals(1, result.resolvedCount());
    }

    @Test
    @DisplayName("toDiscoveryResult handles a null element list gracefully")
    void toDiscoveryResultHandlesNullList() {
        DiscoveryResult result = WebElementDiscoveryAdapter.toDiscoveryResult("about:blank", null);

        assertTrue(result.getElements().isEmpty());
        assertEquals(0, result.resolvedCount());
    }

    @Test
    @DisplayName("an unresolved element (no UNIQUE candidate) reports getBestUniqueCandidate() == null")
    void unresolvedElementHasNoBestCandidate() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.tag = "div";
        info.allXpaths.put("BY NAME  [" + ElementCrawler.LABEL_NOT_UNIQUE + " - 2 found]", "//div[@name='x']");

        DiscoveredElement element = WebElementDiscoveryAdapter.toDiscoveredElement(info);

        assertFalse(element.isResolved());
        assertNull(element.getBestUniqueCandidate());
    }

    private static LocatorCandidate findByStrategy(DiscoveredElement element, String strategyLabel) {
        for (LocatorCandidate candidate : element.getCandidates()) {
            if (candidate.getStrategyLabel().equals(strategyLabel)) {
                return candidate;
            }
        }
        throw new AssertionError("No candidate found for strategy label: " + strategyLabel);
    }
}
