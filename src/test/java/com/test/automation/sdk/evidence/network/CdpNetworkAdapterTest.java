package com.test.automation.sdk.evidence.network;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.HasDevTools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the SDK v1.5.1 CDP-isolation seam: {@link CdpNetworkAdapter}
 * and {@link CdpNetworkAdapters}. These verify the adapter's runtime
 * browser-detection/rejection logic without requiring a live CDP session --
 * the actual request/response capture path is exercised via consumer smoke
 * tests against a real Chrome/Edge session (see v1.5.1 release notes for the
 * documented browser-support matrix).
 */
@DisplayName("CDP network adapter isolation seam")
class CdpNetworkAdapterTest {

    private interface CdpCapableDriver extends WebDriver, HasDevTools, HasCapabilities {
    }

    @Test
    @DisplayName("CdpNetworkAdapters.all() returns at least one registered adapter (currently cdp-v146)")
    void adaptersRegistered() {
        List<CdpNetworkAdapter> adapters = CdpNetworkAdapters.all();
        assertNotNull(adapters);
        assertFalse(adapters.isEmpty());
    }

    @Test
    @DisplayName("registered adapter declines a plain WebDriver mock (no HasDevTools)")
    void adapter_declinesNonCdpDriver() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        for (CdpNetworkAdapter adapter : CdpNetworkAdapters.all()) {
            assertFalse(adapter.supports(driver), adapter.name() + " should decline a non-HasDevTools driver");
        }
    }

    @Test
    @DisplayName("registered adapter declines a HasDevTools driver reporting a non-Chromium browser name")
    void adapter_declinesNonChromiumBrowser() {
        CdpCapableDriver driver = Mockito.mock(CdpCapableDriver.class);
        Capabilities caps = Mockito.mock(Capabilities.class);
        Mockito.when(driver.getCapabilities()).thenReturn(caps);
        Mockito.when(caps.getBrowserName()).thenReturn("firefox");

        for (CdpNetworkAdapter adapter : CdpNetworkAdapters.all()) {
            assertFalse(adapter.supports(driver), adapter.name() + " should decline a non-Chromium browser");
        }
    }

    @Test
    @DisplayName("registered adapter accepts a HasDevTools driver reporting a Chromium-based browser name")
    void adapter_acceptsChromiumBrowser() {
        CdpCapableDriver driver = Mockito.mock(CdpCapableDriver.class);
        Capabilities caps = Mockito.mock(Capabilities.class);
        Mockito.when(driver.getCapabilities()).thenReturn(caps);
        Mockito.when(caps.getBrowserName()).thenReturn("chrome");

        boolean anySupports = false;
        for (CdpNetworkAdapter adapter : CdpNetworkAdapters.all()) {
            anySupports = anySupports || adapter.supports(driver);
        }
        assertTrue(anySupports, "At least one registered adapter should accept a Chrome-reporting driver");
    }
}
