package com.test.automation.sdk.reporting;

import java.util.regex.Pattern;

/**
 * Centralized redaction rules for execution messages and typed values.
 */
public final class SecretRedactor {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(password|passcode|passwd|secret|token|api[-_ ]?key|apikey|authorization|access[-_ ]?key|client[-_ ]?secret)\\s*[:=]\\s*(.*?)(?=\\s+(?:password|passcode|passwd|secret|token|api[-_ ]?key|apikey|authorization|access[-_ ]?key|client[-_ ]?secret)\\s*[:=]|[,;]|$)");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._\\-+/=]+)");

    private SecretRedactor() {}

    public static String redactMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String masked = KEY_VALUE_PATTERN.matcher(input).replaceAll("$1=" + REDACTED);
        masked = BEARER_PATTERN.matcher(masked).replaceAll("$1" + REDACTED);
        return masked;
    }

    public static String redactTypedValue(String elementDescription, String value) {
        if (value == null) {
            return "";
        }
        if (isSensitiveContext(elementDescription)) {
            return REDACTED;
        }
        return redactMessage(value);
    }

    /**
     * SDK v1.5.1 -- header/query-parameter NAME denylist used by network trace
     * capture ({@code com.test.automation.sdk.evidence.NetworkTraceRecorder}).
     * Matching is by exact field name (case-insensitive), not free-text pattern
     * matching, since HTTP header/param names are structured key/value pairs
     * rather than prose that {@link #redactMessage} is designed for.
     */
    private static final java.util.Set<String> SENSITIVE_FIELD_NAMES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "authorization", "cookie", "set-cookie", "x-api-key", "api-key", "apikey",
            "x-auth-token", "x-access-token", "access-token", "token", "password",
            "passwd", "secret", "client-secret", "client_secret", "session", "sessionid",
            "session-id", "x-csrf-token", "csrf-token"));

    /** True when {@code fieldName} (an HTTP header or query-parameter name) should be masked. */
    public static boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return SENSITIVE_FIELD_NAMES.contains(fieldName.trim().toLowerCase());
    }

    /**
     * Masks {@code value} when {@code fieldName} is a known-sensitive HTTP
     * header/query-parameter name; otherwise applies the same free-text
     * {@link #redactMessage} pass a value could still contain (e.g. an
     * embedded bearer token inside a non-denylisted header).
     */
    public static String redactFieldValue(String fieldName, String value) {
        if (value == null) {
            return "";
        }
        if (isSensitiveFieldName(fieldName)) {
            return REDACTED;
        }
        return redactMessage(value);
    }

    public static boolean isSensitiveContext(String context) {
        if (context == null) {
            return false;
        }
        String lower = context.toLowerCase();
        return lower.contains("password")
                || lower.contains("passcode")
                || lower.contains("passwd")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("authorization")
                || lower.contains("access key")
                || lower.contains("client secret");
    }
}
