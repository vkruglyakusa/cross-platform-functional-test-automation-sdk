package com.test.automation.sdk.reporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the SDK v1.5.1 header/query-parameter redaction helpers
 * added to {@link SecretRedactor} for network trace evidence.
 */
@DisplayName("SecretRedactor -- HTTP header/param name-based redaction (network evidence)")
class SecretRedactorTest {

    @Test
    @DisplayName("known-sensitive header names are recognized case-insensitively")
    void isSensitiveFieldName_recognizesKnownHeaders() {
        assertTrue(SecretRedactor.isSensitiveFieldName("Authorization"));
        assertTrue(SecretRedactor.isSensitiveFieldName("authorization"));
        assertTrue(SecretRedactor.isSensitiveFieldName("Cookie"));
        assertTrue(SecretRedactor.isSensitiveFieldName("Set-Cookie"));
        assertTrue(SecretRedactor.isSensitiveFieldName("X-Api-Key"));
    }

    @Test
    @DisplayName("ordinary header names are not flagged as sensitive")
    void isSensitiveFieldName_ignoresOrdinaryHeaders() {
        assertFalse(SecretRedactor.isSensitiveFieldName("Content-Type"));
        assertFalse(SecretRedactor.isSensitiveFieldName("Accept"));
        assertFalse(SecretRedactor.isSensitiveFieldName(null));
    }

    @Test
    @DisplayName("redactFieldValue() masks the value of a sensitive header entirely")
    void redactFieldValue_masksSensitiveHeader() {
        assertEquals("[REDACTED]", SecretRedactor.redactFieldValue("Authorization", "Bearer abc123"));
        assertEquals("[REDACTED]", SecretRedactor.redactFieldValue("Cookie", "sessionId=xyz"));
    }

    @Test
    @DisplayName("redactFieldValue() leaves ordinary header values untouched (aside from generic message redaction)")
    void redactFieldValue_leavesOrdinaryHeaderIntact() {
        assertEquals("application/json", SecretRedactor.redactFieldValue("Content-Type", "application/json"));
    }
}
