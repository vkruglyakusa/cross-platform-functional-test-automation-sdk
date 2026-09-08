package com.test.automation.sdk.driver.web;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebBrowserStackSessionFactoryTest {

    @Test
    void reportsWebPlatformAndBrowserstackRunMode() {
        WebBrowserStackSessionFactory factory = new WebBrowserStackSessionFactory();
        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.BROWSERSTACK, factory.getRunMode());
    }

    @Test
    void isRemote() {
        assertTrue(new WebBrowserStackSessionFactory().isRemote());
    }

    @Test
    void createDriver_failsFast_whenBrowserStackSdkNotActive() {
        // No BrowserStack javaagent is attached in unit tests, so the pre-flight
        // verification must fail before any real browser is ever launched.
        WebBrowserStackSessionFactory factory = new WebBrowserStackSessionFactory();
        ExecutionContext context = ExecutionContext.forWeb("chrome", RunMode.BROWSERSTACK);
        assertThrows(IllegalStateException.class, () -> factory.createDriver(context));
    }
}
