package com.test.automation.sdk.utility;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * ===========================================================================
 * MapWidgetHelper
 * ===========================================================================
 *
 * WHY THIS EXISTS
 * ----------------
 * Map widgets (Google Maps, Leaflet, Mapbox GL JS, MapLibre GL JS, OpenLayers,
 * Bing Maps, and similar libraries) do not follow the normal "unique, stable
 * DOM node per interactive element" model that {@link ElementCrawler} and the
 * rest of this SDK are built around:
 *
 *   - Tiles and many markers are drawn on a &lt;canvas&gt;/WebGL surface with
 *     ZERO DOM representation -- there is nothing an XPath can ever match.
 *   - Marker DOM (when it exists at all) is frequently regenerated with
 *     internal, auto-generated CSS class names that change across library
 *     versions -- never a stable locator.
 *   - Maps do not fire a standard "page loaded" signal; tiles/markers stream
 *     in asynchronously after the container appears in the DOM.
 *
 * This class centralizes the map-specific detection/wait/interaction logic so
 * that ElementCrawler only needs to flag "this is a map widget container" and
 * every consumer test/page-object uses the SAME battle-tested strategy layer:
 *
 *   1. DOM-based locators FIRST (aria-label / alt / title -- these survive
 *      library upgrades far better than internal CSS classes).
 *   2. Pixel/JS fallback SECOND (only when a marker has no DOM presence at
 *      all, e.g. legacy google.maps.Marker or canvas-drawn pins).
 *
 * SUPPORTED PROVIDERS (detected via container CSS-class fingerprint)
 * --------------------------------------------------------------------------
 *   Google Maps      -> .gm-style
 *   Leaflet           -> .leaflet-container
 *   Mapbox GL JS      -> .mapboxgl-map
 *   MapLibre GL JS    -> .maplibregl-map
 *   OpenLayers        -> .ol-viewport
 *   Bing Maps         -> .MicrosoftMap
 *   (anything else)   -> "Generic canvas (unrecognized map/chart library)"
 *
 * This list is intentionally centralized here (not duplicated in
 * ElementCrawler) so adding a new provider only requires one edit.
 *
 * TYPICAL USAGE FROM A PAGE OBJECT / TEST
 * --------------------------------------------------------------------------
 *   MapWidgetHelper.waitForMapReady(driver, mapContainer, Duration.ofSeconds(15));
 *   MapWidgetHelper.searchAddress(driver, mapContainer, "350 5th Ave, New York, NY");
 *   WebElement pin = MapWidgetHelper.findMarkerByLabel(driver, mapContainer, "My Store");
 *   if (pin == null) {
 *       // Marker has no DOM presence (canvas-drawn) -- fall back to a pixel click.
 *       MapWidgetHelper.clickAtPixelOffset(driver, mapContainer, 0, 0);
 *   }
 *
 * LIMITATIONS (documented, not solvable in general)
 * --------------------------------------------------------------------------
 *   - clickAtPixelOffset() cannot verify WHAT it clicked; it is a last-resort
 *     fallback for canvas/WebGL-only markers and should be paired with an
 *     assertion on the resulting side effect (info window text, URL change,
 *     etc.), not on the marker itself.
 *   - waitForMapReady() uses heuristic signals (canvas paint / tile images)
 *     since no map library exposes a universal "fully loaded" DOM event.
 *
 * @author vkruglyak
 */
public final class MapWidgetHelper {

    private static final Logger log = LogManager.getLogger(MapWidgetHelper.class.getName());

    private MapWidgetHelper() {
        // static utility class
    }

    /**
     * Known map-provider container CSS-class fingerprints, in detection priority
     * order. Key = human-readable provider name, Value = CSS class fragment used
     * to locate the container via {@code By.cssSelector("." + value)}.
     * Shared with {@link ElementCrawler} so there is a single source of truth.
     */
    public static final LinkedHashMap<String, String> PROVIDER_CSS_SIGNATURES = new LinkedHashMap<>();
    static {
        PROVIDER_CSS_SIGNATURES.put("Google Maps",   "gm-style");
        PROVIDER_CSS_SIGNATURES.put("Leaflet",       "leaflet-container");
        PROVIDER_CSS_SIGNATURES.put("Mapbox GL JS",  "mapboxgl-map");
        PROVIDER_CSS_SIGNATURES.put("MapLibre GL JS", "maplibregl-map");
        PROVIDER_CSS_SIGNATURES.put("OpenLayers",    "ol-viewport");
        PROVIDER_CSS_SIGNATURES.put("Bing Maps",     "MicrosoftMap");
    }

