package com.test.automation.sdk.mobile.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.appium.java_client.AppiumDriver;

import com.test.automation.sdk.discovery.DiscoveredElement;
import com.test.automation.sdk.discovery.DiscoveryResult;
import com.test.automation.sdk.discovery.ElementDiscoveryService;
import com.test.automation.sdk.discovery.LocatorCandidate;
import com.test.automation.sdk.utility.ElementCrawler;
import com.test.automation.sdk.discovery.WebElementDiscoveryAdapter;

/**
 * Adapts {@link MobileElementCrawler}'s output (native + delegated WebView
 * elements) into the common {@link ElementDiscoveryService}/{@link DiscoveryResult}
 * contract, without changing {@link MobileElementCrawler}'s own crawling/
 * uniqueness-detection algorithm -- see Structure Cleanup Phase 6
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md). WebView
 * elements are normalized via {@link WebElementDiscoveryAdapter#toDiscoveredElement},
 * reusing the exact same conversion the desktop crawler's own elements use --
 * there is only one {@code ElementCrawler.ElementInfo}-to-{@code DiscoveredElement}
 * conversion in the whole SDK.
 */
public class MobileElementDiscoveryAdapter implements ElementDiscoveryService {

    private final MobileElementCrawler crawler;

    public MobileElementDiscoveryAdapter(AppiumDriver driver) {
        this.crawler = new MobileElementCrawler(driver);
    }

    public MobileElementDiscoveryAdapter(MobileElementCrawler crawler) {
        this.crawler = crawler;
    }

    /**
     * Crawls the current screen (via {@link MobileElementCrawler#crawlCurrentScreen()})
     * and normalizes the result. Requires a live {@code AppiumDriver} session -- not
     * unit-tested here; see {@link #toDiscoveryResult(MobileScreenSnapshot)} for the
     * driver-free conversion logic that is unit-tested.
     */
    @Override
    public DiscoveryResult discoverCurrent() {
        return toDiscoveryResult(crawler.crawlCurrentScreen());
    }

    /** Pure, driver-free conversion -- unit-testable without a live session. */
    public static DiscoveryResult toDiscoveryResult(MobileScreenSnapshot snapshot) {
        List<DiscoveredElement> discovered = new ArrayList<>();
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            discovered.add(toDiscoveredElement(info));
        }
        if (snapshot.hasWebViewContent()) {
            for (Map.Entry<String, List<ElementCrawler.ElementInfo>> entry : snapshot.getWebViewElements().entrySet()) {
                for (ElementCrawler.ElementInfo webInfo : entry.getValue()) {
                    discovered.add(WebElementDiscoveryAdapter.toDiscoveredElement(webInfo));
                }
            }
        }
        return new DiscoveryResult(snapshot.getPlatform(), "current screen", discovered);
    }

    /** Pure, driver-free conversion of one {@link MobileElementInfo} -- unit-testable without a live session. */
    public static DiscoveredElement toDiscoveredElement(MobileElementInfo info) {
        List<LocatorCandidate> candidates = new ArrayList<>();
        for (MobileLocatorCandidate candidate : info.getCandidates()) {
            candidates.add(toLocatorCandidate(candidate));
        }
        return new DiscoveredElement(info.getPlatform(), info.getTagOrClassName(), info.getText(), candidates, info);
    }

    private static LocatorCandidate toLocatorCandidate(MobileLocatorCandidate candidate) {
        LocatorCandidate.Marker marker;
        MobileLocatorCandidate.Marker mobileMarker = candidate.getMarker();
        if (mobileMarker == null) {
            marker = LocatorCandidate.Marker.OTHER;
        } else {
            switch (mobileMarker) {
                case UNIQUE:
                    marker = LocatorCandidate.Marker.UNIQUE;
                    break;
                case NOT_UNIQUE:
                    marker = LocatorCandidate.Marker.NOT_UNIQUE;
                    break;
                case DYNAMIC:
                    marker = LocatorCandidate.Marker.DYNAMIC;
                    break;
                case STRUCTURAL:
                    marker = LocatorCandidate.Marker.STRUCTURAL;
                    break;
                default:
                    marker = LocatorCandidate.Marker.OTHER;
                    break;
            }
        }
        return new LocatorCandidate(candidate.getStrategy().name(), candidate.getValue(), marker, candidate.getMatchCount());
    }
}
