package com.test.automation.sdk.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YamlConfigReader.
 *
 * Uses reflection to reset the singleton between tests so each test
 * exercises the reader in isolation.
 *
 * Does NOT require a browser or network connection.
 */
@DisplayName("YamlConfigReader -- YAML config loading and access")
class YamlConfigReaderTest {

    @TempDir
    File tempDir;

    private String originalConfigDir;

    @BeforeEach
    void setup() {
        originalConfigDir = System.getProperty("sdk.config.dir");
        resetSingleton();
    }

    @AfterEach
    void teardown() {
        resetSingleton();
        if (originalConfigDir != null) {
            System.setProperty("sdk.config.dir", originalConfigDir);
        } else {
            System.clearProperty("sdk.config.dir");
        }
        resetSingleton();
    }

    // -- Defaults (no file present) --------------------------------------------

    @Test
    @DisplayName("get() returns default browser when no yaml file exists")
    void defaults_browserDefault() {
        pointToEmptyDir();
        assertEquals("chrome", YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("get() returns default log level when no yaml file exists")
    void defaults_logLevel() {
        pointToEmptyDir();
        assertEquals("INFO", YamlConfigReader.get("logging.level"));
    }

    @Test
    @DisplayName("get() returns default sdk log file path when no yaml file exists")
    void defaults_sdkLogFile() {
        pointToEmptyDir();
        assertEquals("test-output/logs/sdk.log", YamlConfigReader.get("logging.sdkLogFile"));
    }

    @Test
    @DisplayName("get() returns default selenium log file path when no yaml file exists")
    void defaults_seleniumLogFile() {
        pointToEmptyDir();
        assertEquals("test-output/logs/selenium.log", YamlConfigReader.get("logging.seleniumLogFile"));
    }

    @Test
    @DisplayName("get() returns default browser log file path when no yaml file exists")
    void defaults_browserLogFile() {
        pointToEmptyDir();
        assertEquals("test-output/logs/browser.log", YamlConfigReader.get("logging.browserLogFile"));
    }

    @Test
    @DisplayName("getBoolean() returns true for enableSeleniumLogs default")
    void defaults_enableSeleniumLogs_isTrue() {
        pointToEmptyDir();
        assertTrue(YamlConfigReader.getBoolean("logging.enableSeleniumLogs", false));
    }

    @Test
    @DisplayName("getBoolean() returns true for enableBrowserConsoleLogs default")
    void defaults_enableBrowserConsoleLogs_isTrue() {
        pointToEmptyDir();
        assertTrue(YamlConfigReader.getBoolean("logging.enableBrowserConsoleLogs", false));
    }

    @Test
    @DisplayName("getBoolean() returns false for proxy.enabled default")
    void defaults_proxyEnabled_isFalse() {
        pointToEmptyDir();
        assertFalse(YamlConfigReader.getBoolean("proxy.enabled", true));
    }

    @Test
    @DisplayName("getBoolean() returns false for browser.headless default")
    void defaults_headless_isFalse() {
        pointToEmptyDir();
        assertFalse(YamlConfigReader.getBoolean("browser.headless", true));
    }

    @Test
    @DisplayName("getInt() returns 30 for browser.pageLoadTimeoutSeconds default")
    void defaults_pageLoadTimeout_is30() {
        pointToEmptyDir();
        assertEquals(30, YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0));
    }

    @Test
    @DisplayName("get() returns mailinator domain default")
    void defaults_mailinatorDomain() {
        pointToEmptyDir();
        assertEquals("mailinator.com", YamlConfigReader.get("api.mailinator.domain"));
    }

    @Test
    @DisplayName("getInt() returns 3 for mailinator poll interval default")
    void defaults_mailinatorPollInterval_is3() {
        pointToEmptyDir();
        assertEquals(3, YamlConfigReader.getInt("api.mailinator.inboxPollIntervalSeconds", 0));
    }

    @Test
    @DisplayName("getInt() returns 60 for mailinator poll timeout default")
    void defaults_mailinatorPollTimeout_is60() {
        pointToEmptyDir();
        assertEquals(60, YamlConfigReader.getInt("api.mailinator.inboxPollTimeoutSeconds", 0));
    }

    @Test
    @DisplayName("screenshots.captureOnFailure defaults to true")
    void defaults_screenshotCaptureOnFailure_isTrue() {
        pointToEmptyDir();
        assertTrue(YamlConfigReader.getBoolean("screenshots.captureOnFailure", false));
    }

    @Test
    @DisplayName("screenshots.domDumpDir defaults to test-output/dom-dumps")
    void defaults_domDumpDir_isSet() {
        pointToEmptyDir();
        assertEquals("test-output/dom-dumps", YamlConfigReader.get("screenshots.domDumpDir"));
    }

    // -- get() with default fallback -------------------------------------------

    @Test
    @DisplayName("get(key, default) returns provided default when key is missing")
    void get_withDefault_returnsFallback_whenKeyMissing() {
        pointToEmptyDir();
        assertEquals("myDefault", YamlConfigReader.get("nonexistent.key", "myDefault"));
    }

    @Test
    @DisplayName("get(key) returns null when key is missing and no default provided")
    void get_returnsNull_whenKeyMissing() {
        pointToEmptyDir();
        assertNull(YamlConfigReader.get("totally.unknown.key"));
    }

    // -- YAML file parsing -----------------------------------------------------

    @Test
    @DisplayName("Parses top-level section key:value from file")
    void parse_topLevelSection_readsBrowserDefault() throws IOException {
        writeYaml(
            "browser:\n" +
            "  default: firefox\n"
        );
        assertEquals("firefox", YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("Parses nested (4-space) key:value from file")
    void parse_nestedKey_readsMailinatorApiKey() throws IOException {
        writeYaml(
            "api:\n" +
            "  mailinator:\n" +
            "    apiKey: abc123\n"
        );
        assertEquals("abc123", YamlConfigReader.get("api.mailinator.apiKey"));
    }

    @Test
    @DisplayName("Strips inline comments from values")
    void parse_stripsInlineComments() throws IOException {
        writeYaml(
            "browser:\n" +
            "  headless: true  # override for CI\n"
        );
        assertEquals("true", YamlConfigReader.get("browser.headless"));
    }

    @Test
    @DisplayName("Ignores full-line comments")
    void parse_ignoresFullLineComments() throws IOException {
        writeYaml(
            "# This is a full-line comment\n" +
            "browser:\n" +
            "  default: edge\n"
        );
        assertEquals("edge", YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("Ignores empty and blank lines")
    void parse_ignoresBlankLines() throws IOException {
        writeYaml(
            "\n" +
            "browser:\n" +
            "\n" +
            "  default: chrome\n" +
            "\n"
        );
        assertEquals("chrome", YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("File value overrides hardcoded default")
    void parse_fileValueOverridesDefault() throws IOException {
        writeYaml(
            "logging:\n" +
            "  level: DEBUG\n"
        );
        assertEquals("DEBUG", YamlConfigReader.get("logging.level"));
    }

    @Test
    @DisplayName("Parses proxy section correctly")
    void parse_proxySection() throws IOException {
        writeYaml(
            "proxy:\n" +
            "  enabled: true\n" +
            "  host: proxy.company.com\n" +
            "  port: 8080\n"
        );
        assertTrue(YamlConfigReader.getBoolean("proxy.enabled", false));
        assertEquals("proxy.company.com", YamlConfigReader.get("proxy.host"));
        assertEquals(8080, YamlConfigReader.getInt("proxy.port", 0));
    }

    @Test
    @DisplayName("Parses multiple sections from single file")
    void parse_multipleSections() throws IOException {
        writeYaml(
            "browser:\n" +
            "  default: chrome\n" +
            "  headless: true\n" +
            "proxy:\n" +
            "  enabled: false\n"
        );
        assertEquals("chrome", YamlConfigReader.get("browser.default"));
        assertTrue(YamlConfigReader.getBoolean("browser.headless", false));
        assertFalse(YamlConfigReader.getBoolean("proxy.enabled", true));
    }

    // -- getBoolean() edge cases -----------------------------------------------

    @Test
    @DisplayName("getBoolean() is case-insensitive: 'TRUE' returns true")
    void getBoolean_caseInsensitive_uppercase() throws IOException {
        writeYaml("browser:\n  headless: TRUE\n");
        assertTrue(YamlConfigReader.getBoolean("browser.headless", false));
    }

    @Test
    @DisplayName("getBoolean() treats non-true string as false")
    void getBoolean_nonTrueString_returnsFalse() throws IOException {
        writeYaml("browser:\n  headless: yes\n");
        assertFalse(YamlConfigReader.getBoolean("browser.headless", false));
    }

    @Test
    @DisplayName("getBoolean() returns defaultValue when key is absent")
    void getBoolean_returnsDefault_whenKeyAbsent() {
        pointToEmptyDir();
        assertTrue(YamlConfigReader.getBoolean("no.such.key", true));
        assertFalse(YamlConfigReader.getBoolean("no.such.key", false));
    }

    // -- getInt() edge cases ---------------------------------------------------

    @Test
    @DisplayName("getInt() parses valid integer correctly")
    void getInt_parsesValidInt() throws IOException {
        writeYaml("browser:\n  pageLoadTimeoutSeconds: 45\n");
        assertEquals(45, YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0));
    }

    @Test
    @DisplayName("getInt() returns defaultValue for non-numeric string")
    void getInt_returnsDefault_forNonNumeric() throws IOException {
        writeYaml("browser:\n  pageLoadTimeoutSeconds: notanumber\n");
        assertEquals(99, YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 99));
    }

    @Test
    @DisplayName("getInt() returns defaultValue when key is absent")
    void getInt_returnsDefault_whenKeyAbsent() {
        pointToEmptyDir();
        assertEquals(42, YamlConfigReader.getInt("no.such.key", 42));
    }

    // -- Quote stripping -------------------------------------------------------

    @Test
    @DisplayName("Double-quoted value has quotes stripped")
    void parse_doubleQuotedValue_stripsQuotes() throws IOException {
        writeYaml("proxy:\n  bypassList: \"localhost;<-loopback>\"\n");
        assertEquals("localhost;<-loopback>", YamlConfigReader.get("proxy.bypassList"));
    }

    @Test
    @DisplayName("Single-quoted value has quotes stripped")
    void parse_singleQuotedValue_stripsQuotes() throws IOException {
        writeYaml("proxy:\n  bypassList: 'localhost;127.0.0.1'\n");
        assertEquals("localhost;127.0.0.1", YamlConfigReader.get("proxy.bypassList"));
    }

    @Test
    @DisplayName("Unquoted value is returned as-is")
    void parse_unquotedValue_returnedAsIs() throws IOException {
        writeYaml("browser:\n  default: chrome\n");
        assertEquals("chrome", YamlConfigReader.get("browser.default"));
    }

    // -- Proxy defaults --------------------------------------------------------

    @Test
    @DisplayName("proxy.host default is empty string")
    void defaults_proxyHost_isEmpty() {
        pointToEmptyDir();
        String host = YamlConfigReader.get("proxy.host", "");
        assertTrue(host == null || host.isEmpty(), "proxy.host default should be empty");
    }

    @Test
    @DisplayName("proxy.port default is 8080")
    void defaults_proxyPort_is8080() {
        pointToEmptyDir();
        assertEquals(8080, YamlConfigReader.getInt("proxy.port", 0));
    }

    @Test
    @DisplayName("proxy.bypassList default is set")
    void defaults_proxyBypassList_isSet() {
        pointToEmptyDir();
        String bypassList = YamlConfigReader.get("proxy.bypassList");
        assertNotNull(bypassList, "proxy.bypassList default should not be null");
        assertFalse(bypassList.isEmpty(), "proxy.bypassList default should not be empty");
    }

    // -- getAll() --------------------------------------------------------------

    @Test
    @DisplayName("getAll() returns non-null, non-empty map with defaults loaded")
    void getAll_returnsNonEmptyMap() {
        pointToEmptyDir();
        Map<String, String> all = YamlConfigReader.getAll();
        assertNotNull(all);
        assertFalse(all.isEmpty());
    }

    @Test
    @DisplayName("getAll() returns unmodifiable map -- put() throws UnsupportedOperationException")
    void getAll_isUnmodifiable() {
        pointToEmptyDir();
        Map<String, String> all = YamlConfigReader.getAll();
        assertThrows(UnsupportedOperationException.class,
            () -> all.put("hacked.key", "hacked"));
    }

    @Test
    @DisplayName("getAll() contains all default keys")
    void getAll_containsAllDefaultKeys() {
        pointToEmptyDir();
        Map<String, String> all = YamlConfigReader.getAll();
        assertTrue(all.containsKey("browser.default"),              "missing browser.default");
        assertTrue(all.containsKey("logging.level"),                "missing logging.level");
        assertTrue(all.containsKey("logging.sdkLogFile"),           "missing logging.sdkLogFile");
        assertTrue(all.containsKey("logging.seleniumLogFile"),      "missing logging.seleniumLogFile");
        assertTrue(all.containsKey("logging.browserLogFile"),       "missing logging.browserLogFile");
        assertTrue(all.containsKey("proxy.enabled"),                "missing proxy.enabled");
        assertTrue(all.containsKey("proxy.port"),                   "missing proxy.port");
        assertTrue(all.containsKey("proxy.bypassList"),             "missing proxy.bypassList");
        assertTrue(all.containsKey("screenshots.captureOnFailure"), "missing screenshots.captureOnFailure");
        assertTrue(all.containsKey("screenshots.domDumpDir"),       "missing screenshots.domDumpDir");
        assertTrue(all.containsKey("api.mailinator.domain"),        "missing api.mailinator.domain");
        assertTrue(all.containsKey("crawler.pageObject.package"),   "missing crawler.pageObject.package");
        assertTrue(all.containsKey("crawler.pageObject.outputDir"), "missing crawler.pageObject.outputDir");
        assertTrue(all.containsKey("crawler.pageObject.reportDir"), "missing crawler.pageObject.reportDir");
    }

    // -- Crawler pageObject defaults -------------------------------------------

    @Test
    @DisplayName("crawler.pageObject.package default is non-null and non-empty")
    void defaults_crawlerPackage_isSet() {
        pointToEmptyDir();
        String pkg = YamlConfigReader.get("crawler.pageObject.package");
        assertNotNull(pkg, "crawler.pageObject.package default should not be null");
        assertFalse(pkg.isEmpty(), "crawler.pageObject.package default should not be empty");
    }

    @Test
    @DisplayName("crawler.pageObject.outputDir default is non-null and non-empty")
    void defaults_crawlerOutputDir_isSet() {
        pointToEmptyDir();
        String dir = YamlConfigReader.get("crawler.pageObject.outputDir");
        assertNotNull(dir, "crawler.pageObject.outputDir default should not be null");
        assertFalse(dir.isEmpty(), "crawler.pageObject.outputDir default should not be empty");
    }

    @Test
    @DisplayName("crawler.pageObject.reportDir default is 'test-output/crawler/'")
    void defaults_crawlerReportDir_isTestOutputCrawler() {
        pointToEmptyDir();
        assertEquals("test-output/crawler/", YamlConfigReader.get("crawler.pageObject.reportDir"));
    }

    @Test
    @DisplayName("crawler.pageObject.package is overridden by YAML file value")
    void parse_crawlerPackage_overridesDefault() throws IOException {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    package: com.acme.automation.uiActions\n"
        );
        assertEquals("com.acme.automation.uiActions",
            YamlConfigReader.get("crawler.pageObject.package"));
    }

    @Test
    @DisplayName("crawler.pageObject.outputDir is overridden by YAML file value")
    void parse_crawlerOutputDir_overridesDefault() throws IOException {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    outputDir: src/main/java/com/acme/automation/uiActions/\n"
        );
        assertEquals("src/main/java/com/acme/automation/uiActions/",
            YamlConfigReader.get("crawler.pageObject.outputDir"));
    }

    @Test
    @DisplayName("crawler.pageObject.reportDir is overridden by YAML file value")
    void parse_crawlerReportDir_overridesDefault() throws IOException {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    reportDir: reports/locator-reports/\n"
        );
        assertEquals("reports/locator-reports/",
            YamlConfigReader.get("crawler.pageObject.reportDir"));
    }

    @Test
    @DisplayName("All three crawler keys parsed together from a single YAML block")
    void parse_crawlerSection_allThreeKeys() throws IOException {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    package: com.example.uiActions\n" +
            "    outputDir: src/main/java/com/example/uiActions/\n" +
            "    reportDir: output/crawler/\n"
        );
        assertEquals("com.example.uiActions",
            YamlConfigReader.get("crawler.pageObject.package"));
        assertEquals("src/main/java/com/example/uiActions/",
            YamlConfigReader.get("crawler.pageObject.outputDir"));
        assertEquals("output/crawler/",
            YamlConfigReader.get("crawler.pageObject.reportDir"));
    }

    // -- Helpers ---------------------------------------------------------------

    private void pointToEmptyDir() {
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetSingleton();
    }

    private void writeYaml(String content) throws IOException {
        File yaml = new File(tempDir, "sdk-config.yaml");
        FileWriter fw = new FileWriter(yaml);
        try {
            fw.write(content);
        } finally {
            fw.close();
        }
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetSingleton();
    }

    /** Resets the YamlConfigReader singleton via reflection for test isolation. */
    private static void resetSingleton() {
        try {
            Field field = YamlConfigReader.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Could not reset YamlConfigReader singleton", e);
        }
    }
}