    /** Label used when a &lt;canvas&gt; is found but does not match a known provider fingerprint. */
    public static final String UNKNOWN_PROVIDER_LABEL = "Generic canvas (unrecognized map/chart library)";

    // Relative (".//") xpath fragments tried, in priority order, when locating a
    // map's address/place search box. Some map integrations render the search
    // input OUTSIDE the map container div, so callers also get a page-wide fallback.
    private static final String[] SEARCH_BOX_XPATH_FRAGMENTS = {
        "input[contains(@class,'pac-target-input')]",
        "input[@type='search']",
        "input[contains(translate(@aria-label,'SEARCH','search'),'search')]",
        "input[contains(translate(@placeholder,'SEARCH','search'),'search')]"
    };

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    /**
     * Returns the human-readable provider name for the given map container
     * element, based on its CSS class attribute. Returns
     * {@link #UNKNOWN_PROVIDER_LABEL} when no known fingerprint matches.
     */
    public static String detectProvider(WebElement container) {
        String cssClass;
        try {
            cssClass = container.getAttribute("class");
        } catch (Exception e) {
            return UNKNOWN_PROVIDER_LABEL;
        }
        if (cssClass == null) cssClass = "";
        for (Map_Entry entry : entries()) {
            if (cssClass.contains(entry.cssFragment)) return entry.provider;
        }
        return UNKNOWN_PROVIDER_LABEL;
    }

    private static Map_Entry[] entries() {
        Map_Entry[] result = new Map_Entry[PROVIDER_CSS_SIGNATURES.size()];
        int idx = 0;
        for (Map.Entry<String, String> e : PROVIDER_CSS_SIGNATURES.entrySet()) {
            result[idx++] = new Map_Entry(e.getKey(), e.getValue());
        }
        return result;
    }

    private static final class Map_Entry {
        final String provider;
        final String cssFragment;
        Map_Entry(String provider, String cssFragment) {
            this.provider = provider;
            this.cssFragment = cssFragment;
        }
    }

    // -------------------------------------------------------------------------
    // Wait for map ready
    // -------------------------------------------------------------------------

    /**
     * Waits until the map container shows some rendered content (a sized
     * &lt;canvas&gt; or tile &lt;img&gt; elements), then applies a short settle
     * buffer since maps keep streaming tiles/markers in for a moment after the
     * first paint. There is no universal "map fully loaded" DOM event, so this
     * is intentionally heuristic -- prefer asserting on a specific expected
     * result (marker visible, address confirmed) after calling this.
     */
    public static void waitForMapReady(WebDriver driver, WebElement container, Duration timeout) {
        log.info("MapWidgetHelper: waiting for map container to render (timeout=" + timeout.getSeconds() + "s)");
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(d -> hasRenderedMapContent(container));
        // Settle buffer -- tiles/markers keep streaming in briefly after first paint.
        // Prefer an actual DOM-quiet signal over a blind sleep; fall back to a short
        // fixed sleep only if the DOM never settles within the bounded window (some
        // map libraries animate continuously, e.g. attribution/zoom controls).
        boolean settled = ElementCrawler.waitForDomStable(driver, Duration.ofSeconds(2), 300);
        if (!settled) sleep(400);
        log.info("MapWidgetHelper: map container appears rendered.");
    }

