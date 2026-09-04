package com.test.automation.sdk.mobile.crawler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.remote.SupportsContextSwitching;

import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate.Marker;
import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate.Strategy;
import com.test.automation.sdk.mobile.crawler.MobileLocatorCandidate.Verification;
import com.test.automation.sdk.utility.ElementCrawler;

/**
 * Mobile analogue of the desktop SDK's {@code ElementCrawler}: crawls the current
 * native screen (Android/iOS) via a single {@code getPageSource()} snapshot, computes
 * locator-candidate uniqueness entirely in memory (no per-candidate remote calls),
 * and detects/delegates any WebView content to the desktop crawler's DOM logic.
 *
 * See docs/proposals/mobile-automation-strategy.md section 8a for the full design
 * rationale, in particular the remote-call-economics constraint that shaped this
 * class: a screen crawl costs ~2 remote calls (page source + screenshot), not one
 * remote call per candidate locator per element.
 */
public class MobileElementCrawler {

    private static final Logger log = LogManager.getLogger(MobileElementCrawler.class.getName());

    /** Screen-stability polling (see strategy doc "Screen-stability wait before capture"). */
    private static final int STABILITY_MAX_ATTEMPTS = 5;
    private static final long STABILITY_POLL_MS = 300L;

    /**
     * Known auto-generated / list-recycled identifier patterns -- the mobile equivalent
     * of the desktop crawler's {@code mat-input-\d+}/{@code cdk-*} rejection list.
     * Applied to resource-id / accessibility-id values regardless of the current
     * match count, because these are untrustworthy across app builds/runs even when
     * unique in a single snapshot.
     */
    private static final List<Pattern> DYNAMIC_ID_PATTERNS = java.util.Arrays.asList(
            Pattern.compile(".*[:/](item|row|cell|list_item|listitem)[_-]?\\d+$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*/\\d+$"),
            Pattern.compile(".*/[0-9a-fA-F]{8,}$")
    );

    private final AppiumDriver driver;

    public MobileElementCrawler(AppiumDriver driver) {
        this.driver = driver;
    }

    /**
     * Crawls the current screen: waits for stability, captures page source + screenshot
     * (the only two remote calls needed for the native portion), delegates any active
     * WebView context to the desktop {@link ElementCrawler}, then parses/analyzes the
     * native XML tree entirely in memory.
     */
    public MobileScreenSnapshot crawlCurrentScreen() {
        String platform = resolvePlatform();
        String pageSource = waitForStablePageSource();
        byte[] screenshot;
        try {
            screenshot = driver.getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.warn("Failed to capture screenshot during mobile crawl", e);
            screenshot = new byte[0];
        }

        Map<String, List<ElementCrawler.ElementInfo>> webViewElements = crawlWebViewsIfPresent();
        List<MobileElementInfo> nativeElements = analyzePageSource(pageSource, platform);

        return new MobileScreenSnapshot(platform, pageSource, screenshot, nativeElements, webViewElements);
    }

    /**
     * Spends exactly one additional remote round trip to confirm a candidate that
     * could not be statically verified (e.g. a compound {@code -android uiautomator}
     * expression relying on runtime-only predicates). Reserved for the rare edge case
     * flagged in the strategy doc -- most candidates should never need this.
     */
    public void verifyCandidateRemotely(MobileLocatorCandidate candidate, By by) {
        try {
            int count = driver.findElements(by).size();
            candidate.setMatchCount(count);
            candidate.setVerification(Verification.REMOTE);
            candidate.setMarker(count == 1 ? Marker.UNIQUE : Marker.NOT_UNIQUE);
        } catch (Exception e) {
            log.warn("Remote verification failed for candidate {}", candidate, e);
        }
    }

    // ------------------------------------------------------------------
    // Static, driver-free core -- unit-testable without a real Appium session.
    // ------------------------------------------------------------------

    /**
     * Parses a raw {@code getPageSource()} XML snapshot and returns every interesting
     * element with its full locator-candidate ladder, uniqueness pre-computed against
     * this single in-memory tree (zero remote calls).
     */
    public static List<MobileElementInfo> analyzePageSource(String pageSourceXml, String platform) {
        List<MobileElementInfo> results = new ArrayList<>();
        if (pageSourceXml == null || pageSourceXml.trim().isEmpty()) {
            return results;
        }

        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Disable external entity resolution -- page source is device-generated but
            // treat it as untrusted input regardless (XXE hardening).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new ByteArrayInputStream(pageSourceXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Failed to parse mobile page source XML", e);
            return results;
        }

