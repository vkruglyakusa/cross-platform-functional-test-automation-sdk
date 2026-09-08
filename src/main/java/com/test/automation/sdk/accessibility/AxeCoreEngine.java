package com.test.automation.sdk.accessibility;

import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.List;

/**
 * Default {@link AccessibilityEngine} implementation — wraps the existing axe-core-based
 * {@link AccessibilityChecker} (Layer 1 static WCAG scan) so callers can consume its
 * output as normalized {@link AccessibilityFinding}s through the engine-agnostic
 * interface, without any change to {@code AccessibilityChecker}'s own static API.
 *
 * <p>Implementation note: {@link AccessibilityChecker} accumulates findings in a single
 * process-wide list ({@link AccessibilityChecker#getFindings()}). This adapter takes a
 * before/after snapshot around the scan call to return only the findings produced by
 * <em>this</em> call, so it is safe to use even when other scans have already run in
 * the same JVM. As with the rest of {@code AccessibilityChecker}'s in-memory state, this
 * is not safe for truly concurrent scans on multiple threads — matching the SDK's
 * existing single-scan-at-a-time usage pattern.</p>
 */
public final class AxeCoreEngine implements AccessibilityEngine {

    /** Matches {@code com.deque.html.axe-core:selenium} version declared in this SDK's pom.xml. */
    private static final String AXE_CORE_VERSION = "4.10.1";

    @Override
    public List<AccessibilityFinding> scan(WebDriver driver, String pageName, String... tags) {
        int before = AccessibilityChecker.getFindings().size();
        if (tags == null || tags.length == 0) {
            AccessibilityChecker.check(driver, pageName);
        } else {
            AccessibilityChecker.checkWithTags(driver, pageName, tags);
        }
        List<AccessibilityFinding> all = AccessibilityChecker.getFindings();
        return all.size() > before ? all.subList(before, all.size()) : Collections.emptyList();
    }

    @Override
    public String getEngineName() {
        return "axe-core";
    }

    @Override
    public String getEngineVersion() {
        return AXE_CORE_VERSION;
    }
}
