package com.test.automation.sdk.evidence;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link NetworkTraceRecorder} covering the parts of the
 * lifecycle that do not require a live Chrome DevTools Protocol session
 * (config gating, unsupported-driver fail-safety, and the detach lifecycle).
 * The actual CDP request/response capture path requires a real Chromium
 * session and is validated manually/via consumer smoke tests -- see the
 * v1.5.1 release notes for the documented browser-support matrix.
 */
@DisplayName("NetworkTraceRecorder -- config-gated, fail-safe CDP network trace lifecycle")
class NetworkTraceRecorderTest {

    @TempDir
    File tempDir;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("evidence.network.enabled");
    }

    @Test
    @DisplayName("attachIfEnabled() is a no-op when evidence.network.enabled=false (default)")
    void attachIfEnabled_defaultDisabled_isNoOp() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        assertDoesNotThrow(() -> NetworkTraceRecorder.attachIfEnabled(driver));
        assertNull(NetworkTraceRecorder.detachAndWrite(driver, tempDir, "test"),
                "No recorder should have been registered when the feature is disabled");
    }

    @Test
    @DisplayName("attachIfEnabled() fails safe when enabled but the driver does not support CDP (e.g. Firefox)")
    void attachIfEnabled_unsupportedDriver_isNoOp() {
        System.setProperty("evidence.network.enabled", "true");
        WebDriver driver = Mockito.mock(WebDriver.class); // plain mock -- not a HasDevTools implementation
        assertDoesNotThrow(() -> NetworkTraceRecorder.attachIfEnabled(driver));
        assertNull(NetworkTraceRecorder.detachAndWrite(driver, tempDir, "test"),
                "No recorder should have been registered for a non-CDP-capable driver");
    }

    @Test
    @DisplayName("detachQuietly() on a driver with no active recorder is a safe no-op")
    void detachQuietly_noActiveRecorder_isSafeNoOp() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        assertDoesNotThrow(() -> NetworkTraceRecorder.detachQuietly(driver));
    }

    @Test
    @DisplayName("detachAndWrite() on a driver with no active recorder returns null")
    void detachAndWrite_noActiveRecorder_returnsNull() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        assertNull(NetworkTraceRecorder.detachAndWrite(driver, tempDir, "test"));
    }
}
