package com.test.automation.sdk.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionContextTest {

    @Test
    void forWeb_setsPlatformAndBrowserName() {
        ExecutionContext ctx = ExecutionContext.forWeb("chrome", RunMode.LOCAL);
        assertEquals(Platform.WEB, ctx.getPlatform());
        assertEquals(RunMode.LOCAL, ctx.getRunMode());
        assertEquals("chrome", ctx.getBrowserName());
        assertEquals("", ctx.getDeviceName());
    }

    @Test
    void forWeb_rejectsBlankBrowserName() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionContext.forWeb("  ", RunMode.LOCAL));
    }

    @Test
    void forMobile_setsPlatformAndDeviceName() {
        ExecutionContext ctx = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6_API_34", RunMode.LOCAL);
        assertEquals(Platform.ANDROID, ctx.getPlatform());
        assertEquals(RunMode.LOCAL, ctx.getRunMode());
        assertEquals("Pixel_6_API_34", ctx.getDeviceName());
        assertEquals("", ctx.getBrowserName());
    }

    @Test
    void forMobile_rejectsWebPlatform() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionContext.forMobile(Platform.WEB, "device", RunMode.LOCAL));
    }

    @Test
    void forMobile_allowsNullDeviceName() {
        ExecutionContext ctx = ExecutionContext.forMobile(Platform.IOS, null, RunMode.BROWSERSTACK);
        assertEquals("", ctx.getDeviceName());
    }

    @Test
    void nullRunMode_resolvesViaRunModeResolve() {
        System.clearProperty("run.mode");
        System.clearProperty("mobile.execution.target");
        System.clearProperty("testInBrowserstack");
        ExecutionContext ctx = ExecutionContext.forWeb("chrome", null);
        assertEquals(RunMode.resolve(), ctx.getRunMode());
    }

    @Test
    void toString_containsKeyFields() {
        ExecutionContext ctx = ExecutionContext.forWeb("firefox", RunMode.BROWSERSTACK);
        String s = ctx.toString();
        assertTrue(s.contains("WEB") && s.contains("BROWSERSTACK") && s.contains("firefox"));
    }

    /*
     * Priority 5 (Unified SDK Implementation Review, section 11):
     * AutomationTechnology is reserved as a third resolution dimension.
     * Existing 2-arg forWeb/forMobile callers must keep resolving to the
     * platform-implied default technology unchanged.
     */

    @Test
    void forWeb_twoArgOverload_defaultsToSelenium() {
        ExecutionContext ctx = ExecutionContext.forWeb("chrome", RunMode.LOCAL);
        assertEquals(AutomationTechnology.SELENIUM, ctx.getAutomationTechnology());
    }

    @Test
    void forMobile_twoArgOverload_defaultsToAppium() {
        ExecutionContext android = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6", RunMode.LOCAL);
        ExecutionContext ios = ExecutionContext.forMobile(Platform.IOS, "iPhone_15", RunMode.LOCAL);
        assertEquals(AutomationTechnology.APPIUM, android.getAutomationTechnology());
        assertEquals(AutomationTechnology.APPIUM, ios.getAutomationTechnology());
    }

    @Test
    void forWeb_explicitNullTechnology_defaultsToSelenium() {
        ExecutionContext ctx = ExecutionContext.forWeb("chrome", RunMode.LOCAL, null);
        assertEquals(AutomationTechnology.SELENIUM, ctx.getAutomationTechnology());
    }

    @Test
    void forWeb_explicitTechnology_isHonored() {
        ExecutionContext ctx = ExecutionContext.forWeb("chrome", RunMode.LOCAL, AutomationTechnology.SELENIUM);
        assertEquals(AutomationTechnology.SELENIUM, ctx.getAutomationTechnology());
    }

    @Test
    void forMobile_explicitTechnology_isHonored() {
        ExecutionContext ctx = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6", RunMode.LOCAL,
                AutomationTechnology.APPIUM);
        assertEquals(AutomationTechnology.APPIUM, ctx.getAutomationTechnology());
    }

    @Test
    void remoteContext_requiresAndRetainsProvider() {
        ProviderId provider = new ProviderId("BrowserStack");
        ExecutionContext ctx = ExecutionContext.forWebWithProvider("chrome", RunMode.REMOTE, provider);

        assertEquals(new ProviderId("browserstack"), ctx.getProviderId());
        assertEquals(RunMode.REMOTE, ctx.getRunMode());
    }

    @Test
    void remoteContext_rejectsMissingProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionContext.forWeb("chrome", RunMode.REMOTE));
    }

    @Test
    void localContext_rejectsProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionContext.forWebWithProvider("chrome", RunMode.LOCAL, new ProviderId("custom")));
    }

    @Test
    void legacyProviderModes_remainUsableWithoutProviderDuringMigration() {
        ExecutionContext browserStack = ExecutionContext.forWeb("chrome", RunMode.BROWSERSTACK);
        ExecutionContext remoteAppium = ExecutionContext.forMobile(
                Platform.ANDROID, "Pixel_6", RunMode.REMOTE_APPIUM);

        assertEquals(null, browserStack.getProviderId());
        assertEquals(null, remoteAppium.getProviderId());
    }
}
