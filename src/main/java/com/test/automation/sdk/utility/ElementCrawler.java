package com.test.automation.sdk.utility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * ElementCrawler - intelligent element discovery engine.
 *
 * Scans the current page and resolves the most stable XPath for every
 * interactive element using a comprehensive strategy pipeline:
 *
 * STANDALONE STRATEGIES (in priority order):
 *   1.  @id (non-dynamic)
 *   2.  data-testid / data-cy / data-qa / data-automation / data-id / data-test
 *   3.  @formcontrolname
 *   4.  @name + @type
 *   5.  @name
 *   6.  @aria-label (exact / contains)
 *   7.  @placeholder (exact / contains)
 *   8.  @title
 *   9.  @autocomplete (semantic values only)
 *   10. @value (for select options and pre-filled inputs)
 *   11. @src (for image buttons / input[type=image])
 *   12. @alt (for image elements used as buttons)
 *   13. @data-value (Angular chips/autocomplete)
 *   14. visible text (normalize-space / contains)
 *   15. @routerlink
 *   16. @href (last segment)
 *   17. @class (fragile, last resort before structural)
 *
 * CONTEXTUAL STRATEGIES (when standalone fails):
 *   18. Ancestor scope  -- //*[@id='ancestor']//element[@attr='val']
 *   19. Sibling group   -- List<WebElement> + dynamic item method
 *   20. Form-row label  -- //div[label[.='Email']]/input
 *   21. Table column    -- //th[.='Name']/following-sibling locator
 *
 * COLLECTION STRATEGIES (separate passes):
 *   22. Role groups     -- listbox/menu/tablist -> option/menuitem/tab
 *   23. Label for/aria-labelledby/mat-label resolution
 *   24. iframe/frame    -- switches into each same-origin frame
 *   25. contenteditable -- rich text editors
 *   26. select options  -- option children with scoped XPaths
 *   27. file inputs     -- flagged with isFileInput=true
 *   28. hidden inputs   -- flagged with isHidden=true
 *
 * Used by PageObjectGenerator to bootstrap new Page Object classes.
 *
 * @author vkruglyak
 */
public class ElementCrawler {

    private static final Logger log = LogManager.getLogger(ElementCrawler.class.getName());

    // -- Dynamic ID patterns - always rejected ---------------------------------
    // Any ID matching these is auto-generated and must never be used as a locator
    private static final Pattern DYNAMIC_ID_PATTERN = Pattern.compile(
        "mat-input-\\d+"          + "|" +
        "mat-select-\\d+"         + "|" +
        "mat-option-\\d+"         + "|" +
        "mat-tab-\\d+"            + "|" +
        "mat-checkbox-\\d+"       + "|" +
        "mat-radio-\\d+"          + "|" +
        "cdk-[a-z]+-\\d+"         + "|" +
        "cdk-[a-z]+-[a-z]+-\\d+" + "|" +
        "ngb-[a-z]+-\\d+"         + "|" +
        "ng-[a-z]+-\\d+"          + "|" +
        "\\d+"                    + "|" +   // purely numeric
        "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}" // UUID
    );

    // Marker labels used in the strategy map
    public static final String LABEL_UNIQUE     = "UNIQUE [x]";
    public static final String LABEL_NOT_UNIQUE = "NOT UNIQUE";
    public static final String LABEL_STALE      = "STALE";
    public static final String LABEL_DYNAMIC    = "DYNAMIC";
    public static final String LABEL_STRUCTURAL = "STRUCTURAL";

    private static final String[] INTERACTIVE_TAGS = {
        "input", "textarea", "select", "button", "a",
        // Angular Material components
        "mat-select", "mat-checkbox", "mat-radio-button", "mat-slide-toggle",
        "mat-datepicker", "mat-autocomplete", "mat-chip-list", "mat-chip-input",
        "mat-expansion-panel", "mat-slider", "mat-button-toggle"
    };
    private static final String[] HEADING_TAGS = { "h1", "h2", "h3" };
    private static final String[] ALERT_XPATH_PARTS = {
        "@role='alert'",
        "contains(@class,'alert')",
        "contains(@class,'error')",
        "contains(@class,'success')",
        "contains(@class,'message')",
        "contains(@class,'snack')",
        "contains(@class,'toast')"
    };

    /** data-* attribute names to probe (in priority order after data-testid) */
    private static final String[] DATA_ATTRS = {
        "data-testid", "data-cy", "data-qa", "data-automation", "data-id", "data-test"
    };

    /** Maximum ancestor walk depth -- configurable */
    private static final int ANCESTOR_WALK_DEPTH = 12;

    private final WebDriver driver;

