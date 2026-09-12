package com.test.automation.sdk.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies Priority 2 of the Unified SDK Implementation Review
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 8): {@link ConfigurationManager} is the single place implementing
 * the system-property &gt; environment-variable &gt; project-YAML &gt; default
 * precedence chain.
 *
 * <p>No real environment variable can be set from a JVM test, so the
 * env-variable tier is exercised indirectly: {@link #resolveOverride}
 * returning {@code null} for an unset key (with no matching system property
 * or env var) proves the chain falls through correctly to the YAML/default
 * tiers exercised by the other tests.</p>
 */
class ConfigurationManagerTest {

    private static final String TEST_KEY = "test.configurationManager.probeKey";

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(TEST_KEY);
        System.clearProperty("providers.custom.hubUrl");
        System.clearProperty("providers.custom.allowInsecureHttp");
    }

    @Test
    @DisplayName("resolve() prefers a system property over the caller's default")
    void resolve_prefersSystemPropertyOverDefault() {
        System.setProperty(TEST_KEY, "from-system-property");

        assertEquals("from-system-property", ConfigurationManager.resolve(TEST_KEY, "fallback"));
    }

    @Test
    @DisplayName("resolve() falls back to the caller's default when nothing else resolves it")
    void resolve_fallsBackToCallerDefault_whenUnset() {
        assertEquals("fallback", ConfigurationManager.resolve(TEST_KEY, "fallback"));
    }

    @Test
    @DisplayName("resolve() falls back to project YAML (sdk-config.yaml) defaults when no override is set")
    void resolve_fallsBackToProjectYamlDefaults() {
        assertEquals("chrome", ConfigurationManager.resolve("browser.default", "should-not-be-used"));
    }

    @Test
    @DisplayName("resolveOverride() returns null when neither a system property nor an env var is set")
    void resolveOverride_returnsNull_whenUnset() {
        assertNull(ConfigurationManager.resolveOverride(TEST_KEY));
    }

    @Test
    @DisplayName("resolveBoolean() honors a system property override")
    void resolveBoolean_honorsSystemPropertyOverride() {
        System.setProperty(TEST_KEY, "true");

        assertEquals(true, ConfigurationManager.resolveBoolean(TEST_KEY, false));
    }

    @Test
    @DisplayName("resolveInt() honors a system property override")
    void resolveInt_honorsSystemPropertyOverride() {
        System.setProperty(TEST_KEY, "42");

        assertEquals(42, ConfigurationManager.resolveInt(TEST_KEY, 0));
    }

    @Test
    @DisplayName("getWebConfig() exposes a typed view resolved through the same precedence chain")
    void getWebConfig_exposesTypedView() {
        ConfigurationManager.WebConfig webConfig = ConfigurationManager.getWebConfig();

        assertEquals("chrome", webConfig.defaultBrowser());
        assertEquals(false, webConfig.headless());
        assertEquals(30, webConfig.pageLoadTimeoutSeconds());
    }

    @Test
    @DisplayName("getCommonConfig() exposes a typed view resolved through the same precedence chain")
    void getCommonConfig_exposesTypedView() {
        ConfigurationManager.CommonConfig commonConfig = ConfigurationManager.getCommonConfig();

        assertEquals("INFO", commonConfig.loggingLevel());
        assertEquals("test-output/screenshots", commonConfig.screenshotsDir());
        assertEquals("test-output/logs", commonConfig.logsDir());
    }

    @Test
    void remoteProviderConfig_acceptsHttpsEndpoint() {
        System.setProperty("providers.custom.hubUrl", "https://grid.example.test/wd/hub");

        assertEquals("https://grid.example.test/wd/hub",
                ConfigurationManager.getRemoteProviderConfig("custom").hubUri().toString());
    }

    @Test
    void remoteProviderConfig_rejectsMissingEndpoint() {
        assertThrows(IllegalStateException.class,
                () -> ConfigurationManager.getRemoteProviderConfig("custom").hubUri());
    }

    @Test
    void remoteProviderConfig_requiresExplicitOptInForHttp() {
        System.setProperty("providers.custom.hubUrl", "http://127.0.0.1:4444/wd/hub");
        assertThrows(IllegalStateException.class,
                () -> ConfigurationManager.getRemoteProviderConfig("custom").hubUri());

        System.setProperty("providers.custom.allowInsecureHttp", "true");
        assertEquals("http://127.0.0.1:4444/wd/hub",
                ConfigurationManager.getRemoteProviderConfig("custom").hubUri().toString());
    }

    @Test
    void remoteProviderConfig_rejectsCredentialsInUrl() {
        System.setProperty("providers.custom.hubUrl", "https://user:secret@grid.example.test/wd/hub");

        assertThrows(IllegalStateException.class,
                () -> ConfigurationManager.getRemoteProviderConfig("custom").hubUri());
    }
}
