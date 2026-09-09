package com.test.automation.sdk.session;

import org.openqa.selenium.WebDriver;

import io.appium.java_client.AppiumDriver;

import com.test.automation.sdk.driver.DriverManager;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.session.internal.AppiumSession;
import com.test.automation.sdk.session.internal.SeleniumSession;

/**
 * Single public entry point for creating a technology-neutral
 * {@link AutomationSession} -- Structure Cleanup Phase 3
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md, sections
 * 7/20).
 *
 * <p>Deliberately does NOT introduce a second session-resolution mechanism:
 * it delegates driver creation to the existing, already-unified
 * {@link DriverManager#acquire(ExecutionContext)} (itself backed by
 * {@code execution.SessionFactoryRegistry}, the single (platform, runMode)
 * resolution mechanism established in Phase 1), then wraps the resulting
 * driver in the {@link AutomationSession} implementation matching the
 * context's {@link Platform}. Per Guardrail #4 ("one session-selection
 * mechanism"), this class is a thin technology-neutral wrapper around that
 * existing mechanism, not a competing one.
 *
 * <p>{@code testbase.WebDriverFactory} and {@code mobile.driver.
 * MobileDriverFactory} are unaffected by this phase and remain valid,
 * unchanged entry points -- see the roadmap's Phase 3 scope note: "existing
 * WebDriverFactory / MobileDriverFactory may remain as compatibility/
 * internal facades during migration". {@code TestBase} consolidation onto
 * this factory is deferred to Phase 4.
 */
public final class AutomationSessionFactory {

    private AutomationSessionFactory() {}

    /**
     * Creates a new driver session for the given context and wraps it in the
     * {@link AutomationSession} implementation appropriate for its
     * {@link Platform}.
     */
    public static AutomationSession create(ExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        WebDriver driver = DriverManager.acquire(context);
        return wrap(driver, context.getPlatform());
    }

    /**
     * Wraps an already-created driver in the {@link AutomationSession}
     * implementation appropriate for {@code platform}, without creating a
     * new session. Useful for callers migrating incrementally from
     * {@code WebDriverFactory}/{@code MobileDriverFactory}, which already
     * created the driver themselves.
     */
    public static AutomationSession wrap(WebDriver driver, Platform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (platform == Platform.WEB) {
            return new SeleniumSession(driver);
        }
        if (!(driver instanceof AppiumDriver)) {
            throw new IllegalArgumentException(
                    "Expected an AppiumDriver for platform=" + platform
                            + " but got: " + (driver == null ? "null" : driver.getClass().getName()));
        }
        return new AppiumSession((AppiumDriver) driver);
    }
}
