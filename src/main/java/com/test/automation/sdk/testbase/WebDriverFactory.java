package com.test.automation.sdk.testbase;

import java.io.File;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.events.EventFiringDecorator;
import com.test.automation.sdk.listener.WebEventListener;
import com.test.automation.sdk.utility.YamlConfigReader;
import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Factory for creating and configuring WebDriver instances.
 *
 * All settings (proxy, headless, window size, timeouts) are driven by
 * {@code configuration/sdk-config.yaml} via {@link YamlConfigReader}.
 *
 * <p>This is the underlying local-browser creation logic used by
 * {@code com.test.automation.sdk.driver.web.WebLocalSessionFactory}/
 * {@code WebBrowserStackSessionFactory}. New code targeting either platform
 * uniformly should prefer {@code com.test.automation.sdk.driver.DriverManager#acquire}
 * (see {@code docs/proposals/unified-sdk-architect-review.md}); this class is
 * not deprecated and remains fully supported, since it is the real
 * implementation those factories delegate to, not a compatibility shim.
 *
 * NOTE: The static {@code driver} field means tests run sequentially.
 * For parallel execution, migrate to {@code ThreadLocal<WebDriver>}.
 *
 * @author vkruglyak
 */
public class WebDriverFactory {

    public static final Logger log = LogManager.getLogger(WebDriverFactory.class);

    // NOTE: Static driver field -- single-threaded execution only.
    public static WebDriver driver = null;

    // -- One-time JVM-level setup ----------------------------------------------
    static {
        // Reconfigure log4j from project config file if present
        File logConfig = new File(SdkConfig.LOG4J_PROPERTIES);
        if (logConfig.exists()) {
            LoggerContext context =
                    (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            context.setConfigLocation(logConfig.toURI());
        }
        // JVM proxy is configured dynamically per getWebDriver() call
        // so that sdk-config.yaml is fully loaded before proxy settings are applied.
    }

    // -- Public entry point ----------------------------------------------------

    public static WebDriver getWebDriver(String browserName) {
        configureJvmProxy();
        log.info("[WebDriverFactory] Launching browser: {}", browserName);
        switch (browserName.toLowerCase()) {
            case "chrome":
                return getChromeDriver();
            case "firefox":
                return getFirefoxDriver();
            case "edge":
                return getEdgeDriver();
            default:
                throw new IllegalArgumentException("[WebDriverFactory] Unsupported browser: " + browserName);
        }
    }

    // -- JVM proxy configuration -----------------------------------------------

    /**
     * Reads proxy settings from sdk-config.yaml and applies them as JVM system
     * properties for WebDriverManager downloads and other HTTPS connections.
     * Clears proxy properties when proxy is disabled.
     */
    private static void configureJvmProxy() {
        boolean proxyEnabled = YamlConfigReader.getBoolean("proxy.enabled", false);
        if (proxyEnabled) {
            String host = YamlConfigReader.get("proxy.host", "");
            String port = YamlConfigReader.get("proxy.port", "8080");
            if (host != null && !host.trim().isEmpty()) {
                System.setProperty("https.proxyHost", host.trim());
                System.setProperty("https.proxyPort", port.trim());
                System.setProperty("http.proxyHost",  host.trim());
                System.setProperty("http.proxyPort",  port.trim());
                log.info("[WebDriverFactory] JVM proxy configured: {}:{}", host.trim(), port.trim());
            } else {
                log.warn("[WebDriverFactory] proxy.enabled=true but proxy.host is empty -- proxy skipped");
            }
        } else {
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            log.debug("[WebDriverFactory] Proxy disabled -- JVM proxy properties cleared");
        }
    }

    // -- Browser-specific factories --------------------------------------------

    private static WebDriver getChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-extensions");
        options.addArguments("--ignore-certificate-errors");
        options.setAcceptInsecureCerts(true);

        // Window size from YAML (default 1920x1080)
        String windowSize = YamlConfigReader.get("browser.windowSize", "1920x1080");
        options.addArguments("--window-size=" + windowSize.replace("x", ","));
        log.debug("[WebDriverFactory] Chrome window size: {}", windowSize);

        // Headless mode from YAML
        boolean headless = YamlConfigReader.getBoolean("browser.headless", false);
        if (headless) {
            options.addArguments("--headless=new");
            log.info("[WebDriverFactory] Chrome running in headless mode");
        } else {
            log.debug("[WebDriverFactory] Chrome running in headed mode");
        }

        // Proxy from YAML
        configureChromeProxy(options);

        // Browser console logging from YAML
        if (YamlConfigReader.getBoolean("logging.enableBrowserConsoleLogs", true)) {
            java.util.logging.Logger.getLogger("org.openqa.selenium")
                .setLevel(java.util.logging.Level.WARNING);
            org.openqa.selenium.logging.LoggingPreferences logPrefs =
                new org.openqa.selenium.logging.LoggingPreferences();
            logPrefs.enable(org.openqa.selenium.logging.LogType.BROWSER,
                java.util.logging.Level.ALL);
            logPrefs.enable(org.openqa.selenium.logging.LogType.DRIVER,
                java.util.logging.Level.WARNING);
            options.setCapability("goog:loggingPrefs", logPrefs);
            log.debug("[WebDriverFactory] Chrome browser/driver log capture enabled");
        }

        // Resolve driver:
        // 1. If browser.chromeDriverPath is set in sdk-config.yaml and the file exists -> try it
        //    If it fails to init (version mismatch, corrupt binary, etc.) -> fall back to WebDriverManager
        // 2. Default: WebDriverManager resolves the latest version matching the installed Chrome
        String configuredPath = YamlConfigReader.get("browser.chromeDriverPath", "");
        if (!setupLocalChromeDriver(configuredPath)) {
            log.info("[WebDriverFactory] Resolving chromedriver via WebDriverManager (latest compatible version)");
            WebDriverManager.chromedriver().setup();
        }

        if (YamlConfigReader.getBoolean("logging.enableDriverLogs", false)) {
            System.setProperty("webdriver.chrome.logfile", "test-output/logs/chromedriver.log");
            System.setProperty("webdriver.chrome.verboseLogging", "true");
        }

        ChromeDriver rawDriver = new ChromeDriver(options);

        // Log browser + driver versions
        Capabilities cap = rawDriver.getCapabilities();
        log.info("[WebDriverFactory] Chrome browser version: {}", cap.getBrowserVersion());
        Object chromeInfo = cap.getCapability("chrome");
        if (chromeInfo instanceof Map) {
            Map<?, ?> info = (Map<?, ?>) chromeInfo;
            Object driverVersion = info.get("chromedriverVersion");
            log.info("[WebDriverFactory] ChromeDriver version: {}",
                driverVersion != null ? driverVersion.toString() : "unknown");
        }

        return wrapWithEventListener(rawDriver);
    }

    /**
     * Applies Selenium Proxy + Chrome --proxy-bypass-list from sdk-config.yaml.
     * Does nothing when proxy.enabled=false.
     */
    private static void configureChromeProxy(ChromeOptions options) {
        boolean proxyEnabled = YamlConfigReader.getBoolean("proxy.enabled", false);
        if (!proxyEnabled) {
            log.debug("[WebDriverFactory] Proxy disabled -- no proxy applied to Chrome");
            return;
        }

        String host = YamlConfigReader.get("proxy.host", "");
        String port = YamlConfigReader.get("proxy.port", "8080");
        if (host == null || host.trim().isEmpty()) {
            log.warn("[WebDriverFactory] proxy.enabled=true but proxy.host is empty -- proxy skipped for Chrome");
            return;
        }

        String proxyAddress = host.trim() + ":" + port.trim();
        Proxy proxy = new Proxy();
        proxy.setProxyType(ProxyType.MANUAL);
        proxy.setHttpProxy(proxyAddress);
        proxy.setSslProxy(proxyAddress);
        options.setCapability("proxy", proxy);

        String bypassList = YamlConfigReader.get("proxy.bypassList", "localhost;<-loopback>");
        if (bypassList != null && !bypassList.trim().isEmpty()) {
            options.addArguments("--proxy-bypass-list=" + bypassList.trim());
        }

        log.info("[WebDriverFactory] Chrome proxy configured: {} | bypass: {}", proxyAddress, bypassList);
    }

    private static WebDriver getFirefoxDriver() {
        FirefoxOptions options = new FirefoxOptions();
        options.setAcceptInsecureCerts(true);

        boolean headless = YamlConfigReader.getBoolean("browser.headless", false);
        if (headless) {
            options.addArguments("--headless");
            log.info("[WebDriverFactory] Firefox running in headless mode");
        }

        options.addArguments("--width=1920");
        options.addArguments("--height=1080");

        WebDriverManager.firefoxdriver().setup();
        FirefoxDriver rawDriver = new FirefoxDriver(options);
        if (!headless) {
            rawDriver.manage().window().maximize();
        }
        log.info("[WebDriverFactory] Firefox driver initialized");
        return wrapWithEventListener(rawDriver);
    }

    private static WebDriver getEdgeDriver() {
        EdgeOptions options = new EdgeOptions();
        options.setAcceptInsecureCerts(true);

        boolean headless = YamlConfigReader.getBoolean("browser.headless", false);
        if (headless) {
            options.addArguments("--headless=new");
            log.info("[WebDriverFactory] Edge running in headless mode");
        }

        String windowSize = YamlConfigReader.get("browser.windowSize", "1920x1080");
        options.addArguments("--window-size=" + windowSize.replace("x", ","));

        WebDriverManager.edgedriver().setup();
        EdgeDriver rawDriver = new EdgeDriver(options);
        if (!headless) {
            rawDriver.manage().window().maximize();
        }
        log.info("[WebDriverFactory] Edge driver initialized");
        return wrapWithEventListener(rawDriver);
    }

    // -- Helpers ---------------------------------------------------------------

    /**
     * Attempts to configure chromedriver from a local binary path.
     * Returns {@code true} and sets the {@code webdriver.chrome.driver} system property
     * when the path is non-empty and the file exists.
     * Returns {@code false} (triggering WebDriverManager fallback) when the path is
     * empty, blank, or points to a file that does not exist.
     *
     * Package-private for unit testing.
     */
    static boolean setupLocalChromeDriver(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            log.debug("[WebDriverFactory] browser.chromeDriverPath not set -- using WebDriverManager");
            return false;
        }
        File localDriver = new File(configuredPath.trim());
        if (localDriver.exists() && localDriver.isFile()) {
            log.info("[WebDriverFactory] Using local chromedriver from config: {}", localDriver.getAbsolutePath());
            System.setProperty("webdriver.chrome.driver", localDriver.getAbsolutePath());
            return true;
        }
        log.warn("[WebDriverFactory] browser.chromeDriverPath set but file not found: {} -- falling back to WebDriverManager",
                localDriver.getAbsolutePath());
        return false;
    }

    private static WebDriver wrapWithEventListener(WebDriver rawDriver) {
        WebEventListener listener = new WebEventListener(rawDriver);
        return new EventFiringDecorator<WebDriver>(listener).decorate(rawDriver);
    }
}