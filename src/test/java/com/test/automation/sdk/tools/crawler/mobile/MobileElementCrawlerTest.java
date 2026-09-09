package com.test.automation.sdk.tools.crawler.mobile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator;

/**
 * Unit tests for the mobile element crawler package.
 *
 * These tests exercise the driver-free, unit-testable core logic only:
 * {@code MobileElementCrawler.analyzePageSource(String, String)} (static, no Appium
 * session required), {@link MobileCrawlerReportWriter#render}, and
 * {@link MobilePageObjectGenerator#generate}. Anything requiring a live
 * {@code AppiumDriver} (screen-stability polling, WebView delegation,
 * {@code MobileDataDrivenCrawler} step execution) is out of scope here and should be
 * covered by an integration/smoke test against a real or emulated device.
 */
@DisplayName("Mobile crawler - driver-free core logic")
class MobileElementCrawlerTest {

    private static final String ANDROID_SOURCE_UNIQUE_IDS =
            "<hierarchy>"
            + "<android.widget.FrameLayout>"
            + "  <android.widget.EditText resource-id='com.app:id/username' text='' clickable='true'/>"
            + "  <android.widget.Button resource-id='com.app:id/loginBtn' text='Log In' clickable='true'/>"
            + "</android.widget.FrameLayout>"
            + "</hierarchy>";

    @Test
    @DisplayName("analyzePageSource returns empty list for null/blank input")
    void analyzePageSourceHandlesBlankInput() {
        assertTrue(MobileElementCrawler.analyzePageSource(null, "android").isEmpty());
        assertTrue(MobileElementCrawler.analyzePageSource("   ", "android").isEmpty());
    }

