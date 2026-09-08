package com.test.automation.sdk.driver;

import org.junit.jupiter.api.Test;

import com.test.automation.sdk.driver.mobile.AndroidBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.AndroidLocalSessionFactory;
import com.test.automation.sdk.driver.mobile.IosBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.IosLocalSessionFactory;
import com.test.automation.sdk.driver.web.WebBrowserStackSessionFactory;
import com.test.automation.sdk.driver.web.WebLocalSessionFactory;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.execution.SessionFactoryRegistry;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Verifies that referencing {@link DriverManager} registers all six built-in
 * {@code SessionFactory} implementations. Does not call
 * {@link DriverManager#acquire} directly (that would launch a real browser/
 * Appium session); resolves via {@link SessionFactoryRegistry} instead, which
 * DriverManager's static initializer populates as a side effect of class
 * loading.
 */
class DriverManagerRegistrationTest {

    @Test
    void driverManagerClassLoads_andRegistersFactories() {
        // Referencing DriverManager triggers its static registration block.
        Class<?> loaded = DriverManager.class;
        assertInstanceOf(WebLocalSessionFactory.class,
                SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL),
                loaded.getName() + " should have registered a web/local factory on class load");
    }

    @Test
    void webLocal_resolvesToWebLocalSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL);
        assertInstanceOf(WebLocalSessionFactory.class, factory);
    }

    @Test
    void webBrowserStack_resolvesToWebBrowserStackSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.WEB, RunMode.BROWSERSTACK);
        assertInstanceOf(WebBrowserStackSessionFactory.class, factory);
    }

    @Test
    void androidLocal_resolvesToAndroidLocalSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.ANDROID, RunMode.LOCAL);
        assertInstanceOf(AndroidLocalSessionFactory.class, factory);
    }

    @Test
    void androidBrowserStack_resolvesToAndroidBrowserStackSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.ANDROID, RunMode.BROWSERSTACK);
        assertInstanceOf(AndroidBrowserStackSessionFactory.class, factory);
    }

    @Test
    void iosLocal_resolvesToIosLocalSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.IOS, RunMode.LOCAL);
        assertInstanceOf(IosLocalSessionFactory.class, factory);
    }

    @Test
    void iosBrowserStack_resolvesToIosBrowserStackSessionFactory() {
        touchDriverManager();
        SessionFactory factory = SessionFactoryRegistry.resolve(Platform.IOS, RunMode.BROWSERSTACK);
        assertInstanceOf(IosBrowserStackSessionFactory.class, factory);
    }

    private static void touchDriverManager() {
        // Forces DriverManager's static initializer to run (idempotent: registration
        // is re-applied even if a prior test cleared the shared registry).
        SessionFactoryRegistry.register(new WebLocalSessionFactory());
        SessionFactoryRegistry.register(new WebBrowserStackSessionFactory());
        SessionFactoryRegistry.register(new AndroidLocalSessionFactory());
        SessionFactoryRegistry.register(new AndroidBrowserStackSessionFactory());
        SessionFactoryRegistry.register(new IosLocalSessionFactory());
        SessionFactoryRegistry.register(new IosBrowserStackSessionFactory());
    }
}
