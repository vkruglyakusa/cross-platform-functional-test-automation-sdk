package com.test.automation.sdk.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Single configuration-resolution engine for the SDK.
 *
 * <p><b>Unified SDK Review Priority 2</b>
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 8) -- resolution logic must be implemented once. Before this class,
 * {@code System}/env precedence was only honored by the (deliberately
 * self-contained) accessibility config reader; {@link YamlConfigReader} and
 * {@link com.test.automation.sdk.mobile.config.MobileConfigReader} read their
 * flat maps directly with no system-property/env override at all. This class
 * is now the one place that implements the full precedence chain:</p>
 *
 * <pre>
 *   System / Maven property (-Dkey=value)
 *           &gt;
 *   Environment variable (KEY_WITH_UNDERSCORES)
 *           &gt;
 *   Project YAML (sdk-config.yaml, via YamlConfigReader)
 *           &gt;
 *   SDK default (caller-supplied fallback)
 * </pre>
 *
 * <p>{@link com.test.automation.sdk.mobile.config.MobileConfigReader} is a
 * typed Mobile view over this engine: it still owns the temporary
 * standalone-{@code mobile-config.yaml} compatibility path (see its javadoc),
 * but for every key not present in that optional file it now delegates to
 * {@link #resolve(String, String)} instead of reading
 * {@link YamlConfigReader} directly, so the same precedence rules apply to
 * Web and Mobile configuration alike.</p>
 *
 * <p>{@link #getCommonConfig()} / {@link #getWebConfig()} return small typed
 * views over the handful of keys those callers already relied on, without
 * introducing a broader configuration object model than currently needed.</p>
 */
public final class ConfigurationManager {

    private ConfigurationManager() {
    }

    /**
     * Resolves {@code key} using the full precedence chain (system property
     * &gt; environment variable &gt; project YAML &gt; {@code defaultValue}).
     */
    public static String resolve(String key, String defaultValue) {
        String override = resolveOverride(key);
        if (override != null) {
            return override;
        }
        return YamlConfigReader.get(key, defaultValue);
    }

    /** Boolean convenience wrapper around {@link #resolve(String, String)}. */
    public static boolean resolveBoolean(String key, boolean defaultValue) {
        String value = resolve(key, null);
        return (value != null) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    /** Integer convenience wrapper around {@link #resolve(String, String)}. */
    public static int resolveInt(String key, int defaultValue) {
        String value = resolve(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns the highest-precedence override for {@code key} -- a JVM
     * system property or environment variable -- or {@code null} when
     * neither is set, so callers with their own lower-precedence data
     * source (e.g. a standalone {@code mobile-config.yaml}) can check this
     * first without duplicating the system-property/env lookup logic.
     */
    public static String resolveOverride(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        String env = System.getenv(toEnvName(key));
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return null;
    }

    private static String toEnvName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    /** Typed view over the handful of platform-neutral keys used today. */
    public static CommonConfig getCommonConfig() {
        return new CommonConfig();
    }

    /** Typed view over the handful of Web-specific keys used today. */
    public static WebConfig getWebConfig() {
        return new WebConfig();
    }

    /** Typed view over one remote execution provider's configuration. */
    public static RemoteProviderConfig getRemoteProviderConfig(String providerId) {
        return new RemoteProviderConfig(providerId);
    }

    /** Typed, read-only view over platform-neutral (common) configuration. */
    public static final class CommonConfig {
        private CommonConfig() {
        }

        public String loggingLevel() {
            return resolve("logging.level", "INFO");
        }

        public String screenshotsDir() {
            return resolve("reporting.screenshotsDir", "test-output/screenshots");
        }

        public String logsDir() {
            return resolve("reporting.logsDir", "test-output/logs");
        }
    }

    /** Typed, read-only view over Web-specific configuration. */
    public static final class WebConfig {
        private WebConfig() {
        }

        public String defaultBrowser() {
            return resolve("browser.default", "chrome");
        }

        public boolean headless() {
            return resolveBoolean("browser.headless", false);
        }

        public int pageLoadTimeoutSeconds() {
            return resolveInt("browser.pageLoadTimeoutSeconds", 30);
        }
    }

    /** Typed, read-only configuration for {@code providers.<id>}. */
    public static final class RemoteProviderConfig {
        private final String providerId;

        private RemoteProviderConfig(String providerId) {
            if (providerId == null || !providerId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("providerId must be a canonical lower-case identifier");
            }
            this.providerId = providerId;
        }

        public URI hubUri() {
            String key = "providers." + providerId + ".hubUrl";
            String rawValue = resolve(key, "");
            if (rawValue == null || rawValue.trim().isEmpty()) {
                throw new IllegalStateException("Missing required remote provider setting: " + key);
            }

            final URI uri;
            try {
                uri = new URI(rawValue.trim());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Invalid remote provider URL in " + key, e);
            }

            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException(key + " must be an absolute HTTP(S) URL with a host");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException(key + " must not contain credentials; use environment variables");
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException(key + " must not contain a query or fragment");
            }
            if ("http".equalsIgnoreCase(scheme)
                    && !resolveBoolean("providers." + providerId + ".allowInsecureHttp", false)) {
                throw new IllegalStateException(key
                        + " uses HTTP; set providers." + providerId + ".allowInsecureHttp=true only for a trusted grid");
            }
            return uri;
        }
    }
}
