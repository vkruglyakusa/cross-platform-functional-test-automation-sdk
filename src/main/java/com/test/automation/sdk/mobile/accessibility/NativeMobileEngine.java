package com.test.automation.sdk.mobile.accessibility;

import com.test.automation.sdk.accessibility.AccessibilityEngine;
import com.test.automation.sdk.accessibility.AccessibilityFinding;

import io.appium.java_client.AppiumDriver;

import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link AccessibilityEngine} implementation wrapping {@link NativeAccessibilityChecker}
 * for native (non-WebView) Android/iOS screens.
 *
 * <p>Unlike {@code AxeCoreEngine}, this engine has no vendor dependency at all — axe-core
 * requires a DOM, which native mobile screens don't have. All rules are custom-written
 * against Appium's {@code getPageSource()} XML. {@code tags} is accepted for interface
 * compatibility but ignored (native scanning is not tag/WCAG-scoped today).</p>
 */
public final class NativeMobileEngine implements AccessibilityEngine {

    @Override
    public List<AccessibilityFinding> scan(WebDriver driver, String pageName, String... tags) {
        if (!(driver instanceof AppiumDriver)) {
            throw new IllegalArgumentException(
                    "NativeMobileEngine requires an AppiumDriver session; got: "
                            + (driver == null ? "null" : driver.getClass().getName()));
        }
        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check((AppiumDriver) driver, pageName);
        if (issues.isEmpty()) {
            return Collections.emptyList();
        }
        List<AccessibilityFinding> findings = new ArrayList<>(issues.size());
        for (NativeAccessibilityIssue issue : issues) {
            findings.add(toFinding(issue, pageName));
        }
        return findings;
    }

    private static AccessibilityFinding toFinding(NativeAccessibilityIssue issue, String pageName) {
        String element = issue.elementClass
                + (issue.elementIdentifier != null && !issue.elementIdentifier.isEmpty()
                        ? "[" + issue.elementIdentifier + "]" : "");
        return AccessibilityFinding.builder()
                .ruleId(issue.ruleId)
                .severity(issue.severity.name().toLowerCase())
                .confidence(AccessibilityFinding.CONFIDENCE_VIOLATION)
                .description(issue.message)
                .affectedElementSelector(element)
                .affectedElementHtml(issue.boundsOrFrame)
                .pageUrl(pageName)
                .engine("NativeMobile")
                .build();
    }

    @Override
    public String getEngineName() {
        return "NativeMobile";
    }

    @Override
    public String getEngineVersion() {
        return "1.0";
    }
}