    private static boolean hasRenderedMapContent(WebElement container) {
        try {
            List<WebElement> canvases = container.findElements(By.tagName("canvas"));
            for (WebElement c : canvases) {
                Dimension size = c.getSize();
                if (size.getWidth() > 0 && size.getHeight() > 0) return true;
            }
            List<WebElement> tiles = container.findElements(
                By.xpath(".//img[contains(@src,'tile') or contains(@class,'tile') or contains(@class,'gm-')]"));
            return !tiles.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Address search
    // -------------------------------------------------------------------------

    /**
     * Locates the map's address/place search input (inside the container first,
     * then falling back to a page-wide search since some integrations render
     * the search box outside the map div), types the given address, and either
     * clicks the first autocomplete suggestion (e.g. Google Places
     * {@code .pac-item}) or presses ENTER when no suggestion dropdown appears.
     * Waits for the map to re-render afterward.
     *
     * @return true when a search box was found and the address was submitted;
     *         false when no search box could be located (caller should fall
     *         back to a page-object-specific locator).
     */
    public static boolean searchAddress(WebDriver driver, WebElement container, String address) {
        WebElement box = findSearchBox(driver, container);
        if (box == null) {
            log.warn("MapWidgetHelper.searchAddress: no address search box found near this map container.");
            return false;
        }
        log.info("MapWidgetHelper: searching address [" + address + "]");
        box.clear();
        box.sendKeys(address);
        sleep(400); // let autocomplete suggestions render

        List<WebElement> suggestions = driver.findElements(By.cssSelector(".pac-container .pac-item"));
        if (!suggestions.isEmpty()) {
            suggestions.get(0).click();
        } else {
            box.sendKeys(Keys.ENTER);
        }
        waitForMapReady(driver, container, Duration.ofSeconds(15));
        return true;
    }

    /**
     * Locates the map's address/place search input. Tries within the map
     * container first, then falls back to a page-wide search.
     */
    public static WebElement findSearchBox(WebDriver driver, WebElement container) {
        for (String fragment : SEARCH_BOX_XPATH_FRAGMENTS) {
            try {
                List<WebElement> found = container.findElements(By.xpath(".//" + fragment));
                if (!found.isEmpty()) return found.get(0);
            } catch (Exception e) {
                // ignore and try next fragment
            }
        }
        for (String fragment : SEARCH_BOX_XPATH_FRAGMENTS) {
            try {
                List<WebElement> found = driver.findElements(By.xpath("//" + fragment));
                if (!found.isEmpty()) return found.get(0);
            } catch (Exception e) {
                // ignore and try next fragment
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Markers
    // -------------------------------------------------------------------------

    /**
     * Attempts to find a marker/pin by its accessible label, trying (in order)
     * {@code aria-label}, {@code alt}, then {@code title}. Returns null when no
     * DOM element matches -- this normally means the marker is drawn on
     * canvas/WebGL with no DOM presence at all, in which case callers should
     * fall back to {@link #clickAtPixelOffset(WebDriver, WebElement, int, int)}.
     */
    public static WebElement findMarkerByLabel(WebDriver driver, WebElement container, String label) {
        String escaped = label.replace("'", "\\'");
        String[] xpaths = {
            ".//*[@aria-label='" + escaped + "']",
            ".//img[@alt='" + escaped + "']",
            ".//*[@title='" + escaped + "']"
        };
        for (String x : xpaths) {
            try {
                List<WebElement> found = container.findElements(By.xpath(x));
                if (!found.isEmpty()) return found.get(0);
            } catch (Exception e) {
                // ignore and try next strategy
            }
        }
        log.warn("MapWidgetHelper.findMarkerByLabel: no DOM marker found for label '" + label
                + "'. If this marker is canvas/WebGL-rendered, use clickAtPixelOffset() instead.");
        return null;
    }

    /**
     * Last-resort fallback for markers with zero DOM representation (canvas or
     * WebGL rendered pins). Moves to the given pixel offset from the map
     * container and clicks. Cannot verify what was clicked -- always assert on
     * the resulting side effect (info window, URL, panel), not on the marker.
     *
     * NOTE: offset semantics for {@code moveToElement(element, x, y)} come from
     * Selenium's Actions API and are relative to the element per the installed
     * Selenium/W3C driver version -- verify against your Selenium version if
     * pixel-perfect accuracy matters.
     */
    public static void clickAtPixelOffset(WebDriver driver, WebElement container, int xOffset, int yOffset) {
        log.info("MapWidgetHelper: clicking map container at pixel offset (" + xOffset + "," + yOffset + ")");
        new Actions(driver)
            .moveToElement(container, xOffset, yOffset)
            .click()
            .perform();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** True when {@code possibleAncestor} contains {@code el} in the live DOM. */
    static boolean isAncestor(WebDriver driver, WebElement possibleAncestor, WebElement el) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "return arguments[0].contains(arguments[1]);", possibleAncestor, el);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
