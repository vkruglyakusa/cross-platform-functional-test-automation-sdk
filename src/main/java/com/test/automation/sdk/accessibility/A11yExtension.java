package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.config.A11yConfig;
import com.test.automation.sdk.accessibility.report.A11yReporterFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * JUnit 5 extension that makes accessibility scanning fully automatic —
 * the JUnit 5 peer of {@link A11yTestNGListener} (TestNG).
 *
 * <h3>Opt-in only</h3>
 * <p>Unlike {@link A11yTestNGListener} (which TestNG auto-discovers via
 * {@code META-INF/services/org.testng.ITestNGListener} for every suite), this
 * extension is <b>never</b> auto-detected. Activate it explicitly, either
 * with {@link EnableA11y} or {@code @ExtendWith(A11yExtension.class)}:</p>
 * <pre>
 * {@literal @}EnableA11y
 * class LoginPageTest {
 *
 *     static WebDriver driver;          // first WebDriver field is auto-detected
 *
 *     {@literal @}BeforeAll
 *     static void setup() { driver = new ChromeDriver(); }
 *
 *     {@literal @}Test
 *     void loginPageIsAccessible() {
 *         driver.get("https://example.com/login");
 *         // ← accessibility scan fires automatically after this test method
 *     }
 * }
 * </pre>
 *
 * <p>Whether scans actually run is controlled by a single flag — set it once and
 * it governs the whole suite:</p>
 * <pre>
 * # accessibility.properties  (or -Daccessibility.checking.enabled=true on the CLI)
 * accessibility.checking.enabled=true
 * accessibility.session.noise.threshold=SERIOUS
 * </pre>
 * <p>When the flag is {@code false} (the default) the extension is a cheap no-op.</p>
 *
 * <p>Use {@link A11yDriver} only when a class has multiple {@code WebDriver}
 * fields and you want to select a specific one.</p>
 *
 * <h3>What the extension does</h3>
 * <ol>
 *   <li>{@code @BeforeAll} — wraps the {@code WebDriver} field with
 *       {@link A11ySessionManager#wrapWithAutoScan(WebDriver)} so that every
 *       subsequent {@code driver.get()} / {@code navigate()} auto-triggers a
 *       session-managed scan.</li>
 *   <li>{@code @AfterEach} — runs a final session-managed full-suite scan for
 *       the current page (dedup-aware: skipped when the URL was already
 *       scanned).</li>
 *   <li>{@code @AfterAll} — calls {@link A11ySessionManager#logSessionSummary()},
 *       generates the Excel report and the HTML summary.</li>
 * </ol>
 *
 * <p>All dedup, noise-threshold, and allowlist rules from
 * {@link A11ySessionManager} apply — nothing is scanned twice.</p>
 *
 * <h3>SPA / hash-routing support</h3>
 * <p>If your application uses client-side routing, set:</p>
 * <pre>
 * # accessibility.properties
 * accessibility.session.spa.poll.interval.ms=800
 * </pre>
 * <p>The extension will start the URL poller automatically.</p>
 */
public class A11yExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(A11yExtension.class);

    /** Store namespace key so we can retrieve the driver across callbacks. */
    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(A11yExtension.class);

    // ── BeforeAll: wrap the WebDriver with auto-scan ─────────────────────────

    @Override
    public void beforeAll(ExtensionContext ctx) {
        // Always emit a visible status (stdout via Diag when accessibility.debug=true)
        // so it's obvious the extension is registered and whether scanning is on.
        boolean enabled = A11ySessionManager.isEnabled();
        Class<?> testClass = ctx.getRequiredTestClass();
        Diag.print("JUnit 5 extension LOADED for '{}'. enabled={}, working dir='{}', published report dir='{}'. "
                + "(If you can see this line, the extension is registered.)",
                testClass.getSimpleName(), enabled, A11yConfig.workingDir().toAbsolutePath(), A11yConfig.outputDir().toAbsolutePath());
        if (!enabled) {
            Diag.print("Scanning is DISABLED (accessibility.checking.enabled is not true) — no artifacts will be produced.");
            log.debug("[A11Y EXTENSION] Scanning disabled — skipping setup");
            return;
        }

        AccessibilityChecker.setReporter(A11yReporterFactory.buildDefault());
        log.info("[A11Y EXTENSION] Accessibility reporter initialised (SLF4J{}{})",
                A11yReporterFactory.isAllureEnabled() ? " + Allure" : "",
                A11yReporterFactory.isExtentEnabled() ? " + Extent" : "");

        Field driverField = findDriverField(testClass);

        if (driverField == null) {
            Diag.print("No WebDriver field found on '{}' — auto-scan not wired. "
                    + "Expose a WebDriver field (optionally @A11yDriver).", testClass.getSimpleName());
            log.warn("[A11Y EXTENSION] No WebDriver field found on {} — auto-scan not wired. "
                    + "Annotate your driver field with @A11yDriver, or ensure a WebDriver-typed field exists.",
                    testClass.getSimpleName());
            return;
        }

        boolean isStatic = Modifier.isStatic(driverField.getModifiers());
        WebDriver driver = readField(driverField, isStatic ? null : ctx.getTestInstance().orElse(null));

        if (driver == null) {
            log.debug("[A11Y EXTENSION] WebDriver field '{}' is null at BeforeAll — "
                    + "will attempt auto-scan in AfterEach instead.", driverField.getName());
            // Store the field reference so AfterEach can try again
            ctx.getStore(NS).put("driverField", driverField);
            return;
        }

        WebDriver wrapped = A11ySessionManager.wrapWithAutoScan(driver);
        writeField(driverField, isStatic ? null : ctx.getTestInstance().orElse(null), wrapped);
        ctx.getStore(NS).put("driver", wrapped);

        // Start URL poller (SPA) and/or dialog watcher when configured.
        A11ySessionManager.startWatchersIfConfigured(wrapped, testClass.getSimpleName());

        Diag.print("Auto-scan wired for '{}' (driver field: {})", testClass.getSimpleName(), driverField.getName());
        log.info("[A11Y EXTENSION] Auto-scan wired for {} (field: {})", testClass.getSimpleName(), driverField.getName());
    }

    // ── AfterEach: final scan for current page ────────────────────────────────

    @Override
    public void afterEach(ExtensionContext ctx) {
        if (!A11ySessionManager.isEnabled()) return;

        WebDriver driver = resolveDriver(ctx);
        if (driver == null) {
            Diag.print("After test '{}': no WebDriver resolved — scan skipped.", ctx.getDisplayName());
            return;
        }

        String testName = ctx.getDisplayName();
        String pageName = A11ySessionManager.resolvePageName(driver, testName);
        Diag.print("Scanning after test '{}' — page name resolved as '{}'", testName, pageName);
        // session-managed: skips automatically if same URL was already scanned
        int result = A11ySessionManager.checkFullSuite(driver, pageName);
        if (result >= 0) {
            log.debug("[A11Y EXTENSION] AfterEach scan for '{}' — {} violation(s)", testName, result);
        }
    }

    // ── AfterAll: finalise reports ────────────────────────────────────────────

    @Override
    public void afterAll(ExtensionContext ctx) {
        A11ySessionManager.stopUrlPoller();
        A11ySessionManager.logSessionSummary();

        if (!A11ySessionManager.isEnabled()) return;

        try {
            if (A11yReporterFactory.isExcelEnabled()) {
                AccessibilityExcelReporter.generate();
                Diag.print("Excel report written under '{}'", A11yConfig.outputDir().toAbsolutePath());
                log.info("[A11Y EXTENSION] Excel report generated");
            } else {
                Diag.print("Excel report generation disabled (accessibility.reporting.excel=false)");
                log.info("[A11Y EXTENSION] Excel report generation disabled (accessibility.reporting.excel=false)");
            }
        } catch (Throwable e) {
            Diag.print("Could not generate Excel report: {}", e.getMessage());
            log.warn("[A11Y EXTENSION] Could not generate Excel report: {}", e.getMessage());
        }
        try {
            AccessibilitySummaryReportGenerator.generate();
            Diag.print("HTML summary written under '{}'", A11yConfig.outputDir().toAbsolutePath());
            log.info("[A11Y EXTENSION] HTML summary report generated");
        } catch (Throwable e) {
            Diag.print("Could not generate HTML summary: {}", e.getMessage());
            log.warn("[A11Y EXTENSION] Could not generate HTML summary: {}", e.getMessage());
        }
        try {
            AccessibilityVpatReportGenerator.generate();
            Diag.print("VPAT draft report written under '{}'", A11yConfig.outputDir().toAbsolutePath());
            log.info("[A11Y EXTENSION] VPAT draft report generated");
        } catch (Throwable e) {
            Diag.print("Could not generate VPAT draft report: {}", e.getMessage());
            log.warn("[A11Y EXTENSION] Could not generate VPAT draft report: {}", e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Resolves the driver: first from store, then via field reflection. */
    private WebDriver resolveDriver(ExtensionContext ctx) {
        WebDriver stored = (WebDriver) ctx.getStore(NS).get("driver");
        if (stored != null) return stored;

        Field field = (Field) ctx.getStore(NS).get("driverField");
        if (field == null) {
            field = findDriverField(ctx.getRequiredTestClass());
        }
        if (field == null) return null;

        boolean isStatic = Modifier.isStatic(field.getModifiers());
        return readField(field, isStatic ? null : ctx.getTestInstance().orElse(null));
    }

    /**
     * Finds the best WebDriver field in the test class hierarchy:
     * first any field annotated {@link A11yDriver}, then the first
     * {@code WebDriver}-typed field.
     */
    static Field findDriverField(Class<?> cls) {
        Field fallback = null;
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (WebDriver.class.isAssignableFrom(f.getType())) {
                    if (f.isAnnotationPresent(A11yDriver.class)) return f; // priority
                    if (fallback == null) fallback = f;
                }
            }
            current = current.getSuperclass();
        }
        return fallback;
    }

    private static WebDriver readField(Field f, Object instance) {
        try {
            f.setAccessible(true);
            return (WebDriver) f.get(instance);
        } catch (Exception e) {
            log.debug("[A11Y EXTENSION] Could not read field {}: {}", f.getName(), e.getMessage());
            return null;
        }
    }

    private static void writeField(Field f, Object instance, WebDriver value) {
        try {
            f.setAccessible(true);
            f.set(instance, value);
        } catch (Exception e) {
            log.warn("[A11Y EXTENSION] Could not replace field {} with wrapped driver: {}",
                    f.getName(), e.getMessage());
        }
    }
}
