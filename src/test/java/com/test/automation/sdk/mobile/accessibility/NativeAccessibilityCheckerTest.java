package com.test.automation.sdk.mobile.accessibility;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.test.automation.sdk.mobile.accessibility.NativeAccessibilityIssue.Severity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NativeAccessibilityChecker} against captured Android/iOS
 * {@code getPageSource()} XML fixtures -- no live device/Appium session required.
 */
@DisplayName("NativeAccessibilityChecker - page-source-based native accessibility audit")
class NativeAccessibilityCheckerTest {

    // ─────────────────────────────────────────────────────────────────────
    // Android
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Android: clickable element with no content-desc/text is flagged missing-accessible-name")
    void androidFlagsMissingAccessibleName() {
        String xml = "<hierarchy>"
                + "<android.widget.ImageButton clickable=\"true\" content-desc=\"\" text=\"\" "
                + "resource-id=\"com.app:id/submit\" bounds=\"[0,0][100,100]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidMissingName");

        assertEquals(1, countByRule(issues, "missing-accessible-name"));
    }

    @Test
    @DisplayName("Android: clickable element with a content-desc is NOT flagged missing-accessible-name")
    void androidDoesNotFlagLabeledElement() {
        String xml = "<hierarchy>"
                + "<android.widget.Button clickable=\"true\" content-desc=\"Submit\" "
                + "resource-id=\"com.app:id/submit\" bounds=\"[0,0][200,200]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidLabeled");

        assertEquals(0, countByRule(issues, "missing-accessible-name"));
    }

    @Test
    @DisplayName("Android: EditText with no content-desc/text is flagged unlabeled-editable-field")
    void androidFlagsUnlabeledEditText() {
        String xml = "<hierarchy>"
                + "<android.widget.EditText clickable=\"true\" content-desc=\"\" text=\"\" "
                + "resource-id=\"com.app:id/email\" bounds=\"[0,0][300,80]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidEditText");

        assertEquals(1, countByRule(issues, "unlabeled-editable-field"));
        // Also counted once as missing-accessible-name since it's clickable with no name
        assertEquals(1, countByRule(issues, "missing-accessible-name"));
    }

    @Test
    @DisplayName("Android: touch target smaller than 48x48 is flagged touch-target-too-small")
    void androidFlagsSmallTouchTarget() {
        String xml = "<hierarchy>"
                + "<android.widget.Button clickable=\"true\" content-desc=\"X\" "
                + "resource-id=\"com.app:id/close\" bounds=\"[0,0][20,20]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidSmallTarget");

        assertEquals(1, countByRule(issues, "touch-target-too-small"));
        assertEquals(Severity.MODERATE, issues.get(0).severity);
    }

    @Test
    @DisplayName("Android: touch target at/above 48x48 is NOT flagged")
    void androidDoesNotFlagAdequateTouchTarget() {
        String xml = "<hierarchy>"
                + "<android.widget.Button clickable=\"true\" content-desc=\"Close\" "
                + "resource-id=\"com.app:id/close\" bounds=\"[0,0][48,48]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidAdequateTarget");

        assertEquals(0, countByRule(issues, "touch-target-too-small"));
    }

    @Test
    @DisplayName("Android: two clickable elements sharing the same content-desc are both flagged duplicate-accessible-name")
    void androidFlagsDuplicateAccessibleName() {
        String xml = "<hierarchy>"
                + "<android.widget.Button clickable=\"true\" content-desc=\"Delete\" bounds=\"[0,0][100,100]\"/>"
                + "<android.widget.Button clickable=\"true\" content-desc=\"Delete\" bounds=\"[0,200][100,300]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidDuplicateName");

        assertEquals(2, countByRule(issues, "duplicate-accessible-name"));
    }

    @Test
    @DisplayName("Android: non-interactive element (not clickable/checkable) is never flagged")
    void androidIgnoresNonInteractiveElements() {
        String xml = "<hierarchy>"
                + "<android.widget.TextView clickable=\"false\" content-desc=\"\" text=\"\" bounds=\"[0,0][10,10]\"/>"
                + "</hierarchy>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "android", "AndroidStaticText");

        assertTrue(issues.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────
    // iOS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("iOS: button with no label/name is flagged missing-accessible-name")
    void iosFlagsMissingAccessibleName() {
        String xml = "<XCUIElementTypeApplication>"
                + "<XCUIElementTypeButton label=\"\" name=\"\" x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                + "</XCUIElementTypeApplication>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "ios", "IosMissingName");

        assertEquals(1, countByRule(issues, "missing-accessible-name"));
    }

    @Test
    @DisplayName("iOS: button with a label is NOT flagged missing-accessible-name")
    void iosDoesNotFlagLabeledButton() {
        String xml = "<XCUIElementTypeApplication>"
                + "<XCUIElementTypeButton label=\"Submit\" name=\"Submit\" x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                + "</XCUIElementTypeApplication>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "ios", "IosLabeled");

        assertEquals(0, countByRule(issues, "missing-accessible-name"));
    }

    @Test
    @DisplayName("iOS: secure text field with no label/name is flagged unlabeled-editable-field")
    void iosFlagsUnlabeledSecureTextField() {
        String xml = "<XCUIElementTypeApplication>"
                + "<XCUIElementTypeSecureTextField label=\"\" name=\"\" x=\"0\" y=\"0\" width=\"300\" height=\"44\"/>"
                + "</XCUIElementTypeApplication>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "ios", "IosSecureField");

        assertEquals(1, countByRule(issues, "unlabeled-editable-field"));
    }

    @Test
    @DisplayName("iOS: touch target smaller than 44x44pt is flagged touch-target-too-small")
    void iosFlagsSmallTouchTarget() {
        String xml = "<XCUIElementTypeApplication>"
                + "<XCUIElementTypeButton label=\"X\" x=\"0\" y=\"0\" width=\"20\" height=\"20\"/>"
                + "</XCUIElementTypeApplication>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, "ios", "IosSmallTarget");

        assertEquals(1, countByRule(issues, "touch-target-too-small"));
    }

    @Test
    @DisplayName("iOS platform is auto-detected from the XCUIElementType* root when platform arg is null")
    void iosPlatformAutoDetectedFromRootElement() {
        String xml = "<XCUIElementTypeApplication>"
                + "<XCUIElementTypeButton label=\"\" x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                + "</XCUIElementTypeApplication>";

        List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(xml, null, "IosAutoDetect");

        assertEquals(1, countByRule(issues, "missing-accessible-name"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null/empty page source returns an empty issue list, not an exception")
    void emptyPageSourceReturnsEmptyList() {
        assertTrue(NativeAccessibilityChecker.check((String) null, "android", "Empty").isEmpty());
        assertTrue(NativeAccessibilityChecker.check("", "android", "Empty").isEmpty());
        assertTrue(NativeAccessibilityChecker.check("   ", "android", "Empty").isEmpty());
    }

    @Test
    @DisplayName("malformed XML returns an empty issue list, not an exception")
    void malformedXmlReturnsEmptyList() {
        List<NativeAccessibilityIssue> issues =
                NativeAccessibilityChecker.check("<not-valid-xml", "android", "Malformed");
        assertNotNull(issues);
        assertTrue(issues.isEmpty());
    }

    private static long countByRule(List<NativeAccessibilityIssue> issues, String ruleId) {
        return issues.stream().filter(i -> i.ruleId.equals(ruleId)).count();
    }
}
