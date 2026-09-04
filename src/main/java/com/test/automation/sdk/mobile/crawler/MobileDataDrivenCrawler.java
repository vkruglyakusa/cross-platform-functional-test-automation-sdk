package com.test.automation.sdk.mobile.crawler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;

/**
 * Deterministic, step-list-driven multi-screen crawler for native/hybrid mobile flows.
 *
 * <p>This is the mobile analogue of the desktop SDK's {@code DataDrivenCrawler}, and is
 * intentionally scoped as a <b>deterministic replay of an explicit step list</b> rather
 * than the randomized/RL-based exploration used by model-based tools such as ByteDance's
 * Fastbot (github.com/bytedance/Fastbot_Android). A test-automation SDK needs repeatable,
 * reviewable crawls -- not randomized exploration -- so this class borrows Fastbot's core
 * idea (walk the app screen-by-screen, fingerprint each screen, skip states already seen)
 * without introducing non-determinism.</p>
 *
 * <p>Each step costs exactly the remote calls needed to perform the interaction
 * (1 find + 1 tap/type) plus the 2 remote calls {@link MobileElementCrawler#crawlCurrentScreen()}
 * already needs (page source + screenshot) -- consistent with the SDK's remote-call-economics
 * constraint (see docs/proposals/mobile-automation-strategy.md section 8a).</p>
 */
public class MobileDataDrivenCrawler {

    private static final Logger log = LogManager.getLogger(MobileDataDrivenCrawler.class.getName());
    private static final Duration DEFAULT_SETTLE_TIMEOUT = Duration.ofSeconds(10);

    private final AppiumDriver driver;
    private final MobileElementCrawler elementCrawler;
    private boolean stateDeduplicationEnabled = true;

    private final Set<String> visitedStateFingerprints = new java.util.LinkedHashSet<>();
    private final Map<String, MobileElementInfo> mergedElementsByKey = new LinkedHashMap<>();
    private final List<MobileScreenSnapshot> visitedScreens = new ArrayList<>();
    private int skippedDuplicateScreens = 0;

    public MobileDataDrivenCrawler(AppiumDriver driver) {
        this.driver = driver;
        this.elementCrawler = new MobileElementCrawler(driver);
    }

    /** Enables/disables Fastbot-style state-fingerprint dedup (enabled by default). */
    public MobileDataDrivenCrawler setStateDeduplication(boolean enabled) {
        this.stateDeduplicationEnabled = enabled;
        return this;
    }

    /**
     * Crawls the current (initial) screen, then executes each step in order, crawling
     * the resulting screen after every step. Screens whose structural fingerprint was
     * already seen are skipped from the merged element set (but the step itself still
     * runs, since later steps may depend on it having executed).
     *
     * @param flowName   human-readable label used only for logging/report context.
     * @param steps      ordered list of interactions to perform between crawls.
     * @return the deduplicated, merged list of every native element observed across the flow.
     */
    public List<MobileElementInfo> crawlFlow(String flowName, List<MobileCrawlerStep> steps) {
        log.info("Starting mobile data-driven crawl of flow '{}' ({} steps)", flowName, steps.size());

        crawlAndMerge("Initial screen");

        for (MobileCrawlerStep step : steps) {
            String label = step.getDescription().isEmpty()
                    ? step.getAction() + " " + step.getBy() + "=" + step.getSelector()
                    : step.getDescription();
            executeStep(step);
            crawlAndMerge(label);
        }

        log.info("Mobile data-driven crawl of flow '{}' complete: {} unique elements, {} screens visited, "
                        + "{} duplicate screens skipped from merge",
                flowName, mergedElementsByKey.size(), visitedScreens.size(), skippedDuplicateScreens);
        return new ArrayList<>(mergedElementsByKey.values());
    }

    public List<MobileScreenSnapshot> getVisitedScreens() {
        return visitedScreens;
    }

    public int getSkippedDuplicateScreenCount() {
        return skippedDuplicateScreens;
    }

    private void crawlAndMerge(String stepLabel) {
        MobileScreenSnapshot snapshot = elementCrawler.crawlCurrentScreen();
        String fingerprint = computeStateFingerprint(snapshot.getNativeElements());

        if (stateDeduplicationEnabled && !visitedStateFingerprints.add(fingerprint)) {
            skippedDuplicateScreens++;
            log.debug("Skipping merge for '{}' -- screen state already visited (fingerprint match)", stepLabel);
            return;
        }

        visitedScreens.add(snapshot);
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            String key = info.mergeKey();
            MobileElementInfo existing = mergedElementsByKey.get(key);
            if (existing != null) {
                existing.addVisibleAfterStep(stepLabel);
            } else {
                info.addVisibleAfterStep(stepLabel);
                mergedElementsByKey.put(key, info);
            }
        }
    }

    /**
     * Builds a stable structural fingerprint for a screen: tag + resource-id/content-desc
     * (or accessibility id) for every element that has one, in document order. Deliberately
     * ignores free-text values (labels, prices, timestamps) since those are the most common
     * source of false "new state" detections -- mirrors the state-dedup convention already
     * used by the desktop SDK's {@code DataDrivenCrawler.setStateDeduplication()}.
     */
    private String computeStateFingerprint(List<MobileElementInfo> elements) {
        StringBuilder sb = new StringBuilder();
        for (MobileElementInfo info : elements) {
            String identity = structuralIdentity(info);
            if (identity == null) {
                continue; // pure-text/decorative nodes don't contribute to the structural shape
            }
            sb.append(info.getTagOrClassName()).append('|').append(identity).append(';');
        }
        return sb.toString();
    }

    private String structuralIdentity(MobileElementInfo info) {
        for (MobileLocatorCandidate candidate : info.getCandidates()) {
            if (candidate.getStrategy() == MobileLocatorCandidate.Strategy.RESOURCE_ID
                    || candidate.getStrategy() == MobileLocatorCandidate.Strategy.ACCESSIBILITY_ID) {
                return candidate.getStrategy() + "=" + candidate.getValue();
            }
        }
        return null;
    }

    private void executeStep(MobileCrawlerStep step) {
        By by = resolveBy(step);
        WebElement element = driver.findElement(by);
        switch (step.getAction()) {
            case TAP:
                element.click();
                break;
            case TYPE:
                element.clear();
                element.sendKeys(step.getInputValue());
                break;
            default:
                throw new IllegalArgumentException("Unsupported crawler step action: " + step.getAction());
        }
        waitBriefly();
    }

    private By resolveBy(MobileCrawlerStep step) {
        switch (step.getBy()) {
            case RESOURCE_ID:
                return AppiumBy.id(step.getSelector());
            case ACCESSIBILITY_ID:
                return AppiumBy.accessibilityId(step.getSelector());
            case XPATH:
                return By.xpath(step.getSelector());
            case TEXT:
                return By.xpath("//*[normalize-space(@text)='" + step.getSelector()
                        + "' or normalize-space(@label)='" + step.getSelector() + "']");
            default:
                throw new IllegalArgumentException("Unsupported crawler step locator type: " + step.getBy());
        }
    }

    private void waitBriefly() {
        try {
            TimeUnit.MILLISECONDS.sleep(DEFAULT_SETTLE_TIMEOUT.toMillis() / 20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
