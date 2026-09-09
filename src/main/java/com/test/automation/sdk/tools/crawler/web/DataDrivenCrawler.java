package com.test.automation.sdk.tools.crawler.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * DataDrivenCrawler -- multi-pass, full-page-aware crawler for dynamic forms.
 *
 * WHY THIS EXISTS
 * ---------------
 * Enterprise form applications (MS Dynamics 365, Salesforce, ServiceNow, Angular
 * reactive forms) change the DOM in response to user input:
 *   * Selecting a dropdown value shows/hides entire field sections
 *   * Business rules inject new fields, panels, and controls dynamically
 *   * A single static crawl snapshot misses all conditional elements
 *
 * HOW IT WORKS  (fullScanMode=true, the default)
 * ------------------------------------------------
 * 1. Initial crawl   -- snapshot the page in its initial state (baseline)
 * 2. For each step:
 *    a. Capture current URL (page-state key)
 *    b. Resolve the target element -- by raw XPath or via {@link ElementSearchEngine}
 *       using semantic hints (label, placeholder, aria-label, visible text, formcontrolname)
 *    c. Execute the interaction (select, type, click, wait)
 *    d. Wait for DOM to stabilize using a MutationObserver injected via JavaScript
 *    e. Re-crawl the FULL page (all interactive elements, not just DOM diff)
 *    f. Detect page navigation (URL change) -- tag elements with new page-state
 *    g. Merge into master set -- XPath-keyed deduplication retains first-seen tag
 * 3. Return merged list -- ALL elements from ALL steps/pages, deduplicated by XPath
 *
 * FULL-SCAN vs DIFF-ONLY
 * ----------------------
 * fullScanMode=true  (default): crawls every step regardless of DOM mutation count.
 *   - Captures every interactive element visible at each page state in one run.
 *   - No separate per-page crawler runs needed; one test-case run covers everything.
 *   - XPath deduplication is cheap; redundant re-discovery is filtered automatically.
 * fullScanMode=false (diff-only): crawls only when MutationObserver detected changes.
 *   - Faster for shallow forms where pages don't change much.
 *   - May miss elements already present but not yet scanned.
 *
 * PAGE-STATE TAGGING
 * ------------------
 * Each element is tagged with the page-state route (URL hash segment or last path
 * segment) where it was first discovered.  Elements found across multiple page states
 * are tagged "Multiple pages: A, B".
 *
 * TEST CASE MODE  (recommended for ADO-driven workflows)
 * ------------------------------------------------------
 * {@link #crawlTestCase(String, String, List)} follows the entire test case in one
 * continuous flow.  Every step is a full-page snapshot point -- no separate runs needed.
 *
 *   List<CrawlerStep> steps = Arrays.asList(
 *       CrawlerStep.selectByLabel("Complaint Type", "Noise")
 *                  .describe("Step 1: Select Complaint Type"),
 *       CrawlerStep.clickByText("Next")
 *                  .describe("Step 2: Click Next")
 *   );
 *   List<ElementInfo> elements = crawler.crawlTestCase(url, "TC-311: Noise Complaint", steps);
 *
 * MULTI-SCENARIO MODE
 * -------------------
 * {@link #crawl(String)} runs multiple independent {@link CrawlerScenario}s, each
 * representing a different form path (e.g. "Complaint" vs "Request" case type).
 *
 * DEDUPLICATION
 * -------------
 * Elements are keyed by their best unique XPath.
 * Same element appearing across multiple steps/scenarios -> kept once, tagged "All scenarios".
 * Element unique to one step/scenario -> tagged with that step/scenario name.
 *
 * @author vkruglyak
 */
public class DataDrivenCrawler {

    private static final Logger log = LogManager.getLogger(DataDrivenCrawler.class.getName());

    /** No DOM mutations for this many ms -> DOM is considered stable. */
    private static final int STABILITY_QUIET_MS  = 500;
    /** Maximum seconds to wait for DOM stability after a step. */
    private static final int STABILITY_TIMEOUT_S = 10;
    /** Polling interval while waiting for stability. */
    private static final int STABILITY_POLL_MS   = 200;

    private final WebDriver           driver;
    private final ElementCrawler      elementCrawler;
    private final ElementSearchEngine searchEngine;
    private final List<CrawlerScenario> scenarios = new ArrayList<>();

    /** Master element registry: XPath key -> ElementInfo (with scenario/step tags). */
    private final LinkedHashMap<String, ElementInfo> masterElements   = new LinkedHashMap<>();
    /** XPath key -> set of scenario/step names where this element was seen. */
    private final Map<String, Set<String>>           elementScenarios = new LinkedHashMap<>();
    /** XPath key -> set of page-state labels (URL route) where this element was seen. */
    private final Map<String, Set<String>>           elementPageStates = new LinkedHashMap<>();

    /**
     * When true (default): crawl ALL interactive elements on the page after every step,
     * regardless of whether the MutationObserver detected a DOM change.  This ensures
     * every page state visited during the test case flow is fully inventoried in a
     * single crawler run -- no separate per-page runs needed.
     *
     * When false (diff-only): crawl only when DOM mutations were detected.  Faster for
     * shallow forms, but may miss elements that were already present before a step.
     */
    private boolean fullScanMode = true;

    /**
     * When true: before merging a full-scan snapshot, compute a lightweight state
     * fingerprint (hash of the interactive-element structure) and skip the merge if an
     * identical state was already recorded for a non-navigating step. Inspired by the
     * view-hierarchy-hashing / state-deduplication technique used by mobile exploratory
     * crawlers (Google Robo/Firebase Test Lab, Fastbot) to avoid re-processing a state
     * that was already fully inventoried -- e.g. a step that clicks a button with no
     * visible effect, or a modal that re-opens identically. Default false (preserves
     * prior behavior exactly); opt in via {@link #setStateDeduplication(boolean)}.
     */
    private boolean stateDeduplication = false;

    /** Fingerprints of page states already merged -- only populated when {@link #stateDeduplication} is on. */
    private final Set<String> visitedStateFingerprints = new LinkedHashSet<>();

    // =========================================================================

    public DataDrivenCrawler(WebDriver driver) {
        this.driver         = driver;
        this.elementCrawler = new ElementCrawler(driver);
        this.searchEngine   = new ElementSearchEngine(driver);
    }

    /** Add a scenario to execute during a {@link #crawl(String)} run. */
    public DataDrivenCrawler addScenario(CrawlerScenario scenario) {
        scenarios.add(scenario);
        return this;
    }

    /**
     * Switch to diff-only mode: crawl only when the MutationObserver detected DOM changes.
     * By default, fullScanMode=true (recommended -- crawls every step for full coverage).
     * Use this only when crawl speed is critical and the form is known to be shallow.
     */
    public DataDrivenCrawler setDiffOnlyMode(boolean diffOnly) {
        this.fullScanMode = !diffOnly;
        return this;
    }

    /**
     * Enables state-fingerprint deduplication: a full-scan snapshot is skipped (not
     * merged) when its computed state fingerprint exactly matches the previous merged
     * state and the step did not navigate. Use this for scenarios prone to re-visiting
     * an identical UI state (e.g. repeated steps, retried clicks) where you want the
     * discovery report to reflect only genuinely distinct states. Off by default.
     */
    public DataDrivenCrawler setStateDeduplication(boolean enabled) {
        this.stateDeduplication = enabled;
        return this;
    }

    // =========================================================================
    // TEST CASE MODE -- single continuous flow following all steps
    // =========================================================================

    /**
     * Navigate to {@code url} and follow every step in {@code steps} as a single
     * continuous test case flow.  After each step the DOM is checked for changes;
     * newly discovered elements are tagged with the step's label and index.
     *
     * This is the recommended entry point when working from ADO test cases.
     *
     * @param url          page URL to open before starting
     * @param testCaseName name used in element tags and the discovery report
     * @param steps        ordered list of test case steps
     * @return merged list of all elements from baseline + every step
     */
    public List<ElementInfo> crawlTestCase(String url, String testCaseName,
                                            List<CrawlerStep> steps) {
        resetMaster();
        log.info("=== crawlTestCase [" + testCaseName + "] ===================================");
        log.info("    URL   : " + url);
        log.info("    Steps : " + steps.size());

        driver.get(url);
        waitForPageReady();

        // Baseline snapshot
        log.info("-- Baseline -------------------------------------------------------------");
        mergeElements(elementCrawler.crawlCurrentPage(), "Baseline", "", pageStateLabel(safeGetUrl()));

        // Execute steps
        for (int i = 0; i < steps.size(); i++) {
            CrawlerStep step = steps.get(i);
            String stepLabel = buildStepLabel(i + 1, step);

            log.info("-- " + stepLabel + " --------------------------------------------------");

            String urlBefore = safeGetUrl();
            boolean changed = executeAndDetectChange(step);
            String urlAfter = safeGetUrl();
            boolean navigated = !urlBefore.equals(urlAfter);
            String pageState = pageStateLabel(urlAfter);

            if (fullScanMode || changed || navigated) {
                if (!isNewStateOrDedupDisabled(navigated)) {
                    log.info("   Skipped (state dedup, no navigation)");
                    continue;
                }
                String scanLabel = navigated
                    ? stepLabel + " [page: " + pageState + "]"
                    : stepLabel;
                List<ElementInfo> snap = elementCrawler.crawlCurrentPage();
                int added = mergeElements(snap, testCaseName, scanLabel, pageState);
                log.info("   Scanned -> " + added + " new element(s)"
                    + " | page: " + pageState
                    + " | DOM changed: " + changed
                    + " | navigated: " + navigated
                    + " | mode: " + (fullScanMode ? "full" : "diff"));
            } else {
                log.info("   Skipped (diff-only mode, no DOM change, no navigation)");
            }
        }

        applyScenarioTags();
        log.info("crawlTestCase complete. Total elements: " + masterElements.size());
        return new ArrayList<>(masterElements.values());
    }

    // =========================================================================
    // MULTI-SCENARIO MODE -- multiple independent paths
    // =========================================================================

    /**
     * Navigate to {@code url}, run the baseline crawl, execute all registered
     * {@link CrawlerScenario}s, and return the merged element list.
     */
    public List<ElementInfo> crawl(String url) {
        resetMaster();
        log.info("=== DataDrivenCrawler.crawl ============================================");
        log.info("    URL       : " + url);
        log.info("    Scenarios : " + scenarios.size());

        driver.get(url);
        waitForPageReady();

        // Baseline
        log.info("-- Baseline -------------------------------------------------------------");
        mergeElements(elementCrawler.crawlCurrentPage(), "Baseline", "", pageStateLabel(safeGetUrl()));

        for (CrawlerScenario scenario : scenarios) {
            log.info("-- Scenario: \"" + scenario.getName() + "\" ------------------------------");

            if (scenario.isResetPageBeforeRun()) {
                log.info("   Resetting page...");
                driver.navigate().refresh();
                waitForPageReady();
            }

            List<CrawlerStep> steps = scenario.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                CrawlerStep step = steps.get(i);
                String stepLabel = buildStepLabel(i + 1, step);
                log.info("   Executing: " + step);

                String urlBefore = safeGetUrl();
                boolean changed = executeAndDetectChange(step);
                String urlAfter = safeGetUrl();
                boolean navigated = !urlBefore.equals(urlAfter);
                String pageState = pageStateLabel(urlAfter);

                if ((scenario.isSnapshotAfterEachStep() || fullScanMode || navigated) && (fullScanMode || changed || navigated)) {
                    if (!isNewStateOrDedupDisabled(navigated)) {
                        log.info("   After step: skipped (state dedup, no navigation)");
                    } else {
                        String scanLabel = navigated
                            ? stepLabel + " [page: " + pageState + "]"
                            : stepLabel;
                        List<ElementInfo> snap = elementCrawler.crawlCurrentPage();
                        int added = mergeElements(snap, scenario.getName(), scanLabel, pageState);
                        log.info("   After step: " + added + " new element(s)"
                            + " | page: " + pageState
                            + " | navigated: " + navigated);
                    }
                }
            }

            // End-of-scenario snapshot (taken when NOT snapshotAfterEachStep and NOT fullScanMode,
            // or when the last step did not scan yet)
            if (!scenario.isSnapshotAfterEachStep() && !fullScanMode) {
                waitForDomStability();
                String pageState = pageStateLabel(safeGetUrl());
                List<ElementInfo> snap = elementCrawler.crawlCurrentPage();
                int added = mergeElements(snap, scenario.getName(), "", pageState);
                log.info("   Scenario complete: " + added + " new element(s)");
            }
        }

        applyScenarioTags();
        log.info("crawl complete. Total elements: " + masterElements.size());
        return new ArrayList<>(masterElements.values());
    }

    // =========================================================================
    // Step execution -- resolves semantic locators via ElementSearchEngine
    // =========================================================================

    /**
     * Execute a step and return true if the DOM changed as a result.
     * Installs a MutationObserver before executing, then checks after.
     */
    private boolean executeAndDetectChange(CrawlerStep step) {
        if (step.getAction() == CrawlerStep.Action.WAIT) {
            executeStep(step);
            return false; // WAIT never triggers DOM diff on its own
        }
        installMutationObserver();
        executeStep(step);
        return waitForDomStabilityAndReport();
    }

    private void executeStep(CrawlerStep step) {
        try {
            switch (step.getAction()) {

                case SELECT:
                    WebElement selectEl = resolveElement(step);
                    if (selectEl == null) { log.warn("SELECT: element not found -- " + step); break; }
                    performSelect(selectEl, step.getValue());
                    break;

                case TYPE:
                    WebElement typeEl = resolveElement(step);
                    if (typeEl == null) { log.warn("TYPE: element not found -- " + step); break; }
                    typeEl.clear();
                    typeEl.sendKeys(step.getValue());
                    break;

                case CLICK:
                    WebElement clickEl = resolveElement(step);
                    if (clickEl == null) { log.warn("CLICK: element not found -- " + step); break; }
                    clickEl.click();
                    break;

                case CLEAR:
                    WebElement clearEl = resolveElement(step);
                    if (clearEl == null) { log.warn("CLEAR: element not found -- " + step); break; }
                    clearEl.clear();
                    break;

                case WAIT:
                    int ms = Integer.parseInt(step.getValue());
                    try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                    break;

                default:
                    log.warn("Unknown step action: " + step.getAction());
            }
        } catch (Exception e) {
            log.warn("Step execution failed [" + step + "]: " + e.getMessage());
        }
    }

    /**
     * Resolve the target element for a step using either raw XPath or
     * the {@link ElementSearchEngine} semantic resolution chain.
     */
    private WebElement resolveElement(CrawlerStep step) {
        if (!step.isSemantic()) {
            // Raw XPath
            try {
                List<WebElement> els = driver.findElements(By.xpath(step.getLocator()));
                return els.isEmpty() ? null : els.get(0);
            } catch (Exception e) {
                log.warn("resolveElement XPath failed [" + step.getLocator() + "]: " + e.getMessage());
                return null;
            }
        }
        // Semantic resolution
        WebElement el = searchEngine.resolve(step);
        if (el == null) {
            log.warn("resolveElement: semantic resolution failed for " + step);
        }
        return el;
    }

    /**
     * Perform a dropdown selection. Handles:
     *   - Native HTML <select> via Selenium Select
     *   - Angular Material mat-select (click to open, then pick mat-option)
     *   - Any element with role=listbox / combobox
     */
    private void performSelect(WebElement el, String optionValue) {
        String tag = el.getTagName().toLowerCase();
        try {
            if ("select".equals(tag)) {
                new Select(el).selectByVisibleText(optionValue);
                return;
            }
            // Custom / Angular dropdown: click to open panel
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                try { el.click(); return true; }
                catch (Exception ex) { return false; }
            });
            // Look for the option in mat-option, then role=option fallback
            for (String optXpath : new String[]{
                "//mat-option[normalize-space(.)='" + optionValue + "']",
                "//*[@role='option' and normalize-space(.)='" + optionValue + "']",
                "//*[normalize-space(.)='" + optionValue + "' and contains(@class,'option')]"
            }) {
                List<WebElement> options = driver.findElements(By.xpath(optXpath));
                if (!options.isEmpty()) {
                    options.get(0).click();
                    return;
                }
            }
            log.warn("performSelect: option not found: \"" + optionValue + "\"");
        } catch (Exception e) {
            log.warn("performSelect failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // DOM stability detection via MutationObserver
    // =========================================================================

    private void installMutationObserver() {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "window.__domMutationCount = 0;" +
                "window.__domStable = false;" +
                "if (window.__domObserver) { try { window.__domObserver.disconnect(); } catch(e){} }" +
                "window.__domObserver = new MutationObserver(function(m) {" +
                "  window.__domMutationCount += m.length;" +
                "  window.__domStable = false;" +
                "  clearTimeout(window.__domStableTimer);" +
                "  window.__domStableTimer = setTimeout(function(){ window.__domStable=true; }," + STABILITY_QUIET_MS + ");" +
                "});" +
                "window.__domObserver.observe(document.body,{childList:true,subtree:true,attributes:true});" +
                "window.__domStableTimer = setTimeout(function(){ window.__domStable=true; }," + STABILITY_QUIET_MS + ");");
        } catch (Exception e) {
            log.warn("installMutationObserver failed: " + e.getMessage());
        }
    }

    /**
     * Wait for the MutationObserver to report stability.
     * @return true if at least one DOM mutation was observed (page changed)
     */
    private boolean waitForDomStabilityAndReport() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long deadline = System.currentTimeMillis() + (STABILITY_TIMEOUT_S * 1000L);

        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(STABILITY_POLL_MS); } catch (InterruptedException ignored) {}
            try {
                Object stable = js.executeScript("return window.__domStable === true;");
                if (Boolean.TRUE.equals(stable)) {
                    Object count = js.executeScript("return window.__domMutationCount || 0;");
                    long mutations = count instanceof Number ? ((Number) count).longValue() : 0L;
                    log.info("   DOM stable -- " + mutations + " mutation(s)");
                    disconnectObserver(js);
                    return mutations > 0;
                }
            } catch (Exception e) {
                log.warn("DOM stability poll: " + e.getMessage());
                break;
            }
        }
        log.warn("DOM stability timeout after " + STABILITY_TIMEOUT_S + "s");
        disconnectObserver(js);
        return true; // assume changed on timeout
    }

    private void waitForDomStability() {
        installMutationObserver();
        waitForDomStabilityAndReport();
    }

    private void disconnectObserver(JavascriptExecutor js) {
        try {
            js.executeScript(
                "if(window.__domObserver){try{window.__domObserver.disconnect();}catch(e){} window.__domObserver=null;}");
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Element merging and tagging
    // =========================================================================

    /**
     * Merge a snapshot into the master set.
     * New elements are added and tagged with step and page state; existing elements
     * have their scenario and page-state sets updated.
     *
     * @return count of newly added elements
     */
    private int mergeElements(List<ElementInfo> elements, String scenarioName,
                               String stepLabel, String pageState) {
        int added = 0;
        for (ElementInfo el : elements) {
            String key = el.xpath != null && !el.xpath.isEmpty() ? el.xpath : el.primaryXpath;
            if (key == null || key.isEmpty()) continue;

            // Track scenario membership
            Set<String> seenIn = elementScenarios.get(key);
            if (seenIn == null) {
                seenIn = new LinkedHashSet<String>();
                elementScenarios.put(key, seenIn);
            }
            seenIn.add(scenarioName);

            // Track page-state membership
            if (pageState != null && !pageState.isEmpty()) {
                Set<String> pages = elementPageStates.get(key);
                if (pages == null) {
                    pages = new LinkedHashSet<String>();
                    elementPageStates.put(key, pages);
                }
                pages.add(pageState);
            }

            if (!masterElements.containsKey(key)) {
                if (!stepLabel.isEmpty()) {
                    el.stepTag     = stepLabel;
                    el.scenarioTag = scenarioName;
                }
                if (pageState != null && !pageState.isEmpty()) {
                    el.pageState = pageState;
                }
                masterElements.put(key, el);
                added++;
            }
        }
        return added;
    }

    /**
     * After all steps/scenarios ran, finalize scenarioTag and pageState on every element.
     * Elements visible in all scenarios -> "All scenarios"
     * Elements unique to one -> that scenario's name
     * Elements seen on multiple page states -> "Multiple pages: A, B"
     */
    private void applyScenarioTags() {
        Set<String> allNames = new LinkedHashSet<String>();
        allNames.add("Baseline");
        for (CrawlerScenario s : scenarios) allNames.add(s.getName());

        for (Map.Entry<String, ElementInfo> entry : masterElements.entrySet()) {
            String key = entry.getKey();
            ElementInfo el = entry.getValue();

            // Scenario tag
            Set<String> seen = elementScenarios.get(key);
            if (seen == null || seen.isEmpty()) {
                el.scenarioTag = "Baseline";
            } else if (seen.containsAll(allNames)) {
                el.scenarioTag = "All scenarios";
            } else {
                StringBuilder tag = new StringBuilder();
                for (String s : seen) {
                    if (tag.length() > 0) tag.append(", ");
                    tag.append(s);
                }
                el.scenarioTag = tag.toString();
            }

            // Page-state tag -- enrich if element was seen on multiple pages
            Set<String> pages = elementPageStates.get(key);
            if (pages != null && pages.size() > 1) {
                StringBuilder ps = new StringBuilder("Multiple pages: ");
                boolean first = true;
                for (String p : pages) {
                    if (!first) ps.append(", ");
                    ps.append(p);
                    first = false;
                }
                el.pageState = ps.toString();
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildStepLabel(int index, CrawlerStep step) {
        String desc = step.getDescription();
        if (desc.isEmpty()) return "Step " + index + ": " + step.label();
        // Avoid "Step N: Step N: ..." if caller already prefixed the description
        if (desc.matches("(?i)step\\s+\\d+.*")) return desc;
        return "Step " + index + ": " + desc;
    }

    private void resetMaster() {
        masterElements.clear();
        elementScenarios.clear();
        elementPageStates.clear();
        visitedStateFingerprints.clear();
    }

    /**
     * Computes a lightweight structural fingerprint of the current page's interactive
     * elements (tag + id/name/type, in DOM order), for use by {@link #stateDeduplication}.
     * Ignores volatile attributes (text content, values) so that e.g. a re-rendered list
     * with the same shape but different data is still treated as "the same state" -- the
     * goal is to detect true structural repeats (a step that had no effect), not to diff
     * content. Bounded and non-throwing: returns an empty string on any error, which
     * simply disables dedup for that comparison rather than breaking the crawl.
     */
    private String computeStateFingerprint() {
        try {
            Object sig = ((JavascriptExecutor) driver).executeScript(
                "try{" +
                "var els = document.querySelectorAll('input,textarea,select,button,a,[role],mat-select,mat-checkbox');" +
                "var parts = [];" +
                "for (var i=0;i<els.length;i++){" +
                "  var e = els[i];" +
                "  parts.push(e.tagName+'#'+(e.id||'')+'#'+(e.name||'')+'#'+(e.type||''));" +
                "}" +
                "return parts.join('|');" +
                "}catch(e){return '';}");
            return sig != null ? sig.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns true (and records the fingerprint) if this is a new/distinct state, or if
     * dedup is disabled/navigated (dedup only ever applies to non-navigating repeats).
     * Always returns true when {@link #stateDeduplication} is off, preserving prior
     * behavior exactly.
     */
    private boolean isNewStateOrDedupDisabled(boolean navigated) {
        if (!stateDeduplication || navigated) return true;
        String fingerprint = computeStateFingerprint();
        if (fingerprint.isEmpty()) return true; // couldn't compute -- don't block the scan
        boolean isNew = visitedStateFingerprints.add(fingerprint);
        if (!isNew) {
            log.info("   State dedup: identical structural state already recorded, skipping merge");
        }
        return isNew;
    }

    /**
     * Extracts a short human-readable label from a URL for tagging elements by page state.
     * For Angular SPAs with hash routing (#/route): returns the route segment.
     * For regular URLs: returns the last path segment.
     */
    private String pageStateLabel(String url) {
        if (url == null || url.isEmpty()) return "unknown";
        try {
            if (url.contains("#/")) {
                String route = url.substring(url.indexOf("#/") + 2);
                if (route.contains("?")) route = route.substring(0, route.indexOf("?"));
                if (route.contains("/")) {
                    // e.g. "reservation/12345" -> "reservation/12345" (keep full route for clarity)
                    return route.length() > 40 ? route.substring(0, 40) : route;
                }
                return route.isEmpty() ? "home" : route;
            }
            String path = url.replaceAll("\\?.*", "").replaceAll("/$", "");
            int lastSlash = path.lastIndexOf('/');
            String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            return segment.isEmpty() ? "home" : segment;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Safely get current URL without throwing if driver is in a bad state. */
    private String safeGetUrl() {
        try { return driver.getCurrentUrl(); } catch (Exception e) { return ""; }
    }

    private void waitForPageReady() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                try {
                    return "complete".equals(
                        ((JavascriptExecutor) d).executeScript("return document.readyState"));
                } catch (Exception e) { return false; }
            });
        } catch (Exception e) {
            log.warn("waitForPageReady: " + e.getMessage());
        }
    }
}
