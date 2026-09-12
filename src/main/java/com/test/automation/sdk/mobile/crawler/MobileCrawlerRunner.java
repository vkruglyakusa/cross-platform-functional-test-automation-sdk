package com.test.automation.sdk.mobile.crawler;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.automation.sdk.mobile.driver.MobileDriverFactory;
import com.test.automation.sdk.tools.pageobject.JavaIdentifier;
import io.appium.java_client.AppiumDriver;

/** Thin command-line adapter between Automation Studio and the SDK native-mobile crawler. */
public final class MobileCrawlerRunner {
    private static final ObjectMapper JSON = new ObjectMapper();

    private MobileCrawlerRunner() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.fromSystemProperties();
        String reportDir = System.getProperty("pog.reportDir");
        if (reportDir != null && !reportDir.trim().isEmpty()) {
            System.setProperty("pog.mobile.reportDir", reportDir.trim());
        }

        AppiumDriver driver = null;
        try {
            driver = MobileDriverFactory.getDriver(config.mobileOS, config.deviceName);
            MobileScreenSnapshot snapshot = new MobileElementCrawler(driver).crawlCurrentScreen();
            String pageObject = MobilePageObjectGenerator.writeToFile(config.className, snapshot);
            String report = MobileCrawlerReportWriter.write(config.screenName, snapshot);

            Map<String, String> artifacts = writeSnapshotArtifacts(config.resultFile, snapshot);
            artifacts.put("pageObject", Paths.get(pageObject).toAbsolutePath().normalize().toString());
            artifacts.put("report", Paths.get(report).toAbsolutePath().normalize().toString());
            writeAtomically(config.resultFile, completedResult(config, snapshot, artifacts));
        } finally {
            if (driver != null) try { driver.quit(); } catch (RuntimeException ignored) { }
        }
    }

    static Map<String, Object> completedResult(Config config, MobileScreenSnapshot snapshot,
            Map<String, String> artifacts) {
        int nativeCount = snapshot.getNativeElements() == null ? 0 : snapshot.getNativeElements().size();
        int resolved = 0;
        if (snapshot.getNativeElements() != null) {
            for (MobileElementInfo element : snapshot.getNativeElements()) if (element.isResolved()) resolved++;
        }
        int webViewCount = 0;
        if (snapshot.getWebViewElements() != null) {
            for (java.util.List<?> elements : snapshot.getWebViewElements().values()) {
                webViewCount += elements == null ? 0 : elements.size();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("mode", "NATIVE_MOBILE");
        result.put("platform", snapshot.getPlatform());
        result.put("screen", config.screenName);
        result.put("nativeElements", nativeCount);
        result.put("resolvedNativeElements", resolved);
        result.put("unresolvedNativeElements", nativeCount - resolved);
        result.put("webViewElements", webViewCount);
        result.put("artifacts", artifacts);
        return result;
    }

    private static Map<String, String> writeSnapshotArtifacts(Path resultFile, MobileScreenSnapshot snapshot)
            throws IOException {
        Path dir = resultFile.toAbsolutePath().normalize().getParent();
        if (dir == null) throw new IOException("Result file must have a parent directory: " + resultFile);
        Files.createDirectories(dir);
        Map<String, String> artifacts = new LinkedHashMap<>();
        Path source = dir.resolve("page-source.xml");
        Files.writeString(source, snapshot.getPageSourceXml() == null ? "" : snapshot.getPageSourceXml());
        artifacts.put("pageSource", source.toString());
        if (snapshot.getScreenshot() != null && snapshot.getScreenshot().length > 0) {
            Path screenshot = dir.resolve("screen.png");
            Files.write(screenshot, snapshot.getScreenshot());
            artifacts.put("screenshot", screenshot.toString());
        }
        return artifacts;
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
        final String className;
        final String screenName;
        final String mobileOS;
        final String deviceName;
        final Path resultFile;

        private Config(String className, String screenName, String mobileOS, String deviceName, Path resultFile) {
            this.className = className;
            this.screenName = screenName;
            this.mobileOS = mobileOS;
            this.deviceName = deviceName;
            this.resultFile = resultFile;
        }

        static Config fromSystemProperties() {
            String os = required("crawler.mobileOS").trim().toLowerCase(java.util.Locale.ROOT);
            if (!("android".equals(os) || "ios".equals(os))) {
                throw new IllegalArgumentException("crawler.mobileOS must be android or ios");
            }
            return new Config(JavaIdentifier.requireTypeName(required("crawler.className"), "crawler.className"),
                    required("crawler.screenName").trim(), os, required("crawler.deviceName").trim(),
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
