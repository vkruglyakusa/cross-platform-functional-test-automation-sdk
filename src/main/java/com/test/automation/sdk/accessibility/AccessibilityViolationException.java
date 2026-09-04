package com.test.automation.sdk.accessibility;

/**
 * Thrown when an accessibility scan finds violations and the library is configured
 * to fail (via {@code accessibility.fail.on.violation=true} or
 * {@link AccessibilityChecker#assertNoViolations}).
 *
 * <p>This is an unchecked exception so it surfaces as a test failure in <b>both</b>
 * JUnit and TestNG without any framework-specific assertion dependency.</p>
 */
public class AccessibilityViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessibilityViolationException(String message) {
        super(message);
    }

    public AccessibilityViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}

