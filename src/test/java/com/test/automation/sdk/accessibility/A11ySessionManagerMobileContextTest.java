package com.test.automation.sdk.accessibility;

import io.appium.java_client.remote.SupportsContextSwitching;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the mobile native-context guard in {@link A11ySessionManager#shouldScan}.
 *
 * <p>Native Android/iOS screens (Appium {@code NATIVE_APP} context) have no DOM, so
 * axe-core's {@code JavascriptExecutor}-based injection has nothing to scan. Before this
 * guard, enabling {@code accessibility.checking.enabled} for a mobile suite would let
 * {@link AccessibilityChecker} throw on every native-screen test. These tests confirm the
 * guard skips native contexts, does not affect WebView contexts, and does not affect
 * plain (non-Appium) web drivers.</p>
 */
@DisplayName("A11ySessionManager - mobile native-context guard")
class A11ySessionManagerMobileContextTest {

    @BeforeEach
    void enableScanning() {
        System.setProperty("accessibility.checking.enabled", "true");
        A11ySessionManager.resetSession();
    }

    @AfterEach
    void tearDown() {
        A11ySessionManager.resetSession();
        System.clearProperty("accessibility.checking.enabled");
    }

    /** Combined stub type: an Appium-style driver that also supports context switching. */
    private interface MobileDriverStub extends WebDriver, SupportsContextSwitching {
    }

    @Test
    @DisplayName("skips scan when mobile driver is in a native (non-WebView) context")
    void skipsNativeContext() {
        MobileDriverStub driver = mock(MobileDriverStub.class);
        when(driver.getContext()).thenReturn("NATIVE_APP");

        A11ySessionManager.ScanDecision decision = A11ySessionManager.shouldScan(driver);

        assertFalse(decision.shouldScan, "native context must not be scanned");
        assertTrue(decision.reason.contains("native"),
                "skip reason should explain the native-context guard: " + decision.reason);
    }

    @Test
    @DisplayName("skips scan when mobile driver context cannot be determined")
    void skipsWhenContextLookupThrows() {
        MobileDriverStub driver = mock(MobileDriverStub.class);
        when(driver.getContext()).thenThrow(new RuntimeException("context lookup failed"));

        A11ySessionManager.ScanDecision decision = A11ySessionManager.shouldScan(driver);

        assertFalse(decision.shouldScan, "undeterminable context must be treated conservatively as native");
    }

    @Test
    @DisplayName("does not skip on the native-context guard when mobile driver is in a WEBVIEW context")
    void doesNotSkipWebViewContext() {
        MobileDriverStub driver = mock(MobileDriverStub.class);
        when(driver.getContext()).thenReturn("WEBVIEW_com.example.app");
        when(driver.getCurrentUrl()).thenReturn("https://example.com/hybrid");

        A11ySessionManager.ScanDecision decision = A11ySessionManager.shouldScan(driver);

        assertTrue(decision.shouldScan, "WebView context has a real DOM and should be scanned: " + decision.reason);
    }

    @Test
    @DisplayName("plain (non-Appium) WebDriver is unaffected by the mobile native-context guard")
    void plainWebDriverUnaffected() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getCurrentUrl()).thenReturn("https://example.com/page");

        A11ySessionManager.ScanDecision decision = A11ySessionManager.shouldScan(driver);

        assertTrue(decision.shouldScan, "desktop WebDriver should not be treated as a native mobile context: "
                + decision.reason);
    }
}
