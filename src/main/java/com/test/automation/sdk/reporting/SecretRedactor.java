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
