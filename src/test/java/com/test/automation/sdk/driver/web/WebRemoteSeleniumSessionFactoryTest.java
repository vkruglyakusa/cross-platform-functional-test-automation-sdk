package com.test.automation.sdk.driver.web;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRemoteSeleniumSessionFactoryTest {

    @Test
    void exposesCanonicalCustomRemoteMetadata() {
        WebRemoteSeleniumSessionFactory factory = new WebRemoteSeleniumSessionFactory();

        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals("custom", factory.getProviderId().value());
        assertTrue(factory.isRemote());
    }
}
