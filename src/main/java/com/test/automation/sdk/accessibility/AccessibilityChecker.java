package com.test.automation.sdk.accessibility;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.test.automation.sdk.accessibility.config.A11yConfig;
import com.test.automation.sdk.accessibility.report.A11yReporter;
import com.test.automation.sdk.accessibility.report.NoOpReporter;
import com.test.automation.sdk.accessibility.report.Slf4jReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AccessibilityChecker — comprehensive WCAG accessibility scanning powered by
 * Deque's axe-core engine, extended with Spectra-equivalent interaction, WCAG 2.2,
 * structural/screen-reader, and motion/media checks.
 * <p>
 * <b>Framework-agnostic:</b> depends only on Selenium, axe-core, Jackson, POI and SLF4J.
 * Works with JUnit or TestNG, Maven or Gradle, and any (or no) reporting tool.
 * </p>
 *
 * <h3>Engine layers (mirrors BrowserStack Spectra coverage)</h3>
 * <table>
 *   <tr><th>Layer</th><th>Method</th><th>What it covers</th></tr>
 *   <tr><td>1 — Static WCAG</td><td>{@link #check}</td>
 *       <td>axe-core rules, iFrames, shadow DOM</td></tr>
 *   <tr><td>2 — Interaction</td><td>{@link #checkInteraction}</td>
 *       <td>Keyboard nav, touch targets (2.5.5), focus visible (2.4.7),
 *           skip nav (2.4.1), text spacing (1.4.12), zoom/reflow (1.4.10)</td></tr>
 *   <tr><td>3 — WCAG 2.2</td><td>{@link #checkWcag22}</td>
 *       <td>Focus appearance (2.4.11), dragging movements (2.5.7),
 *           target size minimum (2.5.8)</td></tr>
 *   <tr><td>4 — Structural / SR</td><td>{@link #checkStructural}</td>
 *       <td>Page title (2.4.2), language (3.1.1), heading hierarchy (1.3.1/2.4.6),
 *           landmark regions (1.3.6/2.4.1), link text (2.4.4), duplicate IDs (4.1.1)</td></tr>
 *   <tr><td>5 — Motion / Media</td><td>{@link #checkAutoPlayMedia}, {@link #checkReducedMotion}</td>
 *       <td>Auto-play audio/video (1.4.2), prefers-reduced-motion (2.3.3)</td></tr>
 *   <tr><td>ALL</td><td>{@link #checkFullSuite}</td>
 *       <td>Runs all five layers in one call</td></tr>
 * </table>
 *
 * <h3>Needs Review</h3>
 * Some findings (dragging movements, generic link text, reduced-motion) are flagged
 * with {@code needsReview=true} on the {@link InteractionIssue}, matching BrowserStack
 * Spectra's "Assisted Test" / "Needs Review" category — a human must confirm them.
 *
 * <h3>Configuration ({@link A11yConfig})</h3>
 * <pre>
 * accessibility.checking.enabled=true        # master on/off switch
 * accessibility.fail.on.violation=false      # throw on any violation, or log only
 * accessibility.wcag.tags=wcag2a,wcag2aa     # axe-core tag set
 * accessibility.output.dir=test-output/accessibility  # artifact root (optional)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Full Spectra-equivalent scan (all layers)
 * AccessibilityChecker.checkFullSuite(driver, "Login Page");
 *
 * // axe-core only
 * AccessibilityChecker.check(driver, "Login Page");
 *
 * // WCAG 2.2 new criteria only
 * AccessibilityChecker.checkWcag22(driver, "Form Page");
 *
 * // Always assert (throws AccessibilityViolationException on any violation)
 * AccessibilityChecker.assertNoViolations(driver, "Submit Page");
 *
 * // Plug your own reporter (ExtentReports, Allure, etc.)
 * AccessibilityChecker.setReporter(new MyExtentReporter());
 * </pre>
 */
public class AccessibilityChecker {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilityChecker.class);
    private static final ObjectMapper REPORT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    /**
     * Returns the configured output directory, resolved fresh on every call so that
     * {@code -Daccessibility.output.dir} set after class-load (e.g. in a
     * {@code @BeforeClass} or via Surefire {@code <systemPropertyVariables>}) is
     * always respected.  Previously this was a {@code static final} field frozen at
     * class-init time, which caused all output to land in the default directory even
     * when the consumer overrode it at runtime.
     */
    static Path reportDir() {
        return A11yConfig.outputDir();
    }

    /** Derived convenience: the live-updating Excel workbook path. */
    static Path excelReportPath() {
        return reportDir().resolve("accessibility-report.xlsx");
    }

    /** Pluggable reporting sink. Defaults to SLF4J; never null. */
    private static volatile A11yReporter reporter = new Slf4jReporter();

    // In-memory accumulation — populated during the run, Excel is rewritten after every scan.
    // Object[] layout for EXCEL_SUMMARY_ROWS:
    //   [0] outcome  [1] pageName  [2] pageUrl   [3] issueCount
    //   [4] engine   [5] tags      [6] iframes   [7] shadowDom  [8] timestamp
    private static final List<Object[]> EXCEL_SUMMARY_ROWS    = Collections.synchronizedList(new ArrayList<>());
    // Object[] layout for EXCEL_ISSUE_ROWS:
    //   [0] impact   [1] issueType  [2] description  [3] component
    //   [4] element  [5] ruleId     [6] wcag          [7] pageName
    //   [8] pageUrl  [9] engine     [10] helpUrl      [11] timestamp
    private static final List<Object[]> EXCEL_ISSUE_ROWS = Collections.synchronizedList(new ArrayList<>());

    /**
     * In-memory accumulation of normalized findings (see {@link AccessibilityFinding}),
     * populated alongside the JSON/Excel artifacts. Additive — does not affect any
     * existing output format.
     */
    private static final List<AccessibilityFinding> FINDINGS = Collections.synchronizedList(new ArrayList<>());

    /**
     * Returns an unmodifiable snapshot of every {@link AccessibilityFinding} recorded
     * so far in this JVM (axe-core Layer 1 violations + incomplete, plus Interaction/
     * WCAG 2.2/Structural/Motion layer issues). Useful for baseline/regression
     * comparison or a custom dashboard without parsing JSON/Excel artifacts.
     */
    public static List<AccessibilityFinding> getFindings() {
        synchronized (FINDINGS) {
            return Collections.unmodifiableList(new ArrayList<>(FINDINGS));
        }
    }

    /** Clears accumulated {@link AccessibilityFinding}s (e.g. between test suites). */
    public static void resetFindings() {
        FINDINGS.clear();
    }

    /** Maps axe-core violations + incomplete rules for one scan into {@link AccessibilityFinding}s. */
    private static void recordFindings(List<Rule> violations, List<Rule> incomplete, String pageUrl, Results results) {
        String engineVersion = (results != null && results.getTestEngine() != null)
                ? results.getTestEngine().getVersion() : null;
        for (Rule rule : violations) {
            FINDINGS.addAll(AccessibilityFinding.fromAxeRule(rule, pageUrl, null, engineVersion, false));
        }
        for (Rule rule : incomplete) {
            FINDINGS.addAll(AccessibilityFinding.fromAxeRule(rule, pageUrl, null, engineVersion, true));
        }
    }

    // -----------------------------------------------------------------------
    // Reporter wiring
    // -----------------------------------------------------------------------

    /**
     * Replaces the reporting sink. Pass {@code null} to silence inline output
     * (a {@link NoOpReporter} is installed). File artifacts are unaffected.
     */
    public static void setReporter(A11yReporter newReporter) {
        reporter = (newReporter != null) ? newReporter : new NoOpReporter();
    }

    /** Returns the active reporting sink (never null). */
    public static A11yReporter getReporter() {
        return reporter;
    }

    // -----------------------------------------------------------------------
    // Config helpers
    // -----------------------------------------------------------------------

    /** Returns {@code true} when accessibility checking is enabled. */
    public static boolean isEnabled() {
        return A11yConfig.getBoolean("accessibility.checking.enabled", false);
    }

    /** Returns {@code true} when the scan should throw on any accessibility violation. */
    public static boolean isFailOnViolation() {
        return A11yConfig.getBoolean("accessibility.fail.on.violation", false);
    }

    /** Returns the configured WCAG tag array; defaults to {@code wcag2a,wcag2aa}. */
    private static String[] getWcagTags() {
        String raw = A11yConfig.get("accessibility.wcag.tags");
        if (raw == null || raw.trim().isEmpty()) {
            return new String[]{"wcag2a", "wcag2aa"};
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** Integer config lookup with a default (never throws on bad input). */
    private static int intConfig(String key, int def) {
        String v = A11yConfig.get(key);
        if (v == null || v.trim().isEmpty()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Default CSS selectors for "still loading" indicators that should disappear
     * before a page is scanned. Tuned for Salesforce Lightning (SLDS spinner,
     * {@code lightning-spinner}, Aura {@code .loadingIndicator}) plus generic
     * patterns. Override with {@code accessibility.scan.wait.spinner.selectors}.
     */
    private static final String DEFAULT_SPINNER_SELECTORS =
            ".slds-spinner,lightning-spinner,.loadingIndicator,.forceCommunityLoading,"
          + "[role=progressbar],.spinner,.loading,.loader,[aria-busy=true]";

    /**
     * Waits until the page is settled before scanning — critical for SPA / async
     * frameworks (Salesforce Lightning lazy-loads components behind spinners, so an
     * immediate scan would see a partial DOM).
     *
     * <p>Waits (up to a bounded timeout) for, in order: {@code document.readyState}
     * to be {@code complete}, any visible loading spinner to disappear, and the DOM
     * element count to stop changing between polls (mutation settle).</p>
     *
     * <p>Controlled by config (all optional):</p>
     * <ul>
     *   <li>{@code accessibility.scan.wait.enabled} — default {@code true}</li>
     *   <li>{@code accessibility.scan.wait.timeout.ms} — default {@code 5000}</li>
     *   <li>{@code accessibility.scan.wait.spinner.selectors} — CSS, comma-separated</li>
     * </ul>
     *
     * <p>Best-effort and non-fatal: any error or timeout simply proceeds with the scan.</p>
     */
    private static void waitForDomStable(WebDriver driver, String pageName) {
        if (!A11yConfig.getBoolean("accessibility.scan.wait.enabled", true)) {
            return;
        }
        long timeoutMs = intConfig("accessibility.scan.wait.timeout.ms", 5000);
        if (timeoutMs <= 0) return;
        String spinnerSel = A11yConfig.get("accessibility.scan.wait.spinner.selectors", DEFAULT_SPINNER_SELECTORS);

        long deadline = System.currentTimeMillis() + timeoutMs;
        final long pollMs = 200L;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long lastCount = -1;
            int stablePolls = 0;
            while (System.currentTimeMillis() < deadline) {
                Object state = js.executeScript(
                    "var sel=arguments[0];" +
                    "var ready=document.readyState==='complete';" +
                    "var spinner=false;" +
                    "try{var nodes=document.querySelectorAll(sel);" +
                    "for(var i=0;i<nodes.length;i++){var r=nodes[i].getBoundingClientRect();" +
                    "if(r.width>0&&r.height>0){spinner=true;break;}}}catch(e){}" +
                    "return{ready:ready,spinner:spinner,count:document.getElementsByTagName('*').length};",
                    spinnerSel);

                if (!(state instanceof java.util.Map)) break;
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
                boolean ready = Boolean.TRUE.equals(m.get("ready"));
                boolean spinner = Boolean.TRUE.equals(m.get("spinner"));
                long count = (m.get("count") instanceof Number) ? ((Number) m.get("count")).longValue() : -1;

                boolean settled = (count == lastCount);
                lastCount = count;

                if (ready && !spinner && settled) {
                    if (++stablePolls >= 2) { // two consecutive stable reads
                        logger.debug("[SCAN WAIT] '{}' settled (DOM stable, no spinner)", pageName);
                        return;
                    }
                } else {
                    stablePolls = 0;
                }
                try { Thread.sleep(pollMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
            logger.debug("[SCAN WAIT] '{}' wait window ({}ms) elapsed — proceeding with scan", pageName, timeoutMs);
        } catch (Exception e) {
            logger.debug("[SCAN WAIT] Stability wait skipped for '{}': {}", pageName, e.getMessage());
        }
    }


    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Runs a comprehensive accessibility scan on the current page using configured WCAG tags.
     *
     * @param driver   active WebDriver pointing at the page to scan
     * @param pageName human-readable label used in report output
     * @return number of violations found (0 when disabled or clean)
     */
    public static int check(WebDriver driver, String pageName) {
        return checkWithTags(driver, pageName, getWcagTags());
    }

    /**
     * Runs a comprehensive accessibility scan with the supplied axe-core tag set.
     * Scope: main document + all detected iFrames + shadow DOM (traversed by axe-core).
     *
     * @param driver   active WebDriver
     * @param pageName label for reports
     * @param tags     axe-core rule tags (e.g., "wcag2a", "wcag2aa", "best-practice")
     * @return violation count
     */
    public static int checkWithTags(WebDriver driver, String pageName, String... tags) {
        String pageUrl = driver.getCurrentUrl();
        if (!isEnabled()) {
            logger.debug("Accessibility checking disabled — skipping scan for: {}", pageName);
            writeScanArtifact(pageName, pageUrl, tags, Collections.emptyList(), "SKIPPED", "accessibility.checking.enabled=false", null);
            return 0;
        }

        logger.info("[A11Y SCAN] Starting on '{}' ({}) with tags: {} (main doc + iFrames + Shadow DOM)",
                pageName, pageUrl, Arrays.toString(tags));

        try {
            waitForDomStable(driver, pageName);
            verifyAxeInjection(driver, pageName);
            AxeBuilder axeBuilder = new AxeBuilder().withTags(Arrays.asList(tags));
            ScanScopeInfo scopeInfo = detectScopeInfo(driver);
            Results results = axeBuilder.analyze(driver);

            List<Rule> violations   = results.getViolations()   != null ? results.getViolations()   : new ArrayList<>();
            List<Rule> incomplete   = results.getIncomplete()   != null ? results.getIncomplete()   : new ArrayList<>();
            List<Rule> passes       = results.getPasses()       != null ? results.getPasses()       : new ArrayList<>();
            List<Rule> inapplicable = results.getInapplicable() != null ? results.getInapplicable() : new ArrayList<>();

            int totalRulesEvaluated = violations.size() + incomplete.size() + passes.size() + inapplicable.size();
            logger.info("[A11Y RESULTS] '{}' — violations={}, incomplete={}, passes={}, inapplicable={}, total={}",
                    pageName, violations.size(), incomplete.size(), passes.size(), inapplicable.size(), totalRulesEvaluated);

            if (totalRulesEvaluated == 0) {
                String msg = "[A11Y WARNING] axe-core returned 0 results across ALL categories for '" + pageName
                        + "'. This strongly indicates axe.js failed to inject or the page context is incorrect. "
                        + "Check for CSP headers, page-not-loaded state, or driver focus on a sub-frame.";
                logger.error(msg);
                reporter.fail(msg);
                writeScanArtifact(pageName, pageUrl, tags, Collections.emptyList(), "INJECTION_FAILURE",
                        "axe returned 0 results across all categories", scopeInfo);
                return 0;
            }

            if (!incomplete.isEmpty()) {
                logger.warn("[A11Y INCOMPLETE] {} rule(s) could not be fully evaluated (cross-origin iFrames / shadow DOM not accessible): {}",
                        incomplete.size(),
                        incomplete.stream().map(Rule::getId).collect(Collectors.joining(", ")));
            }

            if (scopeInfo.totalIFramesIncluded > 0) {
                scanIndividualFrames(driver, tags, violations, scopeInfo);
            }

            logFindings(pageName, violations, incomplete, tags, scopeInfo);
            logToConsole(pageName, violations);
            writeScanArtifact(pageName, pageUrl, tags, violations, violations.isEmpty() ? "PASS" : "FAIL", null, scopeInfo);
            recordFindings(violations, incomplete, pageUrl, results);

            if (!violations.isEmpty() && isFailOnViolation()) {
                throw new AccessibilityViolationException(buildFailureSummary(pageName, violations));
            }
            return violations.size();

        } catch (AccessibilityViolationException ave) {
            throw ave; // intentional failure — let it propagate
        } catch (Exception e) {
            String msg = "[A11Y ERROR] Scan threw an exception for '" + pageName + "': " + e.getMessage()
                    + " — returning 0 violations. Fix the scan error before trusting these results.";
            logger.error(msg, e);
            writeScanArtifact(pageName, pageUrl, tags, Collections.emptyList(), "ERROR", e.getMessage(), null);
            reporter.fail(msg + "<br/><pre>" + e + "</pre>");
            return 0;
        }
    }

    /**
     * Always asserts no violations, ignoring {@code accessibility.fail.on.violation}.
     * Throws {@link AccessibilityViolationException} when violations are found — which
     * surfaces as a failure in both JUnit and TestNG.
     * <p>If accessibility checking is disabled this is a no-op.</p>
     */
    public static void assertNoViolations(WebDriver driver, String pageName) {
        String pageUrl = driver.getCurrentUrl();
        if (!isEnabled()) {
            writeScanArtifact(pageName, pageUrl, getWcagTags(), Collections.emptyList(), "SKIPPED", "accessibility.checking.enabled=false", null);
            return;
        }
        String[] tags = getWcagTags();
        logger.info("[ASSERT] Checking zero accessibility violations on '{}' ({}) with tags: {}", pageName, pageUrl, Arrays.toString(tags));
        try {
            waitForDomStable(driver, pageName);
            verifyAxeInjection(driver, pageName);
            AxeBuilder axeBuilder = new AxeBuilder().withTags(Arrays.asList(tags));
            ScanScopeInfo scopeInfo = detectScopeInfo(driver);
            Results results = axeBuilder.analyze(driver);
            List<Rule> violations   = results.getViolations()   != null ? results.getViolations()   : new ArrayList<>();
            List<Rule> incomplete   = results.getIncomplete()   != null ? results.getIncomplete()   : new ArrayList<>();
            List<Rule> passes       = results.getPasses()       != null ? results.getPasses()       : new ArrayList<>();
            List<Rule> inapplicable = results.getInapplicable() != null ? results.getInapplicable() : new ArrayList<>();
            int total = violations.size() + incomplete.size() + passes.size() + inapplicable.size();
            logger.info("[A11Y RESULTS] '{}' — violations={}, incomplete={}, passes={}, inapplicable={}, total={}",
                    pageName, violations.size(), incomplete.size(), passes.size(), inapplicable.size(), total);
            if (total == 0) throw new RuntimeException("axe-core returned 0 results across all categories — possible injection failure");
            if (scopeInfo.totalIFramesIncluded > 0) scanIndividualFrames(driver, tags, violations, scopeInfo);
            logFindings(pageName, violations, incomplete, tags, scopeInfo);
            logToConsole(pageName, violations);
            writeScanArtifact(pageName, pageUrl, tags, violations, violations.isEmpty() ? "PASS" : "FAIL", null, scopeInfo);
            recordFindings(violations, incomplete, pageUrl, results);
            if (!violations.isEmpty()) throw new AccessibilityViolationException(buildFailureSummary(pageName, violations));
        } catch (AccessibilityViolationException ave) {
            throw ave;
        } catch (Exception e) {
            writeScanArtifact(pageName, pageUrl, tags, Collections.emptyList(), "ERROR", e.getMessage(), null);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    /**
     * Verifies axe.js can be injected into the current page by executing a
     * lightweight probe script. Throws {@link RuntimeException} when the probe fails.
     */
    private static void verifyAxeInjection(WebDriver driver, String pageName) {
        try {
            Object readyState = ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState");
            if (!"complete".equals(readyState)) {
                logger.warn("[AXE INJECT CHECK] document.readyState='{}' for '{}' — page may not be fully loaded; scan results may be incomplete",
                        readyState, pageName);
            }
            ((JavascriptExecutor) driver).executeScript("return 1 + 1;");
            logger.debug("[AXE INJECT CHECK] JS execution OK for '{}'", pageName);
        } catch (Exception e) {
            throw new RuntimeException("Cannot execute JavaScript on page '" + pageName
                    + "' — axe injection will fail: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Scans every iFrame individually — <b>including nested iFrames</b> — by
     * switching the driver context into each frame and running a full axe pass.
     * This is the key technique for reaching cross-origin and deeply-nested iFrames
     * (common in Salesforce: Visualforce inside Lightning, canvas apps, etc.).
     *
     * <p>Recursion depth is bounded by {@code accessibility.scan.iframe.max.depth}
     * (default {@code 3}). Violations whose rule ID is already present are skipped
     * (dedup by rule ID). The driver is always returned to {@code defaultContent}
     * before returning.</p>
     */
    private static void scanIndividualFrames(WebDriver driver, String[] tags,
                                             List<Rule> allViolations, ScanScopeInfo scopeInfo) {
        if (scopeInfo.totalIFramesIncluded == 0) return;

        int maxDepth = intConfig("accessibility.scan.iframe.max.depth", 3);
        logger.info("[FRAME SCAN] Scanning iFrames recursively (max depth {}) to cover nested / cross-origin contexts",
                maxDepth);

        Set<String> seenRuleIds = new HashSet<>();
        for (Rule v : allViolations) {
            seenRuleIds.add(v.getId());
        }

        // counters: [0]=scanned, [1]=skipped, [2]=newViolations
        int[] counters = new int[]{0, 0, 0};
        try {
            scanFramesRecursive(driver, tags, allViolations, seenRuleIds, 1, maxDepth, "", counters);
        } finally {
            try { driver.switchTo().defaultContent(); } catch (Exception ignored) { /* best-effort */ }
        }

        scopeInfo.iframesScannedIndividually = counters[0];
        scopeInfo.iframesSkippedCrossOrigin  = counters[1];

        logger.info("[FRAME SCAN] Complete — {} frame(s) scanned (incl. nested), {} cross-origin/skipped, {} new violation(s) found",
                counters[0], counters[1], counters[2]);
    }

    /**
     * Depth-first recursion through the iFrame tree of the <em>current</em> driver
     * context. Uses {@code switchTo().parentFrame()} (not {@code defaultContent})
     * to step back up exactly one level so nested frames are traversed correctly.
     */
    private static void scanFramesRecursive(WebDriver driver, String[] tags, List<Rule> allViolations,
                                            Set<String> seenRuleIds, int depth, int maxDepth,
                                            String pathPrefix, int[] counters) {
        if (depth > maxDepth) return;

        int frameCount;
        try {
            frameCount = ((Number) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelectorAll('iframe').length")).intValue();
        } catch (Exception e) {
            return; // current context has no readable DOM (e.g. cross-origin) — nothing to recurse
        }

        for (int i = 0; i < frameCount; i++) {
            String framePath = pathPrefix.isEmpty() ? String.valueOf(i + 1) : pathPrefix + "." + (i + 1);
            try {
                driver.switchTo().frame(i);
                try {
                    Results frameResults = new AxeBuilder()
                            .withTags(Arrays.asList(tags))
                            .analyze(driver);

                    List<Rule> frameViolations = frameResults.getViolations() != null
                            ? frameResults.getViolations() : new ArrayList<>();

                    for (Rule v : frameViolations) {
                        if (!seenRuleIds.contains(v.getId())) {
                            allViolations.add(v);
                            seenRuleIds.add(v.getId());
                            counters[2]++;
                            logger.warn("[FRAME SCAN] New violation in iFrame [{}] (depth {}): [{}] {} — {}",
                                    framePath, depth,
                                    v.getImpact() != null ? v.getImpact().toUpperCase() : "?",
                                    v.getId(), v.getDescription());
                        }
                    }
                    counters[0]++;
                    logger.debug("[FRAME SCAN] iFrame [{}] scanned — {} violation(s)", framePath, frameViolations.size());
                } catch (Exception axeEx) {
                    counters[1]++;
                    logger.info("[FRAME SCAN] iFrame [{}] not scannable (cross-origin or hidden): {}",
                            framePath, axeEx.getMessage());
                }

                // Recurse into this frame's own child iFrames before stepping back up.
                scanFramesRecursive(driver, tags, allViolations, seenRuleIds, depth + 1, maxDepth, framePath, counters);

            } catch (Exception switchEx) {
                counters[1]++;
                logger.info("[FRAME SCAN] Could not switch into iFrame [{}]: {}", framePath, switchEx.getMessage());
            } finally {
                try { driver.switchTo().parentFrame(); } catch (Exception ignored) { /* best-effort */ }
            }
        }
    }



    /**
     * Detects iFrames and shadow DOM for scope reporting only. axe-core 4.x already
     * traverses same-origin iFrames and shadow DOM automatically when no include
     * context is set, so we collect metadata only and leave the scan at full-page scope.
     */
    private static ScanScopeInfo detectScopeInfo(WebDriver driver) {
        ScanScopeInfo scopeInfo = new ScanScopeInfo();
        scopeInfo.mainDocumentScanned = true;

        try {
            int iframeCount = ((Number) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelectorAll('iframe').length"))
                .intValue();

            if (iframeCount > 0) {
                logger.info("[IFRAME DETECTION] Found {} iFrame(s) — axe-core will traverse them automatically", iframeCount);
                for (int i = 0; i < iframeCount; i++) {
                    scopeInfo.iframesIncluded.add("iframe:nth-of-type(" + (i + 1) + ")");
                }
                scopeInfo.totalIFramesIncluded = iframeCount;
            } else {
                logger.info("[IFRAME DETECTION] No iFrames detected on this page");
            }
        } catch (Exception e) {
            logger.debug("[IFRAME DETECTION] Could not count iFrames: {}", e.getMessage());
        }

        try {
            Object shadowResult = ((JavascriptExecutor) driver)
                .executeScript(
                    // Count custom-element-style components across common Salesforce
                    // namespaces (default 'c-', base 'lightning-'/'force-'/'slds-',
                    // plus a generic count of elements that actually expose an OPEN
                    // shadowRoot — the most reliable shadow-DOM signal).
                    "var custom=document.querySelectorAll(" +
                    "  'c-*,lightning-*,force-*,slds-*,[data-aura-rendered-by]'); " +
                    "var shadowHosts=0;" +
                    "var all=document.querySelectorAll('*');" +
                    "for(var i=0;i<all.length;i++){if(all[i].shadowRoot)shadowHosts++;}" +
                    "return { " +
                    "  lwcComponents: custom.length, " +
                    "  shadowHosts: shadowHosts, " +
                    "  hasSalesforceLightning: !!(window.sforce||window.$A||" +
                    "    document.querySelector('[data-aura-rendered-by]')) " +
                    "};"
                );

            if (shadowResult instanceof java.util.Map) {
                java.util.Map<?, ?> shadowMap = (java.util.Map<?, ?>) shadowResult;
                Object lwcRaw = shadowMap.get("lwcComponents");
                int lwcCount = lwcRaw instanceof Number ? ((Number) lwcRaw).intValue() : 0;
                Object shadowRaw = shadowMap.get("shadowHosts");
                int shadowHosts = shadowRaw instanceof Number ? ((Number) shadowRaw).intValue() : 0;
                if (lwcCount > 0 || shadowHosts > 0) {
                    scopeInfo.lwcComponentsDetected = lwcCount;
                    scopeInfo.shadowDomEnabled = true;
                    logger.info("[SHADOW DOM DETECTION] {} custom component(s), {} open shadow root(s) — "
                            + "axe-core and the custom checks both traverse open shadow DOM", lwcCount, shadowHosts);
                }
                Boolean hasSfLight = (Boolean) shadowMap.get("hasSalesforceLightning");
                if (hasSfLight != null && hasSfLight) {
                    scopeInfo.salesforceLightningDetected = true;
                    logger.info("[SALESFORCE] Salesforce Lightning/Aura detected — all iFrames will be scanned");
                }
            }
        } catch (Exception e) {
            logger.debug("Could not detect shadow DOM details: {}", e.getMessage());
        }

        return scopeInfo;
    }

    private static void logFindings(String pageName, List<Rule> violations, List<Rule> incomplete,
                                    String[] tags, ScanScopeInfo scopeInfo) {
        String scopeMsg = "Scanned: main document";
        if (scopeInfo != null && scopeInfo.totalIFramesIncluded > 0) {
            scopeMsg += " + " + scopeInfo.totalIFramesIncluded + " iFrame(s) detected";
            if (scopeInfo.iframesScannedIndividually > 0) {
                scopeMsg += " (" + scopeInfo.iframesScannedIndividually + " individually scanned";
                if (scopeInfo.iframesSkippedCrossOrigin > 0) {
                    scopeMsg += ", " + scopeInfo.iframesSkippedCrossOrigin + " cross-origin/skipped";
                }
                scopeMsg += ")";
            }
        }
        if (scopeInfo != null && scopeInfo.shadowDomEnabled) {
            scopeMsg += " + shadow DOM";
        }
        reporter.info("<b>Scan Scope:</b> " + scopeMsg + " (tags: " + Arrays.toString(tags) + ")");

        if (incomplete != null && !incomplete.isEmpty()) {
            reporter.warn("<b>" + incomplete.size() + " rule(s) incomplete</b> (cross-origin iFrames or inaccessible shadow DOM): "
                    + incomplete.stream().map(Rule::getId).collect(Collectors.joining(", ")));
        }

        if (violations.isEmpty()) {
            reporter.info("<b>Accessibility scan PASSED</b> — " + pageName);
            return;
        }

        // ── Apply noise filter (allowlist + impact threshold) ─────────────────
        // JSON artifacts always contain the full unfiltered violation list.
        // Only the live reporter output respects the session noise settings.
        List<Rule> reportable = violations.stream()
                .filter(v -> A11ySessionManager.isReportable(v.getId(), v.getImpact()))
                .collect(Collectors.toList());
        int suppressed = violations.size() - reportable.size();

        reporter.warn("<b>Accessibility scan — " + violations.size()
                + " violation(s)</b> on <b>" + pageName + "</b>"
                + (suppressed > 0
                   ? " <em>(" + suppressed + " suppressed by noise threshold / allowlist)</em>"
                   : ""));

        for (Rule v : reportable) {
            String impact = v.getImpact() != null ? v.getImpact().toUpperCase() : "UNKNOWN";
            String affectedElements = v.getNodes().stream()
                    .map(n -> n.getHtml() != null ? escapeHtml(n.getHtml()) : "(element)")
                    .limit(5)
                    .collect(Collectors.joining("<br/>"));

            String entry = "<details>"
                    + "<summary>[" + impact + "] " + escapeHtml(v.getDescription()) + "</summary>"
                    + "<b>Rule ID:</b> " + v.getId() + "<br/>"
                    + "<b>Help:</b> <a href='" + v.getHelpUrl() + "' target='_blank'>" + v.getHelpUrl() + "</a><br/>"
                    + "<b>Affected elements (up to 5):</b><br/><code>" + affectedElements + "</code>"
                    + "</details>";

            if ("CRITICAL".equals(impact) || "SERIOUS".equals(impact)) {
                reporter.fail(entry);
            } else {
                reporter.warn(entry);
            }
        }
    }

    private static void logToConsole(String pageName, List<Rule> violations) {
        if (violations.isEmpty()) {
            logger.info("Accessibility scan PASSED — {}", pageName);
            return;
        }
        logger.warn("Accessibility scan found {} violation(s) on '{}':", violations.size(), pageName);
        for (Rule v : violations) {
            logger.warn("  [{}] {} — {} | help: {}",
                    v.getImpact() != null ? v.getImpact().toUpperCase() : "?",
                    v.getId(),
                    v.getDescription(),
                    v.getHelpUrl());
        }
    }

    private static String buildFailureSummary(String pageName, List<Rule> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("Accessibility violations found on '").append(pageName).append("':\n");
        for (Rule v : violations) {
            sb.append("  [").append(v.getImpact() != null ? v.getImpact().toUpperCase() : "?").append("] ")
                    .append(v.getId()).append(": ").append(v.getDescription())
                    .append(" -> ").append(v.getHelpUrl()).append("\n");
        }
        return sb.toString();
    }

    private static String escapeHtml(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void writeScanArtifact(String pageName,
                                          String pageUrl,
                                          String[] tags,
                                          List<Rule> violations,
                                          String outcome,
                                          String errorMessage,
                                          ScanScopeInfo scopeInfo) {
        try {
            Files.createDirectories(reportDir());

            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            String safePageName = sanitizeFileName(pageName);
            String fileName = timestamp + "_" + safePageName + "_a11y.json";
            Path filePath = reportDir().resolve(fileName);

            ObjectNode root = REPORT_MAPPER.createObjectNode();
            root.put("timestamp", timestamp);
            root.put("pageName", pageName);
            root.put("outcome", outcome);
            root.put("enabled", isEnabled());
            root.put("failOnViolation", isFailOnViolation());
            root.put("violationCount", violations.size());
            root.put("threadId", Thread.currentThread().getId());

            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                root.put("error", errorMessage);
            }

            if (scopeInfo != null) {
                ObjectNode scopeNode = root.putObject("scanScope");
                scopeNode.put("mainDocumentScanned",         scopeInfo.mainDocumentScanned);
                scopeNode.put("totalIFramesDetected",        scopeInfo.totalIFramesIncluded);
                scopeNode.put("iframesScannedIndividually",  scopeInfo.iframesScannedIndividually);
                scopeNode.put("iframesSkippedCrossOrigin",   scopeInfo.iframesSkippedCrossOrigin);
                scopeNode.put("shadowDomEnabled",            scopeInfo.shadowDomEnabled);
                scopeNode.put("lwcComponentsDetected",       scopeInfo.lwcComponentsDetected);
                scopeNode.put("salesforceLightningDetected", scopeInfo.salesforceLightningDetected);
                ArrayNode iframesNode = scopeNode.putArray("iframeSelectors");
                for (String iframe : scopeInfo.iframesIncluded) {
                    iframesNode.add(iframe);
                }
            }

            ArrayNode tagsNode = root.putArray("tags");
            for (String tag : tags) {
                tagsNode.add(tag);
            }

            ArrayNode violationsNode = root.putArray("violations");
            for (Rule violation : violations) {
                ObjectNode v = violationsNode.addObject();
                v.put("id", violation.getId());
                v.put("description", violation.getDescription());
                v.put("impact", violation.getImpact() != null ? violation.getImpact().toUpperCase() : "UNKNOWN");
                v.put("help", violation.getHelp());
                v.put("helpUrl", violation.getHelpUrl());
                String wcagCriterion = AccessibilityFinding.extractWcagCriterion(
                        violation.getTags() != null ? violation.getTags() : Collections.emptyList());
                v.put("wcagCriterion", wcagCriterion != null ? wcagCriterion : "");
                v.put("confidence", AccessibilityFinding.CONFIDENCE_VIOLATION);

                ArrayNode nodes = v.putArray("affectedElements");
                violation.getNodes().stream()
                        .limit(20)
                        .forEach(node -> nodes.add(node.getHtml() != null ? node.getHtml() : "(element)"));
            }

            REPORT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), root);

            String relativePath = "accessibility/" + fileName;
            reporter.info("Artifact [" + outcome + "]: <a href='" + relativePath + "' target='_blank'>" + relativePath + "</a>");
            logger.info("Accessibility artifact generated [{}]: {}", outcome, filePath.toAbsolutePath());

            appendSummaryLine(timestamp, pageName, outcome, violations.size(), errorMessage, tags, scopeInfo);

            // ── Excel accumulation ────────────────────────────────────────
            String tagsStr = String.join(", ", tags);
            int iframes = scopeInfo != null ? scopeInfo.totalIFramesIncluded : 0;
            String shadowDom = scopeInfo != null && scopeInfo.shadowDomEnabled ? "Yes" : "No";
            EXCEL_SUMMARY_ROWS.add(new Object[]{
                outcome, pageName, pageUrl, violations.size(), "axe-core", tagsStr, iframes, shadowDom, timestamp});
            for (Rule v : violations) {
                String affected = v.getNodes() != null ? v.getNodes().stream().limit(3)
                    .map(n -> n.getHtml() != null ? n.getHtml() : "(element)")
                    .collect(Collectors.joining(" | ")) : "";
                String impact = v.getImpact() != null ? v.getImpact().toUpperCase() : "UNKNOWN";
                EXCEL_ISSUE_ROWS.add(new Object[]{
                    impact,
                    deriveIssueType(impact, "axe-core"),
                    v.getDescription(),
                    extractComponent(affected),
                    affected,
                    v.getId(),
                    tagsStr,
                    pageName,
                    pageUrl,
                    "axe-core",
                    v.getHelpUrl(),
                    timestamp});
            }
            writeExcelReport();
        } catch (Exception e) {
            logger.warn("Unable to write accessibility artifact for '{}': {}", pageName, e.getMessage());
        }
    }

    private static void appendSummaryLine(String timestamp,
                                          String pageName,
                                          String outcome,
                                          int violationCount,
                                          String errorMessage,
                                          String[] tags,
                                          ScanScopeInfo scopeInfo) throws IOException {
        Path summaryPath = reportDir().resolve("accessibility-summary.jsonl");

        ObjectNode summaryNode = REPORT_MAPPER.createObjectNode();
        summaryNode.put("timestamp", timestamp);
        summaryNode.put("pageName", pageName);
        summaryNode.put("outcome", outcome);
        summaryNode.put("violationCount", violationCount);
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            summaryNode.put("error", errorMessage);
        }
        if (scopeInfo != null) {
            summaryNode.put("iframesScanned", scopeInfo.totalIFramesIncluded);
            summaryNode.put("shadowDomScanned", scopeInfo.shadowDomEnabled);
        }
        ArrayNode tagsNode = summaryNode.putArray("tags");
        for (String tag : tags) {
            tagsNode.add(tag);
        }

        String line = REPORT_MAPPER.writeValueAsString(summaryNode) + System.lineSeparator();
        Files.write(summaryPath, line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static String sanitizeFileName(String text) {
        String value = (text == null || text.trim().isEmpty()) ? "page" : text.trim();
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }
        return sanitized;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERACTION / LAYOUT CHECKS (beyond static axe-core)
    // Keyboard navigation, touch targets, focus visibility, skip navigation,
    // text spacing, zoom/reflow. These complement axe-core but do NOT replace
    // real assistive-technology (screen reader) validation.
    // ═══════════════════════════════════════════════════════════════════════

    /** Minimum touch-target size (WCAG 2.5.5 = 44 px). */
    private static final int TOUCH_TARGET_MIN_PX = 44;
    /** Max interactive elements to Tab through during keyboard-nav check. */
    private static final int KEYBOARD_NAV_MAX_ELEMENTS = 50;

    /**
     * Shared JavaScript prelude that makes the custom DOM checks
     * <b>Shadow-DOM-aware</b>. Plain {@code document.querySelectorAll} stops at
     * shadow boundaries, so on component frameworks (Salesforce Lightning / LWC,
     * Web Components) most interactive content would be invisible to these checks.
     *
     * <p>Defines two globals used by every custom check below:</p>
     * <ul>
     *   <li>{@code __a11yQSA(selector[, root])} — returns an {@code Array} of all
     *       matching elements in flattened tree order, recursing into every
     *       <em>open</em> {@code shadowRoot}. (Closed shadow roots are
     *       inaccessible to any script and cannot be traversed.)</li>
     *   <li>{@code __a11yQS(selector[, root])} — first match or {@code null}.</li>
     * </ul>
     *
     * <p>Prepend this to a script and replace {@code document.querySelectorAll(}
     * with {@code __a11yQSA(} (it returns an Array, so {@code .forEach}/{@code .map}/
     * {@code .slice} all work) and {@code document.querySelector(} with
     * {@code __a11yQS(}.</p>
     */
    private static final String DEEP_DOM_JS =
        "var __a11yQSA=function(selector,root){" +
        "  root=root||document;" +
        "  var out=[];" +
        "  var walk=function(node){" +
        "    var ch=node.children;" +
        "    if(!ch)return;" +
        "    for(var i=0;i<ch.length;i++){" +
        "      var el=ch[i];" +
        "      try{if(el.matches&&el.matches(selector))out.push(el);}catch(e){}" +
        "      if(el.shadowRoot)walk(el.shadowRoot);" +
        "      walk(el);" +
        "    }" +
        "  };" +
        "  walk(root);" +
        "  return out;" +
        "};" +
        "var __a11yQS=function(selector,root){" +
        "  var a=__a11yQSA(selector,root);return a.length?a[0]:null;" +
        "};";

    /**
     * Runs all interaction/layout checks and returns the total issue count.
     * Piggybacks on the master {@code accessibility.checking.enabled} switch.
     */
    public static int checkInteraction(WebDriver driver, String pageName) {
        if (!isEnabled()) {
            logger.debug("Accessibility checking disabled — skipping interaction checks for: {}", pageName);
            return 0;
        }
        logger.info("[INTERACTION] Running all interaction/layout checks on '{}'", pageName);
        waitForDomStable(driver, pageName);
        int total = 0;
        total += checkKeyboardNavigation(driver, pageName);
        total += checkTouchTargets(driver, pageName);
        total += checkFocusVisibility(driver, pageName);
        total += checkSkipNavigation(driver, pageName);
        total += checkTextSpacing(driver, pageName);
        total += checkZoomReflow(driver, pageName);
        logger.info("[INTERACTION] '{}' — total interaction/layout issues: {}", pageName, total);
        return total;
    }

    /**
     * Simulates keyboard Tab navigation — checks focus order, focus traps,
     * and aria-hidden elements in the Tab sequence.
     * <p><b>WCAG:</b> 2.1.1 Keyboard · 2.1.2 No Keyboard Trap · 2.4.3 Focus Order</p>
     */
    public static int checkKeyboardNavigation(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[KEYBOARD NAV] Checking Tab navigation on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> focusable = (List<Map<String, Object>>) js.executeScript(
                DEEP_DOM_JS +
                "var sel='a[href],button:not([disabled]),input:not([disabled])," +
                "select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex=\"-1\"])," +
                "[role=\"button\"]:not([disabled]),[role=\"link\"],[role=\"checkbox\"]:not([disabled])," +
                "[role=\"radio\"]:not([disabled]),[role=\"tab\"]:not([disabled])';" +
                "return __a11yQSA(sel).slice(0,arguments[0]).map(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  return{html:el.outerHTML.substring(0,150)," +
                "    tabindex:el.getAttribute('tabindex')," +
                "    visible:r.width>0&&r.height>0," +
                "    ariaHidden:el.getAttribute('aria-hidden')==='true'};});",
                KEYBOARD_NAV_MAX_ELEMENTS);

            if (focusable == null || focusable.isEmpty()) {
                issues.add(new InteractionIssue("keyboard-no-focusable-elements", "SERIOUS",
                    "WCAG 2.1.1", "No focusable interactive elements found — page may not be keyboard accessible",
                    "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/keyboard.html"));
            } else {
                for (Map<String, Object> el : focusable) {
                    if (Boolean.TRUE.equals(el.get("ariaHidden"))) {
                        issues.add(new InteractionIssue("keyboard-aria-hidden-focusable", "SERIOUS",
                            "WCAG 2.1.1",
                            "Focusable element has aria-hidden=true — receives keyboard focus but invisible to screen readers",
                            truncateEl((String) el.get("html")),
                            "https://www.w3.org/WAI/WCAG21/Understanding/keyboard.html"));
                    }
                    Object tabindex = el.get("tabindex");
                    if (!Boolean.TRUE.equals(el.get("visible")) && tabindex != null && !"-1".equals(tabindex.toString())) {
                        issues.add(new InteractionIssue("keyboard-invisible-focusable", "MODERATE",
                            "WCAG 2.4.3",
                            "Off-screen/hidden element is in the Tab order (tabindex=" + tabindex + ")",
                            truncateEl((String) el.get("html")),
                            "https://www.w3.org/WAI/WCAG21/Understanding/focus-order.html"));
                    }
                }
                try {
                    Actions actions = new Actions(driver);
                    js.executeScript("document.body.focus()");
                    String prevHtml = "";
                    int stuck = 0;
                    int tabCount = Math.min(focusable.size(), 20);
                    for (int i = 0; i < tabCount; i++) {
                        actions.sendKeys(Keys.TAB).perform();
                        String cur = (String) js.executeScript(
                            "var e=document.activeElement;return e?e.outerHTML.substring(0,150):''");
                        if (cur != null && cur.equals(prevHtml)) {
                            if (++stuck >= 2) {
                                issues.add(new InteractionIssue("keyboard-focus-trap", "CRITICAL",
                                    "WCAG 2.1.2",
                                    "Focus trap detected — Tab does not move focus away from: " + truncateEl(cur),
                                    truncateEl(cur),
                                    "https://www.w3.org/WAI/WCAG21/Understanding/no-keyboard-trap.html"));
                                break;
                            }
                        } else { stuck = 0; }
                        prevHtml = cur != null ? cur : "";
                    }
                } catch (Exception e) {
                    logger.debug("[KEYBOARD NAV] Tab simulation skipped: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("[KEYBOARD NAV] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("keyboard-navigation", pageName, issues, "WCAG 2.1.1/2.1.2/2.4.3 — Keyboard Navigation");
    }

    /**
     * Checks all interactive elements meet the minimum touch/click target size.
     * <p><b>WCAG 2.5.5</b> (AA) — 44×44 CSS pixels minimum.</p>
     */
    public static int checkTouchTargets(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[TOUCH TARGETS] Checking minimum {}px target size on '{}'", TOUCH_TARGET_MIN_PX, pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> small = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var min=arguments[0],issues=[];" +
                "__a11yQSA('a[href],button:not([disabled]),input[type=button]," +
                "input[type=submit],input[type=reset],input[type=image],[role=button]:not([disabled])," +
                "[role=link],[role=checkbox],[role=radio],[role=tab],[role=menuitem],[role=switch]')" +
                ".forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width>0&&r.height>0&&(r.width<min||r.height<min))" +
                "    issues.push({html:el.outerHTML.substring(0,150),w:Math.round(r.width),h:Math.round(r.height)});});" +
                "return issues;", TOUCH_TARGET_MIN_PX);

            if (small != null) {
                for (Map<String, Object> t : small) {
                    issues.add(new InteractionIssue("touch-target-size", "SERIOUS", "WCAG 2.5.5",
                        "Element is " + t.get("w") + "×" + t.get("h") + "px — below " + TOUCH_TARGET_MIN_PX + "px minimum",
                        truncateEl((String) t.get("html")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/target-size.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[TOUCH TARGETS] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("touch-target-size", pageName, issues, "WCAG 2.5.5 — Touch / Click Target Size");
    }

    /**
     * Checks every interactive element has a visible focus indicator when focused.
     * <p><b>WCAG 2.4.7</b> (AA) — Focus Visible</p>
     */
    public static int checkFocusVisibility(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[FOCUS VISIBILITY] Checking focus indicators on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> noFocus = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var sel='a[href],button:not([disabled]),input:not([disabled])," +
                "select:not([disabled]),textarea:not([disabled]),[role=button]:not([disabled])," +
                "[role=link],[role=checkbox]:not([disabled]),[role=radio]:not([disabled])';" +
                "var orig=document.activeElement,results=[];" +
                "__a11yQSA(sel).slice(0,30).forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width===0||r.height===0)return;" +
                "  el.focus();" +
                "  var s=window.getComputedStyle(el);" +
                "  var hasIndicator=(s.outlineStyle!=='none'&&parseFloat(s.outlineWidth)>0)" +
                "    ||(s.boxShadow&&s.boxShadow!=='none')" +
                "    ||(s.outlineColor&&s.outlineColor!=='rgba(0, 0, 0, 0)');" +
                "  if(!hasIndicator)results.push({html:el.outerHTML.substring(0,150)," +
                "    outline:s.outlineStyle+'/'+s.outlineWidth,shadow:s.boxShadow});});" +
                "if(orig&&orig.focus)try{orig.focus();}catch(e){}" +
                "return results;");

            if (noFocus != null) {
                for (Map<String, Object> r : noFocus) {
                    issues.add(new InteractionIssue("focus-visible", "SERIOUS", "WCAG 2.4.7",
                        "No visible focus indicator (outline=" + r.get("outline") + ", boxShadow=" + r.get("shadow") + ")",
                        truncateEl((String) r.get("html")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/focus-visible.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[FOCUS VISIBILITY] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("focus-visible", pageName, issues, "WCAG 2.4.7 — Focus Visible");
    }

    /**
     * Checks that a skip-to-main-content link exists and its target is valid.
     * <p><b>WCAG 2.4.1</b> (AA) — Bypass Blocks</p>
     */
    public static int checkSkipNavigation(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[SKIP NAV] Checking skip navigation link on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var links=__a11yQSA('a[href^=\"#\"]').filter(function(a){" +
                "  var t=(a.textContent||a.getAttribute('aria-label')||'').toLowerCase();" +
                "  return t.includes('skip')||t.includes('jump')||t.includes('main')||t.includes('content');});" +
                "if(!links.length)return{found:false};" +
                "var f=links[0],target=__a11yQS(f.getAttribute('href'));" +
                "return{found:true,html:f.outerHTML.substring(0,150),targetExists:!!target};");

            if (result == null || !Boolean.TRUE.equals(result.get("found"))) {
                issues.add(new InteractionIssue("skip-navigation", "MODERATE", "WCAG 2.4.1",
                    "No skip-to-main-content link found — keyboard users must Tab through all nav on every page load",
                    "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/bypass-blocks.html"));
            } else if (!Boolean.TRUE.equals(result.get("targetExists"))) {
                issues.add(new InteractionIssue("skip-navigation-broken-target", "MODERATE", "WCAG 2.4.1",
                    "Skip link exists but its href target is missing from the DOM",
                    truncateEl((String) result.get("html")),
                    "https://www.w3.org/WAI/WCAG21/Understanding/bypass-blocks.html"));
            } else {
                logger.info("[SKIP NAV] '{}' — PASS: skip link found and target exists", pageName);
            }
        } catch (Exception e) {
            logger.warn("[SKIP NAV] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("skip-navigation", pageName, issues, "WCAG 2.4.1 — Bypass Blocks / Skip Navigation");
    }

    /**
     * Injects WCAG 1.4.12 text-spacing overrides and checks for clipped content.
     * Styles are removed after the check.
     * <p><b>WCAG 1.4.12</b> (AA) — Text Spacing</p>
     */
    public static int checkTextSpacing(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[TEXT SPACING] Checking WCAG 1.4.12 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String styleId = "wcag-1-4-12-test";
        try {
            js.executeScript(
                "if(!document.getElementById(arguments[0])){" +
                "  var s=document.createElement('style');s.id=arguments[0];" +
                "  s.textContent='*{line-height:1.5!important;letter-spacing:0.12em!important;" +
                "    word-spacing:0.16em!important;}p{margin-bottom:2em!important;}';" +
                "  document.head.appendChild(s);}", styleId);
            try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clipped = (List<Map<String, Object>>) js.executeScript(
                DEEP_DOM_JS +
                "var issues=[];" +
                "__a11yQSA('p,li,label,h1,h2,h3,h4,h5,h6,button,a,span,td,th,legend')" +
                ".forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width===0&&r.height===0)return;" +
                "  var s=window.getComputedStyle(el);" +
                "  if((el.scrollWidth>el.clientWidth+5||el.scrollHeight>el.clientHeight+5)" +
                "    &&(s.overflow==='hidden'||s.overflowX==='hidden'||s.overflowY==='hidden'))" +
                "    issues.push({html:el.outerHTML.substring(0,150)});});" +
                "return issues.slice(0,10);");

            if (clipped != null) {
                for (Map<String, Object> c : clipped) {
                    issues.add(new InteractionIssue("text-spacing", "SERIOUS", "WCAG 1.4.12",
                        "Content clipped (overflow:hidden) when 1.4.12 text-spacing overrides applied",
                        truncateEl((String) c.get("html")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/text-spacing.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[TEXT SPACING] Check failed for '{}': {}", pageName, e.getMessage(), e);
        } finally {
            try { js.executeScript(
                "var el=document.getElementById(arguments[0]);if(el)el.parentNode.removeChild(el);", styleId);
            } catch (Exception ignored) {}
        }
        return interactionReport("text-spacing", pageName, issues, "WCAG 1.4.12 — Text Spacing");
    }

    /**
     * Resizes the viewport to 320 CSS pixels wide (400% zoom equivalent) and checks
     * for horizontal overflow. Original window size is always restored.
     * <p><b>WCAG 1.4.10</b> (AA) — Reflow</p>
     */
    public static int checkZoomReflow(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[ZOOM REFLOW] Checking 320px reflow (WCAG 1.4.10) on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        Dimension original = null;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            original = driver.manage().window().getSize();
            driver.manage().window().setSize(new Dimension(320, 768));
            try { Thread.sleep(600); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            Boolean overflow = (Boolean) js.executeScript(
                "return document.documentElement.scrollWidth>document.documentElement.clientWidth+5");

            if (Boolean.TRUE.equals(overflow)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> culprits = (List<Map<String, Object>>) js.executeScript(
                    DEEP_DOM_JS +
                    "var w=document.documentElement.clientWidth,issues=[];" +
                    "__a11yQSA('*').forEach(function(el){" +
                    "  var r=el.getBoundingClientRect();" +
                    "  if(r.right>w+5&&r.width>0)" +
                    "    issues.push({html:el.outerHTML.substring(0,150),right:Math.round(r.right),docW:w});});" +
                    "return issues.slice(0,5);");
                if (culprits != null && !culprits.isEmpty()) {
                    for (Map<String, Object> o : culprits) {
                        issues.add(new InteractionIssue("zoom-reflow", "SERIOUS", "WCAG 1.4.10",
                            "Element extends past 320px (right=" + o.get("right") + "px) — requires horizontal scroll at 400% zoom",
                            truncateEl((String) o.get("html")),
                            "https://www.w3.org/WAI/WCAG21/Understanding/reflow.html"));
                    }
                } else {
                    issues.add(new InteractionIssue("zoom-reflow", "SERIOUS", "WCAG 1.4.10",
                        "Page requires horizontal scrolling at 320px viewport width (400% zoom)",
                        "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/reflow.html"));
                }
            } else {
                logger.info("[ZOOM REFLOW] '{}' — PASS: no horizontal overflow at 320px", pageName);
            }
        } catch (Exception e) {
            logger.warn("[ZOOM REFLOW] Check failed for '{}': {}", pageName, e.getMessage(), e);
        } finally {
            if (original != null) {
                try { driver.manage().window().setSize(original);
                    Thread.sleep(400);
                } catch (Exception ignored) {}
            }
        }
        return interactionReport("zoom-reflow", pageName, issues, "WCAG 1.4.10 — Reflow at 320px (400% Zoom)");
    }

    private static int interactionReport(String checkId, String pageName,
                                         List<InteractionIssue> issues, String label) {
        if (issues.isEmpty()) {
            logger.info("[INTERACTION:{}] PASS — '{}'", checkId, pageName);
            reporter.info("<b>Interaction [" + label + "]:</b> PASS — " + pageName);
        } else {
            long needsReviewCount = issues.stream().filter(i -> i.needsReview).count();
            long violationCount   = issues.size() - needsReviewCount;
            String summary = violationCount + " issue(s)" +
                (needsReviewCount > 0 ? ", " + needsReviewCount + " needs-review" : "");
            logger.warn("[INTERACTION:{}] {} on '{}':", checkId, summary, pageName);
            issues.forEach(i -> logger.warn("  {}", i));
            reporter.warn("<b>Interaction [" + label + "] — " + summary + "</b> on <b>" + pageName + "</b>");
            for (InteractionIssue issue : issues) {
                String prefix = issue.needsReview ? "<em>[NEEDS REVIEW]</em> " : "";
                String entry = "<details><summary>" + prefix + "[" + issue.impact + "] "
                    + escapeHtml(issue.description) + "</summary>"
                    + "<b>Rule:</b> " + issue.ruleId + "<br/><b>WCAG:</b> " + issue.wcagRef + "<br/>"
                    + "<b>Help:</b> <a href='" + issue.helpUrl + "' target='_blank'>" + issue.helpUrl + "</a><br/>"
                    + (issue.element != null ? "<b>Element:</b><br/><code>" + escapeHtml(issue.element) + "</code>" : "")
                    + (issue.needsReview ? "<br/><em>This item requires human verification.</em>" : "")
                    + "</details>";
                // Needs-review items always warn; definite violations use impact-based routing
                if (issue.needsReview || !("CRITICAL".equals(issue.impact) || "SERIOUS".equals(issue.impact))) {
                    reporter.warn(entry);
                } else {
                    reporter.fail(entry);
                }
            }
        }
        writeInteractionArtifact(checkId, pageName, issues);
        return issues.size();
    }

    private static void writeInteractionArtifact(String checkId, String pageName, List<InteractionIssue> issues) {
        try {
            Files.createDirectories(reportDir());
            String ts = LocalDateTime.now().format(TS_FORMAT);
            Path file = reportDir().resolve(ts + "_" + sanitizeFileName(pageName) + "_interaction_" + checkId + ".json");
            ObjectNode root = REPORT_MAPPER.createObjectNode();
            root.put("timestamp", ts); root.put("pageName", pageName);
            root.put("checkId", checkId); root.put("outcome", issues.isEmpty() ? "PASS" : "FAIL");
            root.put("issueCount", issues.size());
            long needsReviewCount = issues.stream().filter(i -> i.needsReview).count();
            root.put("needsReviewCount", needsReviewCount);
            ArrayNode arr = root.putArray("issues");
            for (InteractionIssue i : issues) {
                ObjectNode n = arr.addObject();
                n.put("ruleId", i.ruleId); n.put("impact", i.impact); n.put("wcagRef", i.wcagRef);
                n.put("description", i.description); n.put("element", i.element != null ? i.element : "");
                n.put("helpUrl", i.helpUrl); n.put("needsReview", i.needsReview);
                String wcagCriterion = AccessibilityFinding.extractWcagCriterionFromRef(i.wcagRef);
                n.put("wcagCriterion", wcagCriterion != null ? wcagCriterion : "");
                n.put("confidence", i.needsReview
                        ? AccessibilityFinding.CONFIDENCE_MANUAL_REVIEW : AccessibilityFinding.CONFIDENCE_VIOLATION);
            }
            REPORT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);

            EXCEL_SUMMARY_ROWS.add(new Object[]{
                issues.isEmpty() ? "PASS" : "FAIL", pageName, "", issues.size(),
                "Interaction:" + checkId, "", 0, "N/A", ts});
            for (InteractionIssue i : issues) {
                String element = i.element != null ? i.element : "";
                String engine = i.needsReview ? "NeedsReview" : "Interaction";
                EXCEL_ISSUE_ROWS.add(new Object[]{
                    i.impact,
                    deriveIssueType(i.impact, engine),
                    i.description,
                    extractComponent(element),
                    element,
                    i.ruleId,
                    i.wcagRef,
                    pageName,
                    "",
                    engine,
                    i.helpUrl,
                    ts});
                FINDINGS.add(AccessibilityFinding.fromInteractionIssue(i, engine, null, null));
            }
            writeExcelReport();
        } catch (Exception e) {
            logger.debug("Unable to write interaction artifact for '{}': {}", pageName, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Excel report writer (live-updating workbook)
    // -----------------------------------------------------------------------

    /**
     * (Re)generates {@code <output>/accessibility-report.xlsx} from all in-memory
     * accumulated scan results. Called automatically after every scan.
     */
    public static synchronized void writeExcelReport() {
        try {
            Files.createDirectories(reportDir());
            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                CellStyle headerStyle = createHeaderStyle(wb);
                CellStyle passStyle   = createImpactStyle(wb, IndexedColors.LIGHT_GREEN,   false);
                CellStyle failStyle   = createImpactStyle(wb, IndexedColors.ROSE,          true);
                CellStyle critStyle   = createImpactStyle(wb, IndexedColors.RED,           true);
                CellStyle seriousStyle= createImpactStyle(wb, IndexedColors.ORANGE,        true);
                CellStyle moderateStyle=createImpactStyle(wb, IndexedColors.YELLOW,        false);
                CellStyle minorStyle  = createImpactStyle(wb, IndexedColors.LIGHT_YELLOW,  false);
                CellStyle defaultStyle= createImpactStyle(wb, IndexedColors.WHITE,         false);
                CellStyle wrapStyle   = wb.createCellStyle();
                wrapStyle.setWrapText(true);
                wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

                // ── Sheet 1: Summary ─────────────────────────────────────
                Sheet summary = wb.createSheet("Summary");
                String[] summaryHeaders = {
                        "Outcome", "Page Name", "URL", "Issue Count", "Engine",
                        "Tags", "iFrames Detected", "Shadow DOM", "Timestamp"
                };
                writeHeaderRow(summary, summaryHeaders, headerStyle);

                synchronized (EXCEL_SUMMARY_ROWS) {
                    for (int r = 0; r < EXCEL_SUMMARY_ROWS.size(); r++) {
                        Object[] data = EXCEL_SUMMARY_ROWS.get(r);
                        Row row = summary.createRow(r + 1);
                        String outcome = data[0] != null ? data[0].toString() : "";
                        CellStyle rowStyle;
                        if ("PASS".equalsIgnoreCase(outcome)) {
                            rowStyle = passStyle;
                        } else if ("FAIL".equalsIgnoreCase(outcome)) {
                            rowStyle = failStyle;
                        } else {
                            rowStyle = defaultStyle;
                        }
                        for (int c = 0; c < data.length; c++) {
                            Cell cell = row.createCell(c);
                            setCellValue(cell, data[c]);
                            cell.setCellStyle(rowStyle);
                        }
                    }
                }
                autoSizeColumns(summary, summaryHeaders.length);
                summary.setAutoFilter(new CellRangeAddress(0, 0, 0, summaryHeaders.length - 1));

                // ── Sheet 2: All Issues ──────────────────────────────────
                Sheet issuesSheet = wb.createSheet("All Issues");
                String[] issueHeaders = {
                        "Impact", "Issue Type", "Issue Name", "Component", "Affected Element",
                        "Rule ID", "WCAG / Tags", "Page Name", "Page URL", "Engine", "Help URL", "Timestamp"
                };
                writeHeaderRow(issuesSheet, issueHeaders, headerStyle);

                synchronized (EXCEL_ISSUE_ROWS) {
                    for (int r = 0; r < EXCEL_ISSUE_ROWS.size(); r++) {
                        Object[] data = EXCEL_ISSUE_ROWS.get(r);
                        if (data.length != issueHeaders.length) {
                            logger.warn("Column mismatch! Headers: {}, Data: {}", issueHeaders.length, data.length);
                        }
                        Row row = issuesSheet.createRow(r + 1);
                        row.setHeightInPoints(50);
                        String impact = data[0] != null ? data[0].toString().toUpperCase() : "";
                        CellStyle rowStyle;
                        switch (impact) {
                            case "CRITICAL": rowStyle = critStyle;     break;
                            case "SERIOUS":  rowStyle = seriousStyle;  break;
                            case "MODERATE": rowStyle = moderateStyle; break;
                            case "MINOR":    rowStyle = minorStyle;    break;
                            default:         rowStyle = defaultStyle;  break;
                        }
                        for (int c = 0; c < data.length; c++) {
                            Cell cell = row.createCell(c);
                            setCellValue(cell, data[c]);
                            if (c == 2 || c == 4) {
                                cell.setCellStyle(wrapStyle);
                            } else {
                                cell.setCellStyle(rowStyle);
                            }
                        }
                    }
                }
                issuesSheet.setColumnWidth(0,  3500);
                issuesSheet.setColumnWidth(1,  5000);
                issuesSheet.setColumnWidth(2, 15000);
                issuesSheet.setColumnWidth(3,  4000);
                issuesSheet.setColumnWidth(4, 15000);
                issuesSheet.setColumnWidth(5,  8000);
                issuesSheet.setColumnWidth(6,  7000);
                issuesSheet.setColumnWidth(7,  8000);
                issuesSheet.setColumnWidth(8,  4000);
                issuesSheet.setColumnWidth(9, 13000);
                issuesSheet.setColumnWidth(10, 5000);
                issuesSheet.setAutoFilter(new CellRangeAddress(0, 0, 0, issueHeaders.length - 1));

                summary.createFreezePane(0, 1);
                issuesSheet.createFreezePane(0, 1);

                try (FileOutputStream fos = new FileOutputStream(excelReportPath().toFile())) {
                    wb.write(fos);
                }
                logger.info("Excel report updated ({} scan(s), {} issue(s)): {}",
                    EXCEL_SUMMARY_ROWS.size(), EXCEL_ISSUE_ROWS.size(), excelReportPath().toAbsolutePath());

                reporter.info("Excel Report: <a href='accessibility/accessibility-report.xlsx' target='_blank'>accessibility-report.xlsx</a>");
            }
        } catch (Exception e) {
            logger.warn("Could not write Excel accessibility report: {}", e.getMessage(), e);
        }
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private static CellStyle createImpactStyle(Workbook wb, IndexedColors bg, boolean boldFont) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(bg.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        if (boldFont) {
            Font font = wb.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
    }

    private static void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(20);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 15000) sheet.setColumnWidth(i, 15000);
        }
    }

    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static String deriveIssueType(String impact, String engine) {
        if ("Interaction".equalsIgnoreCase(engine))  return "Interaction Check";
        if ("NeedsReview".equalsIgnoreCase(engine))  return "Needs Review";
        if (impact == null) return "Best Practice";
        switch (impact.toUpperCase()) {
            case "CRITICAL":
            case "SERIOUS":  return "Violation";
            default:         return "Best Practice";
        }
    }

    private static String extractComponent(String html) {
        if (html == null || html.trim().isEmpty()) return "(element)";
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("^\\s*<(\\w[\\w-]*)").matcher(html.trim());
        return m.find() ? m.group(1).toLowerCase() : "(element)";
    }

    private static String truncateEl(String html) {
        if (html == null) return "(element)";
        return html.length() > 200 ? html.substring(0, 200) + "…" : html;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WCAG 2.2 CHECKS  (BrowserStack Spectra-equivalent extended coverage)
    // 2.4.11 Focus Appearance · 2.5.7 Dragging Movements · 2.5.8 Target Size
    // These criteria are new in WCAG 2.2 and are NOT covered by axe-core alone.
    // ═══════════════════════════════════════════════════════════════════════

    /** Minimum focus outline width used as a proxy for WCAG 2.4.11 perimeter check. */
    private static final int FOCUS_APPEARANCE_MIN_PX = 2;

    /** WCAG 2.5.8 minimum target size (24 px — smaller floor than 2.5.5's 44 px). */
    private static final int TARGET_SIZE_MINIMUM_PX = 24;

    /**
     * Runs all WCAG 2.2 specific checks in one call.
     * <ul>
     *   <li>2.4.11 Focus Appearance</li>
     *   <li>2.5.7 Dragging Movements</li>
     *   <li>2.5.8 Target Size (Minimum)</li>
     * </ul>
     */
    public static int checkWcag22(WebDriver driver, String pageName) {
        if (!isEnabled()) {
            logger.debug("Accessibility checking disabled — skipping WCAG 2.2 checks for: {}", pageName);
            return 0;
        }
        logger.info("[WCAG 2.2] Running WCAG 2.2 checks on '{}'", pageName);
        int total = 0;
        total += checkFocusAppearance(driver, pageName);
        total += checkTargetSizeMinimum(driver, pageName);
        total += checkDraggingMovements(driver, pageName);
        logger.info("[WCAG 2.2] '{}' — total WCAG 2.2 issues: {}", pageName, total);
        return total;
    }

    /**
     * Checks that every focusable element has a focus indicator of at least
     * {@value #FOCUS_APPEARANCE_MIN_PX} CSS pixels (outline-width or box-shadow),
     * which approximates the WCAG 2.4.11 minimum-perimeter requirement.
     * <p><b>WCAG 2.4.11</b> (AA, new in WCAG 2.2) — Focus Appearance</p>
     */
    public static int checkFocusAppearance(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[FOCUS APPEARANCE] Checking WCAG 2.4.11 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var sel='a[href],button:not([disabled]),input:not([disabled])," +
                "select:not([disabled]),textarea:not([disabled])," +
                "[role=button]:not([disabled]),[role=link]," +
                "[role=checkbox]:not([disabled]),[role=radio]:not([disabled]),[role=tab]';" +
                "var orig=document.activeElement,results=[];" +
                "__a11yQSA(sel).slice(0,30).forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width===0||r.height===0)return;" +
                "  el.focus();" +
                "  var s=window.getComputedStyle(el);" +
                "  var ow=parseFloat(s.outlineWidth)||0;" +
                "  var bs=s.boxShadow&&s.boxShadow!=='none'?s.boxShadow:null;" +
                "  if(ow<arguments[0]&&bs===null)" +
                "    results.push({html:el.outerHTML.substring(0,150),ow:ow});" +
                "});" +
                "if(orig&&orig.focus)try{orig.focus();}catch(e){}" +
                "return results;", FOCUS_APPEARANCE_MIN_PX);

            if (elements != null) {
                for (Map<String, Object> el : elements) {
                    double ow = el.get("ow") instanceof Number
                            ? ((Number) el.get("ow")).doubleValue() : 0;
                    issues.add(new InteractionIssue("focus-appearance", "SERIOUS", "WCAG 2.4.11",
                        "Focus indicator too thin (outlineWidth=" + String.format("%.1f", ow) + "px) — " +
                        FOCUS_APPEARANCE_MIN_PX + "px minimum (WCAG 2.4.11)",
                        truncateEl((String) el.get("html")),
                        "https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[FOCUS APPEARANCE] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("focus-appearance", pageName, issues,
                "WCAG 2.4.11 — Focus Appearance (WCAG 2.2)");
    }

    /**
     * Checks that non-inline interactive elements meet the WCAG 2.5.8 minimum
     * target size of {@value #TARGET_SIZE_MINIMUM_PX}×{@value #TARGET_SIZE_MINIMUM_PX} CSS px.
     * Inline-text anchors are excluded (WCAG 2.5.8 exception).
     * <p><b>WCAG 2.5.8</b> (AA, new in WCAG 2.2) — Target Size (Minimum)</p>
     */
    public static int checkTargetSizeMinimum(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[TARGET SIZE MIN] Checking WCAG 2.5.8 {}px minimum on '{}'",
                TARGET_SIZE_MINIMUM_PX, pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> small = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var min=arguments[0],issues=[];" +
                "__a11yQSA('a[href],button:not([disabled]),input[type=button]," +
                "input[type=submit],input[type=reset],input[type=image]," +
                "[role=button]:not([disabled]),[role=link],[role=checkbox]," +
                "[role=radio],[role=tab],[role=menuitem],[role=switch]')" +
                ".forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width===0||r.height===0)return;" +
                // WCAG 2.5.8 inline-text exception
                "  var tag=el.tagName.toLowerCase();" +
                "  var isInlineText=(tag==='a'&&!el.getAttribute('role'))" +
                "    &&window.getComputedStyle(el).display==='inline';" +
                "  if(isInlineText)return;" +
                "  if(r.width<min||r.height<min)" +
                "    issues.push({html:el.outerHTML.substring(0,150)," +
                "      w:Math.round(r.width),h:Math.round(r.height)});});" +
                "return issues;", TARGET_SIZE_MINIMUM_PX);

            if (small != null) {
                for (Map<String, Object> t : small) {
                    issues.add(new InteractionIssue("target-size-minimum", "MODERATE", "WCAG 2.5.8",
                        "Target is " + t.get("w") + "×" + t.get("h") + "px — below the " +
                        TARGET_SIZE_MINIMUM_PX + "px minimum required by WCAG 2.5.8",
                        truncateEl((String) t.get("html")),
                        "https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[TARGET SIZE MIN] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("target-size-minimum", pageName, issues,
                "WCAG 2.5.8 — Target Size Minimum (WCAG 2.2)");
    }

    /**
     * Checks that draggable elements have a detectable single-pointer alternative
     * (e.g. move/reorder buttons).  Flagged as <em>Needs Review</em> because
     * drag-alternative patterns vary widely and require human confirmation.
     * <p><b>WCAG 2.5.7</b> (AA, new in WCAG 2.2) — Dragging Movements</p>
     */
    public static int checkDraggingMovements(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[DRAGGING] Checking WCAG 2.5.7 drag alternatives on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> draggable = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var issues=[];" +
                "__a11yQSA('[draggable=true]').forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  if(r.width===0||r.height===0)return;" +
                "  var parent=el.parentElement;" +
                "  var hasAlt=parent&&parent.querySelector(" +
                "    'button,[role=button],[aria-label*=move],[aria-label*=reorder]," +
                "     [aria-label*=up],[aria-label*=down],[aria-label*=order]')!=null;" +
                "  if(!hasAlt)issues.push({html:el.outerHTML.substring(0,150)});});" +
                "return issues;");

            if (draggable != null && !draggable.isEmpty()) {
                for (Map<String, Object> el : draggable) {
                    issues.add(new InteractionIssue("dragging-movements", "SERIOUS", "WCAG 2.5.7",
                        "Draggable element with no detectable single-pointer alternative " +
                        "(e.g. move-up/down buttons). Verify an alternative exists.",
                        truncateEl((String) el.get("html")),
                        "https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html",
                        true /* needsReview */));
                }
            } else {
                logger.info("[DRAGGING] '{}' — No draggable elements detected", pageName);
            }
        } catch (Exception e) {
            logger.warn("[DRAGGING] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("dragging-movements", pageName, issues,
                "WCAG 2.5.7 — Dragging Movements (WCAG 2.2)");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STRUCTURAL / SCREEN-READER CHECKS  (Spectra "Content" & "Structure")
    // Heading hierarchy · Landmark regions · Page title · Language attribute
    // Descriptive link text · Duplicate IDs
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs all structural and screen-reader content checks in one call.
     * These mirror BrowserStack Spectra's "Content" and "Structural" scan categories
     * that axe-core does not fully cover.
     * <ul>
     *   <li>2.4.2  Page Titled</li>
     *   <li>3.1.1  Language of Page</li>
     *   <li>1.3.1 / 2.4.6  Headings</li>
     *   <li>1.3.6 / 2.4.1  Landmark Regions</li>
     *   <li>2.4.4  Link Purpose</li>
     *   <li>Duplicate IDs (structural integrity)</li>
     * </ul>
     */
    public static int checkStructural(WebDriver driver, String pageName) {
        if (!isEnabled()) {
            logger.debug("Accessibility checking disabled — skipping structural checks for: {}", pageName);
            return 0;
        }
        logger.info("[STRUCTURAL] Running structural/SR checks on '{}'", pageName);
        int total = 0;
        total += checkPageTitle(driver, pageName);
        total += checkLanguageAttribute(driver, pageName);
        total += checkHeadingHierarchy(driver, pageName);
        total += checkLandmarkRegions(driver, pageName);
        total += checkLinkTextDescriptive(driver, pageName);
        total += checkDuplicateIds(driver, pageName);
        logger.info("[STRUCTURAL] '{}' — total structural issues: {}", pageName, total);
        return total;
    }

    /**
     * Checks that the page has a non-empty, non-default document title.
     * <p><b>WCAG 2.4.2</b> (A) — Page Titled</p>
     */
    public static int checkPageTitle(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[PAGE TITLE] Checking WCAG 2.4.2 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>)
                ((JavascriptExecutor) driver).executeScript(
                "var t=document.title;" +
                "return{title:t,length:t?t.trim().length:0};");

            if (result != null) {
                Number len = (Number) result.get("length");
                String title = (String) result.get("title");
                if (len == null || len.intValue() == 0) {
                    issues.add(new InteractionIssue("page-title-missing", "SERIOUS", "WCAG 2.4.2",
                        "Document title is empty — every page must have a descriptive <title>",
                        "(document)",
                        "https://www.w3.org/WAI/WCAG21/Understanding/page-titled.html"));
                } else if (title != null && (title.trim().equalsIgnoreCase("untitled")
                        || title.trim().equalsIgnoreCase("new tab")
                        || title.trim().equalsIgnoreCase("document"))) {
                    issues.add(new InteractionIssue("page-title-generic", "MODERATE", "WCAG 2.4.2",
                        "Document title '" + title.trim() + "' is generic and not descriptive",
                        "(document)",
                        "https://www.w3.org/WAI/WCAG21/Understanding/page-titled.html"));
                } else {
                    logger.info("[PAGE TITLE] '{}' — PASS: title='{}'", pageName, title.trim());
                }
            }
        } catch (Exception e) {
            logger.warn("[PAGE TITLE] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("page-title", pageName, issues, "WCAG 2.4.2 — Page Titled");
    }

    /**
     * Checks that {@code <html lang="...">} is present and non-empty.
     * <p><b>WCAG 3.1.1</b> (A) — Language of Page</p>
     */
    public static int checkLanguageAttribute(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[LANGUAGE ATTR] Checking WCAG 3.1.1 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>)
                ((JavascriptExecutor) driver).executeScript(
                "var html=document.documentElement;" +
                "return{lang:html.getAttribute('lang')||''," +
                "  xmlLang:html.getAttribute('xml:lang')||''};");

            if (result != null) {
                String lang    = (String) result.get("lang");
                String xmlLang = (String) result.get("xmlLang");
                if ((lang == null || lang.trim().isEmpty()) &&
                        (xmlLang == null || xmlLang.trim().isEmpty())) {
                    issues.add(new InteractionIssue("language-attribute", "SERIOUS", "WCAG 3.1.1",
                        "No lang attribute on <html> element — screen readers cannot determine the page language",
                        "<html> (no lang attribute)",
                        "https://www.w3.org/WAI/WCAG21/Understanding/language-of-page.html"));
                } else {
                    String effectiveLang = (lang != null && !lang.trim().isEmpty()) ? lang.trim() : xmlLang.trim();
                    logger.info("[LANGUAGE ATTR] '{}' — PASS: lang='{}'", pageName, effectiveLang);
                }
            }
        } catch (Exception e) {
            logger.warn("[LANGUAGE ATTR] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("language-attribute", pageName, issues, "WCAG 3.1.1 — Language of Page");
    }

    /**
     * Checks heading levels for correct hierarchy (no skipped levels, one H1).
     * <p><b>WCAG 1.3.1 / 2.4.6</b> (A/AA) — Info and Relationships / Headings and Labels</p>
     */
    public static int checkHeadingHierarchy(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[HEADINGS] Checking heading hierarchy on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var headings=__a11yQSA('h1,h2,h3,h4,h5,h6,[role=heading]');" +
                "var issues=[];" +
                "if(!headings.length){" +
                "  issues.push({type:'no-headings',level:0,html:'(page)'});" +
                "}else{" +
                "  var prev=0,h1Count=0;" +
                "  for(var i=0;i<headings.length;i++){" +
                "    var el=headings[i];" +
                "    var level=parseInt((el.tagName||'').charAt(1))||" +
                "               parseInt(el.getAttribute('aria-level'))||0;" +
                "    if(level===1)h1Count++;" +
                "    if(i>0&&level>prev+1)" +
                "      issues.push({type:'skip',level:level,prev:prev," +
                "        html:el.outerHTML.substring(0,150)});" +
                "    prev=level;" +
                "  }" +
                "  if(h1Count===0)issues.push({type:'no-h1',level:0,html:'(page)'});" +
                "  else if(h1Count>1)issues.push({type:'multi-h1',level:1,count:h1Count,html:'(page)'});" +
                "}" +
                "return issues;");

            if (findings != null) {
                for (Map<String, Object> f : findings) {
                    String type = (String) f.get("type");
                    String html = truncateEl((String) f.get("html"));
                    InteractionIssue issue;
                    if ("no-headings".equals(type)) {
                        issue = new InteractionIssue("heading-hierarchy", "MODERATE", "WCAG 2.4.6",
                            "No heading elements found — use h1-h6 to structure page content",
                            html, "https://www.w3.org/WAI/WCAG21/Understanding/headings-and-labels.html");
                    } else if ("no-h1".equals(type)) {
                        issue = new InteractionIssue("heading-hierarchy-no-h1", "SERIOUS", "WCAG 1.3.1",
                            "No h1 element found — every page should have exactly one h1",
                            html, "https://www.w3.org/WAI/WCAG21/Understanding/info-and-relationships.html");
                    } else if ("multi-h1".equals(type)) {
                        Object count = f.get("count");
                        issue = new InteractionIssue("heading-hierarchy-multi-h1", "MODERATE", "WCAG 1.3.1",
                            "Multiple h1 elements (" + count + ") — only one h1 per page is recommended",
                            html, "https://www.w3.org/WAI/WCAG21/Understanding/info-and-relationships.html");
                    } else { // skip
                        Object level = f.get("level");
                        Object prev  = f.get("prev");
                        issue = new InteractionIssue("heading-hierarchy-skip", "MODERATE", "WCAG 1.3.1",
                            "Heading level skipped from h" + prev + " to h" + level +
                            " — heading levels must increase by one",
                            html, "https://www.w3.org/WAI/WCAG21/Understanding/info-and-relationships.html");
                    }
                    issues.add(issue);
                }
            }
        } catch (Exception e) {
            logger.warn("[HEADINGS] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("heading-hierarchy", pageName, issues,
                "WCAG 1.3.1 / 2.4.6 — Heading Hierarchy");
    }

    /**
     * Checks that the page contains the essential ARIA landmark regions:
     * {@code <main>}, {@code <nav>}, {@code <header>} / {@code [role=banner]},
     * and {@code <footer>} / {@code [role=contentinfo]}.
     * <p><b>WCAG 1.3.6 / 2.4.1</b> (A) — Identify Purpose / Bypass Blocks</p>
     */
    public static int checkLandmarkRegions(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[LANDMARKS] Checking ARIA landmark regions on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "return{" +
                "  hasMain:!!(__a11yQS('main,[role=main]'))," +
                "  hasNav:!!(__a11yQS('nav,[role=navigation]'))," +
                "  hasBanner:!!(__a11yQS('header:not([role]),[role=banner]'))," +
                "  hasContentInfo:!!(__a11yQS('footer:not([role]),[role=contentinfo]'))};");

            if (result != null) {
                if (!Boolean.TRUE.equals(result.get("hasMain"))) {
                    issues.add(new InteractionIssue("landmark-main", "SERIOUS", "WCAG 1.3.6",
                        "No <main> or role=main landmark found — screen reader users need a main landmark to skip navigation",
                        "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/identify-purpose.html"));
                }
                if (!Boolean.TRUE.equals(result.get("hasNav"))) {
                    issues.add(new InteractionIssue("landmark-navigation", "MODERATE", "WCAG 1.3.6",
                        "No <nav> or role=navigation landmark found",
                        "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/identify-purpose.html"));
                }
                if (!Boolean.TRUE.equals(result.get("hasBanner"))) {
                    issues.add(new InteractionIssue("landmark-banner", "MINOR", "WCAG 1.3.6",
                        "No <header> / role=banner landmark found",
                        "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/identify-purpose.html"));
                }
                if (!Boolean.TRUE.equals(result.get("hasContentInfo"))) {
                    issues.add(new InteractionIssue("landmark-contentinfo", "MINOR", "WCAG 1.3.6",
                        "No <footer> / role=contentinfo landmark found",
                        "(page)", "https://www.w3.org/WAI/WCAG21/Understanding/identify-purpose.html"));
                }
                if (issues.isEmpty()) {
                    logger.info("[LANDMARKS] '{}' — PASS: all essential landmarks present", pageName);
                }
            }
        } catch (Exception e) {
            logger.warn("[LANDMARKS] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("landmark-regions", pageName, issues,
                "WCAG 1.3.6 / 2.4.1 — Landmark Regions");
    }

    /**
     * Flags links with empty accessible names or generic text ("click here", "read more", etc.).
     * Generic-text findings are flagged as <em>Needs Review</em> because context can make
     * them acceptable.
     * <p><b>WCAG 2.4.4</b> (A) — Link Purpose (In Context)</p>
     */
    public static int checkLinkTextDescriptive(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[LINK TEXT] Checking WCAG 2.4.4 descriptive link text on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var generic=['click here','here','read more','more','link','continue'," +
                "'learn more','click','go','download','open','view','see more','this','details'];" +
                "var issues=[];" +
                "__a11yQSA('a[href]').forEach(function(a){" +
                "  var text=(a.textContent||'').trim().toLowerCase();" +
                "  var ariaLabel=(a.getAttribute('aria-label')||'').trim().toLowerCase();" +
                "  var ariaLabelledby=a.getAttribute('aria-labelledby');" +
                "  var imgAlt=a.querySelector('img[alt]');" +
                "  var labelText=ariaLabel||text;" +
                "  if(!labelText&&!ariaLabelledby&&!imgAlt)" +
                "    issues.push({html:a.outerHTML.substring(0,150),reason:'empty',needsReview:false});" +
                "  else if(generic.indexOf(labelText)!==-1)" +
                "    issues.push({html:a.outerHTML.substring(0,150),reason:labelText,needsReview:true});" +
                "});" +
                "return issues.slice(0,25);");

            if (findings != null) {
                for (Map<String, Object> f : findings) {
                    boolean needsReview = Boolean.TRUE.equals(f.get("needsReview"));
                    String reason = (String) f.get("reason");
                    String desc = needsReview
                        ? "Link text '" + reason + "' is generic — verify it is sufficient in context (WCAG 2.4.4)"
                        : "Link has no accessible name (text, aria-label, or alt text on inner image)";
                    issues.add(new InteractionIssue("link-text-descriptive", needsReview ? "MODERATE" : "SERIOUS",
                        "WCAG 2.4.4", desc,
                        truncateEl((String) f.get("html")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/link-purpose-in-context.html",
                        needsReview));
                }
            }
        } catch (Exception e) {
            logger.warn("[LINK TEXT] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("link-text-descriptive", pageName, issues,
                "WCAG 2.4.4 — Link Purpose / Descriptive Link Text");
    }

    /**
     * Finds duplicate {@code id} attribute values in the DOM.
     * Duplicate IDs break ARIA relationships (aria-labelledby, aria-describedby)
     * and are a structural integrity failure.
     *
     * <p><b>Performance note:</b> the shadow-DOM recursion uses
     * {@code querySelectorAll('*')} to find shadow hosts, which can traverse tens of
     * thousands of elements on complex Angular/LWC pages.  A hard cap of
     * {@code 5 000} elements is applied per tree level with a WARN when exceeded.
     * This prevents scan timeouts while still catching the vast majority of
     * duplicate-ID problems in practice.</p>
     *
     * <p><b>WCAG 4.1.1</b> (A) — Parsing</p>
     */
    public static int checkDuplicateIds(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[DUPLICATE IDS] Checking for duplicate id attributes on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dupes = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                // IDs are scoped per shadow tree: the same id in two different shadow
                // roots is valid, so we detect duplicates WITHIN each tree separately
                // (light DOM + each open shadowRoot) to avoid false positives.
                // A cap of 5 000 elements per querySelectorAll('*') pass prevents
                // timeouts on large Angular / Salesforce LWC apps.
                "var MAX_ELS=5000,dups=[],cappedWarned=false;" +
                "var checkTree=function(node){" +
                "  var ids={};" +
                "  var els=node.querySelectorAll('[id]');" +
                "  for(var i=0;i<els.length;i++){" +
                "    var id=els[i].getAttribute('id');" +
                "    if(id)ids[id]=(ids[id]||[]).concat(els[i].outerHTML.substring(0,100));}" +
                "  Object.keys(ids).forEach(function(k){" +
                "    if(ids[k].length>1)dups.push({id:k,count:ids[k].length,first:ids[k][0]});});" +
                "  var hosts=node.querySelectorAll('*');" +
                "  var limit=Math.min(hosts.length,MAX_ELS);" +
                "  if(hosts.length>MAX_ELS&&!cappedWarned){" +
                "    cappedWarned=true;" +
                "    dups.push({id:'__cap_warning__',count:hosts.length,first:'Shadow DOM recursion capped at '+MAX_ELS+' elements ('+hosts.length+' total) to prevent timeout'});}" +
                "  for(var j=0;j<limit;j++){if(hosts[j].shadowRoot)checkTree(hosts[j].shadowRoot);}" +
                "};" +
                "checkTree(document);" +
                "return dups;");

            if (dupes != null) {
                for (Map<String, Object> d : dupes) {
                    String id = (String) d.get("id");
                    if ("__cap_warning__".equals(id)) {
                        logger.warn("[DUPLICATE IDS] '{}': {}", pageName, d.get("first"));
                        continue;
                    }
                    Object count = d.get("count");
                    issues.add(new InteractionIssue("duplicate-id", "CRITICAL", "WCAG 4.1.1",
                        "Duplicate id=\"" + id + "\" found " + count + " times — breaks ARIA relationships",
                        truncateEl((String) d.get("first")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/parsing.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[DUPLICATE IDS] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("duplicate-ids", pageName, issues,
                "WCAG 4.1.1 — Parsing / Duplicate IDs");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MOTION / MEDIA CHECKS  (Spectra Motion & Media categories)
    // 1.4.2 Audio Control · 2.3.3 Animation from Interactions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks for {@code <audio>} and {@code <video>} elements that auto-play
     * without controls and without being muted.
     * <p><b>WCAG 1.4.2</b> (A) — Audio Control</p>
     */
    public static int checkAutoPlayMedia(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[AUTO-PLAY MEDIA] Checking WCAG 1.4.2 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var issues=[];" +
                "__a11yQSA('audio[autoplay],video[autoplay]').forEach(function(el){" +
                "  var r=el.getBoundingClientRect();" +
                "  var hasControls=el.hasAttribute('controls');" +
                "  var muted=el.muted||el.getAttribute('muted')!==null||el.volume===0;" +
                "  if(!hasControls&&!muted)" +
                "    issues.push({html:el.outerHTML.substring(0,150)," +
                "      tag:el.tagName.toLowerCase(),hasControls:hasControls,muted:muted});});" +
                "return issues;");

            if (findings != null) {
                for (Map<String, Object> f : findings) {
                    String tag = (String) f.get("tag");
                    issues.add(new InteractionIssue("auto-play-media", "SERIOUS", "WCAG 1.4.2",
                        "<" + tag + " autoplay> without controls or muted — users cannot stop/pause audio",
                        truncateEl((String) f.get("html")),
                        "https://www.w3.org/WAI/WCAG21/Understanding/audio-control.html"));
                }
            }
        } catch (Exception e) {
            logger.warn("[AUTO-PLAY MEDIA] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("auto-play-media", pageName, issues,
                "WCAG 1.4.2 — Audio Control / Auto-play Media");
    }

    /**
     * Checks whether the page contains CSS animations or animated elements
     * without a corresponding {@code prefers-reduced-motion} media-query block.
     * Flagged as <em>Needs Review</em> because animation necessity is context-dependent.
     * <p><b>WCAG 2.3.3</b> (AAA) — Animation from Interactions</p>
     */
    public static int checkReducedMotion(WebDriver driver, String pageName) {
        if (!isEnabled()) return 0;
        logger.info("[REDUCED MOTION] Checking WCAG 2.3.3 on '{}'", pageName);
        List<InteractionIssue> issues = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>)
                ((JavascriptExecutor) driver).executeScript(
                DEEP_DOM_JS +
                "var results=[];" +
                "var animatedEls=__a11yQSA(" +
                "  '[style*=animation],[class*=animate],[class*=slide],[class*=fade]," +
                "   [class*=transition],[class*=spin],[class*=pulse],[class*=bounce]');" +
                "if(animatedEls.length>0){" +
                "  var hasMotionQuery=false;" +
                "  try{Array.from(document.styleSheets).forEach(function(ss){" +
                "    try{Array.from(ss.cssRules||[]).forEach(function(rule){" +
                "      if(rule.conditionText&&rule.conditionText.includes('prefers-reduced-motion'))" +
                "        hasMotionQuery=true;" +
                "    });}catch(e){}" +
                "  });}catch(e){}" +
                "  if(!hasMotionQuery)" +
                "    results.push({count:animatedEls.length,hasMotionQuery:false});" +
                "}" +
                "return results;");

            if (findings != null) {
                for (Map<String, Object> f : findings) {
                    Object count = f.get("count");
                    issues.add(new InteractionIssue("reduced-motion", "MODERATE", "WCAG 2.3.3",
                        count + " animated element(s) detected but no @media (prefers-reduced-motion) block found " +
                        "— verify animations are suppressed for users who prefer reduced motion.",
                        "(stylesheet)",
                        "https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions.html",
                        true /* needsReview */));
                }
            }
        } catch (Exception e) {
            logger.warn("[REDUCED MOTION] Check failed for '{}': {}", pageName, e.getMessage(), e);
        }
        return interactionReport("reduced-motion", pageName, issues,
                "WCAG 2.3.3 — Animation from Interactions / Reduced Motion");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FULL SUITE  —  single call equivalent to a complete Spectra-style scan
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs the full multi-layer accessibility suite on the current page.
     *
     * <p>The engines that fire are controlled individually by config flags
     * (all default to {@code true}). To run axe-core only, set the other
     * three flags to {@code false} in {@code accessibility.properties}:</p>
     * <pre>
     * accessibility.engine.interaction.enabled=false
     * accessibility.engine.wcag22.enabled=false
     * accessibility.engine.structural.enabled=false
     * accessibility.engine.motion.enabled=false
     * </pre>
     *
     * <table>
     *   <tr><th>Config key</th><th>Default</th><th>Controls</th></tr>
     *   <tr><td>accessibility.engine.interaction.enabled</td><td>true</td>
     *       <td>Keyboard nav, touch targets, focus visible, skip nav,
     *           text spacing, zoom/reflow (WCAG 2.1/2.4/2.5)</td></tr>
     *   <tr><td>accessibility.engine.wcag22.enabled</td><td>true</td>
     *       <td>Focus appearance 2.4.11, dragging 2.5.7,
     *           target size minimum 2.5.8</td></tr>
     *   <tr><td>accessibility.engine.structural.enabled</td><td>true</td>
     *       <td>Page title, language attr, heading hierarchy,
     *           landmark regions, link text, duplicate IDs</td></tr>
     *   <tr><td>accessibility.engine.motion.enabled</td><td>true</td>
     *       <td>Auto-play media 1.4.2, reduced-motion 2.3.3</td></tr>
     * </table>
     *
     * @param driver   active WebDriver
     * @param pageName label used in all report output
     * @return total issue count across all enabled engines
     */
    public static int checkFullSuite(WebDriver driver, String pageName) {
        if (!isEnabled()) {
            logger.debug("Accessibility checking disabled — skipping full suite for: {}", pageName);
            return 0;
        }

        boolean runInteraction = A11yConfig.getBoolean("accessibility.engine.interaction.enabled", true);
        boolean runWcag22      = A11yConfig.getBoolean("accessibility.engine.wcag22.enabled",      true);
        boolean runStructural  = A11yConfig.getBoolean("accessibility.engine.structural.enabled",  true);
        boolean runMotion      = A11yConfig.getBoolean("accessibility.engine.motion.enabled",       true);

        logger.info("[FULL SUITE] Starting scan on '{}' — engines: axe-core=true, interaction={}, wcag22={}, structural={}, motion={}",
                pageName, runInteraction, runWcag22, runStructural, runMotion);

        int total = 0;
        total += check(driver, pageName);
        if (runInteraction) total += checkInteraction(driver, pageName);
        if (runWcag22)      total += checkWcag22(driver, pageName);
        if (runStructural)  total += checkStructural(driver, pageName);
        if (runMotion)      total += checkAutoPlayMedia(driver, pageName);
        if (runMotion)      total += checkReducedMotion(driver, pageName);

        logger.info("[FULL SUITE] '{}' — grand total issues: {}", pageName, total);
        return total;
    }

    // -----------------------------------------------------------------------
    // Inner models
    // -----------------------------------------------------------------------

    /**
     * A single issue found by an interaction, layout, WCAG 2.2, structural, or motion check.
     *
     * <p>When {@code needsReview} is {@code true} the finding cannot be fully determined
     * by automated analysis alone — a human should verify it.  This mirrors BrowserStack
     * Spectra's "Needs Review" / "Assisted Test" category.</p>
     */
    public static class InteractionIssue {
        public final String ruleId;
        public final String impact;       // CRITICAL | SERIOUS | MODERATE | MINOR
        public final String wcagRef;      // e.g. "WCAG 2.5.5"
        public final String description;
        public final String element;      // truncated outerHTML
        public final String helpUrl;
        /** {@code true} when a human must confirm — equivalent to Spectra "Needs Review". */
        public final boolean needsReview;

        /** Backward-compatible constructor — {@code needsReview} defaults to {@code false}. */
        public InteractionIssue(String ruleId, String impact, String wcagRef,
                                String description, String element, String helpUrl) {
            this(ruleId, impact, wcagRef, description, element, helpUrl, false);
        }

        public InteractionIssue(String ruleId, String impact, String wcagRef,
                                String description, String element, String helpUrl,
                                boolean needsReview) {
            this.ruleId = ruleId; this.impact = impact; this.wcagRef = wcagRef;
            this.description = description; this.element = element;
            this.helpUrl = helpUrl; this.needsReview = needsReview;
        }

        @Override
        public String toString() {
            return (needsReview ? "[NEEDS REVIEW] " : "") +
                "[" + impact + "] " + ruleId + " (" + wcagRef + "): " + description
                + (element != null && !element.trim().isEmpty() ? " -> " + element : "");
        }
    }

    /** Metadata about what was scanned in a comprehensive accessibility check. */
    public static class ScanScopeInfo {
        public boolean mainDocumentScanned;
        public int totalIFramesIncluded;
        public List<String> iframesIncluded = new ArrayList<>();
        public boolean shadowDomEnabled;
        public int lwcComponentsDetected;
        public boolean salesforceLightningDetected;
        public int iframesScannedIndividually;
        public int iframesSkippedCrossOrigin;

        @Override
        public String toString() {
            return String.format(
                "ScanScope{main=%s, iframesDetected=%d, iframesScanned=%d, iframesCrossOrigin=%d, shadowDOM=%s, lwc=%d, salesforce=%s}",
                mainDocumentScanned, totalIFramesIncluded,
                iframesScannedIndividually, iframesSkippedCrossOrigin,
                shadowDomEnabled, lwcComponentsDetected, salesforceLightningDetected
            );
        }
    }
}

