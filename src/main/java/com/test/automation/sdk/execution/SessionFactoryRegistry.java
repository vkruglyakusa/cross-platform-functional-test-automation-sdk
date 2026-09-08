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

    /** Registers a factory for its own {@link SessionFactory#getPlatform()}/{@link SessionFactory#getRunMode()}. Last registration for a given key wins. */
    public static void register(SessionFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        FACTORIES.put(key(factory.getPlatform(), factory.getRunMode()), factory);
    }

    /** Resolves the factory registered for the given (platform, runMode). */
    public static SessionFactory resolve(Platform platform, RunMode runMode) {
        SessionFactory factory = FACTORIES.get(key(platform, runMode));
        if (factory == null) {
            throw new IllegalStateException(
                    "No SessionFactory registered for platform=" + platform + ", runMode=" + runMode);
        }
        return factory;
    }

    /** Resolves the factory for the context's (platform, runMode). */
    public static SessionFactory resolve(ExecutionContext context) {
        return resolve(context.getPlatform(), context.getRunMode());
    }

    /** Removes all registrations. Package-visible test hook only; not for production use. */
    static void clear() {
        FACTORIES.clear();
    }

    private static String key(Platform platform, RunMode runMode) {
        return platform + "::" + runMode;
    }
}
