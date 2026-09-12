package com.test.automation.sdk.tools.pageobject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Utilities for producing Java identifiers that generated sources can compile. */
public final class JavaIdentifier {
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "_", "var", "yield", "record", "sealed", "permits"));

    private JavaIdentifier() {}

    public static String toFieldName(String raw) {
        String source = raw == null ? "" : raw.trim();
        String[] words = source.replaceAll("[^a-zA-Z0-9_$]+", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() == 0) result.append(Character.toLowerCase(word.charAt(0))).append(word.substring(1));
            else result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (result.length() == 0) result.append("element");
        if (!Character.isJavaIdentifierStart(result.charAt(0))) result.insert(0, "element");
        for (int i = 1; i < result.length(); i++) {
            if (!Character.isJavaIdentifierPart(result.charAt(i))) result.setCharAt(i, '_');
        }
        String candidate = result.toString();
        return isReserved(candidate) ? candidate + "Element" : candidate;
    }

    public static String requireTypeName(String value, String propertyName) {
        String candidate = value == null ? "" : value.trim();
        if (!TYPE_NAME.matcher(candidate).matches() || isReserved(candidate)) {
            throw new IllegalArgumentException(propertyName + " must be a valid, non-reserved Java type name");
        }
        return candidate;
    }

    public static boolean isReserved(String value) { return value != null && RESERVED.contains(value); }
}
