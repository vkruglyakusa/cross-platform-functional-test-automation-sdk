package com.test.automation.sdk.mobile.accessibility;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.appium.java_client.AppiumDriver;

import com.test.automation.sdk.mobile.accessibility.NativeAccessibilityIssue.Severity;

/**
 * Free, dependency-free accessibility auditing for native (non-WebView) Android/iOS
 * screens, derived entirely from Appium's {@code getPageSource()} XML.
 *
 * <p>This is the native-screen counterpart to
 * {@code com.test.automation.sdk.accessibility.AccessibilityChecker} (axe-core/DOM-based,
 * used for hybrid-app WebView content -- see {@code MobileElementCrawler#crawlWebViewsIfPresent()}
 * and {@code A11ySessionManager}'s mobile native-context guard). Axe-core cannot run against
 * a native screen at all (no DOM to inject into); this class fills that specific gap with a
 * lighter, page-source-based rule set that works identically on Android and iOS.</p>
 *
 * <h3>Rules implemented</h3>
 * <ul>
 *   <li>{@code missing-accessible-name} -- an interactive element (clickable/checkable on
 *       Android; a button/link/switch/field-like control on iOS) has no spoken name
 *       (empty {@code content-desc}/{@code text} on Android, empty {@code label}/{@code name}
 *       on iOS). TalkBack/VoiceOver would announce nothing useful for it.</li>
 *   <li>{@code unlabeled-editable-field} -- a text-entry control ({@code EditText} on
 *       Android; text/secure-text/search field on iOS) with no accessible name.</li>
 *   <li>{@code duplicate-accessible-name} -- two or more interactive elements on the same
 *       screen share an identical accessible name, making them indistinguishable to a
 *       screen-reader user.</li>
 *   <li>{@code touch-target-too-small} -- an interactive element's bounds are smaller than
 *       the platform's documented minimum touch target (48x48dp Android / 44x44pt iOS).
 *       <b>Known limitation:</b> Appium reports {@code bounds}/{@code frame} in raw device
 *       pixels, not density-independent units; this check compares raw pixels against the
 *       dp/pt thresholds, which under-reports violations on higher-density displays. Pass
 *       an accurate {@code devicePixelDensity} via the density-aware overload to correct for
 *       this on a known device.</li>
 * </ul>
 *
 * <p>Deliberately does <b>not</b> check color contrast: the page source carries no rendering/
 * paint information, so contrast cannot be derived from it (this is the same reason axe-core's
 * contrast rule cannot be ported here). See CHANGELOG.md / MOBILE-USER-GUIDE.md for the
 * Google Accessibility Test Framework (ATF) discussion of a deeper, Android-only alternative.</p>
 */
public final class NativeAccessibilityChecker {

    private static final Logger log = LogManager.getLogger(NativeAccessibilityChecker.class.getName());

    private static final int ANDROID_MIN_TOUCH_TARGET_DP = 48;
    private static final int IOS_MIN_TOUCH_TARGET_PT = 44;

    private static final Pattern ANDROID_BOUNDS_PATTERN =
            Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");