    public ElementCrawler(WebDriver driver) {
        this.driver = driver;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Navigates to {@code url}, waits for the page to be ready, and returns
     * all discovered elements.
     */
    public List<ElementInfo> crawlUrl(String url) {
        log.info("Navigating to: " + url);
        driver.get(url);
        waitForPageReady();
        return crawlCurrentPage();
    }

    /**
     * Crawls whatever page the driver is currently on.
     * Includes the top-level document AND any accessible same-origin iframes.
     */
    public List<ElementInfo> crawlCurrentPage() {
        log.info("Crawling: " + driver.getCurrentUrl());
        waitForPageReady();

        List<ElementInfo> all = new ArrayList<>();
        all.addAll(collectTopLevelElements());
        all.addAll(collectFromFrames());

        log.info("Discovered " + all.size() + " elements (including frames).");
        return all;
    }

    /**
     * Crawls only the subtree rooted at the given element (e.g. a modal dialog).
     * Finds all interactive descendants matching INTERACTIVE_TAGS within that root.
     * Use this to generate a modal-specific page object after detecting a dialog overlay.
     *
     * @param root the modal root element (e.g. mat-dialog-container)
     * @return list of ElementInfo for interactive elements inside the modal
     */
    public List<ElementInfo> crawlSubtree(WebElement root) {
        log.info("crawlSubtree: crawling modal/overlay subtree");
        List<ElementInfo> result = new ArrayList<>();
        for (String tag : INTERACTIVE_TAGS) {
            result.addAll(collectTagWithStaleRetry(root, tag, STALE_RETRY_ATTEMPTS, STALE_RETRY_BACKOFF_MS));
        }
        log.info("crawlSubtree: found " + result.size() + " elements in modal");
        return result;
    }

    /** Bounded retries for a single stale-element hiccup before giving up on this tag. */
    private static final int STALE_RETRY_ATTEMPTS   = 3;
    /** Base backoff between retries in ms -- multiplied by attempt number (simple linear back-off). */
    private static final long STALE_RETRY_BACKOFF_MS = 150L;

    /**
     * Finds and builds {@link ElementInfo} for every {@code tag} element under {@code root},
     * retrying the whole tag scan (bounded, with linear back-off) if a
     * {@link StaleElementReferenceException} is hit mid-scan -- e.g. a live modal/overlay
     * re-rendering its DOM while being crawled. This mirrors the retry-with-backoff pattern
     * used by production-grade crawlers (Scrapy/Crawlee) for transient failures: only the
     * specific transient exception triggers a retry, and retries are bounded so a genuinely
     * broken page can never stall the crawl indefinitely. Any other exception is logged and
     * treated as before (skip this tag, keep whatever was already collected).
     */
    private List<ElementInfo> collectTagWithStaleRetry(WebElement root, String tag,
                                                        int maxAttempts, long backoffMillis) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ElementInfo> tagResult = new ArrayList<>();
            try {
                List<WebElement> found = root.findElements(By.tagName(tag));
                for (WebElement el : found) {
                    try {
                        ElementInfo info = buildInfo(el);
                        if (info != null) tagResult.add(info);
                    } catch (StaleElementReferenceException stale) {
                        throw stale; // escalate -- retry the whole tag scan, not just this element
                    } catch (Exception e) {
                        log.warn("crawlSubtree: skipping element in modal: " + e.getMessage());
                    }
                }
                return tagResult;
            } catch (StaleElementReferenceException stale) {
                if (attempt == maxAttempts) {
                    log.warn("crawlSubtree: tag [" + tag + "] still stale after "
                        + maxAttempts + " attempt(s), giving up on this tag: " + stale.getMessage());
                    return new ArrayList<>();
                }
                log.warn("crawlSubtree: stale element for tag [" + tag + "], retrying ("
                    + attempt + "/" + maxAttempts + ")");
                try {
                    Thread.sleep(backoffMillis * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                log.warn("crawlSubtree: tag [" + tag + "] error: " + e.getMessage());
                return tagResult;
            }
        }
        return new ArrayList<>();
    }

    /** Collects all elements from the top-level document. */
    private List<ElementInfo> collectTopLevelElements() {
        List<ElementInfo> all = new ArrayList<>();
        all.addAll(collectByTags(HEADING_TAGS));
        all.addAll(collectByTags(INTERACTIVE_TAGS));
        all.addAll(collectImageButtons());
        all.addAll(collectContentEditable());
        all.addAll(collectSelectOptions());
        all.addAll(collectTableColumns());
        all.addAll(collectAlerts());
        all.addAll(collectRoleGroups());
        all.addAll(collectLabelAssociations());
        all.addAll(collectMapWidgets());
        all.addAll(collectFromShadowRoots());
        return all;
    }

    /**
     * Detects known map-widget containers (Google Maps, Leaflet, Mapbox GL JS,
     * MapLibre GL JS, OpenLayers, Bing Maps -- see
     * {@link MapWidgetHelper#PROVIDER_CSS_SIGNATURES}) plus any unmatched
     * &lt;canvas&gt; element that might be a map/chart widget rendered by an
     * unrecognized library.
     * <p>
     * Only the CONTAINER is reported here, tagged with
     * {@link ElementInfo#isMapWidget} = true and {@link ElementInfo#mapProvider}.
     * This lets PageObjectGenerator emit a container field wired to
     * {@link MapWidgetHelper} instead of trying to generate brittle per-tile /
     * per-pixel locators for content that is frequently canvas/WebGL-rendered
     * with no stable DOM at all.
     * <p>
     * Real DOM elements inside the container (search inputs, accessible
     * markers with aria-label/alt/title, zoom controls) are still discovered
     * normally by {@link #collectByTags(String[])} since it scans the whole
     * document by tag name.
     */
    private List<ElementInfo> collectMapWidgets() {
        List<ElementInfo> result = new ArrayList<>();
        List<WebElement> reportedContainers = new ArrayList<>();

        for (Map.Entry<String, String> sig : MapWidgetHelper.PROVIDER_CSS_SIGNATURES.entrySet()) {
            String provider = sig.getKey();
            String cssClass = sig.getValue();
            try {
                for (WebElement el : driver.findElements(By.cssSelector("." + cssClass))) {
                    ElementInfo info = buildInfo(el);
                    if (info == null) continue;
                    info.isMapWidget = true;
                    info.mapProvider = provider;
                    result.add(info);
                    reportedContainers.add(el);
                }
            } catch (Exception e) {
                log.warn("collectMapWidgets: signature [" + cssClass + "] error: " + e.getMessage());
            }
        }

        // Generic fallback -- any <canvas> not already inside a reported container
        // might be an unrecognized map (or chart) library.
        try {
            for (WebElement canvas : driver.findElements(By.tagName("canvas"))) {
                boolean insideKnown = false;
                for (WebElement known : reportedContainers) {
                    if (MapWidgetHelper.isAncestor(driver, known, canvas)) {
                        insideKnown = true;
                        break;
                    }
                }
                if (insideKnown) continue;

                ElementInfo info = buildInfo(canvas);
                if (info == null) continue;
                info.isMapWidget = true;
                info.mapProvider = MapWidgetHelper.UNKNOWN_PROVIDER_LABEL;
                result.add(info);
            }
        } catch (Exception e) {
            log.warn("collectMapWidgets: canvas fallback error: " + e.getMessage());
        }

        if (!result.isEmpty()) log.info("collectMapWidgets: found " + result.size() + " map/canvas widget(s)");
        return result;
    }

    // JS: finds every open shadow-root HOST element in the document, recursing into
    // nested shadow roots (shadow-in-shadow), in document order. Closed shadow roots
    // are invisible to any script (including this one) and cannot be discovered --
    // this is a hard platform limitation, not a gap in the crawler.
    private static final String SHADOW_HOST_FINDER_JS =
        "return (function(){" +
        "  var hosts=[];" +
        "  function walk(root){" +
        "    var all=root.querySelectorAll('*');" +
        "    for(var i=0;i<all.length;i++){" +
        "      if(all[i].shadowRoot){ hosts.push(all[i]); walk(all[i].shadowRoot); }" +
        "    }" +
        "  }" +
        "  walk(document);" +
        "  return hosts;" +
        "})();";

    /**
     * Discovers interactive elements rendered inside <b>open</b> shadow roots --
     * common in Web Components, Lit/Stencil-based design systems, Salesforce
     * Lightning/LWC, and any modern component framework that uses real DOM
     * encapsulation (not Angular's emulated view encapsulation, which does not
     * use actual shadow roots and needs no special handling).
     * <p>
     * XPath cannot cross a shadow boundary -- this is a W3C spec limitation, not
     * a crawler gap -- so elements found here are tagged
     * {@link ElementInfo#inShadowDom} = true with a two-part locator instead of a
     * single XPath: {@link ElementInfo#shadowHostXpath} (light-DOM xpath to the
     * host element) plus {@link ElementInfo#shadowRelativeCss} (CSS selector
     * resolved via {@code host.getShadowRoot()}, since shadow roots only support
     * CSS selectors in Selenium/W3C, never XPath).
     * <p>
     * Closed shadow roots (host.shadowRoot === null from outside) cannot be
     * discovered or traversed by any script or WebDriver command -- this is an
     * intentional browser security boundary, not a bug.
     */
    private List<ElementInfo> collectFromShadowRoots() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(SHADOW_HOST_FINDER_JS);
            if (!(raw instanceof List)) return result;
            @SuppressWarnings("unchecked")
            List<WebElement> hosts = (List<WebElement>) raw;
            log.info("collectFromShadowRoots: found " + hosts.size() + " open shadow-root host(s)");

            for (WebElement host : hosts) {
                try {
                    org.openqa.selenium.SearchContext shadowRoot;
                    try {
                        shadowRoot = host.getShadowRoot();
                    } catch (Exception e) {
                        continue; // closed root, or getShadowRoot() unsupported for this node
                    }
                    String hostXpath = getJsXPath(host);

                    for (String tag : INTERACTIVE_TAGS) {
                        List<WebElement> found;
                        try {
                            found = shadowRoot.findElements(By.cssSelector(tag));
                        } catch (Exception e) {
                            continue;
                        }
                        for (WebElement el : found) {
                            ElementInfo info = buildInfo(el);
                            if (info == null) continue;
                            info.inShadowDom       = true;
                            info.shadowHostXpath   = hostXpath;
                            info.shadowRelativeCss = bestShadowCssSelector(el, tag);
                            // Standalone XPath strategies computed by buildInfo() are always
                            // STALE for shadow-DOM elements (XPath cannot cross a shadow
                            // boundary) -- replace with a single explanatory entry so the
                            // report doesn't show a wall of misleading STALE rows.
                            info.allXpaths = new LinkedHashMap<>();
                            info.allXpaths.put("SHADOW DOM (host + relative CSS)",
                                "host: " + hostXpath + "  ->  shadowRoot.findElement(By.cssSelector(\""
                                    + info.shadowRelativeCss + "\"))");
                            info.xpath = "";
                            result.add(info);
                        }
                    }
                } catch (Exception e) {
                    log.warn("collectFromShadowRoots: host skipped - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("collectFromShadowRoots: " + e.getMessage());
        }
        return result;
    }

    /**
     * Best-effort CSS selector for an element relative to its enclosing shadow
     * root. Shadow roots only support CSS lookups (no XPath), so this mirrors the
     * XPath priority ladder using CSS-compatible attribute selectors.
     */
    private String bestShadowCssSelector(WebElement el, String tag) {
        String id = attr(el, "id");
        if (!id.isEmpty() && !isDynamicId(id)) return tag + "#" + id;

        String dataTestId = attr(el, "data-testid");
        if (!dataTestId.isEmpty()) return tag + "[data-testid='" + dataTestId + "']";

        String name = attr(el, "name");
        if (!name.isEmpty()) return tag + "[name='" + name + "']";

        String ariaLabel = attr(el, "aria-label");
        if (!ariaLabel.isEmpty()) return tag + "[aria-label='" + ariaLabel + "']";

        // No stable attribute found -- tag-only selector, flagged NOT UNIQUE if it
        // matches more than one element inside this shadow root.
        return tag;
    }

    /**
     * Discovers all iframes on the current page and crawls each one that is
     * same-origin (cross-origin frames will raise a SecurityError which is caught).
     * After crawling each frame, switches back to the default content.
     */
    private List<ElementInfo> collectFromFrames() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            List<WebElement> frames = driver.findElements(By.tagName("iframe"));
            frames.addAll(driver.findElements(By.tagName("frame")));
            log.info("Found " + frames.size() + " frame(s) on page");

            for (int i = 0; i < frames.size(); i++) {
                try {
                    driver.switchTo().frame(i);
                    log.info("Crawling frame[" + i + "]");
                    waitForPageReady();

                    List<ElementInfo> frameElements = new ArrayList<>();
                    frameElements.addAll(collectByTags(INTERACTIVE_TAGS));
                    frameElements.addAll(collectContentEditable());
                    frameElements.addAll(collectSelectOptions());
                    frameElements.addAll(collectAlerts());
                    frameElements.addAll(collectLabelAssociations());

                    // Tag each element with its frame index so PageObjectGenerator
                    // can emit driver.switchTo().frame(N) in action methods
                    for (ElementInfo el : frameElements) {
                        el.frameIndex  = i;
                        el.inFrame     = true;
                    }
                    result.addAll(frameElements);
                    log.info("Frame[" + i + "]: " + frameElements.size() + " elements found");
                } catch (Exception e) {
                    log.warn("Frame[" + i + "] skipped (cross-origin or stale): " + e.getMessage());
                } finally {
                    driver.switchTo().defaultContent();
                }
            }
        } catch (Exception e) {
            log.warn("collectFromFrames: " + e.getMessage());
        }
        return result;
    }

    public WebDriver getDriver() { return driver; }

    // -------------------------------------------------------------------------
    // Collection
    // -------------------------------------------------------------------------