        boolean isIos = isIos(platform, doc);
        NodeList allNodes = doc.getElementsByTagName("*");

        // Single-pass frequency count per (attribute, value) -- this is what makes
        // uniqueness "free": counting is O(n) total instead of one query per candidate.
        // Also tracks a compound key (tag+resource-id+text+content-desc) so that when
        // every single attribute is ambiguous alone, a combined XPath predicate can
        // still resolve to exactly one node -- mirrors the "combine attributes for
        // specificity" rule from the desktop locator-strategy skill, and the compound-
        // locator pattern used by tools like Appium MCP's generate-all-locators.
        Map<String, Integer> resourceIdFreq = new HashMap<>();
        Map<String, Integer> accessibilityIdFreq = new HashMap<>();
        Map<String, Integer> textFreq = new HashMap<>();
        Map<String, Integer> compoundFreq = new HashMap<>();

        for (int i = 0; i < allNodes.getLength(); i++) {
            Element el = (Element) allNodes.item(i);
            String resourceId = attr(el, "resource-id");
            String accessibilityId = isIos ? attr(el, "name") : attr(el, "content-desc");
            String text = isIos ? attr(el, "label") : attr(el, "text");
            increment(resourceIdFreq, resourceId);
            increment(accessibilityIdFreq, accessibilityId);
            increment(textFreq, text);
            increment(compoundFreq, compoundKey(el.getTagName(), resourceId, accessibilityId, text));
        }

