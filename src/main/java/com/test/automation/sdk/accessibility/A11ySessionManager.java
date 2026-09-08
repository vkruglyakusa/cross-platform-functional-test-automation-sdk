package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.config.A11yConfig;
import io.appium.java_client.remote.SupportsContextSwitching;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.openqa.selenium.support.events.WebDriverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-level controller for the accessibility scanning subsystem.
 *
 * <p>Adds four capabilities on top of {@link AccessibilityChecker}:</p>
 * <ol>
 *   <li><b>Global runtime flag</b> — {@link #enable()} / {@link #disable()} override
 *       {@code accessibility.checking.enabled} without touching the config file.</li>
 *   <li><b>Scan deduplication</b> — tracks URL + DOM fingerprint so the same page
 *       state is never scanned twice; configurable cooldown and per-URL scan cap.</li>
 *   <li><b>SPA / navigation auto-scan</b> — wrap any {@code WebDriver} with
 *       {@link #wrapWithAutoScan(WebDriver)} (Selenium 4 {@code EventFiringDecorator})
 *       or call {@link #startUrlPoller} for hash-routing SPAs.</li>
 *   <li><b>Noise reduction</b> — allowlist known/accepted rule IDs or URL patterns,
 *       set a minimum impact threshold so minor noise does not pollute reports.</li>
 * </ol>
 *
 * <h3>Supported {@code accessibility.properties} keys</h3>
 * <pre>
 * # ── existing keys ──────────────────────────────────────────────────────
 * accessibility.checking.enabled=false
 * accessibility.fail.on.violation=false
 * accessibility.wcag.tags=wcag2a,wcag2aa
 * accessibility.output.dir=test-output/accessibility
 *
 * # ── session / dedup keys ───────────────────────────────────────────────
 * # Maximum times a URL is scanned per session (0 = unlimited, default 1)
 * accessibility.session.max.scans.per.url=1
 *
 * # Minimum ms between successive scans of the same URL (default 0 = no cooldown)
 * accessibility.session.dedup.cooldown.ms=0
 *
 * # Use DOM fingerprint to skip pages whose content has not changed (default true)
 * accessibility.session.dedup.dom.fingerprint=true
 *
 * # ── noise reduction keys ───────────────────────────────────────────────
 * # Minimum impact level to surface in reporter / Excel (MINOR|MODERATE|SERIOUS|CRITICAL)
 * accessibility.session.noise.threshold=MINOR
 *
 * # Comma-separated axe-core rule IDs to suppress globally
 * accessibility.session.allowed.rules=color-contrast,link-name
 *
 * # Comma-separated URL substrings; pages whose URL contains any of these are skipped
 * accessibility.session.allowed.urls=/admin/,/debug/
 * </pre>
 *
 * <h3>Typical usage</h3>
 * <pre>
 * // ── One-time setup ──────────────────────────────────────────────────
 * A11ySessionManager.setNoiseThreshold(ImpactThreshold.MODERATE); // only MODERATE+
 * A11ySessionManager.allowRule("color-contrast");   // accepted finding
 * A11ySessionManager.allowUrl("/login-redirect/");  // skip transient pages
 *
 * // ── Option A: manual check with session dedup ───────────────────────
 * A11ySessionManager.check(driver, "Home Page");    // skips if already scanned
 *
 * // ── Option B: auto-scan on every navigation ─────────────────────────
 * WebDriver managed = A11ySessionManager.wrapWithAutoScan(driver);
 * managed.get("https://example.com");   // scan fires automatically after navigation
 *
 * // ── Option C: SPA URL polling ───────────────────────────────────────
 * A11ySessionManager.startUrlPoller(driver, 1_000, "SPA");
 * // ... run tests ...
 * A11ySessionManager.stopUrlPoller();
 *
 * // ── Teardown ────────────────────────────────────────────────────────
 * A11ySessionManager.logSessionSummary();
 * A11ySessionManager.resetSession();
 * </pre>
 */
public final class A11ySessionManager {

    private static final Logger log = LoggerFactory.getLogger(A11ySessionManager.class);

    // ── Runtime global flag ───────────────────────────────────────────────
    private static volatile boolean sessionEnabled = true;

    // ── Dedup registry ────────────────────────────────────────────────────
    private static final ConcurrentHashMap<String, Long>    lastScanTimeByUrl       = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String>  lastDomFingerprintByUrl = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> scanCountByUrl          = new ConcurrentHashMap<>();

    // ── Noise reduction ───────────────────────────────────────────────────
    private static final Set<String> allowedRuleIds      = ConcurrentHashMap.newKeySet();
    private static final Set<String> allowedUrlFragments = ConcurrentHashMap.newKeySet();

    // ── Mutable session config (initialised from properties in static block) ─
    private static volatile int             maxScansPerUrl       = 1;
    private static volatile long            dedupCooldownMs      = 0L;
    private static volatile boolean         domFingerprintEnabled = true;
    private static volatile ImpactThreshold noiseThreshold       = ImpactThreshold.MINOR;

    // ── URL poller ────────────────────────────────────────────────────────
    private static volatile Thread  urlPollerThread;
    private static volatile boolean pollerRunning = false;

    /**
     * Default selectors for modal/dialog/overlay/calendar elements that the
     * dialog watcher treats as "dynamic content appeared". Override with
     * {@code accessibility.scan.dialog.selectors}.
     */
    private static final String DEFAULT_DIALOG_SELECTORS =
            "[role=dialog],[role=alertdialog],[aria-modal=true],dialog[open],"
          + ".modal.show,.modal.in,.slds-modal,.ui-dialog,.MuiDialog-root,"
          + ".cdk-overlay-pane,.ReactModal__Content,[class*=datepicker],[class*=calendar]";

    static {
        loadFromConfig();
    }

    private A11ySessionManager() {}

    // ─────────────────────────────────────────────────────────────────────
    // Config initialisation
    // ─────────────────────────────────────────────────────────────────────

    private static void loadFromConfig() {
        maxScansPerUrl = parseInt(
                A11yConfig.get("accessibility.session.max.scans.per.url"), 1);
        dedupCooldownMs = parseLong(
                A11yConfig.get("accessibility.session.dedup.cooldown.ms"), 0L);
        domFingerprintEnabled = A11yConfig.getBoolean(
                "accessibility.session.dedup.dom.fingerprint", true);
        noiseThreshold = ImpactThreshold.from(
                A11yConfig.get("accessibility.session.noise.threshold", "MINOR"));

        String allowedRulesRaw = A11yConfig.get("accessibility.session.allowed.rules");
        if (allowedRulesRaw != null && !allowedRulesRaw.trim().isEmpty()) {
            Arrays.stream(allowedRulesRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(allowedRuleIds::add);
        }
        String allowedUrlsRaw = A11yConfig.get("accessibility.session.allowed.urls");
        if (allowedUrlsRaw != null && !allowedUrlsRaw.trim().isEmpty()) {
            Arrays.stream(allowedUrlsRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(allowedUrlFragments::add);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Impact threshold enum
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Minimum impact severity that will be surfaced in inline reports / Excel.
     * Setting this to {@code SERIOUS} silences {@code MODERATE} and {@code MINOR} noise.
     */
    public enum ImpactThreshold {
        CRITICAL(4), SERIOUS(3), MODERATE(2), MINOR(1);

        private final int level;

        ImpactThreshold(int level) { this.level = level; }

        /**
         * Returns {@code true} when the given impact string is at or above this threshold.
         * {@code null} or blank impact strings are always included (defensive — unknown
         * impact must not be silently dropped).
         */
        public boolean includes(String impact) {
            if (impact == null || impact.trim().isEmpty()) return true; // unknown → always surface
            try {
                return ImpactThreshold.valueOf(impact.trim().toUpperCase()).level >= this.level;
            } catch (IllegalArgumentException e) {
                return true; // unrecognised impact string → always surface
            }
        }

        public static ImpactThreshold from(String value) {
            if (value == null) return MINOR;
            try { return ImpactThreshold.valueOf(value.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return MINOR; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scan decision
    // ─────────────────────────────────────────────────────────────────────

    /** Result returned by {@link #shouldScan(WebDriver)}. */
    public static final class ScanDecision {
        public final boolean shouldScan;
        public final String  reason;

        ScanDecision(boolean shouldScan, String reason) {
            this.shouldScan = shouldScan;
            this.reason     = reason;
        }

        @Override
        public String toString() {
            return (shouldScan ? "SCAN " : "SKIP ") + "— " + reason;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Global enable / disable
    // ─────────────────────────────────────────────────────────────────────

    /** Enables session scanning at runtime (overrides any earlier {@link #disable()} call). */
    public static void enable() {
        sessionEnabled = true;
        log.info("[A11Y SESSION] Scanning ENABLED (runtime override)");
    }

    /** Disables all scanning for this session without touching config files. */
    public static void disable() {
        sessionEnabled = false;
        log.info("[A11Y SESSION] Scanning DISABLED (runtime override)");
    }

    /** Returns {@code true} when both the session flag and the config property are enabled. */
    public static boolean isEnabled() {
        return sessionEnabled && A11yConfig.getBoolean("accessibility.checking.enabled", false);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Session configuration setters
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sets the maximum number of times any single URL may be scanned per session.
     * Pass {@code 0} for unlimited.  Default: {@code 1}.
     */
    public static void setMaxScansPerUrl(int max) {
        maxScansPerUrl = Math.max(0, max);
        log.info("[A11Y SESSION] maxScansPerUrl={}", maxScansPerUrl);
    }

    /**
     * Sets the minimum time (ms) that must elapse between successive scans of
     * the same URL.  Default: {@code 0} (no cooldown).
     */
    public static void setDedupCooldownMs(long ms) {
        dedupCooldownMs = Math.max(0L, ms);
        log.info("[A11Y SESSION] dedupCooldownMs={}", dedupCooldownMs);
    }

    /** Enables or disables DOM fingerprint comparison for dedup. Default: {@code true}. */
    public static void setDomFingerprintEnabled(boolean enabled) {
        domFingerprintEnabled = enabled;
    }

    /**
     * Sets the minimum impact level shown in inline reporter messages and Excel.
     * Violations below this threshold are still written to JSON artifacts but are
     * suppressed from the live reporter output.
     * Default: {@link ImpactThreshold#MINOR} (show everything).
     */
    public static void setNoiseThreshold(ImpactThreshold threshold) {
        noiseThreshold = (threshold != null) ? threshold : ImpactThreshold.MINOR;
        log.info("[A11Y SESSION] noiseThreshold={}", noiseThreshold);
    }

    /** String-based overload: {@code "moderate"}, {@code "serious"}, etc. */
    public static void setNoiseThreshold(String threshold) {
        setNoiseThreshold(ImpactThreshold.from(threshold));
    }

    /** Returns the current noise threshold. */
    public static ImpactThreshold getNoiseThreshold() { return noiseThreshold; }

    // ─────────────────────────────────────────────────────────────────────
    // Allowlist management
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds an axe-core rule ID to the global allowlist.
     * Violations for this rule are suppressed from inline reporter output and Excel.
     * JSON scan artifacts are unaffected (complete record is always preserved).
     */
    public static void allowRule(String ruleId) {
        if (ruleId != null && !ruleId.trim().isEmpty()) {
            allowedRuleIds.add(ruleId.trim());
            log.info("[A11Y SESSION] Rule '{}' added to allowlist (suppressed from reports)", ruleId.trim());
        }
    }

    /** Adds multiple rule IDs in one call. */
    public static void allowRules(String... ruleIds) {
        for (String r : ruleIds) allowRule(r);
    }

    /** Removes a rule from the allowlist. */
    public static void removeAllowedRule(String ruleId) { allowedRuleIds.remove(ruleId); }

    /** Returns an unmodifiable view of the current rule allowlist. */
    public static Set<String> getAllowedRules() { return Collections.unmodifiableSet(allowedRuleIds); }

    /**
     * Adds a URL substring to the allowlist.
     * Pages whose {@code getCurrentUrl()} contains this string are skipped entirely.
     */
    public static void allowUrl(String urlFragment) {
        if (urlFragment != null && !urlFragment.trim().isEmpty()) {
            allowedUrlFragments.add(urlFragment.trim());
            log.info("[A11Y SESSION] URL fragment '{}' added to allowlist (pages skipped)", urlFragment.trim());
        }
    }

    /** Removes a URL fragment from the allowlist. */
    public static void removeAllowedUrl(String urlFragment) { allowedUrlFragments.remove(urlFragment); }

    // ─────────────────────────────────────────────────────────────────────
    // Noise filter (public so AccessibilityChecker can call it)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the violation with the given rule ID and impact
     * should be surfaced in reports (not in the allowlist, meets noise threshold).
     * JSON artifacts are always complete; this gate only applies to the live reporter.
     */
    public static boolean isReportable(String ruleId, String impact) {
        if (ruleId != null && allowedRuleIds.contains(ruleId.trim())) return false;
        return noiseThreshold.includes(impact);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scan decision logic
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Evaluates whether a scan should run for the driver's current page.
     * Checks — in order:
     * <ol>
     *   <li>Session / config enable flag</li>
     *   <li>URL fragment allowlist</li>
     *   <li>Per-URL scan count cap</li>
     *   <li>Cooldown (minimum ms between scans of same URL)</li>
     *   <li>DOM fingerprint (skip if page content unchanged)</li>
     * </ol>
     */
    public static ScanDecision shouldScan(WebDriver driver) {
        if (!sessionEnabled) {
            return new ScanDecision(false, "session scanning disabled via A11ySessionManager.disable()");
        }
        if (!A11yConfig.getBoolean("accessibility.checking.enabled", false)) {
            return new ScanDecision(false, "accessibility.checking.enabled=false");
        }
        if (isMobileNativeContext(driver)) {
            return new ScanDecision(false, "mobile driver is in a native (non-WebView) context -- "
                    + "axe-core requires a DOM to inject into, scan skipped (see MOBILE-USER-GUIDE.md Hybrid App Testing)");
        }

        String url = tryGetUrl(driver);
        if (url == null) {
            return new ScanDecision(false, "could not read current URL from driver");
        }

        // URL allowlist
        for (String fragment : allowedUrlFragments) {
            if (url.contains(fragment)) {
                return new ScanDecision(false, "URL matches allowlist fragment '" + fragment + "'");
            }
        }

        // Per-URL scan cap
        int count = scanCountByUrl.getOrDefault(url, 0);
        if (maxScansPerUrl > 0 && count >= maxScansPerUrl) {
            return new ScanDecision(false, "already scanned " + count + "× — maxScansPerUrl=" + maxScansPerUrl
                    + " for: " + abbreviate(url));
        }

        // Cooldown
        Long lastTime = lastScanTimeByUrl.get(url);
        if (lastTime != null && dedupCooldownMs > 0) {
            long elapsed = System.currentTimeMillis() - lastTime;
            if (elapsed < dedupCooldownMs) {
                return new ScanDecision(false, "cooldown: " + elapsed + "ms < " + dedupCooldownMs
                        + "ms required for: " + abbreviate(url));
            }
        }

        // DOM fingerprint
        if (domFingerprintEnabled) {
            String prev = lastDomFingerprintByUrl.get(url);
            if (prev != null) {
                String current = computeDomFingerprint(driver);
                if (current != null && current.equals(prev)) {
                    return new ScanDecision(false, "DOM unchanged since last scan of: " + abbreviate(url));
                }
            }
        }

        return new ScanDecision(true, abbreviate(url));
    }

    /**
     * Records a completed scan in the session registry.
     * Must be called after every successful scan to keep the dedup state accurate.
     * Called automatically by {@link #check} and {@link #checkFullSuite}.
     */
    public static void recordScan(WebDriver driver) {
        String url = tryGetUrl(driver);
        if (url == null) return;
        lastScanTimeByUrl.put(url, System.currentTimeMillis());
        scanCountByUrl.merge(url, 1, Integer::sum);
        if (domFingerprintEnabled) {
            String fp = computeDomFingerprint(driver);
            if (fp != null) lastDomFingerprintByUrl.put(url, fp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Session-managed scan entry points
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Session-aware wrapper around {@link AccessibilityChecker#check}.
     * Applies dedup before scanning and records the scan afterwards.
     *
     * @return violation count, or {@code -1} if the scan was skipped by dedup logic
     */
    public static int check(WebDriver driver, String pageName) {
        ScanDecision d = shouldScan(driver);
        if (!d.shouldScan) {
            log.info("[A11Y SESSION] check() SKIPPED for '{}' — {}", pageName, d.reason);
            return -1;
        }
        log.info("[A11Y SESSION] {}", d);
        try {
            int count = AccessibilityChecker.check(driver, pageName);
            recordScan(driver);
            return count;
        } catch (AccessibilityViolationException e) {
            recordScan(driver);
            throw e;
        }
    }

    /**
     * Session-aware wrapper around {@link AccessibilityChecker#checkFullSuite}.
     *
     * @return total issue count, or {@code -1} if skipped by dedup logic
     */
    public static int checkFullSuite(WebDriver driver, String pageName) {
        ScanDecision d = shouldScan(driver);
        if (!d.shouldScan) {
            log.info("[A11Y SESSION] checkFullSuite() SKIPPED for '{}' — {}", pageName, d.reason);
            return -1;
        }
        log.info("[A11Y SESSION] {}", d);
        try {
            int count = AccessibilityChecker.checkFullSuite(driver, pageName);
            recordScan(driver);
            return count;
        } catch (AccessibilityViolationException e) {
            recordScan(driver);
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Auto-scan via Selenium 4 EventFiringDecorator
    // ─────────────────────────────────────────────────────────────────────


    /**
     * Wraps the provided {@code WebDriver} with a Selenium 4
     * {@link EventFiringDecorator} that automatically triggers a
     * session-managed {@link #check} after every navigation event
     * ({@code get}, {@code navigate().to()}, {@code back()}, {@code forward()},
     * {@code refresh()}).
     *
     * <p>All dedup, noise, and allowlist rules still apply — duplicate page
     * states are automatically skipped.</p>
     *
     * <pre>
     * WebDriver driver = A11ySessionManager.wrapWithAutoScan(new ChromeDriver());
     * driver.get("https://example.com");  // scan fires automatically
     * driver.navigate().back();           // scan fires automatically
     * </pre>
     *
     * @param driver the raw WebDriver to decorate
     * @return a wrapped WebDriver; pass this instance to all subsequent calls
     */
    public static WebDriver wrapWithAutoScan(WebDriver driver) {
        EventFiringDecorator<WebDriver> decorator =
                new EventFiringDecorator<>(new AutoScanListener(driver));
        WebDriver wrapped = decorator.decorate(driver);
        log.info("[A11Y SESSION] WebDriver wrapped with auto-scan listener");
        return wrapped;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SPA URL poller (hash-routing / pushState SPAs)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Starts a background daemon thread that polls {@code driver.getCurrentUrl()}
     * at the given interval.  When the URL changes, a session-managed
     * {@link #check} is triggered automatically.
     *
     * <p>Use this for single-page applications that change the URL without
     * generating Selenium navigation events.</p>
     *
     * <p><b>Thread-safety note:</b> the poller calls {@code getCurrentUrl()} and
     * {@code AccessibilityChecker.check()} from a background thread.  Ensure your
     * WebDriver implementation supports concurrent access, or use
     * {@link #wrapWithAutoScan} (event-driven, same thread) instead.</p>
     *
     * @param driver         the WebDriver to monitor
     * @param pollIntervalMs poll frequency in ms (minimum 200)
     * @param sessionLabel   prefix for auto-generated page names in reports
     */
    public static synchronized void startUrlPoller(WebDriver driver,
                                                   long pollIntervalMs,
                                                   String sessionLabel) {
        boolean watchDialogs = A11yConfig.getBoolean("accessibility.scan.on.dialog", false);
        startWatcher(driver, pollIntervalMs, sessionLabel, true, watchDialogs);
    }

    /**
     * Starts watchers based on configuration, for a freshly discovered driver.
     * Enables URL polling when {@code accessibility.session.spa.poll.interval.ms > 0}
     * and/or dialog/overlay watching when {@code accessibility.scan.on.dialog=true}.
     * Does nothing when neither is configured.
     */
    public static synchronized void startWatchersIfConfigured(WebDriver driver, String sessionLabel) {
        long spaPoll = parseLong(A11yConfig.get("accessibility.session.spa.poll.interval.ms"), 0L);
        boolean watchDialogs = A11yConfig.getBoolean("accessibility.scan.on.dialog", false);
        boolean watchUrl = spaPoll > 0;
        if (!watchUrl && !watchDialogs) {
            return;
        }
        long interval = watchUrl
                ? spaPoll
                : parseLong(A11yConfig.get("accessibility.scan.dialog.poll.interval.ms"), 1000L);
        startWatcher(driver, interval, sessionLabel, watchUrl, watchDialogs);
    }

    /**
     * Unified background watcher. Polls at {@code pollIntervalMs} and, depending on
     * the flags, triggers scans when:
     * <ul>
     *   <li><b>watchUrl</b> — the current URL changes (SPA / client-side routing); a
     *       session-managed {@link #check} runs for the new URL.</li>
     *   <li><b>watchDialogs</b> — a new modal/dialog/overlay/calendar appears (matched
     *       by {@code accessibility.scan.dialog.selectors}); a full-suite scan of the
     *       current DOM runs so the dynamic content is captured. Dialog scans bypass the
     *       per-URL dedup cap (the same URL is usually already scanned) and are
     *       deduplicated by a signature of the open dialogs instead.</li>
     * </ul>
     *
     * <p><b>Thread-safety note:</b> calls {@code getCurrentUrl()}, JS execution and the
     * scanner from a background thread — ensure your WebDriver tolerates concurrent
     * access, or rely on the per-test end scan instead.</p>
     */
    public static synchronized void startWatcher(WebDriver driver,
                                                 long pollIntervalMs,
                                                 String sessionLabel,
                                                 boolean watchUrl,
                                                 boolean watchDialogs) {
        stopUrlPoller();
        if (!watchUrl && !watchDialogs) {
            return;
        }
        pollerRunning = true;
        final long interval = Math.max(200L, pollIntervalMs);
        final String[] lastUrl = { tryGetUrl(driver) };
        final String[] lastDialogSig = { "" };

        urlPollerThread = new Thread(() -> {
            while (pollerRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (watchUrl) {
                    String current = tryGetUrl(driver);
                    if (current != null && !current.equals(lastUrl[0])) {
                        log.info("[A11Y POLLER] URL changed {} → {} — triggering auto-scan",
                                abbreviate(lastUrl[0]), abbreviate(current));
                        lastUrl[0] = current;
                        // URL changed — reset dialog signature so a dialog on the new page rescans
                        lastDialogSig[0] = "";
                        String pageName = resolvePageName(driver, abbreviate(current));
                        check(driver, pageName);
                    }
                }

                if (watchDialogs) {
                    String sig = computeDialogSignature(driver);
                    if (sig == null) {
                        // could not read DOM (cross-origin / not ready) — skip this tick
                        continue;
                    }
                    if (sig.isEmpty()) {
                        // no dialog open now — reset so the next open is treated as new
                        lastDialogSig[0] = "";
                    } else if (!sig.equals(lastDialogSig[0])) {
                        lastDialogSig[0] = sig;
                        log.info("[A11Y WATCHER] New dialog/overlay detected — triggering full-suite scan");
                        Diag.print("Dialog/overlay detected — scanning dynamic content");
                        try {
                            // Bypass per-URL dedup: scan the live DOM directly (dialog sig dedups).
                            AccessibilityChecker.checkFullSuite(driver, sessionLabel + " [dialog]");
                            recordScan(driver);
                        } catch (Exception e) {
                            log.debug("[A11Y WATCHER] Dialog scan error: {}", e.getMessage());
                        }
                    }
                }
            }
            log.info("[A11Y POLLER] Stopped");
        }, "a11y-watcher");
        urlPollerThread.setDaemon(true);
        urlPollerThread.start();
        log.info("[A11Y SESSION] Watcher started (interval={}ms, url={}, dialogs={})",
                interval, watchUrl, watchDialogs);
    }

    /**
     * Returns a signature of the currently <em>visible</em> dialog/overlay elements,
     * or {@code ""} when none are open, or {@code null} when the DOM can't be read.
     * Selectors are configurable via {@code accessibility.scan.dialog.selectors}.
     */
    private static String computeDialogSignature(WebDriver driver) {
        String selectors = A11yConfig.get("accessibility.scan.dialog.selectors", DEFAULT_DIALOG_SELECTORS);
        try {
            Object res = ((JavascriptExecutor) driver).executeScript(
                "var sel=arguments[0];var out=[];" +
                "try{var nodes=document.querySelectorAll(sel);" +
                "for(var i=0;i<nodes.length;i++){var el=nodes[i];" +
                "var r=el.getBoundingClientRect();" +
                "if(r.width>0&&r.height>0){" +
                "out.push((el.id||'')+'#'+(el.getAttribute('aria-label')||'')+'#'+" +
                "(typeof el.className==='string'?el.className:'')+'#'+" +
                "Math.round(r.width)+'x'+Math.round(r.height));}}}catch(e){return null;}" +
                "return out.join('|');",
                selectors);
            return res == null ? "" : res.toString();
        } catch (Exception e) {
            return null;
        }
    }


    /** Stops the URL poller thread if it is running. */
    public static synchronized void stopUrlPoller() {
        pollerRunning = false;
        if (urlPollerThread != null) {
            urlPollerThread.interrupt();
            urlPollerThread = null;
            log.info("[A11Y SESSION] URL poller stopped");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Session state management
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Clears scan history (URL counts, timestamps, DOM fingerprints).
     * Allowlist and threshold settings are preserved.
     */
    public static void resetScanHistory() {
        lastScanTimeByUrl.clear();
        lastDomFingerprintByUrl.clear();
        scanCountByUrl.clear();
        log.info("[A11Y SESSION] Scan history cleared ({} URLs removed)", scanCountByUrl.size());
    }

    /**
     * Full reset: clears history AND resets all configuration back to
     * property-file / static-initializer defaults.
     * Stops the URL poller if running.
     */
    public static void resetSession() {
        stopUrlPoller();
        resetScanHistory();
        allowedRuleIds.clear();
        allowedUrlFragments.clear();
        sessionEnabled       = true;
        noiseThreshold       = ImpactThreshold.MINOR;
        maxScansPerUrl       = 1;
        dedupCooldownMs      = 0L;
        domFingerprintEnabled = true;
        loadFromConfig(); // re-apply property file values
        log.info("[A11Y SESSION] Full session reset complete");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Diagnostics
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Logs a deduplicated session summary at INFO level.
     * Call this in your {@code @AfterSuite} / {@code @AfterAll} to see a clean
     * post-run report of what was scanned and what was skipped.
     */
    public static void logSessionSummary() {
        int totalScans  = scanCountByUrl.values().stream().mapToInt(Integer::intValue).sum();
        int uniqueUrls  = scanCountByUrl.size();
        log.info("[A11Y SESSION SUMMARY] ==========================================");
        log.info("[A11Y SESSION SUMMARY]  Unique URLs scanned   : {}", uniqueUrls);
        log.info("[A11Y SESSION SUMMARY]  Total scans performed : {}", totalScans);
        log.info("[A11Y SESSION SUMMARY]  Noise threshold       : {}", noiseThreshold);
        log.info("[A11Y SESSION SUMMARY]  Allowed rules ({})    : {}", allowedRuleIds.size(), allowedRuleIds);
        log.info("[A11Y SESSION SUMMARY]  Allowed URL patterns  : {}", allowedUrlFragments);
        log.info("[A11Y SESSION SUMMARY]  maxScansPerUrl        : {}", maxScansPerUrl);
        log.info("[A11Y SESSION SUMMARY]  dedupCooldownMs       : {}", dedupCooldownMs);
        if (!scanCountByUrl.isEmpty()) {
            log.info("[A11Y SESSION SUMMARY]  Per-URL scan counts:");
            scanCountByUrl.forEach((url, count) ->
                log.info("[A11Y SESSION SUMMARY]    {}× {}", count, abbreviate(url)));
        }
        log.info("[A11Y SESSION SUMMARY] ==========================================");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    static String computeDomFingerprint(WebDriver driver) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                "var b=document.body;if(!b)return '';" +
                "return (document.title||'')" +
                "  +'|'+(document.URL||'')" +
                "  +'|'+(b.children.length||0)" +
                "  +'|'+(b.innerText||'').substring(0,500);");
            return r != null ? String.valueOf(r.hashCode()) : null;
        } catch (Exception e) {
            log.debug("[A11Y SESSION] DOM fingerprint error: {}", e.getMessage());
            return null;
        }
    }

    private static String tryGetUrl(WebDriver driver) {
        try { return driver.getCurrentUrl(); }
        catch (Exception e) { return null; }
    }

    /**
     * True when {@code driver} is a mobile (Appium) driver currently in a native
     * (non-WebView) context. Native Android/iOS screens have no DOM at all, so
     * axe-core's {@code JavascriptExecutor}-based injection has nothing to scan --
     * without this guard, {@link AccessibilityChecker} throws on every native-screen
     * test once accessibility scanning is enabled for a mobile suite. Web/desktop
     * drivers (not {@link SupportsContextSwitching}) always return {@code false} here
     * and are unaffected.
     */
    private static boolean isMobileNativeContext(WebDriver driver) {
        if (!(driver instanceof SupportsContextSwitching)) {
            return false;
        }
        try {
            String context = ((SupportsContextSwitching) driver).getContext();
            return context == null || !context.startsWith("WEBVIEW");
        } catch (Exception e) {
            // Context could not be determined -- be conservative and skip rather than
            // let axe-core throw against an unknown/native context.
            return true;
        }
    }

    static String abbreviate(String url) {
        if (url == null) return "(null)";
        return url.length() > 70 ? url.substring(0, 67) + "..." : url;
    }

    /**
     * Resolves a human-readable page name for report {@code pageName} columns.
     *
     * <p>Resolution order (first non-generic winner wins):</p>
     * <ol>
     *   <li><b>Browser page title</b> — {@code driver.getTitle()} when non-blank and
     *       not a generic browser placeholder ("New Tab", "Untitled", etc.).</li>
     *   <li><b>URL path + host</b> — host + path extracted from
     *       {@code driver.getCurrentUrl()}, query/fragment stripped.</li>
     *   <li><b>fallback</b> — whatever the caller supplied (test name, trigger label,
     *       etc.).</li>
     * </ol>
     *
     * <p>Never throws — all driver calls are guarded so a broken driver does not
     * prevent the scan artifact from being written.</p>
     *
     * @param driver   active WebDriver (may be wrapped / decorated)
     * @param fallback value to use when title and URL are both unavailable/generic
     * @return a human-readable page label, never {@code null}
     */
    public static String resolvePageName(WebDriver driver, String fallback) {
        // ── 1. Page title ─────────────────────────────────────────────────
        try {
            String title = driver.getTitle();
            if (title != null && !title.trim().isEmpty()) {
                String t = title.trim();
                String tl = t.toLowerCase(java.util.Locale.ROOT);
                if (!tl.equals("new tab")
                        && !tl.equals("untitled")
                        && !tl.equals("document")
                        && !tl.equals("loading...")
                        && !tl.equals("waiting...")
                        && !tl.startsWith("about:")
                        && tl.length() > 1) {
                    log.debug("[A11Y PAGE NAME] Resolved from title: '{}'", t);
                    return t;
                }
            }
        } catch (Exception ignored) { /* driver may not support getTitle in current state */ }

        // ── 2. URL path ───────────────────────────────────────────────────
        try {
            String url = tryGetUrl(driver);
            if (url != null && !url.isEmpty()
                    && !url.startsWith("about:")
                    && !url.startsWith("data:")
                    && !url.startsWith("chrome-extension:")) {
                java.net.URI uri = new java.net.URI(url);
                String host = uri.getHost();
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && !path.equals("/")) {
                    // strip trailing slash, collapse multiple slashes
                    String cleanPath = path.replaceAll("/+$", "").replaceAll("//+", "/");
                    String label = (host != null && !host.isEmpty())
                            ? host + cleanPath
                            : cleanPath;
                    log.debug("[A11Y PAGE NAME] Resolved from URL path: '{}'", label);
                    return label;
                }
                if (host != null && !host.isEmpty()) {
                    log.debug("[A11Y PAGE NAME] Resolved from URL host: '{}'", host);
                    return host;
                }
            }
        } catch (Exception ignored) { /* malformed URL or driver error */ }

        // ── 3. Caller fallback (test name / trigger label) ────────────────
        log.debug("[A11Y PAGE NAME] Using fallback: '{}'", fallback);
        return fallback != null ? fallback : "(unknown)";
    }

    private static int parseInt(String value, int def) {
        if (value == null || value.trim().isEmpty()) return def;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String value, long def) {
        if (value == null || value.trim().isEmpty()) return def;
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return def; }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Inner class: Selenium 4 WebDriverListener for auto-scan
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Selenium 4 {@link WebDriverListener} that fires a session-managed scan
     * after every navigation event.
     * Obtain a decorated driver via {@link A11ySessionManager#wrapWithAutoScan}.
     *
     * <p>Navigation method signatures in Selenium 4:</p>
     * <ul>
     *   <li>{@code afterGet(url, WebDriver)} — fires for {@code driver.get(url)}</li>
     *   <li>{@code afterNavigateTo/Back/Forward/Refresh(Navigation)} — fires for
     *       {@code driver.navigate().*()}</li>
     * </ul>
     * The original (pre-decoration) driver is stored at construction time and used
     * for navigate() hooks where no {@code WebDriver} parameter is available.
     */
    public static final class AutoScanListener implements WebDriverListener {

        /** The raw driver held for Navigation callbacks (which pass Navigation, not WebDriver). */
        private final WebDriver originalDriver;
        private String lastScannedUrl = null;

        public AutoScanListener(WebDriver driver) {
            this.originalDriver = driver;
        }

        // ── driver.get(url) ──────────────────────────────────────────────
        // Selenium 4.19 signature: afterGet(WebDriver driver, String url)
        @Override
        public void afterGet(WebDriver driver, String url) {
            scanIfUrlChanged(driver, "get");
        }

        // ── driver.navigate().to(url) ────────────────────────────────────
        // Selenium 4.19 signature: afterTo(Navigation nav, String url)
        @Override
        public void afterTo(WebDriver.Navigation navigation, String url) {
            scanIfUrlChanged(originalDriver, "navigate-to");
        }

        // ── driver.navigate().back() ─────────────────────────────────────
        // Selenium 4.19 signature: afterBack(Navigation nav)
        @Override
        public void afterBack(WebDriver.Navigation navigation) {
            scanIfUrlChanged(originalDriver, "navigate-back");
        }

        // ── driver.navigate().forward() ──────────────────────────────────
        // Selenium 4.19 signature: afterForward(Navigation nav)
        @Override
        public void afterForward(WebDriver.Navigation navigation) {
            scanIfUrlChanged(originalDriver, "navigate-forward");
        }

        // ── driver.navigate().refresh() ──────────────────────────────────
        // Selenium 4.19 signature: afterRefresh(Navigation nav)
        @Override
        public void afterRefresh(WebDriver.Navigation navigation) {
            String url = tryGetUrl(originalDriver);
            if (url != null) {
                // On refresh the URL is the same but DOM may differ — clear fingerprint
                lastDomFingerprintByUrl.remove(url);
            }
            scanIfUrlChanged(originalDriver, "navigate-refresh");
        }

        private void scanIfUrlChanged(WebDriver driver, String trigger) {
            String url = tryGetUrl(driver);
            if (url == null) return;
            if (url.equals(lastScannedUrl)) return;
            lastScannedUrl = url;
            log.debug("[A11Y AUTO-SCAN] Triggered by '{}' for: {}", trigger, abbreviate(url));
            String pageName = resolvePageName(driver, abbreviate(url));
            checkFullSuite(driver, pageName);
        }
    }
}