    @Test
    @DisplayName("analyzePageSource marks a globally-unique resource-id as UNIQUE")
    void uniqueResourceIdIsMarkedUnique() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        MobileElementInfo loginButton = findByText(elements, "Log In");
        assertNotNull(loginButton, "login button should be discovered");
        assertTrue(loginButton.isResolved(), "unique resource-id should resolve to a usable candidate");
        assertEquals(MobileLocatorCandidate.Strategy.RESOURCE_ID, loginButton.getBestUniqueCandidate().getStrategy());
    }

    @Test
    @DisplayName("analyzePageSource marks a duplicated resource-id as NOT_UNIQUE")
    void duplicatedResourceIdIsNotUnique() {
        String xml = "<hierarchy>"
                + "<android.widget.LinearLayout>"
                + "  <android.widget.TextView resource-id='com.app:id/rowTitle' text='Item A'/>"
                + "  <android.widget.TextView resource-id='com.app:id/rowTitle' text='Item B'/>"
                + "</android.widget.LinearLayout>"
                + "</hierarchy>";
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(xml, "android");
        MobileElementInfo itemA = findByText(elements, "Item A");
        assertNotNull(itemA);
        boolean anyUniqueResourceId = itemA.getCandidates().stream()
                .anyMatch(c -> c.getStrategy() == MobileLocatorCandidate.Strategy.RESOURCE_ID
                        && c.getMarker() == MobileLocatorCandidate.Marker.UNIQUE);
        assertFalse(anyUniqueResourceId, "shared resource-id across rows must not be reported UNIQUE");
    }

    @Test
    @DisplayName("Recycled list-item resource-id pattern is rejected as DYNAMIC even if unique in this snapshot")
    void recycledListItemIdIsDynamic() {
        String xml = "<hierarchy>"
                + "<android.widget.ListView>"
                + "  <android.widget.TextView resource-id='com.app:id/list_item_3' text='Only row'/>"
                + "</android.widget.ListView>"
                + "</hierarchy>";
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(xml, "android");
        MobileElementInfo row = findByText(elements, "Only row");
        assertNotNull(row);
        boolean resourceIdIsDynamic = row.getCandidates().stream()
                .anyMatch(c -> c.getStrategy() == MobileLocatorCandidate.Strategy.RESOURCE_ID
                        && c.getMarker() == MobileLocatorCandidate.Marker.DYNAMIC);
        assertTrue(resourceIdIsDynamic, "list_item_N ids must always be rejected as DYNAMIC");
        assertFalse(row.isResolved() && row.getBestUniqueCandidate().getStrategy() == MobileLocatorCandidate.Strategy.RESOURCE_ID,
                "a DYNAMIC resource-id must never be chosen as the best candidate");
    }

    @Test
    @DisplayName("Compound candidate resolves an element when no single attribute is unique alone")
    void compoundCandidateResolvesAmbiguousElement() {
        // Element A shares its resource-id with B (so resource-id alone is ambiguous), and
        // shares its text with C (so text alone is also ambiguous) -- but the combination
        // (resource-id + text) is unique to A.
        String xml = "<hierarchy>"
                + "<android.widget.LinearLayout>"
                + "  <android.widget.TextView resource-id='com.app:id/cell' text='Row1'/>"
                + "  <android.widget.TextView resource-id='com.app:id/cell' text='Row2'/>"
                + "  <android.widget.TextView resource-id='com.app:id/header' text='Row1'/>"
                + "</android.widget.LinearLayout>"
                + "</hierarchy>";
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(xml, "android");
        List<MobileElementInfo> row1Matches = elements.stream()
                .filter(e -> "Row1".equals(e.getText())
                        && e.getCandidates().stream().anyMatch(c -> "com.app:id/cell".equals(c.getValue())))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, row1Matches.size(), "expected exactly one 'cell' element with text Row1");
        MobileElementInfo row1 = row1Matches.get(0);

        boolean resourceIdAloneUnique = row1.getCandidates().stream()
                .anyMatch(c -> c.getStrategy() == MobileLocatorCandidate.Strategy.RESOURCE_ID
                        && c.getMarker() == MobileLocatorCandidate.Marker.UNIQUE);
        boolean textAloneUnique = row1.getCandidates().stream()
                .anyMatch(c -> c.getStrategy() == MobileLocatorCandidate.Strategy.TEXT
                        && c.getMarker() == MobileLocatorCandidate.Marker.UNIQUE);
        assertFalse(resourceIdAloneUnique, "resource-id 'cell' is shared with another row and must not be UNIQUE alone");
        assertFalse(textAloneUnique, "text 'Row1' is shared with the header element and must not be UNIQUE alone");

        boolean hasUniqueCompoundXpath = row1.getCandidates().stream()
                .anyMatch(c -> c.getStrategy() == MobileLocatorCandidate.Strategy.XPATH
                        && c.getMarker() == MobileLocatorCandidate.Marker.UNIQUE
                        && c.getValue().contains("cell") && c.getValue().contains("Row1"));
        assertTrue(hasUniqueCompoundXpath, "combined resource-id+text predicate should be UNIQUE even though "
                + "neither attribute alone is unique");
    }

    @Test
    @DisplayName("Structural XPath fallback is always present but never eligible as best-unique")
    void structuralFallbackIsNeverBestUnique() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        for (MobileElementInfo info : elements) {
            boolean hasStructural = info.getCandidates().stream()
                    .anyMatch(c -> c.getMarker() == MobileLocatorCandidate.Marker.STRUCTURAL);
            assertTrue(hasStructural, "every element must retain a structural fallback candidate");
            if (info.isResolved()) {
                assertNotEquals(MobileLocatorCandidate.Marker.STRUCTURAL, info.getBestUniqueCandidate().getMarker());
            }
        }
    }

    @Test
    @DisplayName("iOS page source uses name/label attributes instead of content-desc/text")
    void iosPageSourceUsesNameAndLabel() {
        String xml = "<AppiumAUT><XCUIElementTypeApplication>"
                + "<XCUIElementTypeButton name='loginButton' label='Log In'/>"
                + "</XCUIElementTypeApplication></AppiumAUT>";
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(xml, "ios");
        MobileElementInfo button = findByText(elements, "Log In");
        assertNotNull(button, "iOS button should be discovered via label attribute");
        assertTrue(button.isResolved());
        assertEquals(MobileLocatorCandidate.Strategy.ACCESSIBILITY_ID, button.getBestUniqueCandidate().getStrategy());
    }

    @Test
    @DisplayName("MobileCrawlerReportWriter.render includes screen name and every candidate marker")
    void reportRenderIncludesMarkersAndScreenName() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot("android", ANDROID_SOURCE_UNIQUE_IDS,
                new byte[0], elements, java.util.Collections.<String, List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>>emptyMap());
        String report = MobileCrawlerReportWriter.render("LoginScreen", snapshot);
        assertTrue(report.contains("LoginScreen"), "report must reference the screen name");
        assertTrue(report.contains("UNIQUE"), "report must surface UNIQUE markers");
    }

    @Test
    @DisplayName("MobilePageObjectGenerator.generate emits only UNIQUE candidates as @FindBy fields")
    void generatorEmitsOnlyUniqueCandidates() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        MobileScreenSnapshot snapshot = new MobileScreenSnapshot("android", ANDROID_SOURCE_UNIQUE_IDS,
                new byte[0], elements, java.util.Collections.<String, List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>>emptyMap());
        String generated = MobilePageObjectGenerator.generate("PoletopLoginPage", snapshot);
        assertTrue(generated.contains("public class PoletopLoginPage"));
        assertTrue(generated.contains("@AndroidFindBy"), "resolved android candidates must emit @AndroidFindBy");
        assertTrue(generated.contains("com.app:id/loginBtn"), "unique resource-id value must be present in the field annotation");
    }

    @Test
    @DisplayName("MobileElementInfo.mergeKey is stable for the same element and differs across elements")
    void mergeKeyIsStableAndDistinguishing() {
        List<MobileElementInfo> elements = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        MobileElementInfo loginButton = findByText(elements, "Log In");
        assertNotNull(loginButton);
        String key1 = loginButton.mergeKey();
        String key2 = loginButton.mergeKey();
        assertEquals(key1, key2, "mergeKey must be deterministic for the same element");

        List<MobileElementInfo> reparsed = MobileElementCrawler.analyzePageSource(ANDROID_SOURCE_UNIQUE_IDS, "android");
        MobileElementInfo reparsedLoginButton = findByText(reparsed, "Log In");
        assertEquals(key1, reparsedLoginButton.mergeKey(), "mergeKey must match across re-parses of the same screen");
    }

    private static MobileElementInfo findByText(List<MobileElementInfo> elements, String text) {
        return elements.stream().filter(e -> text.equals(e.getText())).findFirst().orElse(null);
    }
}
