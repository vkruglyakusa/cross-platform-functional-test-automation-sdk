package com.test.automation.sdk.accessibility;

import com.deque.html.axecore.results.Node;
import com.deque.html.axecore.results.Rule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalized, structured representation of a single accessibility finding, regardless
 * of which internal engine produced it (axe-core Layer 1, or one of the custom
 * Interaction / WCAG 2.2 / Structural / Motion layers).
 *
 * <p>This is purely <b>additive</b>: it does not replace {@link com.test.automation.sdk.accessibility.report.A11yReporter},
 * the JSON scan artifacts, or the Excel report — those are unchanged. It exists so
 * callers (custom reporters, baseline/regression tooling, dashboards) can consume a
 * single stable shape instead of parsing engine-specific JSON or Excel rows.</p>
 *
 * <p>Fields deliberately mirror the shape proven useful across several independent
 * OSS accessibility tools researched for this SDK (axe-core's own result JSON, IBM
 * Equal Access's result taxonomy, Pa11y's result object) — see
 * {@code docs/proposals/accessibility-strategy.md}.</p>
 */
public final class AccessibilityFinding {

    /** Confidence gradient — richer than a raw pass/fail, modeled on IBM Equal Access's taxonomy. */
    public static final String CONFIDENCE_VIOLATION     = "violation";
    public static final String CONFIDENCE_MANUAL_REVIEW = "manual_review";

    private final String ruleId;
    private final String wcagCriterion;      // e.g. "1.4.3", or null when not derivable
    private final String wcagLevel;          // "A" | "AA" | "AAA", or null
    private final String severity;           // "minor" | "moderate" | "serious" | "critical" | "unknown"
    private final String confidence;         // CONFIDENCE_VIOLATION | CONFIDENCE_MANUAL_REVIEW
    private final String description;
    private final String affectedElementSelector;
    private final String affectedElementHtml;
    private final String remediationGuidance;
    private final String pageUrl;
    private final String testName;
    private final Instant timestamp;
    private final String screenshotPath;
    private final String engine;             // "axe-core" | "Interaction" | "WCAG2.2" | "Structural" | "Motion" | "NativeMobile"
    private final String engineVersion;

    private AccessibilityFinding(Builder b) {
        this.ruleId = b.ruleId;
        this.wcagCriterion = b.wcagCriterion;
        this.wcagLevel = b.wcagLevel;
        this.severity = b.severity;
        this.confidence = b.confidence;
        this.description = b.description;
        this.affectedElementSelector = b.affectedElementSelector;
        this.affectedElementHtml = b.affectedElementHtml;
        this.remediationGuidance = b.remediationGuidance;
        this.pageUrl = b.pageUrl;
        this.testName = b.testName;
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now();
        this.screenshotPath = b.screenshotPath;
        this.engine = b.engine;
        this.engineVersion = b.engineVersion;
    }

    public String getRuleId() { return ruleId; }
    public String getWcagCriterion() { return wcagCriterion; }
    public String getWcagLevel() { return wcagLevel; }
    public String getSeverity() { return severity; }
    public String getConfidence() { return confidence; }
    public String getDescription() { return description; }
    public String getAffectedElementSelector() { return affectedElementSelector; }
    public String getAffectedElementHtml() { return affectedElementHtml; }
    public String getRemediationGuidance() { return remediationGuidance; }
    public String getPageUrl() { return pageUrl; }
    public String getTestName() { return testName; }
    public Instant getTimestamp() { return timestamp; }
    public String getScreenshotPath() { return screenshotPath; }
    public String getEngine() { return engine; }
    public String getEngineVersion() { return engineVersion; }

