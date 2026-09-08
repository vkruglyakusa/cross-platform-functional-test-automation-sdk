package com.test.automation.sdk.accessibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link AxeCoreEngine} metadata (scan() itself requires a live WebDriver, covered indirectly via {@link AccessibilityChecker} tests). */
@DisplayName("AxeCoreEngine - AccessibilityEngine metadata")
class AxeCoreEngineTest {

    @Test
    @DisplayName("reports its engine name and version, and is usable as an AccessibilityEngine")
    void reportsEngineNameAndVersion() {
        AccessibilityEngine engine = new AxeCoreEngine();
        assertEquals("axe-core", engine.getEngineName());
        assertNotNull(engine.getEngineVersion());
        assertFalse(engine.getEngineVersion().isEmpty());
    }
}
