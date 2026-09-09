package com.test.automation.sdk.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@link SessionFactory} registered for a given
 * ({@link Platform}, {@link RunMode}) combination. Generalizes
 * {@code com.test.automation.sdk.mobile.execution.MobileExecutionStrategyFactory}
 * (previously a single {@code switch} keyed only on {@code ExecutionTarget})
 * to a two-axis lookup covering both platforms.
 *
 * Phase 1 introduces this registry with no factories pre-registered --
 * concrete {@code SessionFactory} implementations (web local/BrowserStack,
 * Android local/BrowserStack, iOS local/BrowserStack) are wired in during
 * Phase 2 (driver/session unification), typically via a static initializer
 * in {@code com.test.automation.sdk.driver.DriverManager}. This class is not
 * yet called by any production code path.
 */
public final class SessionFactoryRegistry {

    private static final Map<String, SessionFactory> FACTORIES = new ConcurrentHashMap<>();

    private SessionFactoryRegistry() {}

    /**
     * Registers a factory for its own {@link SessionFactory#getPlatform()}/
     * {@link SessionFactory#getRunMode()}/
     * {@link SessionFactory#getAutomationTechnology()}. Last registration for
     * a given key wins.
     */
    public static void register(SessionFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        FACTORIES.put(key(factory.getPlatform(), factory.getRunMode(), factory.getAutomationTechnology()), factory);
    }

    /**
     * Resolves the factory registered for the given (platform, runMode),
     * using the platform-implied default {@link AutomationTechnology} (see
     * {@link SessionFactory#getAutomationTechnology()}). Kept for backward
     * compatibility -- equivalent to
     * {@link #resolve(Platform, RunMode, AutomationTechnology)} with a
     * {@code null} technology.
     */
    public static SessionFactory resolve(Platform platform, RunMode runMode) {
        return resolve(platform, runMode, null);
    }

    /**
     * Resolves the factory registered for the given
     * (platform, runMode, technology) (Unified SDK Review Priority 5,
     * section 11) -- reserved for a future second Web/Mobile technology;
     * {@code null} defaults to the platform-implied technology.
     */
    public static SessionFactory resolve(Platform platform, RunMode runMode, AutomationTechnology technology) {
        AutomationTechnology resolvedTechnology = technology == null
                ? (platform == Platform.WEB ? AutomationTechnology.SELENIUM : AutomationTechnology.APPIUM)
                : technology;
        SessionFactory factory = FACTORIES.get(key(platform, runMode, resolvedTechnology));
        if (factory == null) {
            throw new IllegalStateException(
                    "No SessionFactory registered for platform=" + platform + ", runMode=" + runMode
                            + ", automationTechnology=" + resolvedTechnology);
        }
        return factory;
    }

    /** Resolves the factory for the context's (platform, runMode, automationTechnology). */
    public static SessionFactory resolve(ExecutionContext context) {
        return resolve(context.getPlatform(), context.getRunMode(), context.getAutomationTechnology());
    }

    /** Removes all registrations. Package-visible test hook only; not for production use. */
    static void clear() {
        FACTORIES.clear();
    }

    private static String key(Platform platform, RunMode runMode, AutomationTechnology technology) {
        return platform + "::" + runMode + "::" + technology;
    }
}
