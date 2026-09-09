package com.test.automation.sdk.mobile.actions;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.remote.SupportsContextSwitching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link MobileActions}'s NATIVE_APP/WEBVIEW context-switching helpers
 * -- promoted from the proven pattern in {@code MobileElementCrawler.crawlWebViewsIfPresent()}
 * per docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md.
 *
 * Mocks {@link AppiumDriver} with the {@link SupportsContextSwitching} extra interface
 * (mirrors {@code ElementCrawlerTest}'s {@code JavascriptExecutor} extra-interface pattern) --
 * no real Appium session is ever launched.
 */
@DisplayName("MobileActions - context switching")
class MobileActionsTest {

    private AppiumDriver mockContextSwitchingDriver() {
        return mock(AppiumDriver.class, withSettings().extraInterfaces(SupportsContextSwitching.class));
    }

    @Test
    @DisplayName("getAvailableContexts returns the driver's reported context handles")
    void getAvailableContextsReturnsHandles() {
        AppiumDriver driver = mockContextSwitchingDriver();
        Set<String> contexts = new LinkedHashSet<>();
        contexts.add("NATIVE_APP");
        contexts.add("WEBVIEW_com.example.app");
        when(((SupportsContextSwitching) driver).getContextHandles()).thenReturn(contexts);

        assertEquals(contexts, MobileActions.getAvailableContexts(driver));
    }

    @Test
    @DisplayName("getAvailableContexts throws when the driver does not support context switching")
    void getAvailableContextsThrowsWhenUnsupported() {
        AppiumDriver plainDriver = mock(AppiumDriver.class);
        assertThrows(IllegalStateException.class, () -> MobileActions.getAvailableContexts(plainDriver));
    }

    @Test
    @DisplayName("getCurrentContext returns the driver's current context")
    void getCurrentContextReturnsCurrent() {
        AppiumDriver driver = mockContextSwitchingDriver();
        when(((SupportsContextSwitching) driver).getContext()).thenReturn("WEBVIEW_com.example.app");

        assertEquals("WEBVIEW_com.example.app", MobileActions.getCurrentContext(driver));
    }

    @Test
    @DisplayName("isInWebViewContext is true only when current context starts with WEBVIEW")
    void isInWebViewContextChecksPrefix() {
        AppiumDriver driver = mockContextSwitchingDriver();
        when(((SupportsContextSwitching) driver).getContext()).thenReturn("NATIVE_APP");
        assertFalse(MobileActions.isInWebViewContext(driver));

        when(((SupportsContextSwitching) driver).getContext()).thenReturn("WEBVIEW_com.example.app");
        assertTrue(MobileActions.isInWebViewContext(driver));
    }

    @Test
    @DisplayName("switchToNativeContext switches the driver to NATIVE_APP")
    void switchToNativeContextSwitchesToNativeApp() {
        AppiumDriver driver = mockContextSwitchingDriver();
        MobileActions.switchToNativeContext(driver);
        verify((SupportsContextSwitching) driver).context("NATIVE_APP");
    }

    @Test
    @DisplayName("switchToContext switches the driver to the exact context name given")
    void switchToContextSwitchesToExactName() {
        AppiumDriver driver = mockContextSwitchingDriver();
        MobileActions.switchToContext(driver, "WEBVIEW_com.example.app");
        verify((SupportsContextSwitching) driver).context("WEBVIEW_com.example.app");
    }

    @Test
    @DisplayName("switchToWebViewContext() switches to the first available WEBVIEW context")
    void switchToWebViewContextSwitchesToFirstWebview() {
        AppiumDriver driver = mockContextSwitchingDriver();
        Set<String> contexts = new LinkedHashSet<>();
        contexts.add("NATIVE_APP");
        contexts.add("WEBVIEW_com.example.app");
        when(((SupportsContextSwitching) driver).getContextHandles()).thenReturn(contexts);

        MobileActions.switchToWebViewContext(driver);

        verify((SupportsContextSwitching) driver).context("WEBVIEW_com.example.app");
    }

    @Test
    @DisplayName("switchToWebViewContext() throws when no WEBVIEW context is available")
    void switchToWebViewContextThrowsWhenNoneAvailable() {
        AppiumDriver driver = mockContextSwitchingDriver();
        Set<String> contexts = new LinkedHashSet<>();
        contexts.add("NATIVE_APP");
        when(((SupportsContextSwitching) driver).getContextHandles()).thenReturn(contexts);

        assertThrows(IllegalStateException.class, () -> MobileActions.switchToWebViewContext(driver));
    }

    @Test
    @DisplayName("switchToWebViewContext(nameContains) switches to the matching WEBVIEW context")
    void switchToWebViewContextByNameSwitchesToMatch() {
        AppiumDriver driver = mockContextSwitchingDriver();
        Set<String> contexts = new LinkedHashSet<>();
        contexts.add("NATIVE_APP");
        contexts.add("WEBVIEW_com.example.app");
        contexts.add("WEBVIEW_com.example.other");
        when(((SupportsContextSwitching) driver).getContextHandles()).thenReturn(contexts);

        MobileActions.switchToWebViewContext(driver, "other");

        verify((SupportsContextSwitching) driver).context("WEBVIEW_com.example.other");
    }

    @Test
    @DisplayName("switchToWebViewContext(nameContains) throws when no context matches")
    void switchToWebViewContextByNameThrowsWhenNoMatch() {
        AppiumDriver driver = mockContextSwitchingDriver();
        Set<String> contexts = new LinkedHashSet<>();
        contexts.add("NATIVE_APP");
        contexts.add("WEBVIEW_com.example.app");
        when(((SupportsContextSwitching) driver).getContextHandles()).thenReturn(contexts);

        assertThrows(IllegalStateException.class, () -> MobileActions.switchToWebViewContext(driver, "nonexistent"));
    }
}
