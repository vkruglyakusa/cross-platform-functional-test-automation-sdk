package com.test.automation.sdk.mobile.testbase;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;

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
import com.test.automation.sdk.testbase.TestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies Priority 4 of the Unified SDK Implementation Review
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 10): a concurrently-running Web {@link TestBase} test and Mobile
 * {@link MobileTestBase} test never leak session/device state into each
 * other, and two concurrently-running Mobile test instances with different
 * {@code mobileOS}/{@code deviceName} values never leak into each other
 * either -- proving {@code MobileTestBase.mobileOsName}/{@code deviceName}
 * are genuine per-instance state, not shared mutable statics.
 *
 * <p>Registers fake {@link SessionFactory}s for (WEB, LOCAL) and
 * (ANDROID, LOCAL) so no real browser/Appium session is ever launched (same
 * pattern as {@code AutomationSessionFactoryTest}/
 * {@code TestBaseSessionIntegrationTest}), then restores the real built-in
 * factories so later tests are unaffected. A mocked concurrency test is
 * sufficient for this architecture-level validation per the review's own
 * guidance in section 10; real mixed Web/Mobile BrowserStack/Appium
 * validation remains a separate integration-test concern.
 *
 * <p>The Web path exercises {@code TestBase.initialization(...)}, which
 * reads {@code configuration/config.properties} via {@code SdkConfig} as a
 * real side effect (see {@code TestBaseSessionIntegrationTest} for the same
 * situation) -- {@link #createMinimalConfigFilesIfAbsent()}/
 * {@link #removeConfigFilesThisTestCreated()} create just enough of it for
 * the duration of this test class only, following the exact same pattern.
 */
class MixedWebMobileIsolationTest {

    private static File configPropertiesFile;
    private static File log4jPropertiesFile;
    private static boolean createdConfigProperties;
    private static boolean createdLog4jProperties;
    private static boolean createdConfigDir;

    @BeforeAll
    static void createMinimalConfigFilesIfAbsent() throws Exception {
        File configDir = new File(com.test.automation.sdk.config.SdkConfig.CONFIG_PROPERTIES).getParentFile();
        if (configDir != null && !configDir.exists()) {
            createdConfigDir = configDir.mkdirs();
        }

        configPropertiesFile = new File(com.test.automation.sdk.config.SdkConfig.CONFIG_PROPERTIES);
        if (!configPropertiesFile.exists()) {
            org.apache.commons.io.FileUtils.writeStringToFile(configPropertiesFile,
                    "browser=chrome\ntst_base_url=https://example.com\n", "UTF-8");
            createdConfigProperties = true;
        }

        log4jPropertiesFile = new File(com.test.automation.sdk.config.SdkConfig.LOG4J_PROPERTIES);
        if (!log4jPropertiesFile.exists()) {
            org.apache.commons.io.FileUtils.writeStringToFile(log4jPropertiesFile, "status=warn\n", "UTF-8");
            createdLog4jProperties = true;
        }
    }

    @AfterAll
    static void removeConfigFilesThisTestCreated() {
        // Best-effort cleanup only -- see TestBaseSessionIntegrationTest for why a leftover
        // file here can be harmless (gitignored) rather than a hard failure on Windows.
        if (createdConfigProperties && configPropertiesFile != null) {
            if (!configPropertiesFile.delete()) {
                configPropertiesFile.deleteOnExit();
            }
        }
        if (createdLog4jProperties && log4jPropertiesFile != null) {
            if (!log4jPropertiesFile.delete()) {
                log4jPropertiesFile.deleteOnExit();
            }
        }
        if (createdConfigDir) {
            File configDir = configPropertiesFile.getParentFile();
            String[] remaining = configDir.list();
            if (remaining != null && remaining.length == 0) {
                configDir.delete();
            }
        }
    }

    @BeforeEach
    void registerFakeFactories() throws ClassNotFoundException {
        Class.forName("com.test.automation.sdk.driver.DriverManager");
        SessionFactoryRegistry.registerOrReplace(fakeWebFactory());
        SessionFactoryRegistry.registerOrReplace(fakeAndroidFactory());
        System.setProperty("run.mode", "LOCAL");
    }

    @AfterEach
    void restoreRealFactories() {
        System.clearProperty("run.mode");
        SessionFactoryRegistry.registerOrReplace(new WebLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new WebBrowserStackSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new AndroidLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new AndroidBrowserStackSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new IosLocalSessionFactory());
        SessionFactoryRegistry.registerOrReplace(new IosBrowserStackSessionFactory());
    }

    private static WebDriver newWebMock() {
        WebDriver mock = (WebDriver) Mockito.mock(WebDriver.class,
                Mockito.withSettings()
                        .extraInterfaces(JavascriptExecutor.class)
                        .defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        Mockito.when(((JavascriptExecutor) mock).executeScript("return document.readyState"))
                .thenReturn("complete");
        return mock;
    }

    private static SessionFactory fakeWebFactory() {
        return new SessionFactory() {
            @Override
            public Platform getPlatform() {
                return Platform.WEB;
            }

            @Override
            public RunMode getRunMode() {
                return RunMode.LOCAL;
            }

            @Override
            public WebDriver createDriver(ExecutionContext context) {
                return newWebMock();
            }
        };
    }

    private static SessionFactory fakeAndroidFactory() {
        return new SessionFactory() {
            @Override
            public Platform getPlatform() {
                return Platform.ANDROID;
            }

            @Override
            public RunMode getRunMode() {
                return RunMode.LOCAL;
            }

            @Override
            public WebDriver createDriver(ExecutionContext context) {
                return Mockito.mock(AndroidDriver.class, Mockito.RETURNS_DEEP_STUBS);
            }
        };
    }

    @Test
    @DisplayName("A Web TestBase instance and a Mobile MobileTestBase instance running concurrently never share driver/session state")
    void webAndMobileTests_runningConcurrently_doNotLeakSessionState() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startTogether = new CountDownLatch(2);

        AtomicReference<TestBase> webResult = new AtomicReference<>();
        AtomicReference<MobileTestBase> mobileResult = new AtomicReference<>();

        Future<?> webTask = pool.submit(() -> {
            startTogether.countDown();
            await(startTogether);
            TestableTestBase web = new TestableTestBase();
            try {
                web.init("chrome", "https://example.com");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            webResult.set(web);
        });

        Future<?> mobileTask = pool.submit(() -> {
            startTogether.countDown();
            await(startTogether);
            MobileTestBase mobile = new MobileTestBase();
            mobile.setUpDriver("android", "Pixel_6_API_34");
            mobileResult.set(mobile);
        });

        webTask.get(30, TimeUnit.SECONDS);
        mobileTask.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        TestBase web = webResult.get();
        MobileTestBase mobile = mobileResult.get();

        assertNotSame(web.automationSession, mobile.automationSession,
                "Web and Mobile test instances must never end up sharing the same AutomationSession");
        assertNotSame(web.driver, mobile.driver,
                "Web and Mobile test instances must never end up sharing the same driver");
        assertEquals("android", mobile.getCurrentPlatformOS(),
                "Mobile instance must retain its own mobileOsName despite concurrent Web execution");
    }

    @Test
    @DisplayName("Two concurrently-running Mobile test instances with different devices never leak mobileOsName/deviceName into each other")
    void twoConcurrentMobileTests_withDifferentDevices_doNotLeakDeviceState() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startTogether = new CountDownLatch(2);

        AtomicReference<MobileTestBase> androidTest = new AtomicReference<>();
        AtomicReference<MobileTestBase> secondAndroidTest = new AtomicReference<>();

        Future<?> firstTask = pool.submit(() -> {
            startTogether.countDown();
            await(startTogether);
            MobileTestBase mobile = new MobileTestBase();
            mobile.setUpDriver("android", "Pixel_6_API_34");
            androidTest.set(mobile);
        });

        Future<?> secondTask = pool.submit(() -> {
            startTogether.countDown();
            await(startTogether);
            MobileTestBase mobile = new MobileTestBase();
            mobile.setUpDriver("android", "Galaxy_S23_API_33");
            secondAndroidTest.set(mobile);
        });

        firstTask.get(30, TimeUnit.SECONDS);
        secondTask.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        MobileTestBase first = androidTest.get();
        MobileTestBase second = secondAndroidTest.get();

        assertEquals("Pixel_6_API_34", first.deviceName);
        assertEquals("Galaxy_S23_API_33", second.deviceName);
        assertNotSame(first.automationSession, second.automationSession);
        assertNotSame(first.driver, second.driver);
        assertSame(Mockito.mock(AndroidDriver.class).getClass(), first.driver.getClass());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Exposes {@code TestBase.initialization(...)} (protected, cross-package here) for this test only. */
    private static final class TestableTestBase extends TestBase {
        void init(String browser, String baseUrl) throws Exception {
            initialization(browser, baseUrl);
        }
    }
}
