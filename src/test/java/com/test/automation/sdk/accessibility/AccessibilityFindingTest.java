package com.test.automation.sdk.accessibility;

import com.deque.html.axecore.results.Rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AccessibilityFinding} -- the normalized finding model and its
 * factory methods that map axe-core {@link Rule} results and custom-layer
 * {@link AccessibilityChecker.InteractionIssue} results into a single shared shape.
 */
@DisplayName("AccessibilityFinding - normalized model mapping")
class AccessibilityFindingTest {

    private static Rule axeRule(String id, String impact, List<String> tags) {
        Rule rule = new Rule();
        rule.setId(id);
        rule.setImpact(impact);
        rule.setDescription("Elements must have sufficient color contrast");
        rule.setHelp("Ensure contrast ratio meets WCAG");
        rule.setHelpUrl("https://dequeuniversity.com/rules/axe/4.10/color-contrast");
        rule.setTags(tags);
        rule.setNodes(Collections.emptyList());
        return rule;
    }

    @Test
    @DisplayName("extracts WCAG success criterion and level from axe-core tags")
    void extractsWcagCriterionAndLevelFromTags() {
        assertEquals("1.4.3", AccessibilityFinding.extractWcagCriterion(Arrays.asList("wcag2aa", "wcag143", "cat.color")));
        assertEquals("AA", AccessibilityFinding.extractWcagLevel(Arrays.asList("wcag2aa", "wcag143")));

        assertEquals("1.4.10", AccessibilityFinding.extractWcagCriterion(Arrays.asList("wcag21aa", "wcag1410")));
        assertEquals("AAA", AccessibilityFinding.extractWcagLevel(Collections.singletonList("wcag22aaa")));

        assertNull(AccessibilityFinding.extractWcagCriterion(Collections.singletonList("cat.color")));
        assertNull(AccessibilityFinding.extractWcagLevel(Collections.emptyList()));
    }

    @Test
    @DisplayName("fromAxeRule produces one finding per affected node, preserving rule/impact/WCAG/description")
    void fromAxeRuleProducesOneFindingPerNode() {
        Rule rule = axeRule("color-contrast", "serious", Arrays.asList("wcag2aa", "wcag143"));

        List<AccessibilityFinding> findings = AccessibilityFinding.fromAxeRule(
                rule, "https://example.com/login", "LoginTest", "4.10.1", false);

        assertEquals(1, findings.size());
        AccessibilityFinding f = findings.get(0);
        assertEquals("color-contrast", f.getRuleId());
        assertEquals("1.4.3", f.getWcagCriterion());
        assertEquals("AA", f.getWcagLevel());
        assertEquals("serious", f.getSeverity());
        assertEquals(AccessibilityFinding.CONFIDENCE_VIOLATION, f.getConfidence());
        assertEquals("https://example.com/login", f.getPageUrl());
        assertEquals("LoginTest", f.getTestName());
        assertEquals("axe-core", f.getEngine());
        assertEquals("4.10.1", f.getEngineVersion());
        assertNotNull(f.getTimestamp());
    }

    @Test
    @DisplayName("fromAxeRule marks manual-review findings from axe-core's incomplete bucket")
    void fromAxeRuleMarksManualReviewForIncomplete() {
        Rule rule = axeRule("aria-hidden-focus", "moderate", Collections.singletonList("wcag2a"));

        List<AccessibilityFinding> findings = AccessibilityFinding.fromAxeRule(
                rule, null, null, "4.10.1", true);

        assertEquals(AccessibilityFinding.CONFIDENCE_MANUAL_REVIEW, findings.get(0).getConfidence());
    }

    @Test
    @DisplayName("fromAxeRule never drops a violation when the rule has no attached nodes")
    void fromAxeRuleNeverDropsRuleWithNoNodes() {
        Rule rule = new Rule();
        rule.setId("orphan-rule");
        rule.setImpact("critical");
        rule.setDescription("desc");
        rule.setTags(Collections.emptyList());
        rule.setNodes(Collections.emptyList());

        List<AccessibilityFinding> findings = AccessibilityFinding.fromAxeRule(rule, null, null, null, false);

        assertEquals(1, findings.size());
        assertEquals("orphan-rule", findings.get(0).getRuleId());
        assertNull(findings.get(0).getAffectedElementSelector());
    }

    @Test
    @DisplayName("fromInteractionIssue maps custom-layer issues, extracting WCAG SC from the free-text ref")
    void fromInteractionIssueMapsCustomLayerIssue() {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "touch-target-size", "SERIOUS", "WCAG 2.5.5", "Touch target too small",
                "<button>x</button>", "https://example.com/help", false);

        AccessibilityFinding f = AccessibilityFinding.fromInteractionIssue(issue, "Interaction", "https://example.com", "TapTest");

        assertEquals("touch-target-size", f.getRuleId());
        assertEquals("2.5.5", f.getWcagCriterion());
        assertEquals("serious", f.getSeverity());
        assertEquals(AccessibilityFinding.CONFIDENCE_VIOLATION, f.getConfidence());
        assertEquals("Interaction", f.getEngine());
        assertEquals("<button>x</button>", f.getAffectedElementHtml());
    }

    @Test
    @DisplayName("fromInteractionIssue marks manual-review when the issue was flagged needsReview")
    void fromInteractionIssueMarksManualReview() {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "link-text-descriptive", "MODERATE", "WCAG 2.4.4", "Ambiguous link text",
                "<a>click here</a>", null, true);

        AccessibilityFinding f = AccessibilityFinding.fromInteractionIssue(issue, "Interaction", null, null);

        assertEquals(AccessibilityFinding.CONFIDENCE_MANUAL_REVIEW, f.getConfidence());
    }
}
