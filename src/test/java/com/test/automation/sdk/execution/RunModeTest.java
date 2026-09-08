package com.test.automation.sdk.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunModeTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("run.mode");
        System.clearProperty("mobile.execution.target");
        System.clearProperty("testInBrowserstack");
    }

    @Test
    void resolve_defaultsToBrowserstack_whenNothingSet() {
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
        assertThrows(IllegalArgumentException.class, RunMode::resolve);
    }
}
