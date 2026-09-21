package com.test.automation.sdk.accessibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the SDK build/library version that produced an accessibility report, so a
 * report can always be traced back to the exact SDK build that generated it (e.g. when
 * comparing behavior across upgrades, or filing a defect against the accessibility module).
 * <p>
 * The version is stamped into {@code accessibility/a11y-library-version.properties} at
 * build time via Maven resource filtering (see the SDK {@code pom.xml}), so it reflects
 * {@code project.version} at build time -- it is intentionally NOT read reflectively from
 * the running JVM, since a consumer project may shade/relocate classes.
 */
public final class A11yLibraryVersion {

    private static final Logger logger = LoggerFactory.getLogger(A11yLibraryVersion.class);
    private static final String RESOURCE_PATH = "accessibility/a11y-library-version.properties";
    private static final String UNKNOWN = "unknown";

    private static volatile String cachedVersion;

    private A11yLibraryVersion() {
    }

    /**
     * @return the SDK's own version (e.g. {@code "1.5.2"}), or {@code "unknown"} if the
     * stamped resource could not be found/read (e.g. running from unpacked classes that
     * were never put through a Maven build).
     */
    public static String get() {
        String version = cachedVersion;
        if (version != null) {
            return version;
        }
        version = load();
        cachedVersion = version;
        return version;
    }

    private static String load() {
        try (InputStream in = A11yLibraryVersion.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                logger.debug("Accessibility library version resource not found on classpath: {}", RESOURCE_PATH);
                return UNKNOWN;
            }
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty("library.version", UNKNOWN).trim();
            // Guard against an unfiltered checked-in placeholder (e.g. IDE compile that
            // bypassed Maven resource filtering) -- never surface a raw "${project.version}".
            if (value.isEmpty() || value.startsWith("${")) {
                return UNKNOWN;
            }
            return value;
        } catch (IOException e) {
            logger.debug("Failed to read accessibility library version resource", e);
            return UNKNOWN;
        }
    }
}
