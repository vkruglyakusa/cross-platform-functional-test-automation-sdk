package com.test.automation.sdk.driver.mobile;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileSessionFactoryMetadataTest {

    @Test
    void androidLocal_reportsCorrectMetadata() {
        AndroidLocalSessionFactory factory = new AndroidLocalSessionFactory();
        assertEquals(Platform.ANDROID, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
        assertFalse(factory.isRemote());
    }

    @Test
    void androidBrowserStack_reportsCorrectMetadata() {
        AndroidBrowserStackSessionFactory factory = new AndroidBrowserStackSessionFactory();
        assertEquals(Platform.ANDROID, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals("browserstack", factory.getProviderId().value());
        assertTrue(factory.isRemote());
    }

    @Test
    void iosLocal_reportsCorrectMetadata() {
        IosLocalSessionFactory factory = new IosLocalSessionFactory();
        assertEquals(Platform.IOS, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
        assertFalse(factory.isRemote());
    }

    @Test
    void iosBrowserStack_reportsCorrectMetadata() {
        IosBrowserStackSessionFactory factory = new IosBrowserStackSessionFactory();
        assertEquals(Platform.IOS, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals("browserstack", factory.getProviderId().value());
        assertTrue(factory.isRemote());
    }
}
