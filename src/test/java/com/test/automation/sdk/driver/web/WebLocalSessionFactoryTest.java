package com.test.automation.sdk.driver.web;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebLocalSessionFactoryTest {

    @Test
    void reportsWebPlatformAndLocalRunMode() {
        WebLocalSessionFactory factory = new WebLocalSessionFactory();
        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
    }

    @Test
    void isNotRemote() {
        assertFalse(new WebLocalSessionFactory().isRemote());
    }
}
