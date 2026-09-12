package com.test.automation.sdk.utility;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.automation.sdk.discovery.DiscoveryResult;
import com.test.automation.sdk.discovery.WebElementDiscoveryAdapter;
import com.test.automation.sdk.testbase.WebDriverFactory;
import com.test.automation.sdk.tools.crawler.web.ElementCrawler;
import com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo;
import com.test.automation.sdk.tools.pageobject.JavaIdentifier;
import com.test.automation.sdk.tools.pageobject.PageObjectGenerator;

/** Thin command-line adapter between Automation Studio and the SDK web crawler. */
public final class WebCrawlerRunner {
    private static final ObjectMapper JSON = new ObjectMapper();

    private WebCrawlerRunner() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.fromSystemProperties();
        WebDriver driver = null;
        try {
            driver = WebDriverFactory.getWebDriver(config.browser);
            applyViewport(driver, config.mode, config.viewport);
            List<ElementInfo> elements = new ElementCrawler(driver).crawlUrl(config.url);
            DiscoveryResult discovery = WebElementDiscoveryAdapter.toDiscoveryResult(config.url, elements);
            String pageObject = new PageObjectGenerator(driver)
                    .generateFromElements(config.className, config.url, elements);

            Map<String, String> artifacts = new LinkedHashMap<>();
            artifacts.put("pageObject", Paths.get(pageObject).toAbsolutePath().normalize().toString());
            addScreenshot(driver, config.resultFile, artifacts);
            writeAtomically(config.resultFile, completedResult(config.mode, discovery, artifacts));
        } finally {
            if (driver != null) try { driver.quit(); } catch (RuntimeException ignored) { }
        }
    }

    static Map<String, Object> completedResult(String mode, DiscoveryResult discovery,
            Map<String, String> artifacts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("mode", mode);
        result.put("elements", discovery.getElements().size());
        result.put("resolvedElements", discovery.resolvedCount());
        result.put("unresolvedElements", discovery.getElements().size() - discovery.resolvedCount());
        result.put("source", discovery.getSourceLabel());
        result.put("artifacts", artifacts);
        return result;
    }

    private static void applyViewport(WebDriver driver, String mode, String viewport) {
        if (!"MOBILE_WEB".equals(mode)) return;
        Dimension size;
        switch (viewport) {
            case "tablet": size = new Dimension(1024, 1366); break;
            case "phone":
            case "mobile": size = new Dimension(390, 844); break;
            default: throw new IllegalArgumentException("Unsupported crawler.viewport: " + viewport);
        }
        driver.manage().window().setSize(size);
    }

    private static void addScreenshot(WebDriver driver, Path resultFile, Map<String, String> artifacts) {
        if (!(driver instanceof TakesScreenshot)) return;
        try {
            Path screenshot = resultFile.toAbsolutePath().resolveSibling("page.png");
            if (screenshot.getParent() != null) Files.createDirectories(screenshot.getParent());
            Files.write(screenshot, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
            artifacts.put("screenshot", screenshot.normalize().toString());
        } catch (RuntimeException | IOException ignored) {
            // Screenshot evidence is optional; discovery output remains valid without it.
        }
    }

    private static void writeAtomically(Path target, Map<String, Object> result) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Result file must have a parent directory: " + absolute);
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), result);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static final class Config {
        final String mode;
        final String className;
        final String url;
        final String browser;
        final String viewport;
        final Path resultFile;

        private Config(String mode, String className, String url, String browser, String viewport, Path resultFile) {
            this.mode = mode;
            this.className = className;
            this.url = url;
            this.browser = browser;
            this.viewport = viewport;
            this.resultFile = resultFile;
        }

        static Config fromSystemProperties() {
            String mode = required("crawler.mode").trim().toUpperCase(java.util.Locale.ROOT);
            if (!("WEB".equals(mode) || "MOBILE_WEB".equals(mode))) {
                throw new IllegalArgumentException("crawler.mode must be WEB or MOBILE_WEB");
            }
            String className = JavaIdentifier.requireTypeName(required("crawler.className"), "crawler.className");
            String browser = System.getProperty("crawler.browser", "chrome").trim().toLowerCase(java.util.Locale.ROOT);
            if (!("chrome".equals(browser) || "firefox".equals(browser) || "edge".equals(browser))) {
                throw new IllegalArgumentException("crawler.browser must be chrome, firefox, or edge");
            }
            return new Config(mode, className, required("crawler.url").trim(), browser,
                    System.getProperty("crawler.viewport", "phone").trim().toLowerCase(java.util.Locale.ROOT),
                    Paths.get(required("crawler.resultFile")));
        }
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required system property: " + key);
        }
        return value;
    }
}
