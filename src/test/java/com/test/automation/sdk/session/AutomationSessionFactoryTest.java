package com.test.automation.sdk.session;

import io.appium.java_client.AppiumDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.driver.mobile.AndroidBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.AndroidLocalSessionFactory;
import com.test.automation.sdk.driver.mobile.IosBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.IosLocalSessionFactory;
import com.test.automation.sdk.driver.web.WebBrowserStackSessionFactory;
import com.test.automation.sdk.driver.web.WebLocalSessionFactory;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.execution.SessionFactoryRegistry;
import com.test.automation.sdk.session.internal.AppiumSession;
import com.test.automation.sdk.session.internal.SeleniumSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Verifies {@link AutomationSessionFactory} delegates to the existing single
 * resolution mechanism ({@code driver.DriverManager} /
 * {@code execution.SessionFactoryRegistry}) and wraps the result in the
 * correct {@link AutomationSession} implementation. Registers fake
 * {@link SessionFactory}s (same pattern as
 * {@code execution.SessionFactoryRegistryTest} /
 * {@code driver.DriverManagerRegistrationTest}) so no real browser/Appium
 * session is ever launched, then restores the real built-in factories so
 * later tests relying on {@code DriverManager}'s registrations are
 * unaffected.
 */
class AutomationSessionFactoryTest {

    @BeforeEach
    void ensureDriverManagerAlreadyInitialized() throws ClassNotFoundException {
        // Forces DriverManager's static initializer (which registers the real
        // built-in factories) to run BEFORE this test registers its fakes, so a
        // fake registered below is never silently overwritten by DriverManager's
        // one-time class-initialization side effect if this happens to be the
        // first test in the whole suite to reference DriverManager.
        Class.forName("com.test.automation.sdk.driver.DriverManager");
    }

    @AfterEach
    void restoreRealFactories() {
        SessionFactoryRegistry.registerOrReplace(new WebLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new WebBrowserStackSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new AndroidLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new AndroidBrowserStackSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new IosLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new IosBrowserStackSessionFactory());
    }

    private static SessionFactory fakeFactory(Platform platform, RunMode runMode, WebDriver toReturn) {
        return new SessionFactory() {
            @Override
            public Platform getPlatform() {
                return platform;
            }

            @Override
            public RunMode getRunMode() {
                return runMode;
            }

            @Override
            public WebDriver createDriver(ExecutionContext context) {
                return toReturn;
            }
        };
    }

    @Test
    @DisplayName("create() for a WEB context returns a SeleniumSession wrapping the resolved driver")
    void create_web_returnsSeleniumSession() {
        WebDriver mockDriver = mock(WebDriver.class);
        SessionFactoryRegistry.registerOrReplace(fakeFactory(Platform.WEB, RunMode.LOCAL, mockDriver));

        ExecutionContext context = ExecutionContext.forWeb("chrome", RunMode.LOCAL);
        AutomationSession session = AutomationSessionFactory.create(context);

        assertInstanceOf(SeleniumSession.class, session);
        assertSame(mockDriver, session.unwrap(WebDriver.class));
    }

    @Test
    @DisplayName("create() for an ANDROID context returns an AppiumSession wrapping the resolved driver")
    void create_android_returnsAppiumSession() {
        AppiumDriver mockDriver = mock(AppiumDriver.class);
        SessionFactoryRegistry.registerOrReplace(fakeFactory(Platform.ANDROID, RunMode.LOCAL, mockDriver));

        ExecutionContext context = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6", RunMode.LOCAL);
        AutomationSession session = AutomationSessionFactory.create(context);

        assertInstanceOf(AppiumSession.class, session);
        assertSame(mockDriver, session.unwrap(AppiumDriver.class));
    }

    @Test
    @DisplayName("create() for an IOS context returns an AppiumSession wrapping the resolved driver")
    void create_ios_returnsAppiumSession() {
        AppiumDriver mockDriver = mock(AppiumDriver.class);
        SessionFactoryRegistry.registerOrReplace(fakeFactory(Platform.IOS, RunMode.BROWSERSTACK, mockDriver));

        ExecutionContext context = ExecutionContext.forMobile(Platform.IOS, "iPhone_15", RunMode.BROWSERSTACK);
        AutomationSession session = AutomationSessionFactory.create(context);

        assertInstanceOf(AppiumSession.class, session);
        assertSame(mockDriver, session.unwrap(AppiumDriver.class));
    }

    @Test
    @DisplayName("create() throws for a null context")
    void create_throwsForNullContext() {
        assertThrows(IllegalArgumentException.class, () -> AutomationSessionFactory.create(null));
    }

    @Test
    @DisplayName("wrap() for WEB platform returns a SeleniumSession without touching the registry")
    void wrap_web_returnsSeleniumSession() {
        WebDriver mockDriver = mock(WebDriver.class);
        AutomationSession session = AutomationSessionFactory.wrap(mockDriver, Platform.WEB);
        assertInstanceOf(SeleniumSession.class, session);
        assertSame(mockDriver, session.unwrap(WebDriver.class));
    }

    @Test
    @DisplayName("wrap() for ANDROID platform with a non-AppiumDriver throws")
    void wrap_android_withNonAppiumDriver_throws() {
        WebDriver mockDriver = mock(WebDriver.class);
        assertThrows(IllegalArgumentException.class, () -> AutomationSessionFactory.wrap(mockDriver, Platform.ANDROID));
    }

    @Test
    @DisplayName("wrap() throws for a null platform")
    void wrap_throwsForNullPlatform() {
        WebDriver mockDriver = mock(WebDriver.class);
        assertThrows(IllegalArgumentException.class, () -> AutomationSessionFactory.wrap(mockDriver, null));
    }
}