    private List<ElementInfo> collectByTags(String[] tags) {
        List<ElementInfo> result = new ArrayList<>();
        for (String tag : tags) {
            try {
                for (WebElement el : driver.findElements(By.tagName(tag))) {
                    // Skip hidden inputs -- they need locators but flag them clearly
                    String type = attr(el, "type");
                    if ("hidden".equalsIgnoreCase(type)) {
                        ElementInfo info = buildInfo(el);
                        if (info != null) {
                            info.isHidden = true;
                            result.add(info);
                        }
                        continue;
                    }
                    // Skip invisible elements (display:none, off-screen, zero-size)
                    // to reduce noise -- but only for non-interactive tags
                    ElementInfo info = buildInfo(el);
                    if (info != null) result.add(info);
                }
            } catch (Exception e) {
                log.warn("Error collecting tag [" + tag + "]: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Collects contenteditable divs/sections (rich text editors).
     * Emits ElementInfo with isContentEditable=true so PageObjectGenerator
     * can produce clear()/sendKeys() instead of clearAndType().
     */
    private List<ElementInfo> collectContentEditable() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            List<WebElement> editors = driver.findElements(
                By.xpath("//*[@contenteditable='true']"));
            for (WebElement el : editors) {
                ElementInfo info = buildInfo(el);
                if (info != null) {
                    info.isContentEditable = true;
                    result.add(info);
                }
            }
            log.info("contenteditable elements found: " + result.size());
        } catch (Exception e) {
            log.warn("collectContentEditable: " + e.getMessage());
        }
        return result;
    }

    /**
     * Collects all {@code <option>} children of every {@code <select>} on the page.
     * Emits one ElementInfo per option with a stable scoped XPath:
     *   //select[@id='mySelect']/option[normalize-space(.)='Value']
     * File inputs are flagged with isFileInput=true for special handling.
     */
    private List<ElementInfo> collectSelectOptions() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            List<WebElement> selects = driver.findElements(By.tagName("select"));
            for (WebElement select : selects) {
                // Flag file inputs from <input type="file"> -- separate check
                List<WebElement> options = select.findElements(By.tagName("option"));
                String selectId = attr(select, "id");
                for (WebElement option : options) {
                    ElementInfo info = buildInfo(option);
                    if (info == null) continue;
                    info.isSelectOption = true;
                    // Build scoped option XPath
                    if (!selectId.isEmpty() && !isDynamicId(selectId) && !info.text.isEmpty()) {
                        String scopedXpath = "//select[@id='" + selectId
                            + "']/option[normalize-space(.)='" + info.text + "']";
                        int count = countElements(scopedXpath);
                        if (count == 1) {
                            info.xpath = scopedXpath;
                            info.primaryXpath = scopedXpath;
                        }
                    }
                    result.add(info);
                }
            }

            // Flag file inputs
            List<WebElement> fileInputs = driver.findElements(
                By.xpath("//input[@type='file']"));
            for (WebElement fi : fileInputs) {
                ElementInfo info = buildInfo(fi);
                if (info != null) {
                    info.isFileInput = true;
                    result.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("collectSelectOptions: " + e.getMessage());
        }
        return result;
    }

    /**
     * Collects image buttons: {@code <img>} elements inside {@code <button>},
     * {@code <input type="image">}, and standalone {@code <img>} with click handlers.
     * Builds locators using {@code @alt}, {@code @src} last-segment, and {@code @title}.
     */
    private List<ElementInfo> collectImageButtons() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            // input[type=image]
            for (WebElement el : driver.findElements(By.xpath("//input[@type='image']"))) {
                ElementInfo info = buildInfo(el);
                if (info != null) {
                    info.isImageButton = true;
                    result.add(info);
                }
            }
            // button > img  or  a > img
            for (WebElement el : driver.findElements(By.xpath("//button//img | //a//img"))) {
                ElementInfo info = buildInfo(el);
                if (info != null) {
                    info.isImageButton = true;
                    result.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("collectImageButtons: " + e.getMessage());
        }
        return result;
    }

    /**
     * Table column strategy -- discovers table headers and maps them to column locators.
     *
     * For each {@code <th>} with visible text, emits:
     *   - A header locator: {@code //th[normalize-space(.)='Name']}
     *   - A column cells locator: {@code //td[count(//th[normalize-space(.)='Name']/preceding-sibling::th)+1]}
     *   - A row-cell locator template: for finding a cell in a specific row
     *
     * Example:
     *   //table//th[normalize-space(.)='Status']
     *   //table//tr/td[count(//th[normalize-space(.)='Status']/preceding-sibling::th)+1]
     *
     * This matches how manual testers describe grid assertions:
     *   "verify the Status column of the row with Pole ID 'ABC123' shows 'Active'"
     */
    private List<ElementInfo> collectTableColumns() {
        List<ElementInfo> result = new ArrayList<>();
        try {
            List<WebElement> headers = driver.findElements(By.xpath("//th"));
            for (WebElement th : headers) {
                String headerText = safeText(th);
                if (headerText.isEmpty()) continue;

                ElementInfo headerInfo = buildInfo(th);
                if (headerInfo == null) continue;
                headerInfo.isTableHeader = true;
                headerInfo.tableColumnText = headerText;

                // Column index locator -- targets all cells in this column
                String colXpath = "//table//tr/td[count(//th[normalize-space(.)='"
                    + headerText + "']/preceding-sibling::th)+1]";
                int colCount = countElements(colXpath);

                headerInfo.tableColumnCellsXpath = colXpath;
                // Dynamic row+column locator template:
                // //table//tr[td[normalize-space(.)='{ROW_KEY}']]/td[N]
                String dynamicCellTemplate = "//table//tr[.//td[normalize-space(.)='{ROW_KEY}']]"
                    + "/td[count(//th[normalize-space(.)='" + headerText
                    + "']/preceding-sibling::th)+1]";
                headerInfo.tableColumnDynamicTemplate = dynamicCellTemplate;

                log.info("TABLE COLUMN '" + headerText + "' -> " + colCount + " cells  " + colXpath);
                result.add(headerInfo);
            }
        } catch (Exception e) {
            log.warn("collectTableColumns: " + e.getMessage());
        }
        return result;
    }

    private List<ElementInfo> collectAlerts() {
        List<ElementInfo> result = new ArrayList<>();
        String xp = "//*[" + String.join(" or ", ALERT_XPATH_PARTS) + "]";
        try {
            for (WebElement el : driver.findElements(By.xpath(xp))) {
                ElementInfo info = buildInfo(el);
                if (info != null) result.add(info);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Strategy 5 -- Role-based group collection.
     *
     * Finds every container with role="listbox", "menu", or "tablist" and
     * collects its role="option"/"menuitem"/"tab" children as a group.
     *
     * For each container, emits:
     *   - One ElementInfo for the container itself (list locator)
     *   - ElementInfos for each child item with dynamic item locator
     *
     * Example output for a mat-select:
     *   CONTAINER: //*[@id='borough-select'][@role='listbox']
     *   ITEM:      //*[@id='borough-select']//*[@role='option' and normalize-space(.)='Manhattan']
     */
    private List<ElementInfo> collectRoleGroups() {
        List<ElementInfo> result = new ArrayList<>();
        // container role -> child item role
        String[][] rolePairs = {
            { "listbox",  "option"   },
            { "menu",     "menuitem" },
            { "tablist",  "tab"      },
            { "combobox", "option"   },
            { "grid",     "row"      }
        };
        for (String[] pair : rolePairs) {
            String containerRole = pair[0];
            String itemRole      = pair[1];
            try {
                List<WebElement> containers = driver.findElements(
                    By.xpath("//*[@role='" + containerRole + "']"));
                for (WebElement container : containers) {
                    ElementInfo containerInfo = buildInfo(container);
                    if (containerInfo == null) continue;
                    containerInfo.roleGroup      = containerRole;
                    containerInfo.roleGroupChild = itemRole;

                    // Build scoped item locator using container id or ancestor
                    String containerId = containerInfo.id.isEmpty()
                        ? null : containerInfo.id;
                    String itemsXpath = containerId != null
                        ? "//*[@id='" + containerId + "']//*[@role='" + itemRole + "']"
                        : "//*[@role='" + containerRole + "']//*[@role='" + itemRole + "']";

                    List<WebElement> items = container.findElements(
                        By.xpath(".//*[@role='" + itemRole + "']"));
                    log.info("ROLE GROUP [" + containerRole + "] id='"
                        + containerId + "' -- " + items.size() + " [" + itemRole + "] items");

                    for (WebElement item : items) {
                        ElementInfo itemInfo = buildInfo(item);
                        if (itemInfo == null) continue;
                        String itemText = itemInfo.text;
                        if (!itemText.isEmpty() && containerId != null) {
                            // Dynamic item locator: scoped by container id + role + text
                            String dynamicXpath = "//*[@id='" + containerId + "']"
                                + "//*[@role='" + itemRole + "'"
                                + " and normalize-space(.)='" + itemText + "']";
                            int count = countElements(dynamicXpath);
                            itemInfo.xpath        = dynamicXpath;
                            itemInfo.primaryXpath = dynamicXpath;
                            itemInfo.roleGroup    = itemRole;
                            itemInfo.ancestorId   = containerId;
                            log.info("  ROLE ITEM [" + (count == 1 ? "UNIQUE [x]" : "NOT UNIQUE")
                                + "] " + dynamicXpath);
                        }
                        result.add(itemInfo);
                    }
                    result.add(containerInfo);
                }
            } catch (Exception e) {
                log.warn("collectRoleGroups [" + containerRole + "]: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Strategy 6 -- Full label resolution engine.
     *
     * Manual testers describe elements by their visible label text ("enter value in
     * Borough field", "click Email"). This strategy builds a complete label->input
     * mapping so generated page object field names and report entries reflect the
     * human-readable label -- not internal IDs.
     *
     * Three resolution mechanisms:
     *
     * A) {@code <label for="X">} -- explicit HTML association.
     *    Finds the input by id and sets its labelText to the label's visible text.
     *    XPath: //label[normalize-space(.)='Email'] following-sibling or
     *           //input[@id='email'] -- whichever is UNIQUE [x]
     *
     * B) {@code aria-labelledby="Y"} -- element points to another element's id as label.
     *    Resolves the label text from //*[@id='Y'] and sets it on the input.
     *    XPath: //*[@aria-labelledby='Y'] (already in ARIA-LABEL chain via text)
     *
     * C) Angular Material proximity -- {@code <mat-label>} sibling inside same
     *    {@code <mat-form-field>} container. No for/id needed; resolved by DOM
     *    traversal: find mat-form-field, get mat-label text, get child input/mat-select.
     *
     * Result: every ElementInfo produced by collectByTags() can have its
     * {@code labelText} populated so PageObjectGenerator names the field after
     * what manual testers call it (e.g. "emailInput", "boroughSelect").
     *
     * Also emits standalone label ElementInfos with linkedInputXpath set.
     */
    private List<ElementInfo> collectLabelAssociations() {
        List<ElementInfo> result = new ArrayList<>();

        // A) <label for="X"> explicit association
        try {
            List<WebElement> labels = driver.findElements(By.tagName("label"));
            for (WebElement label : labels) {
                String forAttr  = attr(label, "for");
                String labelTxt = safeText(label);
                if (labelTxt.isEmpty()) continue;

                ElementInfo info = buildInfo(label);
                if (info == null) continue;
                info.isLabel = true;

                if (!forAttr.isEmpty() && !isDynamicId(forAttr)) {
                    info.linkedForAttr = forAttr;
                    // Resolve target input XPath -- try all form element types
                    String[] inputTags = {"input", "textarea", "select", "mat-select"};
                    for (String inputTag : inputTags) {
                        String xpath = "//" + inputTag + "[@id='" + forAttr + "']";
                        if (countElements(xpath) == 1) {
                            info.linkedInputXpath = xpath;
                            break;
                        }
                    }
                    // Also build label-text-based locator for the input
                    // (useful when id is dynamic but label text is stable)
                    String labelBasedXpath = "//input[@id=following-sibling::label[normalize-space(.)='"
                        + labelTxt + "']/@for]";
                    log.info("LABEL[for] '" + labelTxt + "' -> id='" + forAttr
                        + "' input: " + info.linkedInputXpath);
                } else {
                    // Label without 'for' -- try proximity (wrapping parent contains input)
                    String proximityXpath = "//label[normalize-space(.)='" + labelTxt
                        + "']/following-sibling::input[1]";
                    if (countElements(proximityXpath) == 1) {
                        info.linkedInputXpath = proximityXpath;
                        log.info("LABEL[proximity] '" + labelTxt + "' -> " + proximityXpath);
                    }
                }
                result.add(info);
            }
        } catch (Exception e) {
            log.warn("collectLabelAssociations [label for]: " + e.getMessage());
        }

        // B) aria-labelledby -- element references another element's id as its label
        try {
            List<WebElement> labelledBy = driver.findElements(
                By.xpath("//*[@aria-labelledby]"));
            for (WebElement el : labelledBy) {
                String labelledById = attr(el, "aria-labelledby");
                if (labelledById.isEmpty()) continue;
                // Resolve the referenced element's text
                String labelText = "";
                try {
                    List<WebElement> labelEls = driver.findElements(
                        By.xpath("//*[@id='" + labelledById + "']"));
                    if (!labelEls.isEmpty()) labelText = safeText(labelEls.get(0));
                } catch (Exception ignored) {}

                if (!labelText.isEmpty()) {
                    ElementInfo info = buildInfo(el);
                    if (info != null) {
                        info.labelText = labelText;
                        info.isLabelledBy = true;
                        log.info("ARIA-LABELLEDBY '" + labelText + "' -> " + info.tag
                            + " id='" + info.id + "'");
                        result.add(info);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("collectLabelAssociations [aria-labelledby]: " + e.getMessage());
        }

        // C) Angular Material <mat-form-field> proximity -- <mat-label> sibling
        try {
            List<WebElement> formFields = driver.findElements(
                By.tagName("mat-form-field"));
            for (WebElement field : formFields) {
                // Get the mat-label text inside this form field
                List<WebElement> matLabels = field.findElements(By.tagName("mat-label"));
                if (matLabels.isEmpty()) continue;
                String labelText = safeText(matLabels.get(0));
                if (labelText.isEmpty()) continue;

                // Find the input or mat-select inside this form field
                String[] controlTags = {"input", "mat-select", "textarea"};
                for (String ctag : controlTags) {
                    List<WebElement> controls = field.findElements(By.tagName(ctag));
                    if (controls.isEmpty()) continue;
                    WebElement control = controls.get(0);
                    ElementInfo info = buildInfo(control);
                    if (info == null) continue;
                    info.labelText      = labelText;
                    info.isMatLabelled  = true;
                    // Build a stable label-following XPath:
                    // //mat-form-field[.//mat-label[normalize-space(.)='Borough']]//mat-select
                    String labelXpath = "//mat-form-field[.//mat-label[normalize-space(.)='"
                        + labelText + "']]//" + ctag;
                    int count = countElements(labelXpath);
                    if (count == 1) {
                        info.labelFollowingXpath = labelXpath;
                        log.info("MAT-LABEL '" + labelText + "' -> " + ctag
                            + "  UNIQUE [x]  " + labelXpath);
                    } else {
                        log.info("MAT-LABEL '" + labelText + "' -> " + ctag
                            + "  NOT UNIQUE (" + count + ")  " + labelXpath);
                    }
                    result.add(info);
                    break; // one control per form field
                }
            }
        } catch (Exception e) {
            log.warn("collectLabelAssociations [mat-form-field]: " + e.getMessage());
        }

        log.info("collectLabelAssociations: " + result.size() + " label-linked elements");
        return result;
    }

    // -------------------------------------------------------------------------
    // Build ElementInfo
    // -------------------------------------------------------------------------

    ElementInfo buildInfo(WebElement el) {
        try {
            ElementInfo info = new ElementInfo();
            info.tag             = el.getTagName();
            info.id              = attr(el, "id");
            info.name            = attr(el, "name");
            info.type            = attr(el, "type");
            info.placeholder     = attr(el, "placeholder");
            info.formControlName = attr(el, "formcontrolname");
            info.ariaLabel       = attr(el, "aria-label");
            info.href            = attr(el, "href");
            info.routerLink      = attr(el, "routerlink");
            info.cssClass        = attr(el, "class");
            info.dataTestId      = attr(el, "data-testid");
            info.title           = attr(el, "title");
            info.autocomplete    = attr(el, "autocomplete");
            info.alt             = attr(el, "alt");
            info.src             = attr(el, "src");
            info.dataValue       = attr(el, "data-value");
            info.text            = safeText(el);
            info.visible         = isVisible(el);
            info.isFileInput     = "file".equalsIgnoreCase(info.type);
            info.isImageButton   = "image".equalsIgnoreCase(info.type);
            info.allXpaths       = buildAllXPaths(el, info); // sets info.primaryXpath
            info.xpath           = info.primaryXpath;
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // XPath Builder - generates ALL valid strategies for an element
    // Returns LinkedHashMap<strategyLabel, xpath> ordered best -> fallback
    // -------------------------------------------------------------------------

    private LinkedHashMap<String, String> buildAllXPaths(WebElement el, ElementInfo i) {
        // Step 1 - collect all candidate strategies
        LinkedHashMap<String, String> candidates = new LinkedHashMap<>();
        String t = i.tag;

        if (!i.id.isEmpty())
            candidates.put("BY ID",
                "//" + t + "[@id='" + i.id + "']");

        // All data-* attribute variants (data-testid, data-cy, data-qa, data-automation, etc.)
        for (String dataAttr : DATA_ATTRS) {
            String val = attr(el, dataAttr);
            if (!val.isEmpty()) {
                candidates.put("BY " + dataAttr.toUpperCase(),
                    "//" + t + "[@" + dataAttr + "='" + val + "']");
            }
        }

        if (!i.formControlName.isEmpty())
            candidates.put("BY FORM-CONTROL-NAME",
                "//" + t + "[@formcontrolname='" + i.formControlName + "']");

        if (!i.name.isEmpty() && !i.type.isEmpty())
            candidates.put("BY NAME + TYPE",
                "//" + t + "[@name='" + i.name + "' and @type='" + i.type + "']");

        if (!i.name.isEmpty())
            candidates.put("BY NAME",
                "//" + t + "[@name='" + i.name + "']");

        if (!i.ariaLabel.isEmpty())
            candidates.put("BY ARIA-LABEL (exact)",
                "//" + t + "[@aria-label='" + i.ariaLabel + "']");

        if (!i.ariaLabel.isEmpty())
            candidates.put("BY ARIA-LABEL (contains)",
                "//" + t + "[contains(@aria-label,'" + i.ariaLabel + "')]");

        if (!i.placeholder.isEmpty())
            candidates.put("BY PLACEHOLDER (exact)",
                "//" + t + "[@placeholder='" + i.placeholder + "']");

        if (!i.placeholder.isEmpty())
            candidates.put("BY PLACEHOLDER (contains)",
                "//" + t + "[contains(@placeholder,'" + i.placeholder + "')]");

        // title attribute -- useful for icon buttons and legacy inputs
        String titleAttr = attr(el, "title");
        if (!titleAttr.isEmpty())
            candidates.put("BY TITLE",
                "//" + t + "[@title='" + titleAttr + "']");

        // autocomplete -- stable on known-semantic form fields
        String autocomplete = attr(el, "autocomplete");
        if (!autocomplete.isEmpty() && !autocomplete.equals("off") && !autocomplete.equals("on"))
            candidates.put("BY AUTOCOMPLETE",
                "//" + t + "[@autocomplete='" + autocomplete + "']");

        // @value -- for select options, pre-filled inputs, AND icon-only buttons
        // (e.g. Angular stage-action buttons: <button value="IN_CONSTRUCTION">)
        // Priority: above text-based strategies so icon-only buttons are found first.
        String valueAttr = attr(el, "value");
        if (!valueAttr.isEmpty()
                && !"password".equalsIgnoreCase(i.type)
                && !"hidden".equalsIgnoreCase(i.type)
                && valueAttr.length() <= 60)
            candidates.put("BY VALUE",
                "//" + t + "[@value='" + valueAttr + "']");

        // @src -- for image buttons (input[type=image], img inside button/a)
        String srcAttr = attr(el, "src");
        if (!srcAttr.isEmpty() && !srcAttr.startsWith("data:"))
            candidates.put("BY SRC (last segment)",
                "//" + t + "[contains(@src,'" + lastSegment(srcAttr) + "')]");

        // @alt -- for image elements used as buttons
        String altAttr = attr(el, "alt");
        if (!altAttr.isEmpty())
            candidates.put("BY ALT",
                "//" + t + "[@alt='" + altAttr + "']");

        // @data-value -- Angular chips, autocomplete options, custom dropdowns
        String dataValue = attr(el, "data-value");
        if (!dataValue.isEmpty())
            candidates.put("BY DATA-VALUE",
                "//" + t + "[@data-value='" + dataValue + "']");

        if (!i.text.isEmpty() && i.text.length() <= 50)
            candidates.put("BY TEXT (normalize-space)",
                "//" + t + "[normalize-space(.)='" + i.text + "']");

        if (!i.text.isEmpty() && i.text.length() <= 50)
            candidates.put("BY TEXT (contains)",
                "//" + t + "[contains(normalize-space(.),'" + i.text + "')]");

        // Form-row label pattern -- //div[label[normalize-space(.)='Email']]/input
        // Covers cases where label is a sibling in a containing div row, not <label for>
        if ("input".equals(t) || "select".equals(t) || "textarea".equals(t)
                || "mat-select".equals(t)) {
            String formRowXpath = buildFormRowLabelXpath(el, t);
            if (formRowXpath != null)
                candidates.put("BY FORM-ROW LABEL",
                    formRowXpath);
        }

        if (!i.routerLink.isEmpty())
            candidates.put("BY ROUTER-LINK",
                "//" + t + "[@routerlink='" + i.routerLink + "']");

        if (!i.href.isEmpty() && !i.href.equals("#")
                && !i.href.startsWith("javascript"))
            candidates.put("BY HREF (contains)",
                "//" + t + "[contains(@href,'" + lastSegment(i.href) + "')]");

        if (!i.cssClass.isEmpty()) {
            String firstClass = firstMeaningfulClass(i.cssClass);
            if (!firstClass.isEmpty())
                candidates.put("BY CLASS (contains) [fragile]",
                    "//" + t + "[contains(@class,'" + firstClass + "')]");
        }

        // Structural fallback -- always added last, always flagged
        candidates.put("STRUCTURAL [fallback - verify in DevTools]",
            getJsXPath(el));

        // Step 2 - validate each candidate: uniqueness + dynamic-ID check
        LinkedHashMap<String, String> validated = new LinkedHashMap<>();
        String firstUniqueXPath = null;

        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            String strategyLabel = entry.getKey();
            String xpath         = entry.getValue();

            // Dynamic ID check
            if (strategyLabel.equals("BY ID") && isDynamicId(i.id)) {
                validated.put(strategyLabel + "  [" + LABEL_DYNAMIC + " - auto-generated ID]",
                    xpath);
                continue;
            }

            // Structural is always flagged, never used as primary
            if (strategyLabel.startsWith("STRUCTURAL")) {
                validated.put(strategyLabel, xpath);
                continue;
            }

            // Uniqueness check - test against live page
            int count = countElements(xpath);
            if (count == 1) {
                validated.put(strategyLabel + "  [" + LABEL_UNIQUE + "]", xpath);
                if (firstUniqueXPath == null) firstUniqueXPath = xpath;
            } else if (count == 0) {
                validated.put(strategyLabel + "  [" + LABEL_STALE + " - 0 elements]", xpath);
            } else {
                validated.put(strategyLabel + "  [" + LABEL_NOT_UNIQUE
                    + " - " + count + " found]", xpath);
            }
        }

        // Step 3 - if no unique standalone locator found, try ancestor-scoped strategies
        // Walks DOM upward to find the nearest stable ancestor id, then scopes
        // each candidate to that ancestor and re-tests uniqueness.
        // This is how //*[@id='nav-primary']//a[...] gets discovered automatically.
        if (firstUniqueXPath == null) {
            String[] ancestor = resolveStableAncestor(el);
            if (ancestor != null) {
                String ancestorId = ancestor[0];
                log.info("  No unique standalone locator -- trying ancestor scope [@id='"
                    + ancestorId + "']");

                for (Map.Entry<String, String> entry : candidates.entrySet()) {
                    if (entry.getKey().startsWith("STRUCTURAL")) continue;
                    String baseXpath   = entry.getValue();
                    String scopedXpath = buildScopedXpath(ancestorId, baseXpath);
                    int count          = countElements(scopedXpath);

                    if (count == 1) {
                        String label = "BY ANCESTOR [@id='" + ancestorId + "'] + "
                            + entry.getKey() + "  [" + LABEL_UNIQUE + "]";
                        validated.put(label, scopedXpath);
                        if (firstUniqueXPath == null) firstUniqueXPath = scopedXpath;
                        log.info("  ANCESTOR-SCOPED UNIQUE [x]  " + scopedXpath);
                        break; // first unique is enough
                    } else if (count > 1) {
                        // Also check: sibling group -- emit list locator
                        int siblingCount = detectSiblingGroupSize(el);
                        if (siblingCount >= 2) {
                            // Build scoped list locator for the whole group
                            String listXpath = "//*[@id='" + ancestorId + "']//" + i.tag;
                            validated.put(
                                "BY ANCESTOR [@id='" + ancestorId + "'] LIST ["
                                + siblingCount + " siblings]  [GROUP]",
                                listXpath);
                            // Dynamic item locator template
                            if (!i.text.isEmpty()) {
                                String dynamicXpath = "//*[@id='" + ancestorId + "']//" + i.tag
                                    + "[normalize-space(.)='" + i.text + "']";
                                int dynCount = countElements(dynamicXpath);
                                String dynMarker = dynCount == 1
                                    ? LABEL_UNIQUE : "NOT UNIQUE (" + dynCount + ")";
                                validated.put(
                                    "BY ANCESTOR + TEXT (dynamic item)  [" + dynMarker + "]",
                                    dynamicXpath);
                                if (dynCount == 1 && firstUniqueXPath == null)
                                    firstUniqueXPath = dynamicXpath;
                            }
                        }
                    }
                }

                // Store ancestor info on ElementInfo for report generation
                i.ancestorId = ancestorId;
            }
        }

        // Promote: best unique xpath is stored on the ElementInfo
        i.primaryXpath = firstUniqueXPath != null
            ? firstUniqueXPath
            : candidates.values().iterator().next(); // fallback: first candidate

        return validated;
    }

    /** Tests the xpath against the live DOM; returns element count. */
    private int countElements(String xpath) {
        try {
            return driver.findElements(By.xpath(xpath)).size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Returns true if the id value looks like an auto-generated Angular/CDK id. */
    private boolean isDynamicId(String id) {
        return id != null && !id.isEmpty() && DYNAMIC_ID_PATTERN.matcher(id).matches();
    }

    // -------------------------------------------------------------------------
    // Page readiness
    // -------------------------------------------------------------------------

    public void waitForPageReady() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(d -> ((JavascriptExecutor) d)
                    .executeScript("return document.readyState").equals("complete"));
            // Angular zone stability
            new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                    "try{return window.getAllAngularTestabilities()[0].isStable();}catch(e){return true;}")));
            // Generic DOM-mutation stability net for React/Vue/vanilla SPAs that
            // have no Angular testability hook -- bounded so it never stalls a crawl.
            waitForDomStable(driver, Duration.ofSeconds(5), 300);
            // Network-idle net for SPAs that fetch data asynchronously and only mutate
            // the DOM after a debounced render -- complements the DOM-mutation check
            // above rather than replacing it. Bounded so it never stalls a crawl.
            waitForNetworkIdle(driver, Duration.ofSeconds(5), 300);
        } catch (Exception ignored) {}
    }

    /**
     * Waits until the page's DOM stops mutating for {@code quietPeriodMillis}
     * (via a MutationObserver counter), or {@code timeout} elapses -- whichever
     * comes first. This is a heuristic "adaptive" readiness signal (inspired by
     * how modern content crawlers detect page settle) that works across any
     * framework, not just Angular. Bounded and non-throwing: on any error, or if
     * the DOM never quiets down within the timeout, simply returns {@code false}
     * so callers can proceed rather than stall indefinitely.
     *
     * @return true when the DOM was observed quiet for the full quiet period.
     */
    public static boolean waitForDomStable(WebDriver driver, Duration timeout, long quietPeriodMillis) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "if (!window.__sdkMutationObserver) {" +
                "  window.__sdkMutationCount = 0;" +
                "  var mo = new MutationObserver(function(m){ window.__sdkMutationCount += m.length; });" +
                "  mo.observe(document.documentElement, {childList:true, subtree:true, attributes:true});" +
                "  window.__sdkMutationObserver = mo;" +
                "}");
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                Object before = js.executeScript("return window.__sdkMutationCount;");
                Thread.sleep(quietPeriodMillis);
                Object after = js.executeScript("return window.__sdkMutationCount;");
                if (before != null && before.equals(after)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Waits until there have been no in-flight {@code fetch()}/{@code XMLHttpRequest}
     * network calls for {@code quietPeriodMillis}, or {@code timeout} elapses --
     * whichever comes first. Mirrors Playwright's "networkidle" signal: complements
     * {@link #waitForDomStable} rather than replacing it, since some SPAs (React, Vue)
     * only mutate the DOM well after their own network calls settle (e.g. debounced or
     * batched renders), so the DOM-mutation counter alone can report "stable" before
     * data has actually finished loading. Bounded and non-throwing: on any error, or if
     * the page never goes network-idle within the timeout, simply returns {@code false}
     * so callers can proceed rather than stall indefinitely.
     *
     * @return true when no pending fetch/XHR calls were observed for the full quiet period.
     */
    public static boolean waitForNetworkIdle(WebDriver driver, Duration timeout, long quietPeriodMillis) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "if (!window.__sdkNetworkPatched) {" +
                "  window.__sdkPendingRequests = 0;" +
                "  if (window.fetch) {" +
                "    var origFetch = window.fetch;" +
                "    window.fetch = function(){" +
                "      window.__sdkPendingRequests++;" +
                "      var p = origFetch.apply(this, arguments);" +
                "      var done = function(){ window.__sdkPendingRequests--; };" +
                "      return p.then(function(r){ done(); return r; }, function(e){ done(); throw e; });" +
                "    };" +
                "  }" +
                "  var OrigXHR = window.XMLHttpRequest;" +
                "  var origSend = OrigXHR.prototype.send;" +
                "  OrigXHR.prototype.send = function(){" +
                "    window.__sdkPendingRequests++;" +
                "    var done = function(){ window.__sdkPendingRequests--; };" +
                "    this.addEventListener('loadend', done);" +
                "    return origSend.apply(this, arguments);" +
                "  };" +
                "  window.__sdkNetworkPatched = true;" +
                "}");
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                Object pending = js.executeScript("return window.__sdkPendingRequests || 0;");
                long count = pending instanceof Number ? ((Number) pending).longValue() : 0L;
                if (count <= 0) {
                    Thread.sleep(quietPeriodMillis);
                    Object after = js.executeScript("return window.__sdkPendingRequests || 0;");
                    long countAfter = after instanceof Number ? ((Number) after).longValue() : 0L;
                    if (countAfter <= 0) return true;
                } else {
                    Thread.sleep(Math.min(100, quietPeriodMillis));
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clicks the given element, scrolling it into view first. If the plain click is
     * intercepted (e.g. by a sticky footer, snackbar, or CDK/Angular Material overlay
     * backdrop that has not yet detached), retries once via a JS-executed click on the
     * same element.
     *
     * Use this instead of {@code element.click()} for any action button discovered
     * during a live crawl or exercised by a Page Object, where the surrounding layout
     * (modals, sticky footers, CDK overlays) is not guaranteed to be fully settled.
     * Only {@link ElementClickInterceptedException} triggers the JS-click fallback --
     * any other exception propagates unchanged. Does not affect locator-uniqueness
     * validation/labeling ({@link #LABEL_UNIQUE} etc.) in any way.
     */
    public static void safeClick(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver)
            .executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        // Best-effort proactive actionability check (visible + enabled), bounded and
        // non-throwing -- mirrors Playwright's auto-waiting actionability model. If the
        // element never becomes clickable within this short bound, fall through and
        // attempt the click anyway so a false-negative check never blocks a click that
        // would otherwise succeed; the ElementClickInterceptedException fallback below
        // still covers that case.
        try {
            new WebDriverWait(driver, Duration.ofMillis(300))
                .until(ExpectedConditions.elementToBeClickable(element));
        } catch (Exception ignored) {}
        try {
            element.click();
        } catch (ElementClickInterceptedException intercepted) {
            log.warn("Click intercepted, retrying via JS click: " + intercepted.getMessage());
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getJsXPath(WebElement el) {
        try {
            String js =
                "function xp(e){if(e.id)return '//'+e.tagName.toLowerCase()+'[@id=\"'+e.id+'\"]';" +
                "var p='',n=e;while(n&&n.nodeType===1){var i=1,s=n.previousSibling;" +
                "while(s){if(s.nodeType===1&&s.tagName===n.tagName)i++;s=s.previousSibling;}" +
                "p='/'+n.tagName.toLowerCase()+'['+i+']'+p;n=n.parentNode;}return p;}" +
                "return xp(arguments[0]);";
            Object r = ((JavascriptExecutor) driver).executeScript(js, el);
            return r != null ? r.toString() : "//unknown";
        } catch (Exception e) { return "//unknown"; }
    }

    private String attr(WebElement el, String a) {
        try { String v = el.getAttribute(a); return v != null ? v.trim() : ""; }
        catch (Exception e) { return ""; }
    }

    private String safeText(WebElement el) {
        try {
            String t = el.getText();
            if (t == null || t.isBlank()) return "";
            return t.trim().replaceAll("\\s+", " ").substring(0, Math.min(t.length(), 80));
        } catch (Exception e) { return ""; }
    }

    private boolean isVisible(WebElement el) {
        try { return el.isDisplayed(); } catch (Exception e) { return false; }
    }

    private String lastSegment(String href) {
        String clean = href.replaceAll("[#?].*", "");
        String[] parts = clean.split("/");
        for (int i = parts.length - 1; i >= 0; i--)
            if (!parts[i].isEmpty()) return parts[i];
        return href;
    }

    private String firstMeaningfulClass(String cssClass) {
        for (String token : cssClass.split("\\s+")) {
            if (!token.isEmpty() && !token.startsWith("ng-") && !token.startsWith("mat-")
                    && !token.equals("form-control") && !token.equals("ng-pristine")
                    && !token.equals("ng-valid") && !token.equals("ng-invalid")
                    && !token.equals("ng-touched") && !token.equals("ng-untouched"))
                return token;
        }
        return "";
    }

    /**
     * Builds a form-row label XPath for inputs that sit alongside a {@code <label>}
     * sibling inside a common container div/section/td -- without a {@code for} attribute.
     *
     * Patterns tried (in order):
     *   //div[label[normalize-space(.)='Email']]//input
     *   //td[label[normalize-space(.)='Email']]//input
     *   //li[label[normalize-space(.)='Email']]//input
     *   //section[label[normalize-space(.)='Email']]//input
     *   //tr[td[normalize-space(.)='Email']]//input   -- table row label pattern
     *
     * Returns the first XPath that matches exactly 1 element, or null if none found.
     */
    private String buildFormRowLabelXpath(WebElement el, String tag) {
        try {
            // Walk up to find a sibling label text
            String js =
                "var e = arguments[0];" +
                "var parent = e.parentElement;" +
                "var depth = 0;" +
                "while (parent && depth < 5) {" +
                "  var labels = parent.querySelectorAll('label, th, legend');" +
                "  for (var i = 0; i < labels.length; i++) {" +
                "    var txt = labels[i].textContent.trim().replace(/\\s+/g,' ');" +
                "    if (txt.length > 0 && txt.length <= 60) return [txt, parent.tagName.toLowerCase()];" +
                "  }" +
                "  parent = parent.parentElement;" +
                "  depth++;" +
                "}" +
                "return null;";
            Object raw = ((JavascriptExecutor) driver).executeScript(js, el);
            if (raw == null) return null;

            @SuppressWarnings("unchecked")
            java.util.List<Object> res = (java.util.List<Object>) raw;
            if (res.size() < 2) return null;

            String labelText    = res.get(0).toString();
            String containerTag = res.get(1).toString();

            // Try container patterns
            String[] containers = { containerTag, "div", "td", "li", "section", "tr" };
            for (String c : containers) {
                String xpath = "//" + c + "[.//label[normalize-space(.)='"
                    + labelText + "']]//" + tag;
                if (countElements(xpath) == 1) {
                    log.info("FORM-ROW LABEL '" + labelText + "' -> " + xpath);
                    return xpath;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // -------------------------------------------------------------------------
    // Stable Ancestor Resolution -- walks DOM upward to find container anchor
    // -------------------------------------------------------------------------

    /**
     * Walks the DOM upward from {@code el} to find the nearest ancestor that
     * has a stable (non-dynamic, non-numeric) {@code id} attribute.
     *
     * <p>This is the core of the crawler's self-healing locator strategy:
     * when an element has no unique locator on its own, it can be scoped
     * relative to a stable ancestor container -- e.g.:
     * <pre>
     *   //*[@id='nav-primary']//a[normalize-space(.)='New Reservation']
     * </pre>
     * instead of a fragile absolute XPath.
     *
     * @param el the element to start from
     * @return ancestor id and tag as {@code String[]{id, tag, scopedXpath}}
     *         or {@code null} when no stable ancestor is found within 8 levels
     */
    String[] resolveStableAncestor(WebElement el) {
        try {
            String js =
                "var e = arguments[0];" +
                "var maxDepth = arguments[1];" +
                "var depth = 0;" +
                "while (e && e.parentElement && depth < maxDepth) {" +
                "  e = e.parentElement;" +
                "  depth++;" +
                "  var id = e.getAttribute('id');" +
                "  if (id && id.trim().length > 0) {" +
                "    return [id.trim(), e.tagName.toLowerCase(), depth + ''];" +
                "  }" +
                "}" +
                "return null;";
            Object raw = ((JavascriptExecutor) driver).executeScript(js, el, (long) ANCESTOR_WALK_DEPTH);
            if (raw == null) return null;

            @SuppressWarnings("unchecked")
            java.util.List<Object> result = (java.util.List<Object>) raw;
            if (result.size() < 3) return null;

            String ancestorId  = result.get(0).toString();
            String ancestorTag = result.get(1).toString();

            // Reject dynamic ancestor ids
            if (isDynamicId(ancestorId)) return null;

            return new String[]{ ancestorId, ancestorTag };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds a scoped XPath using a stable ancestor container anchor.
     *
     * <p>Example output:
     * <pre>
     *   //*[@id='nav-primary']//a[normalize-space(.)='New Reservation']
     *   //*[@id='enrollForm']//input[@formcontrolname='poleId']
     * </pre>
     *
     * @param ancestorId stable ancestor id
     * @param elementXpath relative XPath of the element within the ancestor
     * @return fully scoped XPath string
     */
    private String buildScopedXpath(String ancestorId, String elementXpath) {
        // Remove leading // from element xpath to make it relative
        String rel = elementXpath.startsWith("//")
            ? elementXpath.substring(2) : elementXpath;
        return "//*[@id='" + ancestorId + "']//" + rel;
    }

    /**
     * Detects whether an element belongs to a sibling group by checking if its
     * parent has multiple children with the same tag.
     *
     * <p>When true, the crawler should generate a list locator for the whole
     * group and a dynamic item locator scoped to the stable ancestor.
     *
     * @param el        the element to check
     * @param minSiblings minimum sibling count to qualify as a group (default 2)
     * @return sibling count, or 0 when not in a repeating group
     */
    int detectSiblingGroupSize(WebElement el) {
        try {
            String js =
                "var e = arguments[0];" +
                "var parent = e.parentElement;" +
                "if (!parent) return 0;" +
                "var tag = e.tagName;" +
                "var siblings = parent.querySelectorAll(':scope > ' + tag);" +
                "return siblings.length;";
            Object result = ((JavascriptExecutor) driver).executeScript(js, el);
            return result != null ? ((Long) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }



    /**
     * Detects elements that belong to a repeating group -- siblings that share a
     * common {@code id} stem or {@code class} token -- and generates:
     * <ol>
     *   <li>A <b>list locator</b> ({@code @FindBy} with {@code contains(@id,'stem')})
     *       that captures ALL members as {@code List<WebElement>}</li>
     *   <li>A <b>dynamic single-item locator</b> that combines the stem with the
     *       element's visible text, e.g.
     *       {@code //*[contains(@id,'field-label') and normalize-space(.)='Pole ID']}</li>
     * </ol>
     *
     * <p>Detection logic:
     * <ul>
     *   <li>If an element's {@code id} contains a non-numeric stem that is shared by
     *       2+ elements on the page -> id-stem group</li>
     *   <li>If 2+ elements share a meaningful {@code class} token (non-Angular noise)
     *       -> class group</li>
     * </ul>
     *
     * <p>Usage in generated Page Object:
     * <pre>
     *   // List locator -- all members
     *   {@literal @}FindBy(xpath = "//*[contains(@id,'field-label')]")
     *   List<WebElement> fieldLabels;
     *
     *   // Dynamic single-item locator -- built at runtime by text
     *   public WebElement getLabelByText(String text) {
     *       return driver.findElement(By.xpath(
     *           "//*[contains(@id,'field-label') and normalize-space(.)='" + text + "']"));
     *   }
     * </pre>
     *
     * @param elements full list of crawled elements from the current page
     * @return list of {@link RepeatingGroupInfo} -- one entry per detected group
     */
    public List<RepeatingGroupInfo> detectRepeatingGroups(List<ElementInfo> elements) {
        // Map: stem -> list of elements that share it
        Map<String, List<ElementInfo>> idStemGroups   = new LinkedHashMap<>();
        Map<String, List<ElementInfo>> classStemGroups = new LinkedHashMap<>();

        for (ElementInfo el : elements) {

            // --- id stem detection ---
            if (!el.id.isEmpty() && !isDynamicId(el.id)) {
                // Extract the non-numeric stem: "field-label-3" -> "field-label"
                String stem = el.id.replaceAll("[-_]?\\d+$", "").trim();
                if (!stem.isEmpty() && !stem.equals(el.id)) {
                    // id has a numeric suffix -> definite repeating pattern
                    if (!idStemGroups.containsKey(stem))
                        idStemGroups.put(stem, new ArrayList<ElementInfo>());
                    idStemGroups.get(stem).add(el);
                } else if (!stem.isEmpty()) {
                    // same stem shared with others -- we'll see after full scan
                    if (!idStemGroups.containsKey(stem))
                        idStemGroups.put(stem, new ArrayList<ElementInfo>());
                    idStemGroups.get(stem).add(el);
                }
            }

            // --- class stem detection ---
            if (!el.cssClass.isEmpty()) {
                for (String token : el.cssClass.split("\\s+")) {
                    if (token.isEmpty()
                            || token.startsWith("ng-")
                            || token.startsWith("mat-")
                            || token.startsWith("cdk-")
                            || token.equals("form-control")
                            || token.equals("ng-pristine")
                            || token.equals("ng-valid")
                            || token.equals("ng-invalid")
                            || token.equals("ng-touched")
                            || token.equals("ng-untouched")) continue;
                    if (!classStemGroups.containsKey(token))
                        classStemGroups.put(token, new ArrayList<ElementInfo>());
                    classStemGroups.get(token).add(el);
                }
            }
        }

        List<RepeatingGroupInfo> groups = new ArrayList<>();

        // --- Build groups from id stems (2+ members) ---
        for (Map.Entry<String, List<ElementInfo>> entry : idStemGroups.entrySet()) {
            List<ElementInfo> members = entry.getValue();
            if (members.size() < 2) continue;

            String stem         = entry.getKey();
            String listXpath    = "//*[contains(@id,'" + stem + "')]";
            int    liveCount    = countElements(listXpath);

            // Verify on live page
            if (liveCount < 2) continue;

            RepeatingGroupInfo group = new RepeatingGroupInfo();
            group.groupType         = "ID_STEM";
            group.stem              = stem;
            group.listLocator       = listXpath;
            group.listAnnotation    = "@FindBy(xpath = \"" + listXpath + "\")\nList<WebElement> "
                                      + toCamelCase(stem) + "List;";
            group.dynamicLocatorTemplate =
                "//*[contains(@id,'" + stem + "') and normalize-space(.)='{TEXT}']";
            group.dynamicMethodExample   = buildDynamicMethod(stem, "id", stem);
            group.liveCount             = liveCount;

            // Collect member texts
            for (ElementInfo m : members) {
                if (!m.text.isEmpty()) group.memberTexts.add(m.text);
            }

            log.info("GROUP [ID_STEM='" + stem + "'] -- " + liveCount
                + " elements -- list: " + listXpath);
            for (String t : group.memberTexts) log.info("  item: '" + t + "'");
            groups.add(group);
        }

        // --- Build groups from class stems (2+ members, not already covered by id) ---
        for (Map.Entry<String, List<ElementInfo>> entry : classStemGroups.entrySet()) {
            List<ElementInfo> members = entry.getValue();
            if (members.size() < 2) continue;

            String token      = entry.getKey();
            String listXpath  = "//*[contains(@class,'" + token + "')]";
            int    liveCount  = countElements(listXpath);
            if (liveCount < 2) continue;

            // Skip if an id-stem group already covers these same elements
            boolean alreadyCovered = false;
            for (RepeatingGroupInfo g : groups) {
                if (g.groupType.equals("ID_STEM") && g.memberTexts.containsAll(
                        getTexts(members))) { alreadyCovered = true; break; }
            }
            if (alreadyCovered) continue;

            RepeatingGroupInfo group = new RepeatingGroupInfo();
            group.groupType         = "CLASS_STEM";
            group.stem              = token;
            group.listLocator       = listXpath;
            group.listAnnotation    = "@FindBy(xpath = \"" + listXpath + "\")\nList<WebElement> "
                                      + toCamelCase(token) + "List;";
            group.dynamicLocatorTemplate =
                "//*[contains(@class,'" + token + "') and normalize-space(.)='{TEXT}']";
            group.dynamicMethodExample   = buildDynamicMethod(token, "class", token);
            group.liveCount             = liveCount;

            for (ElementInfo m : members) {
                if (!m.text.isEmpty()) group.memberTexts.add(m.text);
            }

            log.info("GROUP [CLASS_STEM='" + token + "'] -- " + liveCount
                + " elements -- list: " + listXpath);
            groups.add(group);
        }

        log.info("detectRepeatingGroups: " + groups.size() + " groups found");
        return groups;
    }

    /** Extracts text values from a list of ElementInfo. */
    private List<String> getTexts(List<ElementInfo> elements) {
        List<String> texts = new ArrayList<>();
        for (ElementInfo e : elements)
            if (!e.text.isEmpty()) texts.add(e.text);
        return texts;
    }

    /** Converts a kebab/underscore stem to camelCase field name. */
    private String toCamelCase(String stem) {
        String[] parts = stem.split("[-_]");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty())
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                  .append(parts[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Generates the dynamic getter method source snippet. */
    private String buildDynamicMethod(String stem, String attrType, String attrValue) {
        String condition = attrType.equals("id")
            ? "contains(@id,'" + attrValue + "')"
            : "contains(@class,'" + attrValue + "')";
        return
            "public WebElement get" + toCamelCase(stem).substring(0, 1).toUpperCase()
            + toCamelCase(stem).substring(1) + "ByText(String text) {\n"
            + "    return driver.findElement(By.xpath(\n"
            + "        \"//*[" + condition + " and normalize-space(.)='\" + text + \"']\"));\n"
            + "}";
    }

    // =========================================================================
    // RepeatingGroupInfo - data holder for a detected element group
    // =========================================================================

    /**
     * Describes a group of repeating elements (e.g. all form labels, all nav items)
     * detected by {@link #detectRepeatingGroups(List)}.
     */
    public static class RepeatingGroupInfo {
        /** "ID_STEM" or "CLASS_STEM" */
        public String       groupType              = "";
        /** Shared stem, e.g. "field-label" or "nav-item" */
        public String       stem                   = "";
        /** XPath that matches ALL members, e.g. //*[contains(@id,'field-label')] */
        public String       listLocator            = "";
        /** Full @FindBy + field declaration for the list */
        public String       listAnnotation         = "";
        /** Dynamic template -- replace {TEXT} with the item's visible text */
        public String       dynamicLocatorTemplate = "";
        /** Ready-to-paste Java method that builds the dynamic locator at runtime */
        public String       dynamicMethodExample   = "";
        /** Number of elements matched on the live page */
        public int          liveCount              = 0;
        /** Visible text values of all detected members */
        public List<String> memberTexts            = new ArrayList<>();
    }

    // =========================================================================
    // ElementInfo - data holder
    // =========================================================================

    public static class ElementInfo {
        public String  tag             = "";
        public String  id              = "";
        public String  name            = "";
        public String  type            = "";
        public String  placeholder     = "";
        public String  formControlName = "";
        public String  ariaLabel       = "";
        public String  href            = "";
        public String  routerLink      = "";
        public String  cssClass        = "";
        public String  dataTestId      = "";
        public String  title           = "";
        public String  autocomplete    = "";
        public String  alt             = "";
        public String  src             = "";
        public String  dataValue       = "";
        public String  text            = "";
        public boolean visible         = true;
        /** Nearest stable ancestor id -- set when no unique standalone locator exists. */
        public String  ancestorId        = "";
        /** Role of a group container (listbox/menu/tablist) or item (option/menuitem/tab). */
        public String  roleGroup         = "";
        /** Expected child role when this element is a group container. */
        public String  roleGroupChild    = "";
        /** For label elements: the 'for' attribute value linking to an input id. */
        public String  linkedForAttr     = "";
        /** For label elements: the resolved input locator //input[@id='for']. */
        public String  linkedInputXpath  = "";
        /** Human-readable label text resolved from label[for], aria-labelledby, or mat-label. */
        public String  labelText         = "";
        /**
         * Label-following XPath -- targets this element via its visible label text.
         * Example: //mat-form-field[.//mat-label[normalize-space(.)='Borough']]//mat-select
         * This matches how manual testers describe elements in test cases.
         */
        public String  labelFollowingXpath = "";
        /** True when this element is a <label> element. */
        public boolean isLabel           = false;
        /** True when this element's label is resolved via aria-labelledby. */
        public boolean isLabelledBy      = false;
        /** True when this element's label is resolved via Angular Material mat-label. */
        public boolean isMatLabelled     = false;
        /** True when element is inside an iframe. Frame index stored in frameIndex. */
        public boolean inFrame           = false;
        /** Index of the frame this element was found in (0-based). -1 = top document. */
        public int     frameIndex        = -1;
        /** True for input[type=hidden] elements -- useful but need special handling. */
        public boolean isHidden          = false;
        /** True for input[type=file] -- use sendKeys(filePath) not clearAndType(). */
        public boolean isFileInput       = false;
        /** True for contenteditable divs -- rich text editors. */
        public boolean isContentEditable = false;
        /** True for option elements inside a select. */
        public boolean isSelectOption    = false;
        /** True when element is an image button (input[type=image] or img inside button). */
        public boolean isImageButton     = false;
        /** True when element is a table header th. */
        public boolean isTableHeader     = false;
        /** Visible text of the table column header. */
        public String  tableColumnText             = "";
        /** XPath targeting all cells in this column. */
        public String  tableColumnCellsXpath       = "";
        /** Dynamic template: replace {ROW_KEY} with a cell value to find that row's cell. */
        public String  tableColumnDynamicTemplate  = "";
        /**
         * True when this element is a detected map/chart widget container
         * (Google Maps, Leaflet, Mapbox GL, MapLibre GL, OpenLayers, Bing Maps,
         * or an unrecognized canvas-based library). See {@link MapWidgetHelper}
         * for the recommended interaction pattern (waitForMapReady/searchAddress/
         * findMarkerByLabel/clickAtPixelOffset) instead of per-tile locators.
         */
        public boolean isMapWidget = false;
        /** Human-readable provider name when isMapWidget is true, e.g. "Google Maps", "Leaflet". */
        public String  mapProvider = "";
        /**
         * True when this element lives inside an <em>open</em> shadow root (Web
         * Components, Lit/Stencil, Salesforce Lightning/LWC, etc.). XPath cannot
         * cross a shadow boundary, so standalone {@code xpath} is empty for these
         * elements -- use {@link #shadowHostXpath} + {@link #shadowRelativeCss}
         * via {@code host.getShadowRoot().findElement(By.cssSelector(...))}.
         */
        public boolean inShadowDom = false;
        /** Light-DOM XPath to the shadow-root HOST element, when isInShadowDom=true. */
        public String  shadowHostXpath = "";
        /** CSS selector for this element relative to its enclosing shadow root. */
        public String  shadowRelativeCss = "";

        /** Best unique, non-dynamic XPath -- set by buildAllXPaths(). */
        public String xpath        = "";
        /** Internal staging field used during buildAllXPaths(). */
        public String primaryXpath = "";

        /**
         * Scenario name(s) that revealed this element.
         * Set by DataDrivenCrawler after merging multi-scenario snapshots.
         * Values: "All scenarios", "Baseline", or comma-separated scenario names.
         */
        public String scenarioTag = "";

        /**
         * Test case step that first revealed this element during a crawlTestCase() run.
         * Format: "Step N: <description>"  e.g. "Step 3: Select Category = Noise"
         * Empty when element was present at baseline (initial page load).
         */
        public String stepTag = "";

        /**
         * Page-state route label where this element was first discovered.
         * Derived from the URL at the time of discovery:
         *   - Angular SPA hash routes: e.g. "reservation/12345", "dashboard", "construction"
         *   - Regular pages: last URL path segment
         *   - Multiple pages: "Multiple pages: dashboard, reservation/12345"
         * Empty for elements discovered at the baseline URL.
         */
        public String pageState = "";

        /**
         * All XPath strategies with uniqueness/dynamic labels.
         * Key   = "BY ID  [UNIQUE [x]]"  or  "BY ID  [DYNAMIC]"  etc.
         * Value = XPath expression
         */
        public LinkedHashMap<String, String> allXpaths = new LinkedHashMap<>();

        /** Primary @FindBy using the best unique xpath. */
        public String findByAnnotation() {
            return "@FindBy(xpath = \"" + xpath.replace("\"", "'") + "\")";
        }

        /** True when at least one UNIQUE [x] strategy was found. */
        public boolean hasUniqueLocator() {
            for (String label : allXpaths.keySet())
                if (label.contains(LABEL_UNIQUE)) return true;
            return false;
        }

        @Override
        public String toString() {
            return "[" + tag + "] id=" + id + " | unique=" + hasUniqueLocator()
                 + " | xpath=" + xpath
                 + (inFrame       ? " | frame=" + frameIndex  : "")
                 + (isFileInput   ? " | FILE-INPUT"           : "")
                 + (isImageButton ? " | IMAGE-BUTTON"         : "")
                 + (isContentEditable ? " | CONTENTEDITABLE"  : "")
                 + (isHidden      ? " | HIDDEN"               : "")
                 + (isTableHeader ? " | TABLE-HEADER=" + tableColumnText : "")
                 + (isMapWidget   ? " | MAP-WIDGET=" + mapProvider : "")
                 + (inShadowDom   ? " | SHADOW-DOM host=" + shadowHostXpath + " css=" + shadowRelativeCss : "")
                 + (!labelText.isEmpty()  ? " | label='"     + labelText  + "'" : "")
                 + (!pageState.isEmpty()  ? " | page="       + pageState        : "")
                 + (!stepTag.isEmpty()    ? " | step="       + stepTag          : "")
                 + (!scenarioTag.isEmpty() ? " | scenario="  + scenarioTag      : "");
        }
    }
}
