package com.test.automation.sdk.accessibility;

import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Pluggable accessibility scanning engine contract.
 *
 * <p>The SDK's public accessibility API (this interface, {@link AccessibilityFinding})
 * is deliberately independent of any single vendor engine. {@link AxeCoreEngine} (the
 * default, wrapping {@link AccessibilityChecker}'s axe-core-based Layer 1 scanning) and
 * {@code com.test.automation.sdk.mobile.accessibility.NativeMobileEngine} (wrapping the
 * vendor-free {@code NativeAccessibilityChecker} for native mobile screens) are the two
 * implementations shipped today. A consumer or a future SDK version can add another
 * implementation (e.g. a second web engine, for cross-validation) without changing any
 * code that depends on this interface.</p>
 *
 * <p>This is an additive facade over the existing static {@link AccessibilityChecker} /
 * {@code NativeAccessibilityChecker} APIs — both remain fully supported and unchanged
 * for direct use. Use {@code AccessibilityEngine} when you want engine-agnostic,
 * swappable scanning (e.g. for a custom reporter, baseline comparison, or dashboard
 * that should not care which engine produced a given {@link AccessibilityFinding}).</p>
 *
 * @see AxeCoreEngine
 * @see AccessibilityFinding
 */
public interface AccessibilityEngine {

    /**
     * Runs a scan and returns the findings produced by this engine, in the normalized
     * {@link AccessibilityFinding} shape.
     *
     * @param driver   the active WebDriver/AppiumDriver session
     * @param pageName human-readable label for the page/screen under test, used in
     *                 artifacts and reports
     * @param tags     optional engine-specific scope filter (e.g. axe-core WCAG tags
     *                 such as {@code "wcag2a"}, {@code "wcag21aa"}); engines that don't
     *                 use tag-based scoping (e.g. native mobile) ignore this parameter
     * @return findings from this scan; empty (never {@code null}) when the page passes
     */
    List<AccessibilityFinding> scan(WebDriver driver, String pageName, String... tags);

    /** Short, stable engine identifier, e.g. {@code "axe-core"} or {@code "NativeMobile"}. */
    String getEngineName();

    /** Engine version string, e.g. {@code "4.10.1"}, or {@code "unknown"} if not resolvable. */
    String getEngineVersion();
}
