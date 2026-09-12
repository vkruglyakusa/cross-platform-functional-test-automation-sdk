package com.test.automation.sdk.execution;

import java.util.Locale;

/**
 * A normalized, validated identifier for a cloud provider (e.g., "browserstack", "appium")
 * used to disambiguate session factories in the registry.
 *
 * This is a value object that enforces lowercase canonical identifiers with a documented character policy:
 * - Only alphanumeric characters and hyphens are allowed
 * - Must not start or end with a hyphen
 * - Must be at least 1 character long
 */
public final class ProviderId {

    private final String value;

    public ProviderId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider ID must not be null or empty");
        }

        // Validate character policy: only alphanumeric and hyphens, no leading/trailing hyphen
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("-") || normalized.endsWith("-")) {
            throw new IllegalArgumentException(
                "Provider ID must not start or end with a hyphen: '" + normalized + "'");
        }

        // Check for valid characters
        if (!normalized.matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException(
                "Provider ID contains invalid characters. Only alphanumeric characters and hyphens are allowed: '"
                        + normalized + "'");
        }

        this.value = normalized;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ProviderId that = (ProviderId) obj;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
