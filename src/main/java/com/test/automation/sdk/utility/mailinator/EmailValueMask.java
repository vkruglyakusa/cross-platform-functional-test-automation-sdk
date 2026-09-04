package com.test.automation.sdk.utility.mailinator;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Applies ordered transformations (masks) to a string value extracted from an email.
 */
public class EmailValueMask {

    private EmailValueMask() {
    }

    public static String apply(String value, List<String> masks) {
        if (value == null || masks == null || masks.isEmpty()) {
            return value;
        }
        String result = value;
        for (String mask : masks) {
            if (mask != null) {
                result = applyOne(result, mask.trim());
            }
        }
        return result;
    }

    private static String applyOne(String value, String mask) {
        if (value == null || mask == null || mask.isEmpty()) {
            return value;
        }
        if ("uppercase".equalsIgnoreCase(mask)) {
            return value.toUpperCase();
        }
        if ("lowercase".equalsIgnoreCase(mask)) {
            return value.toLowerCase();
        }
        if ("trim".equalsIgnoreCase(mask)) {
            return value.trim();
        }
        if (mask.startsWith("substring:")) {
            String[] parts = mask.split(":", 3);
            if (parts.length == 3) {
                try {
                    int start = Integer.parseInt(parts[1].trim());
                    int end = Integer.parseInt(parts[2].trim());
                    if (start < 0) {
                        start = 0;
                    }
                    if (end == -1 || end > value.length()) {
                        end = value.length();
                    }
                    if (start < end) {
                        return value.substring(start, end);
                    }
                } catch (NumberFormatException e) {
                    return value;
                }
            }
        }
        if (mask.startsWith("dateFormat:")) {
            String[] parts = mask.split(":", 3);
            if (parts.length == 3) {
                try {
                    SimpleDateFormat inputFmt = new SimpleDateFormat(parts[1].trim());
                    SimpleDateFormat outputFmt = new SimpleDateFormat(parts[2].trim());
                    return outputFmt.format(inputFmt.parse(value.trim()));
                } catch (Exception e) {
                    return value;
                }
            }
        }
        if (mask.startsWith("replaceAll:")) {
            String[] parts = mask.split(":", 3);
            if (parts.length == 3) {
                try {
                    return value.replaceAll(parts[1].trim(), parts[2].trim());
                } catch (Exception e) {
                    return value;
                }
            }
        }
        return value;
    }
}
