package com.test.automation.sdk.utility.mailinator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailValueMask -- all transformation tokens, chaining, and edge cases.
 * No network or file I/O required.
 */
@DisplayName("EmailValueMask -- value transformations")
class EmailValueMaskTest {

    // -- Null / empty guards ---------------------------------------------------

    @Test
    @DisplayName("apply() with null value returns null")
    void nullValue_returnsNull() {
        assertNull(EmailValueMask.apply(null, Arrays.asList("trim")));
    }

    @Test
    @DisplayName("apply() with null mask list returns original value")
    void nullMasks_returnsOriginal() {
        assertEquals("hello", EmailValueMask.apply("hello", null));
    }

    @Test
    @DisplayName("apply() with empty mask list returns original value")
    void emptyMasks_returnsOriginal() {
        assertEquals("hello", EmailValueMask.apply("hello", Collections.<String>emptyList()));
    }

    // -- uppercase -------------------------------------------------------------

    @Test
    @DisplayName("uppercase: converts value to UPPER CASE")
    void uppercase_convertsToUpperCase() {
        assertEquals("HELLO WORLD", EmailValueMask.apply("hello world", list("uppercase")));
    }

    @Test
    @DisplayName("uppercase: case-insensitive token (UPPERCASE also works)")
    void uppercase_tokenCaseInsensitive() {
        assertEquals("ABC", EmailValueMask.apply("abc", list("UPPERCASE")));
    }

    // -- lowercase -------------------------------------------------------------

    @Test
    @DisplayName("lowercase: converts value to lower case")
    void lowercase_convertsToLowerCase() {
        assertEquals("hello world", EmailValueMask.apply("HELLO WORLD", list("lowercase")));
    }

    @Test
    @DisplayName("lowercase: case-insensitive token (LOWERCASE also works)")
    void lowercase_tokenCaseInsensitive() {
        assertEquals("abc", EmailValueMask.apply("ABC", list("LOWERCASE")));
    }

    // -- trim ------------------------------------------------------------------

    @Test
    @DisplayName("trim: removes leading and trailing whitespace")
    void trim_removesLeadingAndTrailingSpaces() {
        assertEquals("hello", EmailValueMask.apply("  hello  ", list("trim")));
    }

    @Test
    @DisplayName("trim: no-op on already trimmed value")
    void trim_noopOnTrimmedValue() {
        assertEquals("hello", EmailValueMask.apply("hello", list("trim")));
    }

    @Test
    @DisplayName("trim: preserves internal whitespace")
    void trim_preservesInternalWhitespace() {
        assertEquals("hello world", EmailValueMask.apply("  hello world  ", list("trim")));
    }

    // -- substring -------------------------------------------------------------

    @Test
    @DisplayName("substring:0:5 extracts first 5 characters")
    void substring_extractsRange() {
        assertEquals("Hello", EmailValueMask.apply("Hello World", list("substring:0:5")));
    }

    @Test
    @DisplayName("substring:6:-1 extracts from index 6 to end")
    void substring_negativeEndMeansEndOfString() {
        assertEquals("World", EmailValueMask.apply("Hello World", list("substring:6:-1")));
    }

    @Test
    @DisplayName("substring: start > end returns original value unchanged")
    void substring_invalidRange_returnsOriginal() {
        assertEquals("Hello", EmailValueMask.apply("Hello", list("substring:5:2")));
    }

    @Test
    @DisplayName("substring: end beyond string length clamps to string length")
    void substring_endBeyondLength_clampsToEnd() {
        assertEquals("Hello World", EmailValueMask.apply("Hello World", list("substring:0:999")));
    }

    // -- dateFormat ------------------------------------------------------------

    @Test
    @DisplayName("dateFormat:MM/dd/yyyy:yyyy-MM-dd reformats US date to ISO date")
    void dateFormat_usToIso() {
        assertEquals("2025-01-15", EmailValueMask.apply("01/15/2025",
                list("dateFormat:MM/dd/yyyy:yyyy-MM-dd")));
    }

    @Test
    @DisplayName("dateFormat:yyyy-MM-dd:MM/dd/yyyy reformats ISO date to US date")
    void dateFormat_isoToUs() {
        assertEquals("01/15/2025", EmailValueMask.apply("2025-01-15",
                list("dateFormat:yyyy-MM-dd:MM/dd/yyyy")));
    }

    @Test
    @DisplayName("dateFormat: invalid date string returns original value unchanged")
    void dateFormat_invalidDate_returnsOriginal() {
        String invalid = "not-a-date";
        assertEquals(invalid, EmailValueMask.apply(invalid,
                list("dateFormat:MM/dd/yyyy:yyyy-MM-dd")));
    }

    @Test
    @DisplayName("dateFormat: malformed token (missing output pattern) returns original")
    void dateFormat_malformedToken_returnsOriginal() {
        assertEquals("01/15/2025", EmailValueMask.apply("01/15/2025",
                list("dateFormat:MM/dd/yyyy")));
    }

    // -- replaceAll ------------------------------------------------------------

    @Test
    @DisplayName("replaceAll: replaces all digit runs with X")
    void replaceAll_replacesDigits() {
        assertEquals("Code: X", EmailValueMask.apply("Code: 123456",
                list("replaceAll:\\d+:X")));
    }

    @Test
    @DisplayName("replaceAll: removes whitespace characters")
    void replaceAll_removesWhitespace() {
        assertEquals("HelloWorld", EmailValueMask.apply("Hello World",
                list("replaceAll:\\s+:")));
    }

    @Test
    @DisplayName("replaceAll: no match leaves value unchanged")
    void replaceAll_noMatch_unchanged() {
        assertEquals("HelloWorld", EmailValueMask.apply("HelloWorld",
                list("replaceAll:xyz:ABC")));
    }

    // -- Unknown mask ----------------------------------------------------------

    @Test
    @DisplayName("Unknown mask token leaves value unchanged")
    void unknownMask_returnsOriginal() {
        assertEquals("hello", EmailValueMask.apply("hello", list("nonexistentMask")));
    }

    // -- Chaining --------------------------------------------------------------

    @Test
    @DisplayName("Chained: trim then uppercase")
    void chain_trimThenUppercase() {
        assertEquals("HELLO", EmailValueMask.apply("  hello  ", list("trim", "uppercase")));
    }

    @Test
    @DisplayName("Chained: trim then dateFormat")
    void chain_trimThenDateFormat() {
        assertEquals("2025-03-20", EmailValueMask.apply("  03/20/2025  ",
                list("trim", "dateFormat:MM/dd/yyyy:yyyy-MM-dd")));
    }

    @Test
    @DisplayName("Chained: replaceAll then trim then uppercase")
    void chain_replaceAllTrimUppercase() {
        assertEquals("CODE-X", EmailValueMask.apply("  code-123  ",
                list("trim", "replaceAll:\\d+:X", "uppercase")));
    }

    @Test
    @DisplayName("Chained: substring then lowercase")
    void chain_substringThenLowercase() {
        assertEquals("hello", EmailValueMask.apply("HELLO WORLD", list("substring:0:5", "lowercase")));
    }

    // -- Helper ----------------------------------------------------------------

    private static List<String> list(String... items) {
        return Arrays.asList(items);
    }
}
