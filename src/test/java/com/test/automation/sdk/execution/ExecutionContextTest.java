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
}
