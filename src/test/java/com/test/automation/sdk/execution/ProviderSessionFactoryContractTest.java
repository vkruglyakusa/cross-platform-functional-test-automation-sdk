package com.test.automation.sdk.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.driver.mobile.AndroidBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.AndroidLocalSessionFactory;
import com.test.automation.sdk.driver.mobile.AndroidRemoteAppiumSessionFactory;
import com.test.automation.sdk.driver.mobile.IosLocalSessionFactory;
import com.test.automation.sdk.driver.mobile.IosBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.IosRemoteAppiumSessionFactory;
import com.test.automation.sdk.driver.web.WebBrowserStackSessionFactory;
import com.test.automation.sdk.driver.web.WebLocalSessionFactory;
import com.test.automation.sdk.driver.web.WebRemoteSeleniumSessionFactory;

public class ProviderSessionFactoryContractTest {

    @Test
    public void testWebLocalSessionFactory() {
        WebLocalSessionFactory factory = new WebLocalSessionFactory();
        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
        assertEquals(AutomationTechnology.SELENIUM, factory.getAutomationTechnology());
        assertEquals(null, factory.getProviderId());
        assertEquals(false, factory.isRemote());
    }

    @Test
    public void testWebBrowserStackSessionFactory() {
        WebBrowserStackSessionFactory factory = new WebBrowserStackSessionFactory();
        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.SELENIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("browserstack"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }

    @Test
    public void testWebRemoteSeleniumSessionFactory() {
        WebRemoteSeleniumSessionFactory factory = new WebRemoteSeleniumSessionFactory();
        assertEquals(Platform.WEB, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.SELENIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("custom"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }

    @Test
    public void testAndroidLocalSessionFactory() {
        AndroidLocalSessionFactory factory = new AndroidLocalSessionFactory();
        assertEquals(Platform.ANDROID, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(null, factory.getProviderId());
        assertEquals(false, factory.isRemote());
    }

    @Test
    public void testAndroidBrowserStackSessionFactory() {
        AndroidBrowserStackSessionFactory factory = new AndroidBrowserStackSessionFactory();
        assertEquals(Platform.ANDROID, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("browserstack"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }

    @Test
    public void testAndroidRemoteAppiumSessionFactory() {
        AndroidRemoteAppiumSessionFactory factory = new AndroidRemoteAppiumSessionFactory();
        assertEquals(Platform.ANDROID, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("custom"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }

    @Test
    public void testIosLocalSessionFactory() {
        IosLocalSessionFactory factory = new IosLocalSessionFactory();
        assertEquals(Platform.IOS, factory.getPlatform());
        assertEquals(RunMode.LOCAL, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(null, factory.getProviderId());
        assertEquals(false, factory.isRemote());
    }

    @Test
    public void testIosBrowserStackSessionFactory() {
        IosBrowserStackSessionFactory factory = new IosBrowserStackSessionFactory();
        assertEquals(Platform.IOS, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("browserstack"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }

    @Test
    public void testIosRemoteAppiumSessionFactory() {
        IosRemoteAppiumSessionFactory factory = new IosRemoteAppiumSessionFactory();
        assertEquals(Platform.IOS, factory.getPlatform());
        assertEquals(RunMode.REMOTE, factory.getRunMode());
        assertEquals(AutomationTechnology.APPIUM, factory.getAutomationTechnology());
        assertEquals(new ProviderId("custom"), factory.getProviderId());
        assertEquals(true, factory.isRemote());
    }
}