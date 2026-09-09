package com.test.automation.sdk.tools.crawler.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ElementCrawler public API.
 *
 * These tests verify API surface and visibility contracts without
 * requiring a live WebDriver/browser.
 *
 * SDK version: 1.2.7
 * Changes:
 *  - iframe crawling support (collectFromFrames)
 *  - Additional Angular Material interactive tags
 *  - Extended data-* attribute strategy (data-cy, data-qa, data-automation, data-id, data-test)
 *  - title and autocomplete attribute strategies
 *  - Hidden, file input, contenteditable, select option flags on ElementInfo
 *  - Configurable ancestor walk depth (ANCESTOR_WALK_DEPTH = 12)
 */
@DisplayName("ElementCrawler - public API contract")
class ElementCrawlerTest {

    @Test
    @DisplayName("ElementCrawler class is public")
    void classIsPublic() {
        assertTrue(Modifier.isPublic(ElementCrawler.class.getModifiers()),
            "ElementCrawler must be a public class");
    }

    @Test
    @DisplayName("crawlCurrentPage() is public")
    void crawlCurrentPageIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("crawlCurrentPage");
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "crawlCurrentPage() must be public");
    }

    @Test
    @DisplayName("crawlUrl(String) is public")
    void crawlUrlIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("crawlUrl", String.class);
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "crawlUrl(String) must be public");
    }

    @Test
    @DisplayName("waitForPageReady() is public (1.2.4+)")
    void waitForPageReadyIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("waitForPageReady");
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "waitForPageReady() must be public from 1.2.4 onwards " +
            "so consumer tools (LocatorInvestigator) can call it after navigation");
    }

    @Test
    @DisplayName("getDriver() is public")
    void getDriverIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("getDriver");
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "getDriver() must be public");
    }

    @Test
    @DisplayName("ElementInfo inner class is public static")
    void elementInfoIsPublicStatic() {
        int mods = ElementCrawler.ElementInfo.class.getModifiers();
        assertTrue(Modifier.isPublic(mods),  "ElementInfo must be public");
        assertTrue(Modifier.isStatic(mods),  "ElementInfo must be static");
    }

    @Test
    @DisplayName("ElementInfo.hasUniqueLocator() is public")
    void elementInfoHasUniqueLocatorIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.ElementInfo.class.getMethod("hasUniqueLocator");
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "ElementInfo.hasUniqueLocator() must be public");
    }

    @Test
    @DisplayName("ElementInfo.findByAnnotation() is public")
    void elementInfoFindByAnnotationIsPublic() throws NoSuchMethodException {
        Method m = ElementCrawler.ElementInfo.class.getMethod("findByAnnotation");
        assertTrue(Modifier.isPublic(m.getModifiers()),
            "ElementInfo.findByAnnotation() must be public");
    }

    @Test
    @DisplayName("LABEL constants are public")
    void labelConstantsArePublic() throws NoSuchFieldException {
        assertTrue(Modifier.isPublic(ElementCrawler.class.getField("LABEL_UNIQUE").getModifiers()));
        assertTrue(Modifier.isPublic(ElementCrawler.class.getField("LABEL_NOT_UNIQUE").getModifiers()));
        assertTrue(Modifier.isPublic(ElementCrawler.class.getField("LABEL_STALE").getModifiers()));
        assertTrue(Modifier.isPublic(ElementCrawler.class.getField("LABEL_DYNAMIC").getModifiers()));
        assertTrue(Modifier.isPublic(ElementCrawler.class.getField("LABEL_STRUCTURAL").getModifiers()));
    }

    @Test
    @DisplayName("ElementCrawler loads without errors")
    void classLoads() {
        assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.tools.crawler.web.ElementCrawler"),
            "ElementCrawler must be loadable");
    }

    // --- New fields/constants added in 1.2.7 ------------------------------

    @Test
    @DisplayName("DATA_ATTRS constant is present")
    void dataAttrsConstantExists() throws NoSuchFieldException {
        // Should not throw -- DATA_ATTRS is a private static field
        java.lang.reflect.Field f = ElementCrawler.class.getDeclaredField("DATA_ATTRS");
        assertNotNull(f, "DATA_ATTRS field must exist");
    }

    @Test
    @DisplayName("ANCESTOR_WALK_DEPTH constant is present")
    void ancestorWalkDepthConstantExists() throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field f = ElementCrawler.class.getDeclaredField("ANCESTOR_WALK_DEPTH");
        assertNotNull(f);
        f.setAccessible(true);
        assertEquals(12, f.get(null), "ANCESTOR_WALK_DEPTH must be 12");
    }

    @Test
    @DisplayName("ElementInfo has inFrame field")
    void elementInfoHasInFrameField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("inFrame");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has frameIndex field")
    void elementInfoHasFrameIndexField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("frameIndex");
        assertNotNull(f);
        assertEquals(int.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo frameIndex defaults to -1")
    void elementInfoFrameIndexDefault() throws Exception {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        assertEquals(-1, info.frameIndex, "frameIndex default must be -1 (top document)");
    }

    @Test
    @DisplayName("ElementInfo has isHidden field")
    void elementInfoHasIsHiddenField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isHidden");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isFileInput field")
    void elementInfoHasIsFileInputField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isFileInput");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isContentEditable field")
    void elementInfoHasIsContentEditableField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isContentEditable");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isSelectOption field")
    void elementInfoHasIsSelectOptionField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isSelectOption");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has title field")
    void elementInfoHasTitleField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("title");
        assertNotNull(f);
        assertEquals(String.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has autocomplete field")
    void elementInfoHasAutocompleteField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("autocomplete");
        assertNotNull(f);
        assertEquals(String.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo.toString includes frame info when inFrame=true")
    void elementInfoToStringIncludesFrame() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.inFrame = true;
        info.frameIndex = 2;
        assertTrue(info.toString().contains("frame=2"),
            "toString() must include frame index when inFrame=true");
    }

    @Test
    @DisplayName("ElementInfo.toString includes FILE-INPUT when isFileInput=true")
    void elementInfoToStringIncludesFileInput() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.isFileInput = true;
        assertTrue(info.toString().contains("FILE-INPUT"),
            "toString() must include FILE-INPUT marker");
    }

    @Test
    @DisplayName("ElementInfo.toString includes CONTENTEDITABLE when isContentEditable=true")
    void elementInfoToStringIncludesContentEditable() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.isContentEditable = true;
        assertTrue(info.toString().contains("CONTENTEDITABLE"),
            "toString() must include CONTENTEDITABLE marker");
    }

    @Test
    @DisplayName("ElementInfo has labelText field")
    void elementInfoHasLabelTextField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("labelText");
        assertNotNull(f);
        assertEquals(String.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has labelFollowingXpath field")
    void elementInfoHasLabelFollowingXpathField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("labelFollowingXpath");
        assertNotNull(f);
        assertEquals(String.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isLabel field")
    void elementInfoHasIsLabelField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isLabel");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isLabelledBy field")
    void elementInfoHasIsLabelledByField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isLabelledBy");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isMatLabelled field")
    void elementInfoHasIsMatLabelledField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isMatLabelled");
        assertNotNull(f);
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo labelFollowingXpath defaults to empty string")
    void elementInfoLabelFollowingXpathDefault() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        assertEquals("", info.labelFollowingXpath,
            "labelFollowingXpath must default to empty string");
    }

    @Test
    @DisplayName("ElementInfo labelText defaults to empty string")
    void elementInfoLabelTextDefault() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        assertEquals("", info.labelText, "labelText must default to empty string");
    }

    // --- New fields added in 1.2.9 ----------------------------------------

    @Test
    @DisplayName("ElementInfo has alt field")
    void elementInfoHasAltField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("alt"));
    }

    @Test
    @DisplayName("ElementInfo has src field")
    void elementInfoHasSrcField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("src"));
    }

    @Test
    @DisplayName("ElementInfo has dataValue field")
    void elementInfoHasDataValueField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("dataValue"));
    }

    @Test
    @DisplayName("ElementInfo has isImageButton field")
    void elementInfoHasIsImageButtonField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isImageButton");
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has isTableHeader field")
    void elementInfoHasIsTableHeaderField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isTableHeader");
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has tableColumnCellsXpath field")
    void elementInfoHasTableColumnCellsXpathField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("tableColumnCellsXpath"));
    }

    @Test
    @DisplayName("ElementInfo has tableColumnDynamicTemplate field")
    void elementInfoHasTableColumnDynamicTemplateField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("tableColumnDynamicTemplate"));
    }

    @Test
    @DisplayName("ElementInfo.toString includes IMAGE-BUTTON when isImageButton=true")
    void elementInfoToStringIncludesImageButton() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.isImageButton = true;
        assertTrue(info.toString().contains("IMAGE-BUTTON"));
    }

    @Test
    @DisplayName("ElementInfo.toString includes TABLE-HEADER when isTableHeader=true")
    void elementInfoToStringIncludesTableHeader() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.isTableHeader = true;
        info.tableColumnText = "Status";
        assertTrue(info.toString().contains("TABLE-HEADER=Status"));
    }

    @Test
    @DisplayName("ElementInfo.toString includes label when labelText is set")
    void elementInfoToStringIncludesLabel() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.labelText = "Borough";
        assertTrue(info.toString().contains("label='Borough'"));
    }

    // --- Map widget support (new) ------------------------------------------

    @Test
    @DisplayName("ElementInfo has isMapWidget field")
    void elementInfoHasIsMapWidgetField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("isMapWidget");
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has mapProvider field")
    void elementInfoHasMapProviderField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("mapProvider"));
    }

    @Test
    @DisplayName("ElementInfo isMapWidget defaults to false")
    void elementInfoIsMapWidgetDefault() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        assertFalse(info.isMapWidget, "isMapWidget must default to false");
    }

    @Test
    @DisplayName("ElementInfo.toString includes MAP-WIDGET when isMapWidget=true")
    void elementInfoToStringIncludesMapWidget() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.isMapWidget = true;
        info.mapProvider = "Google Maps";
        assertTrue(info.toString().contains("MAP-WIDGET=Google Maps"));
    }

    @Test
    @DisplayName("waitForDomStable(WebDriver, Duration, long) is public static")
    void waitForDomStableIsPublicStatic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("waitForDomStable",
                org.openqa.selenium.WebDriver.class, java.time.Duration.class, long.class);
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), "waitForDomStable must be public");
        assertTrue(Modifier.isStatic(mods), "waitForDomStable must be static");
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    @DisplayName("waitForDomStable returns false gracefully on unusable driver")
    void waitForDomStableReturnsFalseOnError() {
        // A mocked WebDriver that is not a JavascriptExecutor should be handled
        // without throwing -- verifies the bounded, non-throwing contract.
        org.openqa.selenium.WebDriver plainDriver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class);
        boolean result = ElementCrawler.waitForDomStable(plainDriver, java.time.Duration.ofMillis(200), 50);
        assertFalse(result, "waitForDomStable must return false (not throw) when driver isn't a JavascriptExecutor");
    }

    @Test
    @DisplayName("waitForNetworkIdle(WebDriver, Duration, long) is public static")
    void waitForNetworkIdleIsPublicStatic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("waitForNetworkIdle",
                org.openqa.selenium.WebDriver.class, java.time.Duration.class, long.class);
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), "waitForNetworkIdle must be public");
        assertTrue(Modifier.isStatic(mods), "waitForNetworkIdle must be static");
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    @DisplayName("waitForNetworkIdle returns false gracefully on unusable driver")
    void waitForNetworkIdleReturnsFalseOnError() {
        // A mocked WebDriver that is not a JavascriptExecutor should be handled
        // without throwing -- verifies the bounded, non-throwing contract.
        org.openqa.selenium.WebDriver plainDriver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class);
        boolean result = ElementCrawler.waitForNetworkIdle(plainDriver, java.time.Duration.ofMillis(200), 50);
        assertFalse(result, "waitForNetworkIdle must return false (not throw) when driver isn't a JavascriptExecutor");
    }

    @Test
    @DisplayName("crawlSubtree retries once on a transient StaleElementReferenceException then succeeds")
    void crawlSubtreeRetriesOnStaleElement() {
        org.openqa.selenium.WebElement root = org.mockito.Mockito.mock(org.openqa.selenium.WebElement.class);
        org.mockito.Mockito.when(root.findElements(org.mockito.ArgumentMatchers.any(org.openqa.selenium.By.class)))
                .thenThrow(new org.openqa.selenium.StaleElementReferenceException("stale"))
                .thenReturn(java.util.Collections.emptyList());

        ElementCrawler crawler = new ElementCrawler(org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class));
        java.util.List<ElementCrawler.ElementInfo> result = crawler.crawlSubtree(root);

        assertNotNull(result, "crawlSubtree must return (not throw) after retrying a transient stale element");
    }

    // --- Shadow DOM support (new) -------------------------------------------

    @Test
    @DisplayName("ElementInfo has inShadowDom field")
    void elementInfoHasInShadowDomField() throws NoSuchFieldException {
        java.lang.reflect.Field f = ElementCrawler.ElementInfo.class.getField("inShadowDom");
        assertEquals(boolean.class, f.getType());
    }

    @Test
    @DisplayName("ElementInfo has shadowHostXpath field")
    void elementInfoHasShadowHostXpathField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("shadowHostXpath"));
    }

    @Test
    @DisplayName("ElementInfo has shadowRelativeCss field")
    void elementInfoHasShadowRelativeCssField() throws NoSuchFieldException {
        assertNotNull(ElementCrawler.ElementInfo.class.getField("shadowRelativeCss"));
    }

    @Test
    @DisplayName("ElementInfo inShadowDom defaults to false")
    void elementInfoInShadowDomDefault() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        assertFalse(info.inShadowDom, "inShadowDom must default to false");
    }

    @Test
    @DisplayName("ElementInfo.toString includes SHADOW-DOM when inShadowDom=true")
    void elementInfoToStringIncludesShadowDom() {
        ElementCrawler.ElementInfo info = new ElementCrawler.ElementInfo();
        info.inShadowDom = true;
        info.shadowHostXpath = "//my-component[@id='host']";
        info.shadowRelativeCss = "button#submit";
        String s = info.toString();
        assertTrue(s.contains("SHADOW-DOM"));
        assertTrue(s.contains("//my-component[@id='host']"));
        assertTrue(s.contains("button#submit"));
    }

    // --- safeClick (new) -----------------------------------------------------

    @Test
    @DisplayName("safeClick(WebDriver, WebElement) is public static")
    void safeClickIsPublicStatic() throws NoSuchMethodException {
        Method m = ElementCrawler.class.getMethod("safeClick",
                org.openqa.selenium.WebDriver.class, org.openqa.selenium.WebElement.class);
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), "safeClick must be public");
        assertTrue(Modifier.isStatic(mods), "safeClick must be static");
        assertEquals(void.class, m.getReturnType());
    }

    @Test
    @DisplayName("safeClick scrolls into view and performs a plain click when uninterrupted")
    void safeClickPerformsPlainClickWhenUninterrupted() {
        org.openqa.selenium.remote.RemoteWebDriver driver =
                org.mockito.Mockito.mock(org.openqa.selenium.remote.RemoteWebDriver.class,
                        org.mockito.Mockito.withSettings()
                                .extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.openqa.selenium.WebElement element = org.mockito.Mockito.mock(org.openqa.selenium.WebElement.class);

        ElementCrawler.safeClick(driver, element);

        org.mockito.Mockito.verify((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript(org.mockito.ArgumentMatchers.contains("scrollIntoView"), org.mockito.ArgumentMatchers.eq(element));
        org.mockito.Mockito.verify(element).click();
        // No JS-click fallback should be invoked when the plain click succeeds.
        org.mockito.Mockito.verify((org.openqa.selenium.JavascriptExecutor) driver, org.mockito.Mockito.never())
                .executeScript(org.mockito.ArgumentMatchers.eq("arguments[0].click();"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("safeClick falls back to a JS click only on ElementClickInterceptedException")
    void safeClickFallsBackToJsClickOnIntercepted() {
        org.openqa.selenium.remote.RemoteWebDriver driver =
                org.mockito.Mockito.mock(org.openqa.selenium.remote.RemoteWebDriver.class,
                        org.mockito.Mockito.withSettings()
                                .extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.openqa.selenium.WebElement element = org.mockito.Mockito.mock(org.openqa.selenium.WebElement.class);
        org.mockito.Mockito.doThrow(new org.openqa.selenium.ElementClickInterceptedException("intercepted"))
                .when(element).click();

        ElementCrawler.safeClick(driver, element);

        org.mockito.Mockito.verify(element).click();
        org.mockito.Mockito.verify((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript(org.mockito.ArgumentMatchers.eq("arguments[0].click();"), org.mockito.ArgumentMatchers.eq(element));
    }

    @Test
    @DisplayName("safeClick propagates exceptions other than ElementClickInterceptedException")
    void safeClickPropagatesOtherExceptions() {
        org.openqa.selenium.remote.RemoteWebDriver driver =
                org.mockito.Mockito.mock(org.openqa.selenium.remote.RemoteWebDriver.class,
                        org.mockito.Mockito.withSettings()
                                .extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.openqa.selenium.WebElement element = org.mockito.Mockito.mock(org.openqa.selenium.WebElement.class);
        org.mockito.Mockito.doThrow(new org.openqa.selenium.StaleElementReferenceException("stale"))
                .when(element).click();

        assertThrows(org.openqa.selenium.StaleElementReferenceException.class,
                () -> ElementCrawler.safeClick(driver, element));
    }
}
