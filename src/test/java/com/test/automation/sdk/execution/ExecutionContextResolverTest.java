package com.test.automation.sdk.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionContextResolverTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("run.mode");
        System.clearProperty("execution.provider");
        System.clearProperty("mobile.execution.target");
        System.clearProperty("testInBrowserstack");
    }

    @Test
    void localWebContextNeedsNoProvider() {
        System.setProperty("run.mode", "LOCAL");

        ExecutionContext context = ExecutionContextResolver.forWeb("chrome");

        assertEquals(RunMode.LOCAL, context.getRunMode());
        assertNull(context.getProviderId());
    }

    @Test
    void canonicalRemoteWebContextUsesConfiguredProvider() {
        System.setProperty("run.mode", "REMOTE");
        System.setProperty("execution.provider", "Custom");

        ExecutionContext context = ExecutionContextResolver.forWeb("firefox");

        assertEquals(RunMode.REMOTE, context.getRunMode());
        assertEquals(new ProviderId("custom"), context.getProviderId());
    }

    @Test
    void canonicalRemoteRequiresProvider() {
        System.setProperty("run.mode", "REMOTE");

        assertThrows(IllegalStateException.class,
                () -> ExecutionContextResolver.forWeb("chrome"));
    }

    @Test
    void legacyBrowserStackRemainsCompatibleWithoutNewProperty() {
        System.setProperty("run.mode", "BROWSERSTACK");

        ExecutionContext context = ExecutionContextResolver.forMobile(Platform.ANDROID, "Pixel 8");

        assertEquals(RunMode.BROWSERSTACK, context.getRunMode());
        assertNull(context.getProviderId());
    }

    @Test
    void detectsCanonicalAndLegacyBrowserStack() {
        System.setProperty("run.mode", "REMOTE");
        System.setProperty("execution.provider", "browserstack");
        assertEquals(true, ExecutionContextResolver.isBrowserStackConfigured());

        System.setProperty("run.mode", "BROWSERSTACK");
        System.clearProperty("execution.provider");
        assertEquals(true, ExecutionContextResolver.isBrowserStackConfigured());
    }
}