        for (int i = 0; i < allNodes.getLength(); i++) {
            Element el = (Element) allNodes.item(i);
            MobileElementInfo info = buildElementInfo(el, isIos, resourceIdFreq, accessibilityIdFreq, textFreq, compoundFreq);
            if (info != null) {
                results.add(info);
            }
        }
        return results;
    }

    /** Combines tag + whichever single attributes are non-empty into one compound-uniqueness key. */
    private static String compoundKey(String tag, String resourceId, String accessibilityId, String text) {
        return tag + "|" + resourceId + "|" + accessibilityId + "|" + text;
    }

    private static MobileElementInfo buildElementInfo(Element el, boolean isIos,
            Map<String, Integer> resourceIdFreq, Map<String, Integer> accessibilityIdFreq,
            Map<String, Integer> textFreq, Map<String, Integer> compoundFreq) {

        String resourceId = attr(el, "resource-id");
        String accessibilityId = isIos ? attr(el, "name") : attr(el, "content-desc");
        String text = isIos ? attr(el, "label") : attr(el, "text");
        String tag = el.getTagName();
        boolean clickable = isIos || "true".equalsIgnoreCase(attr(el, "clickable"));

        boolean hasAnyIdentity = !resourceId.isEmpty() || !accessibilityId.isEmpty() || !text.isEmpty();
        if (!hasAnyIdentity) {
            return null; // not interesting -- pure layout container with no identity at all
        }

        MobileElementInfo info = new MobileElementInfo(isIos ? "ios" : "android", tag, text);

        boolean anySingleAttributeUnique = false;
        if (!resourceId.isEmpty()) {
            MobileLocatorCandidate c = new MobileLocatorCandidate(Strategy.RESOURCE_ID, resourceId);
            markCandidate(c, resourceIdFreq.get(resourceId));
            anySingleAttributeUnique |= c.isUnique();
            info.addCandidate(c);
        }
        if (!accessibilityId.isEmpty()) {
            Strategy strategy = isIos ? Strategy.ACCESSIBILITY_ID : Strategy.ACCESSIBILITY_ID;
            MobileLocatorCandidate c = new MobileLocatorCandidate(strategy, accessibilityId);
            markCandidate(c, accessibilityIdFreq.get(accessibilityId));
            anySingleAttributeUnique |= c.isUnique();
            info.addCandidate(c);
        }
        if (!text.isEmpty()) {
            Strategy strategy = isIos ? Strategy.PREDICATE_STRING : Strategy.TEXT;
            String value = isIos ? "label == '" + text.replace("'", "\\'") + "'" : text;
            MobileLocatorCandidate c = new MobileLocatorCandidate(strategy, value);
            markCandidate(c, textFreq.get(text));
            anySingleAttributeUnique |= c.isUnique();
            info.addCandidate(c);
        }

        // Compound fallback: only worth adding when every single attribute above was
        // ambiguous/dynamic on its own -- combining ambiguous attributes into one XPath
        // predicate can still be exactly-1 even though none of the parts are alone.
        if (!anySingleAttributeUnique && (!resourceId.isEmpty() || !accessibilityId.isEmpty() || !text.isEmpty())) {
            MobileLocatorCandidate compound = buildCompoundCandidate(tag, resourceId, accessibilityId, text,
                    isIos, compoundFreq);
            if (compound != null) {
                info.addCandidate(compound);
            }
        }

        // Structural XPath fallback -- always last, always STRUCTURAL, never eligible
        // to be the "best unique" candidate used in a generated page object.
        MobileLocatorCandidate structural = new MobileLocatorCandidate(Strategy.XPATH, "//" + tag);
        structural.setMarker(Marker.STRUCTURAL);
        structural.setMatchCount(-1);
        info.addCandidate(structural);

        info.setSuggestedFieldName(deriveFieldName(info, clickable));
        return info;
    }

    /**
     * Builds one XPath predicate combining every non-empty single attribute for this
     * element (tag + resource-id/content-desc-or-name + text-or-label), and marks it
     * UNIQUE/NOT_UNIQUE against the precomputed compound frequency map. Mirrors the
     * "combine attributes for specificity" rule from the desktop locator-strategy
     * skill and the compound-locator approach used by Appium MCP's
     * {@code generate-all-locators} (see docs/proposals/mobile-automation-strategy.md
     * section 8a and this feature's web-research citations in the CHANGELOG).
     */
    private static MobileLocatorCandidate buildCompoundCandidate(String tag, String resourceId,
            String accessibilityId, String text, boolean isIos, Map<String, Integer> compoundFreq) {
        StringBuilder predicate = new StringBuilder();
        if (!resourceId.isEmpty()) {
            appendAnd(predicate, "@resource-id='" + xpathEscape(resourceId) + "'");
        }
        String accessAttr = isIos ? "@name" : "@content-desc";
        if (!accessibilityId.isEmpty()) {
            appendAnd(predicate, accessAttr + "='" + xpathEscape(accessibilityId) + "'");
        }
        String textAttr = isIos ? "@label" : "@text";
        if (!text.isEmpty()) {
            appendAnd(predicate, textAttr + "='" + xpathEscape(text) + "'");
        }
        if (predicate.length() == 0) {
            return null; // nothing to combine
        }
        String xpath = "//" + tag + "[" + predicate + "]";
        MobileLocatorCandidate candidate = new MobileLocatorCandidate(Strategy.XPATH, xpath);
        String key = compoundKey(tag, resourceId, accessibilityId, text);
        int count = compoundFreq.getOrDefault(key, 0);
        candidate.setMatchCount(count);
        candidate.setVerification(Verification.STATIC);
        candidate.setMarker(count == 1 ? Marker.UNIQUE : Marker.NOT_UNIQUE);
        return candidate;
    }

    private static void appendAnd(StringBuilder sb, String condition) {
        if (sb.length() > 0) {
            sb.append(" and ");
        }
        sb.append(condition);
    }

    private static String xpathEscape(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    private static void markCandidate(MobileLocatorCandidate candidate, Integer count) {
        int n = count == null ? 0 : count;
        candidate.setMatchCount(n);
        candidate.setVerification(Verification.STATIC);
        if (isDynamicPattern(candidate.getValue())) {
            candidate.setMarker(Marker.DYNAMIC);
        } else if (n == 1) {
            candidate.setMarker(Marker.UNIQUE);
        } else {
            candidate.setMarker(Marker.NOT_UNIQUE);
        }
    }

    private static boolean isDynamicPattern(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (Pattern p : DYNAMIC_ID_PATTERNS) {
            if (p.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String deriveFieldName(MobileElementInfo info, boolean clickable) {
        String base = firstNonEmpty(
                info.getBestUniqueCandidate() != null ? info.getBestUniqueCandidate().getValue() : null,
                info.getText());
        if (base == null || base.isEmpty()) {
            base = info.getTagOrClassName();
        }
        // Strip Android resource-id package prefix ("com.app:id/submitButton" -> "submitButton").
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash < base.length() - 1) {
            base = base.substring(slash + 1);
        }
        String[] words = base.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(Character.toLowerCase(w.charAt(0))).append(w.substring(1));
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
            }
        }
        if (sb.length() == 0) {
            sb.append("element");
        }
        if (clickable && sb.toString().toLowerCase().indexOf("button") < 0) {
            sb.append("Element");
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static void increment(Map<String, Integer> freq, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        freq.merge(value, 1, Integer::sum);
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute(name);
        return v == null ? "" : v.trim();
    }

    private static boolean isIos(String platform, Document doc) {
        if (platform != null) {
            String p = platform.toLowerCase();
            if (p.contains("ios") || p.contains("iphone") || p.contains("ipad")) {
                return true;
            }
            if (p.contains("android")) {
                return false;
            }
        }
        // Fallback: iOS page source root elements are named AppiumAUT/XCUIElementType*.
        Node root = doc.getDocumentElement();
        return root != null && root.getNodeName().startsWith("XCUIElementType")
                || (root != null && "AppiumAUT".equalsIgnoreCase(root.getNodeName()));
    }

    // ------------------------------------------------------------------
    // Driver-dependent helpers.
    // ------------------------------------------------------------------

    private String resolvePlatform() {
        try {
            Object platformName = driver.getCapabilities().getCapability("platformName");
            return platformName == null ? null : platformName.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Polls {@code getPageSource()} until two consecutive reads match, or gives up after a bounded number of attempts. */
    private String waitForStablePageSource() {
        String previous = null;
        for (int attempt = 0; attempt < STABILITY_MAX_ATTEMPTS; attempt++) {
            String current;
            try {
                current = driver.getPageSource();
            } catch (Exception e) {
                log.warn("getPageSource() failed during stability wait (attempt {})", attempt + 1, e);
                current = previous == null ? "" : previous;
            }
            if (current != null && current.equals(previous)) {
                return current;
            }
            previous = current;
            if (attempt < STABILITY_MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(STABILITY_POLL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("Screen did not stabilize after {} attempts -- crawling latest snapshot anyway", STABILITY_MAX_ATTEMPTS);
        return previous == null ? "" : previous;
    }

    /**
     * Detects any active WebView context and, if found, delegates that portion of the
     * screen to the desktop SDK's {@link ElementCrawler} (which already does CDP-free
     * DOM crawling) instead of reinventing DOM crawling here. Always restores the
     * NATIVE_APP context before returning so the native XML-tree pass isn't disrupted.
     */
    private Map<String, List<ElementCrawler.ElementInfo>> crawlWebViewsIfPresent() {
        Map<String, List<ElementCrawler.ElementInfo>> result = new HashMap<>();
        if (!(driver instanceof SupportsContextSwitching)) {
            log.debug("Driver does not support context switching -- skipping WebView detection");
            return result;
        }
        SupportsContextSwitching contextDriver = (SupportsContextSwitching) driver;
        Set<String> contexts;
        try {
            contexts = contextDriver.getContextHandles();
        } catch (Exception e) {
            log.debug("Unable to query context handles (context switching may be unsupported here)", e);
            return result;
        }
        for (String context : contexts) {
            if (context == null || !context.startsWith("WEBVIEW")) {
                continue;
            }
            try {
                contextDriver.context(context);
                List<ElementCrawler.ElementInfo> elements = new ElementCrawler((WebDriver) driver).crawlCurrentPage();
                result.put(context, elements);
                log.info("Delegated WebView context [{}] to desktop ElementCrawler -- {} elements found",
                        context, elements.size());
            } catch (Exception e) {
                log.warn("Failed to crawl WebView context [{}]", context, e);
            } finally {
                try {
                    contextDriver.context("NATIVE_APP");
                } catch (Exception e) {
                    log.warn("Failed to restore NATIVE_APP context after WebView crawl", e);
                }
            }
        }
        return result;
    }
}
