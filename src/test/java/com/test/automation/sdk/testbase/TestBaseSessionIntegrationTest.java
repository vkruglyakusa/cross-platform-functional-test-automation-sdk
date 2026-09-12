package com.test.automation.sdk.testbase;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.File;

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
import com.test.automation.sdk.session.AutomationSession;
import com.test.automation.sdk.session.internal.SeleniumSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Priority 1 of the Unified SDK Implementation Review
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 7): {@link TestBase#initialization(String, String)} acquires its
 * session through {@code ExecutionContext -> AutomationSessionFactory ->
 * DriverManager -> SessionFactoryRegistry} rather than calling
 * {@code WebDriverFactory} directly, and {@link TestBase#closeBrowser()}
 * tears the session down through the same {@link AutomationSession} handle.
 *
 * Registers a fake {@link SessionFactory} for (WEB, LOCAL) so no real
 * browser is ever launched (same pattern as
 * {@code session.AutomationSessionFactoryTest}), then restores the real
 * built-in factories so later tests are unaffected.
 *
 * {@link TestBase#initialization(String, String)} reads
 * {@code configuration/config.properties} (via {@code SdkConfig}) as a real
 * side effect of exercising the full lifecycle -- this SDK's own repository
 * intentionally ships no such file (it is a consumer-project artifact), so
 * {@link #createMinimalConfigFilesIfAbsent()}/{@link #removeConfigFilesThisTestCreated()}
 * create just enough of it for the duration of this test class only, and
 * remove exactly what they created (never touching a file that already
 * existed, e.g. from a real consumer checkout).
 */
class TestBaseSessionIntegrationTest {

    private static File configPropertiesFile;
    private static File log4jPropertiesFile;
    private static boolean createdConfigProperties;
    private static boolean createdLog4jProperties;
    private static boolean createdConfigDir;

    private WebDriver mockDriver;

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
        // Best-effort cleanup only. On Windows, config.properties can remain
        // briefly held open (e.g. by log4j/Properties loading elsewhere in this
        // JVM), so File#delete() may silently return false even though nothing
        // in this test class keeps it open. The files are gitignored (see
        // .gitignore) as a fixture-of-last-resort, so a leftover file here is
        // harmless and never gets committed.
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
    void registerFakeWebLocalFactory() throws ClassNotFoundException {
        Class.forName("com.test.automation.sdk.driver.DriverManager");
        mockDriver = (WebDriver) Mockito.mock(WebDriver.class,
                Mockito.withSettings()
                        .extraInterfaces(JavascriptExecutor.class)
                        .defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        Mockito.when(((JavascriptExecutor) mockDriver).executeScript("return document.readyState"))
                .thenReturn("complete");
        SessionFactoryRegistry.registerOrReplace(fakeWebFactory(mockDriver));
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

    private static SessionFactory fakeWebFactory(WebDriver toReturn) {
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
                return toReturn;
            }
        };
    }

    @Test
    @DisplayName("initialization() sets both automationSession and driver via the common session architecture")
    void initialization_acquiresSessionThroughAutomationSessionFactory() throws Exception {
        TestBase testBase = new TestBase();

        testBase.initialization("chrome", "https://example.com");

        assertNotNull(testBase.automationSession, "automationSession must be set by initialization()");
        assertInstanceOf(SeleniumSession.class, testBase.automationSession);
        assertSame(mockDriver, testBase.driver, "driver must be the WebDriver resolved by the registered SessionFactory");
        assertSame(mockDriver, testBase.automationSession.unwrap(WebDriver.class));
    }

    @Test
    @DisplayName("closeBrowser() quits through automationSession when it is set")
    void closeBrowser_quitsThroughAutomationSession() throws Exception {
        TestBase testBase = new TestBase();
        testBase.initialization("chrome", "https://example.com");

        testBase.closeBrowser();

        Mockito.verify(mockDriver, Mockito.times(1)).quit();
    }

    @Test
    @DisplayName("closeBrowser() falls back to driver.quit() when automationSession is null")
    void closeBrowser_fallsBackToDriverQuit_whenNoAutomationSession() throws Exception {
        TestBase testBase = new TestBase();
        testBase.driver = mockDriver;
        // automationSession intentionally left null to exercise the fallback path.

        testBase.closeBrowser();

        Mockito.verify(mockDriver, Mockito.times(1)).quit();
    }
}
