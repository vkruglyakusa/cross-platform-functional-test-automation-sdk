package com.test.automation.sdk.mobile.accessibility;

/**
 * One accessibility issue found by {@link NativeAccessibilityChecker} against a native
 * (non-WebView) Android/iOS screen. Unlike {@code com.test.automation.sdk.accessibility.AccessibilityChecker}
 * (axe-core, DOM-based), this has no external ruleset dependency -- everything here is
 * derived directly from Appium's {@code getPageSource()} XML attributes.
 */
public final class NativeAccessibilityIssue {

    /** Stable identifier for the rule that produced this issue, e.g. {@code "missing-accessible-name"}. */
    public final String ruleId;

    /** Severity, using the same four-level scale as the desktop axe-core-based checker. */
    public final Severity severity;

    /** Human-readable description of the specific violation. */
    public final String message;

    /** Native element tag/class, e.g. {@code android.widget.Button} or {@code XCUIElementTypeButton}. */
    public final String elementClass;

    /** Best available identifier for the offending element (resource-id / content-desc / name), for triage. */
    public final String elementIdentifier;

    /** Raw {@code bounds} (Android) or {@code x,y,width,height} (iOS) string, for locating the element on screen. */
    public final String boundsOrFrame;

    public NativeAccessibilityIssue(String ruleId, Severity severity, String message,
            String elementClass, String elementIdentifier, String boundsOrFrame) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.elementClass = elementClass;
        this.elementIdentifier = elementIdentifier;
        this.boundsOrFrame = boundsOrFrame;
    }

    /** Severity scale, ordered least to most severe -- mirrors the desktop {@code ImpactThreshold} naming. */
    public enum Severity {
        MINOR, MODERATE, SERIOUS, CRITICAL
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + ruleId + " on <" + elementClass + "> "
                + (elementIdentifier == null || elementIdentifier.isEmpty() ? "" : "'" + elementIdentifier + "' ")
                + "-- " + message
                + (boundsOrFrame == null || boundsOrFrame.isEmpty() ? "" : " (" + boundsOrFrame + ")");
    }
}
