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

    private static final ProviderId BROWSERSTACK = new ProviderId("browserstack");
    private static final ProviderId CUSTOM = new ProviderId("custom");
    private static final Map<FactoryKey, SessionFactory> FACTORIES = new ConcurrentHashMap<>();

    private SessionFactoryRegistry() {}

    /**
     * Registers a factory for its own {@link SessionFactory#getPlatform()}/
     * {@link SessionFactory#getRunMode()}/
     * {@link SessionFactory#getAutomationTechnology()}/
     * {@link SessionFactory#getProviderId()}.
     *
     * @throws IllegalArgumentException if the factory's provider ID is not compatible with its run mode
     */
    public static void register(SessionFactory factory) {
        FactoryKey key = registrationKey(factory);
        SessionFactory existing = FACTORIES.putIfAbsent(key, factory);
        if (existing != null) {
            throw new IllegalStateException("A SessionFactory is already registered for " + key);
        }
    }

    /**
     * Explicitly replaces an existing registration. This is intentionally
     * separate from {@link #register(SessionFactory)} so production bootstrap
     * detects accidental collisions while test fixtures and deliberate runtime
     * reconfiguration remain possible.
     */
    public static void registerOrReplace(SessionFactory factory) {
        FACTORIES.put(registrationKey(factory), factory);
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
        return resolve(platform, runMode, technology, null);
    }

    /** Resolves a factory using the complete provider-aware execution key. */
    public static SessionFactory resolve(Platform platform, RunMode runMode,
            AutomationTechnology technology, ProviderId providerId) {
        AutomationTechnology resolvedTechnology = technology == null
                ? (platform == Platform.WEB ? AutomationTechnology.SELENIUM : AutomationTechnology.APPIUM)
                : technology;
        FactoryKey key = canonicalKey(platform, runMode, resolvedTechnology, providerId);
        SessionFactory factory = FACTORIES.get(key);
        if (factory == null) {
            throw new IllegalStateException(
                    "No SessionFactory registered for platform=" + platform + ", runMode=" + runMode
                            + ", automationTechnology=" + resolvedTechnology + ", providerId=" + providerId);
        }
        return factory;
    }

    /** Resolves the factory for the context's (platform, runMode, automationTechnology). */
    public static SessionFactory resolve(ExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return resolve(context.getPlatform(), context.getRunMode(), context.getAutomationTechnology(),
                context.getProviderId());
    }

    /** Removes all registrations. Package-visible test hook only; not for production use. */
    static void clear() {
        FACTORIES.clear();
    }

    private static void validateProvider(RunMode runMode, ProviderId providerId) {
        if (runMode == RunMode.REMOTE && providerId == null) {
            throw new IllegalArgumentException("providerId must not be null when runMode is REMOTE");
        }
        if (runMode != RunMode.REMOTE && providerId != null) {
            throw new IllegalArgumentException("providerId is supported only when runMode is REMOTE");
        }
    }

    private static FactoryKey registrationKey(SessionFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        return canonicalKey(factory.getPlatform(), factory.getRunMode(),
                factory.getAutomationTechnology(), factory.getProviderId());
    }

    private static FactoryKey canonicalKey(Platform platform, RunMode runMode,
            AutomationTechnology technology, ProviderId providerId) {
        RunMode canonicalMode = runMode;
        ProviderId canonicalProvider = providerId;
        if (runMode == RunMode.BROWSERSTACK) {
            canonicalMode = RunMode.REMOTE;
            canonicalProvider = BROWSERSTACK;
        } else if (runMode == RunMode.REMOTE_APPIUM) {
            canonicalMode = RunMode.REMOTE;
            canonicalProvider = CUSTOM;
        }
        validateProvider(canonicalMode, canonicalProvider);
        return key(platform, canonicalMode, technology, canonicalProvider);
    }

    private static FactoryKey key(Platform platform, RunMode runMode,
            AutomationTechnology technology, ProviderId providerId) {
        if (platform == null || runMode == null || technology == null) {
            throw new IllegalArgumentException("platform, runMode, and automationTechnology must not be null");
        }
        return new FactoryKey(platform, runMode, technology, providerId);
    }

    private record FactoryKey(Platform platform, RunMode runMode,
            AutomationTechnology technology, ProviderId providerId) {}
}
