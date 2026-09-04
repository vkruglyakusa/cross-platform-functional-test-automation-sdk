package com.test.automation.sdk.accessibility.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Framework-agnostic configuration source for the accessibility library.
 *
 * <p>Resolution order for any key (highest precedence first):</p>
 * <ol>
 *   <li>JVM system property — {@code -Daccessibility.checking.enabled=true}</li>
 *   <li>Environment variable — dots/dashes become underscores and upper-cased,
 *       e.g. {@code ACCESSIBILITY_CHECKING_ENABLED}</li>
 *   <li>{@code accessibility.properties} in the working directory</li>
 *   <li>{@code accessibility.properties} on the classpath</li>
 * </ol>
 *
 * <p>This deliberately has <b>no dependency</b> on any host project's config system,
 * so the library drops into any Maven/Gradle, JUnit/TestNG project unchanged.</p>
 */
public final class A11yConfig {

    private static final Properties PROPS = new Properties();

    static {
        // Lowest precedence first so later loads override earlier ones.
        loadFromClasspath();
        loadFromWorkingDir();
    }

    private A11yConfig() {
    }

    private static void loadFromClasspath() {
        try (InputStream in = A11yConfig.class.getClassLoader()
                .getResourceAsStream("accessibility.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException ignored) {
            // optional file — ignore
        }
    }

    private static void loadFromWorkingDir() {
        try {
            Path p = Paths.get("accessibility.properties");
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    PROPS.load(in);
                }
            }
        } catch (IOException ignored) {
            // optional file — ignore
        }
    }

    /** Returns the raw value for {@code key}, or {@code null} if not set anywhere. */
    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        String env = System.getenv(toEnvName(key));
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return PROPS.getProperty(key);
    }

    /** Returns the value for {@code key}, or {@code def} when unset. */
    public static String get(String key, String def) {
        String v = get(key);
        return (v != null) ? v : def;
    }

    /** Boolean lookup with a default. */
    public static boolean getBoolean(String key, boolean def) {
        String v = get(key);
        return (v != null) ? Boolean.parseBoolean(v.trim()) : def;
    }

    /**
     * Root directory for all accessibility artifacts (JSON, JSONL, Excel, HTML).
     * Resolution order:
     * <ol>
     *   <li>{@code -Dreporting.accessibilityDir} system property (set by YamlConfigReader bridge)</li>
     *   <li>{@code -Daccessibility.output.dir} system property (legacy override)</li>
     *   <li>Default: {@code test-output/accessibility}</li>
     * </ol>
     */
    public static Path outputDir() {
        // Prefer the unified reporting key set by YamlConfigReader
        String dir = get("reporting.accessibilityDir");
        if (dir == null || dir.trim().isEmpty()) {
            // Fall back to legacy key
            dir = get("accessibility.output.dir");
        }
        if (dir == null || dir.trim().isEmpty()) {
            return Paths.get("test-output", "accessibility");
        }
        return Paths.get(dir.trim());
    }

    private static String toEnvName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }
}

