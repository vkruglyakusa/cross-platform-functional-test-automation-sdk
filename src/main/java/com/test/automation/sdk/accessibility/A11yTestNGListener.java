package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * TestNG listener that makes accessibility scanning fully automatic.
 *
 * <h3>Zero-configuration default (recommended)</h3>
 *
 * <p>This listener is registered for automatic discovery via
 * {@code META-INF/services/org.testng.ITestNGListener}. TestNG picks it up from
 * the classpath, so it applies to <b>every test in the suite automatically</b> —
 * no {@code @Listeners} annotation and no {@code testng.xml} entry required:</p>
 * <pre>
 * public class LoginPageTest {
 *
 *     public WebDriver driver;        // first WebDriver field is auto-detected
 *
 *     {@literal @}Test
 *     public void loginPageIsAccessible() {
 *         driver.get("https://example.gov/login");
 *         // ← scan fires automatically after this test method
 *     }
 * }
 * </pre>
 *
 * <p>Whether scans actually run is controlled by a single flag — set it once and
 * it governs the whole suite:</p>
 * <pre>
 * # accessibility.properties  (or -Daccessibility.checking.enabled=true)
 * accessibility.checking.enabled=true
 * accessibility.session.noise.threshold=SERIOUS
 * </pre>
 * <p>When the flag is {@code false} (the default) the listener is a no-op.</p>
 *
 * <h3>Optional explicit registration</h3>
 * <p>You can still register the listener explicitly if you prefer to scope it,
 * either on a class with {@code @Listeners(A11yTestNGListener.class)} or in
 * {@code testng.xml}:</p>
 * <pre>
 * &lt;listeners&gt;
 *     &lt;listener class-name="com.test.automation.sdk.accessibility.A11yTestNGListener"/&gt;
 * &lt;/listeners&gt;
 * </pre>
 * <p>Use {@link A11yDriver} only when a class has multiple {@code WebDriver}
 * fields and you want to select a specific one.</p>
 *
 * <h3>What the listener does</h3>
 * <ul>
 *   <li>{@code onStart(ITestContext)} — starts the URL poller if configured.</li>
 *   <li>{@code onTestSuccess} / {@code onTestFailure} — runs
 *       {@link A11ySessionManager#check(WebDriver, String)} for the current page
 *       after every test, capturing state even on failure.</li>
 *   <li>{@code onFinish(ITestContext)} — stops the poller, logs session summary,
 *       and generates the Excel and HTML reports.</li>
 * </ul>
 *
 * <p>All dedup and noise-reduction rules apply — no URL is scanned twice.</p>
 */
public class A11yTestNGListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(A11yTestNGListener.class);

    /** Ensures the "no WebDriver found" guidance is logged at WARN only once per run. */
    private final java.util.concurrent.atomic.AtomicBoolean noDriverWarned =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // ── Suite start ───────────────────────────────────────────────────────────

    @Override
    public void onStart(ITestContext context) {
        // Always log a one-line status so it's obvious the listener IS registered
        // and whether scanning is actually on — the #1 source of "nothing happened".
        boolean enabled = A11ySessionManager.isEnabled();
        Diag.print("TestNG listener LOADED for suite '{}'. enabled={}, output dir='{}'. "
                + "(If you can see this line, the listener is registered.)",
                context.getName(), enabled, A11yConfig.outputDir().toAbsolutePath());
        log.info("[A11Y LISTENER] Registered for suite '{}' — accessibility.checking.enabled={}, output dir='{}'",
                context.getName(), enabled, A11yConfig.outputDir().toAbsolutePath());
        if (!enabled) {
            Diag.print("Scanning is DISABLED (accessibility.checking.enabled is not true) — no artifacts will be produced.");
            log.warn("[A11Y LISTENER] Scanning is DISABLED — no artifacts will be produced. "
                    + "Set accessibility.checking.enabled=true (accessibility.properties on the test classpath, "
                    + "or -Daccessibility.checking.enabled=true).");
            return;
        }
        long pollMs = parseLong(A11yConfig.get("accessibility.session.spa.poll.interval.ms"), 0L);
        if (pollMs > 0) {
            log.info("[A11Y LISTENER] SPA poll interval configured ({}ms) — "
                    + "URL poller will start when the first driver is discovered.", pollMs);
        }
        log.info("[A11Y LISTENER] Accessibility auto-scan active for suite: {}", context.getName());
    }

    // ── After each test (both pass and fail) ──────────────────────────────────

    @Override
    public void onTestSuccess(ITestResult result) {
        scanAfterTest(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        // Scan even on failure — captures the accessibility state at the time of failure
        scanAfterTest(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // Skipped tests have no meaningful DOM state — do nothing
    }

    // ── Suite finish ──────────────────────────────────────────────────────────

    @Override
    public void onFinish(ITestContext context) {
        A11ySessionManager.stopUrlPoller();
        A11ySessionManager.logSessionSummary();

        if (!A11ySessionManager.isEnabled()) return;

        try {
            AccessibilityExcelReporter.generate();
            Diag.print("Excel report written under '{}'", A11yConfig.outputDir().toAbsolutePath());
            log.info("[A11Y LISTENER] Excel report generated");
        } catch (Exception e) {
            Diag.print("Could not generate Excel report: {}", e.getMessage());
            log.warn("[A11Y LISTENER] Could not generate Excel report: {}", e.getMessage());
        }
        try {
            AccessibilitySummaryReportGenerator.generate();
            Diag.print("HTML summary written under '{}'", A11yConfig.outputDir().toAbsolutePath());
            log.info("[A11Y LISTENER] HTML summary report generated");
        } catch (Exception e) {
            Diag.print("Could not generate HTML summary: {}", e.getMessage());
            log.warn("[A11Y LISTENER] Could not generate HTML summary: {}", e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void scanAfterTest(ITestResult result) {
        if (!A11ySessionManager.isEnabled()) return;

        Object instance = result.getInstance();
        if (instance == null) return;

        WebDriver driver = findDriver(instance);
        if (driver == null) {
            Diag.print("After test '{}': NO WebDriver field found on the test instance — scan skipped. "
                    + "Expose your driver as a field (optionally @A11yDriver) or call "
                    + "AccessibilityChecker.checkFullSuite(driver, name) directly.",
                    result.getTestClass().getName());
            if (noDriverWarned.compareAndSet(false, true)) {
                log.warn("[A11Y LISTENER] No WebDriver field found on test instance '{}' — scans are being SKIPPED. "
                        + "The scanner discovers a WebDriver-typed FIELD on the test class (or a superclass). "
                        + "If your driver lives in a ThreadLocal/manager/base utility instead, either expose it as a "
                        + "field (optionally annotated @A11yDriver), or call "
                        + "AccessibilityChecker.checkFullSuite(driver, pageName) directly in your tests.",
                        result.getTestClass().getName());
            } else {
                log.debug("[A11Y LISTENER] No WebDriver found on {} — scan skipped.", result.getTestClass().getName());
            }
            return;
        }

        // Start URL poller for SPA on first discovered driver
        startPollerIfNeeded(driver, result.getTestClass().getName());

        String testFallback = result.getTestClass().getRealClass().getSimpleName()
                + "#" + result.getName();
        String pageName = A11ySessionManager.resolvePageName(driver, testFallback);
        Diag.print("Scanning after test '{}' — page name resolved as '{}' (driver found: {})",
                testFallback, pageName, driver.getClass().getSimpleName());
        int count = A11ySessionManager.checkFullSuite(driver, pageName);
        if (count >= 0) {
            log.debug("[A11Y LISTENER] Scan after '{}' — {} violation(s)", pageName, count);
        }
    }

    /**
     * Finds the WebDriver from the test instance:
     * first any field annotated {@link A11yDriver}, then the first
     * {@code WebDriver}-typed field.
     */
    private WebDriver findDriver(Object instance) {
        WebDriver fallback = null;
        Class<?> cls = instance.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (WebDriver.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        WebDriver d = (WebDriver) f.get(
                                Modifier.isStatic(f.getModifiers()) ? null : instance);
                        if (d != null) {
                            if (f.isAnnotationPresent(A11yDriver.class)) return d; // priority
                            if (fallback == null) fallback = d;
                        }
                    } catch (Exception ignored) { /* continue */ }
                }
            }
            cls = cls.getSuperclass();
        }
        return fallback;
    }

    /** Start the SPA URL poller / dialog watcher once (idempotent — guarded by pollerStarted flag). */
    private volatile boolean pollerStarted = false;

    private synchronized void startPollerIfNeeded(WebDriver driver, String suiteLabel) {
        if (pollerStarted) return;
        // Starts URL polling (SPA) and/or the dialog/overlay watcher, per config.
        A11ySessionManager.startWatchersIfConfigured(driver, suiteLabel);
        pollerStarted = true;
    }

    private static long parseLong(String v, long def) {
        if (v == null || v.trim().isEmpty()) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }
}