    @Override
    public String toString() {
        return "[" + severity + "/" + confidence + "] " + ruleId
                + (wcagCriterion != null ? " (WCAG " + wcagCriterion
                    + (wcagLevel != null ? " " + wcagLevel : "") + ")" : "")
                + ": " + description
                + (affectedElementSelector != null ? " -> " + affectedElementSelector : "");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String ruleId;
        private String wcagCriterion;
        private String wcagLevel;
        private String severity = "unknown";
        private String confidence = CONFIDENCE_VIOLATION;
        private String description;
        private String affectedElementSelector;
        private String affectedElementHtml;
        private String remediationGuidance;
        private String pageUrl;
        private String testName;
        private Instant timestamp;
        private String screenshotPath;
        private String engine;
        private String engineVersion;

        public Builder ruleId(String v) { this.ruleId = v; return this; }
        public Builder wcagCriterion(String v) { this.wcagCriterion = v; return this; }
        public Builder wcagLevel(String v) { this.wcagLevel = v; return this; }
        public Builder severity(String v) { this.severity = v; return this; }
        public Builder confidence(String v) { this.confidence = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder affectedElementSelector(String v) { this.affectedElementSelector = v; return this; }
        public Builder affectedElementHtml(String v) { this.affectedElementHtml = v; return this; }
        public Builder remediationGuidance(String v) { this.remediationGuidance = v; return this; }
        public Builder pageUrl(String v) { this.pageUrl = v; return this; }
        public Builder testName(String v) { this.testName = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public Builder screenshotPath(String v) { this.screenshotPath = v; return this; }
        public Builder engine(String v) { this.engine = v; return this; }
        public Builder engineVersion(String v) { this.engineVersion = v; return this; }

        public AccessibilityFinding build() {
            return new AccessibilityFinding(this);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Factory methods — map engine-specific result types to this normalized model.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds one {@link AccessibilityFinding} per affected DOM node reported by an
     * axe-core {@link Rule} (one node = one concrete offending element). If the rule
     * has no nodes attached (unusual, but defensive), a single finding is still
     * produced so the violation itself is never silently dropped.
     *
     * @param rule          the axe-core violation/incomplete rule
     * @param pageUrl       page URL under test, may be {@code null}
     * @param testName      current test case name, may be {@code null}
     * @param engineVersion axe-core engine version, e.g. from {@code Results.getTestEngine().getVersion()}
     * @param manualReview  {@code true} when the rule came from axe-core's "incomplete" bucket
     */
    public static List<AccessibilityFinding> fromAxeRule(Rule rule, String pageUrl, String testName,
                                                          String engineVersion, boolean manualReview) {
        List<AccessibilityFinding> findings = new ArrayList<>();
        if (rule == null) {
            return findings;
        }
        List<String> tags = rule.getTags() != null ? rule.getTags() : Collections.emptyList();
        String wcagCriterion = extractWcagCriterion(tags);
        String wcagLevel = extractWcagLevel(tags);
        String severity = rule.getImpact() != null ? rule.getImpact().toLowerCase() : "unknown";
        String confidence = manualReview ? CONFIDENCE_MANUAL_REVIEW : CONFIDENCE_VIOLATION;

        List<? extends Node> nodes = rule.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            findings.add(builder()
                    .ruleId(rule.getId())
                    .wcagCriterion(wcagCriterion)
                    .wcagLevel(wcagLevel)
                    .severity(severity)
                    .confidence(confidence)
                    .description(rule.getDescription())
                    .remediationGuidance(rule.getHelpUrl())
                    .pageUrl(pageUrl)
                    .testName(testName)
                    .engine("axe-core")
                    .engineVersion(engineVersion)
                    .build());
            return findings;
        }

        for (Node node : nodes) {
            findings.add(builder()
                    .ruleId(rule.getId())
                    .wcagCriterion(wcagCriterion)
                    .wcagLevel(wcagLevel)
                    .severity(severity)
                    .confidence(confidence)
                    .description(rule.getDescription())
                    .affectedElementSelector(targetToString(node))
                    .affectedElementHtml(node.getHtml())
                    .remediationGuidance(rule.getHelpUrl())
                    .pageUrl(pageUrl)
                    .testName(testName)
                    .engine("axe-core")
                    .engineVersion(engineVersion)
                    .build());
        }
        return findings;
    }

    /**
     * Builds a normalized finding from a custom-layer {@link AccessibilityChecker.InteractionIssue}
     * (Interaction / WCAG 2.2 / Structural / Motion layers).
     *
     * @param issue    the custom-layer issue
     * @param engine   engine label, e.g. {@code "Interaction"}, {@code "WCAG2.2"}, {@code "Structural"}, {@code "Motion"}
     * @param pageUrl  page URL under test, may be {@code null}
     * @param testName current test case name, may be {@code null}
     */
    public static AccessibilityFinding fromInteractionIssue(AccessibilityChecker.InteractionIssue issue,
                                                             String engine, String pageUrl, String testName) {
        return builder()
                .ruleId(issue.ruleId)
                .wcagCriterion(extractWcagCriterionFromRef(issue.wcagRef))
                .severity(issue.impact != null ? issue.impact.toLowerCase() : "unknown")
                .confidence(issue.needsReview ? CONFIDENCE_MANUAL_REVIEW : CONFIDENCE_VIOLATION)
                .description(issue.description)
                .affectedElementHtml(issue.element)
                .remediationGuidance(issue.helpUrl)
                .pageUrl(pageUrl)
                .testName(testName)
                .engine(engine)
                .build();
    }

    private static String targetToString(Node node) {
        Object target = node.getTarget();
        if (target == null) {
            return null;
        }
        if (target instanceof List) {
            return String.join(" ", ((List<?>) target).stream().map(String::valueOf)
                    .toArray(String[]::new));
        }
        return String.valueOf(target);
    }

    /** e.g. "wcag111" -&gt; "1.1.1", "wcag1410" -&gt; "1.4.10". Returns {@code null} when no SC tag is present. */
    private static final Pattern WCAG_SC_TAG = Pattern.compile("^wcag(\\d)(\\d)(\\d{1,2})$");

    static String extractWcagCriterion(List<String> tags) {
        for (String tag : tags) {
            Matcher m = WCAG_SC_TAG.matcher(tag);
            if (m.matches()) {
                return m.group(1) + "." + m.group(2) + "." + m.group(3);
            }
        }
        return null;
    }

    static String extractWcagLevel(List<String> tags) {
        if (tags.contains("wcag2aaa") || tags.contains("wcag21aaa") || tags.contains("wcag22aaa")) {
            return "AAA";
        }
        if (tags.contains("wcag2aa") || tags.contains("wcag21aa") || tags.contains("wcag22aa")) {
            return "AA";
        }
        if (tags.contains("wcag2a") || tags.contains("wcag21a") || tags.contains("wcag22a")) {
            return "A";
        }
        return null;
    }

    /** Extracts "2.5.5" from a free-text ref like "WCAG 2.5.5". Returns {@code null} if not found. */
    private static final Pattern WCAG_REF_TEXT = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");

    static String extractWcagCriterionFromRef(String wcagRef) {
        if (wcagRef == null) {
            return null;
        }
        Matcher m = WCAG_REF_TEXT.matcher(wcagRef);
        return m.find() ? m.group(1) : null;
    }
}
