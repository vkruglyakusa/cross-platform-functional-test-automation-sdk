package com.test.automation.sdk.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.discovery.LocatorCandidate.Marker;
import com.test.automation.sdk.utility.ElementCrawler;

/**
 * Adapts the desktop {@link ElementCrawler}'s output into the common
 * {@link ElementDiscoveryService}/{@link DiscoveryResult} contract, without
 * changing {@link ElementCrawler}'s own crawling/uniqueness-detection algorithm
 * -- see Structure Cleanup Phase 6 (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md).
 *
 * <p>Moved from {@code utility.WebElementDiscoveryAdapter} to this package in
 * Phase 8 (Package Cleanup) so that all {@code discovery} contract
 * implementations live next to the contract they implement -- mirrors
 * {@code mobile.crawler.MobileElementDiscoveryAdapter}'s location alongside
 * its own crawler package.
 */
public class WebElementDiscoveryAdapter implements ElementDiscoveryService {

    private static final Pattern MATCH_COUNT_PATTERN = Pattern.compile("(\\d+) found");

    private final ElementCrawler crawler;

    public WebElementDiscoveryAdapter(WebDriver driver) {
        this.crawler = new ElementCrawler(driver);
    }

    public WebElementDiscoveryAdapter(ElementCrawler crawler) {
        this.crawler = crawler;
    }

    /**
     * Crawls the current page (via {@link ElementCrawler#crawlCurrentPage()}) and
     * normalizes the result. Requires a live {@code WebDriver} session -- not
     * unit-tested here; see {@link #toDiscoveryResult(String, List)} for the
     * driver-free conversion logic that is unit-tested.
     */
    @Override
    public DiscoveryResult discoverCurrent() {
        List<ElementCrawler.ElementInfo> elements = crawler.crawlCurrentPage();
        String url;
        try {
            url = crawler.getDriver().getCurrentUrl();
        } catch (Exception e) {
            url = "";
        }
        return toDiscoveryResult(url, elements);
    }

    /** Pure, driver-free conversion -- unit-testable without a live session. */
    public static DiscoveryResult toDiscoveryResult(String sourceLabel, List<ElementCrawler.ElementInfo> elements) {
        List<DiscoveredElement> discovered = new ArrayList<>();
        if (elements != null) {
            for (ElementCrawler.ElementInfo info : elements) {
                discovered.add(toDiscoveredElement(info));
            }
        }
        return new DiscoveryResult("web", sourceLabel, discovered);
    }

    /** Pure, driver-free conversion of one {@link ElementCrawler.ElementInfo} -- unit-testable without a live session. */
    public static DiscoveredElement toDiscoveredElement(ElementCrawler.ElementInfo info) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, String> entry : info.allXpaths.entrySet()) {
            candidates.add(toLocatorCandidate(entry.getKey(), entry.getValue()));
        }
        String text = info.text != null && !info.text.isEmpty() ? info.text : info.labelText;
        return new DiscoveredElement("web", info.tag, text, candidates, info);
    }

    /**
     * Parses one {@code allXpaths} composite key (e.g. {@code "BY ID  [UNIQUE [x]]"})
     * into a normalized candidate, reusing {@link ElementCrawler}'s own public
     * {@code LABEL_*} constants so this normalization can never drift out of sync
     * with the crawler's own labeling.
     */
    private static LocatorCandidate toLocatorCandidate(String compositeKey, String xpath) {
        Marker marker;
        int matchCount = -1;
        if (compositeKey.contains(ElementCrawler.LABEL_UNIQUE)) {
            marker = Marker.UNIQUE;
        } else if (compositeKey.contains(ElementCrawler.LABEL_DYNAMIC)) {
            marker = Marker.DYNAMIC;
        } else if (compositeKey.contains(ElementCrawler.LABEL_STALE)) {
            marker = Marker.STALE;
        } else if (compositeKey.contains(ElementCrawler.LABEL_NOT_UNIQUE)) {
            marker = Marker.NOT_UNIQUE;
            matchCount = parseMatchCount(compositeKey);
        } else if (compositeKey.contains(ElementCrawler.LABEL_STRUCTURAL)) {
            marker = Marker.STRUCTURAL;
        } else {
            marker = Marker.OTHER;
        }
        int markerStart = compositeKey.indexOf("  [");
        String strategyLabel = markerStart >= 0 ? compositeKey.substring(0, markerStart) : compositeKey;
        return new LocatorCandidate(strategyLabel, xpath, marker, matchCount);
    }

    private static int parseMatchCount(String compositeKey) {
        Matcher matcher = MATCH_COUNT_PATTERN.matcher(compositeKey);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