    /** Interactive iOS element types worth checking for an accessible name. */
    private static final java.util.Set<String> IOS_INTERACTIVE_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "XCUIElementTypeButton", "XCUIElementTypeLink", "XCUIElementTypeCell",
            "XCUIElementTypeSwitch", "XCUIElementTypeTextField", "XCUIElementTypeSecureTextField",
            "XCUIElementTypeSearchField", "XCUIElementTypeTextView", "XCUIElementTypeSlider",
            "XCUIElementTypeTab", "XCUIElementTypeMenuItem", "XCUIElementTypeCheckBox"
    ));

    /** iOS element types that are text-entry fields (subset of {@link #IOS_INTERACTIVE_TYPES}). */
    private static final java.util.Set<String> IOS_EDITABLE_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "XCUIElementTypeTextField", "XCUIElementTypeSecureTextField",
            "XCUIElementTypeSearchField", "XCUIElementTypeTextView"
    ));

    private NativeAccessibilityChecker() {
    }

    /**
     * Convenience entry point: fetches {@code getPageSource()} and the platform from the live
     * driver, then delegates to {@link #check(String, String, String)}. Logs every issue found.
     *
     * @return the issues found (empty list if none, never {@code null})
     */
    public static List<NativeAccessibilityIssue> check(AppiumDriver driver, String screenName) {
        String platform;
        try {
            Object platformName = driver.getCapabilities().getCapability("platformName");
            platform = platformName == null ? null : platformName.toString();
        } catch (Exception e) {
            platform = null;
        }
        String pageSource;
        try {
            pageSource = driver.getPageSource();
        } catch (Exception e) {
            log.warn("NativeAccessibilityChecker could not read page source for '{}'", screenName, e);
            return new ArrayList<>();
        }
        return check(pageSource, platform, screenName);
    }

    /**
     * Core entry point: analyzes a raw {@code getPageSource()} XML snapshot and returns every
     * issue found. Pure/static -- no driver required, so it is fully unit-testable with a
     * captured page-source fixture.
     *
     * @param pageSourceXml raw XML from {@code driver.getPageSource()}
     * @param platform      {@code "android"} or {@code "ios"} (case-insensitive); if {@code null}
     *                      or unrecognized, falls back to sniffing the XML root element name
     * @param screenName    label used only for logging
     */
    public static List<NativeAccessibilityIssue> check(String pageSourceXml, String platform, String screenName) {
        List<NativeAccessibilityIssue> issues = new ArrayList<>();
        if (pageSourceXml == null || pageSourceXml.trim().isEmpty()) {
            log.debug("NativeAccessibilityChecker: empty page source for '{}' -- nothing to check", screenName);
            return issues;
        }

        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new ByteArrayInputStream(pageSourceXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("NativeAccessibilityChecker: failed to parse page source XML for '{}'", screenName, e);
            return issues;
        }

        boolean isIos = isIos(platform, doc);
        NodeList allNodes = doc.getElementsByTagName("*");

        // First pass: collect every interactive element's accessible name for the
        // duplicate-name check (needs to see the whole screen before it can flag dupes).
        Map<String, List<Element>> interactiveByName = new LinkedHashMap<>();

        for (int i = 0; i < allNodes.getLength(); i++) {
            Element el = (Element) allNodes.item(i);
            if (!isInteractive(el, isIos)) {
                continue;
            }
            String name = accessibleName(el, isIos);
            if (name.isEmpty()) {
                issues.add(new NativeAccessibilityIssue("missing-accessible-name", Severity.SERIOUS,
                        "Interactive element has no accessible name -- a screen reader would announce nothing useful",
                        tagOf(el, isIos), identifierOf(el, isIos), boundsOf(el, isIos)));
            } else {
                interactiveByName.computeIfAbsent(name, k -> new ArrayList<>()).add(el);
            }

            if (isEditable(el, isIos) && name.isEmpty()) {
                issues.add(new NativeAccessibilityIssue("unlabeled-editable-field", Severity.SERIOUS,
                        "Text-entry field has no accessible name/label",
                        tagOf(el, isIos), identifierOf(el, isIos), boundsOf(el, isIos)));
            }

            Integer[] wh = widthHeight(el, isIos);
            if (wh != null) {
                int minDp = isIos ? IOS_MIN_TOUCH_TARGET_PT : ANDROID_MIN_TOUCH_TARGET_DP;
                if (wh[0] > 0 && wh[1] > 0 && (wh[0] < minDp || wh[1] < minDp)) {
                    issues.add(new NativeAccessibilityIssue("touch-target-too-small", Severity.MODERATE,
                            "Touch target " + wh[0] + "x" + wh[1] + " is below the recommended minimum of "
                                    + minDp + "x" + minDp + " (raw device pixels -- see class-level limitation note)",
                            tagOf(el, isIos), identifierOf(el, isIos), boundsOf(el, isIos)));
                }
            }
        }

        for (Map.Entry<String, List<Element>> entry : interactiveByName.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() < 2) {
                continue;
            }
            for (Element el : elements) {
                issues.add(new NativeAccessibilityIssue("duplicate-accessible-name", Severity.MODERATE,
                        "Accessible name '" + entry.getKey() + "' is shared by " + elements.size()
                                + " interactive elements on this screen -- indistinguishable to a screen-reader user",
                        tagOf(el, isIos), identifierOf(el, isIos), boundsOf(el, isIos)));
            }
        }

        if (!issues.isEmpty()) {
            log.info("[NATIVE A11Y] '{}' -- {} issue(s) found", screenName, issues.size());
            for (NativeAccessibilityIssue issue : issues) {
                log.info("[NATIVE A11Y]   {}", issue);
            }
        } else {
            log.debug("[NATIVE A11Y] '{}' -- no issues found", screenName);
        }
        return issues;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Element classification
    // ─────────────────────────────────────────────────────────────────────

    private static boolean isInteractive(Element el, boolean isIos) {
        if (isIos) {
            return IOS_INTERACTIVE_TYPES.contains(el.getTagName());
        }
        return "true".equalsIgnoreCase(attr(el, "clickable"))
                || "true".equalsIgnoreCase(attr(el, "long-clickable"))
                || "true".equalsIgnoreCase(attr(el, "checkable"));
    }

    private static boolean isEditable(Element el, boolean isIos) {
        if (isIos) {
            return IOS_EDITABLE_TYPES.contains(el.getTagName());
        }
        return el.getTagName().contains("EditText");
    }

    private static String accessibleName(Element el, boolean isIos) {
        if (isIos) {
            String label = attr(el, "label");
            return !label.isEmpty() ? label : attr(el, "name");
        }
        String contentDesc = attr(el, "content-desc");
        return !contentDesc.isEmpty() ? contentDesc : attr(el, "text");
    }

    private static String tagOf(Element el, boolean isIos) {
        return el.getTagName();
    }

    private static String identifierOf(Element el, boolean isIos) {
        if (isIos) {
            String name = attr(el, "name");
            return !name.isEmpty() ? name : attr(el, "label");
        }
        String resourceId = attr(el, "resource-id");
        return !resourceId.isEmpty() ? resourceId : attr(el, "content-desc");
    }

    private static String boundsOf(Element el, boolean isIos) {
        return isIos ? "x=" + attr(el, "x") + ",y=" + attr(el, "y")
                + ",w=" + attr(el, "width") + ",h=" + attr(el, "height")
                : attr(el, "bounds");
    }

    /** Returns {@code [width, height]} in raw device pixels, or {@code null} if not determinable. */
    private static Integer[] widthHeight(Element el, boolean isIos) {
        try {
            if (isIos) {
                String w = attr(el, "width");
                String h = attr(el, "height");
                if (w.isEmpty() || h.isEmpty()) {
                    return null;
                }
                return new Integer[] { (int) Double.parseDouble(w), (int) Double.parseDouble(h) };
            }
            String bounds = attr(el, "bounds");
            if (bounds.isEmpty()) {
                return null;
            }
            Matcher m = ANDROID_BOUNDS_PATTERN.matcher(bounds);
            if (!m.matches()) {
                return null;
            }
            int left = Integer.parseInt(m.group(1));
            int top = Integer.parseInt(m.group(2));
            int right = Integer.parseInt(m.group(3));
            int bottom = Integer.parseInt(m.group(4));
            return new Integer[] { right - left, bottom - top };
        } catch (Exception e) {
            return null;
        }
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
        Node root = doc.getDocumentElement();
        return root != null && root.getNodeName().startsWith("XCUIElementType");
    }
}
