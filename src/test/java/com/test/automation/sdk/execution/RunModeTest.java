package com.test.automation.sdk.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunModeTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("run.mode");
        System.clearProperty("mobile.execution.target");
        System.clearProperty("testInBrowserstack");
    }

    @Test
    void resolve_defaultsToLocal_whenNothingSet() {
        assertEquals(RunMode.LOCAL, RunMode.resolve());
    }

    @Test
    void resolve_usesCanonicalRunModeProperty_whenSetToBrowserstack() {
        System.setProperty("run.mode", "BROWSERSTACK");
        assertEquals(RunMode.BROWSERSTACK, RunMode.resolve());
    }

    @Test
    void resolve_usesCanonicalRunModeProperty_whenSet() {
        System.setProperty("run.mode", "local");
        assertEquals(RunMode.LOCAL, RunMode.resolve());
    }

    @Test
    void resolve_fallsBackToLegacyMobileExecutionTarget_whenCanonicalUnset() {
        System.setProperty("mobile.execution.target", "LOCAL");
        assertEquals(RunMode.LOCAL, RunMode.resolve());
    }

    @Test
    void resolve_fallsBackToLegacyTestInBrowserstackFlag_whenNothingElseSet() {
        System.setProperty("testInBrowserstack", "false");
        assertEquals(RunMode.LOCAL, RunMode.resolve());

        System.setProperty("testInBrowserstack", "true");
        assertEquals(RunMode.BROWSERSTACK, RunMode.resolve());
    }

    @Test
    void resolve_canonicalPropertyTakesPrecedenceOverLegacyOnes() {
        System.setProperty("run.mode", "BROWSERSTACK");
        System.setProperty("mobile.execution.target", "LOCAL");
        System.setProperty("testInBrowserstack", "false");
        assertEquals(RunMode.BROWSERSTACK, RunMode.resolve());
    }

    @Test
    void resolve_throwsOnInvalidCanonicalValue() {
        System.setProperty("run.mode", "not-a-real-mode");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, RunMode::resolve);
        assertTrue(ex.getMessage().contains("run.mode"));
        assertTrue(ex.getMessage().contains("not-a-real-mode"));
    }

    @Test
    void resolve_throwsOnInvalidLegacyMobileExecutionTargetValue() {
        System.setProperty("mobile.execution.target", "not-a-real-mode");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, RunMode::resolve);
        assertTrue(ex.getMessage().contains("mobile.execution.target"));
        assertTrue(ex.getMessage().contains("not-a-real-mode"));
    }
}
