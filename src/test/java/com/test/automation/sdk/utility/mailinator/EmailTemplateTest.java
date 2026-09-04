package com.test.automation.sdk.utility.mailinator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailTemplate -- constructor, getters, defaults, and immutability.
 */
@DisplayName("EmailTemplate -- construction and accessors")
class EmailTemplateTest {

    // -- 5-arg constructor (no textPattern) -----------------------------------

    @Test
    @DisplayName("5-arg constructor: getters return supplied values")
    void fiveArgConstructor_gettersReturnValues() {
        List<String> urlPatterns  = Arrays.asList("validateToken", "validateReset");
        List<String> excludes     = Arrays.asList("deactivate", "amp;");
        List<String> masks        = Arrays.asList("trim", "uppercase");

        EmailTemplate t = new EmailTemplate("confirmation", "Confirm Your Email",
                urlPatterns, excludes, masks);

        assertEquals("confirmation",       t.getName());
        assertEquals("Confirm Your Email", t.getSubjectContains());
        assertEquals(urlPatterns,          t.getUrlPathPatterns());
        assertEquals(excludes,             t.getExcludePatterns());
        assertEquals(masks,                t.getMasks());
        assertEquals("",                   t.getTextPattern());
    }

    // -- 6-arg constructor (with textPattern) ---------------------------------

    @Test
    @DisplayName("6-arg constructor: textPattern is returned correctly")
    void sixArgConstructor_textPatternReturned() {
        EmailTemplate t = new EmailTemplate("otp", "Your Code", Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), "Code: (\\d{6})");

        assertEquals("otp",          t.getName());
        assertEquals("Code: (\\d{6})", t.getTextPattern());
    }

    // -- Null-safety defaults --------------------------------------------------

    @Test
    @DisplayName("null subjectContains defaults to empty string")
    void nullSubjectContains_defaultsToEmpty() {
        EmailTemplate t = new EmailTemplate("t", null,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList());
        assertEquals("", t.getSubjectContains());
    }

    @Test
    @DisplayName("null textPattern defaults to empty string")
    void nullTextPattern_defaultsToEmpty() {
        EmailTemplate t = new EmailTemplate("t", "", Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), null);
        assertEquals("", t.getTextPattern());
    }

    @Test
    @DisplayName("null urlPathPatterns defaults to empty list")
    void nullUrlPathPatterns_defaultsToEmptyList() {
        EmailTemplate t = new EmailTemplate("t", "", null,
                Collections.<String>emptyList(), Collections.<String>emptyList());
        assertNotNull(t.getUrlPathPatterns());
        assertTrue(t.getUrlPathPatterns().isEmpty());
    }

    @Test
    @DisplayName("null excludePatterns defaults to empty list")
    void nullExcludePatterns_defaultsToEmptyList() {
        EmailTemplate t = new EmailTemplate("t", "", Collections.<String>emptyList(),
                null, Collections.<String>emptyList());
        assertNotNull(t.getExcludePatterns());
        assertTrue(t.getExcludePatterns().isEmpty());
    }

    @Test
    @DisplayName("null masks defaults to empty list")
    void nullMasks_defaultsToEmptyList() {
        EmailTemplate t = new EmailTemplate("t", "", Collections.<String>emptyList(),
                Collections.<String>emptyList(), null);
        assertNotNull(t.getMasks());
        assertTrue(t.getMasks().isEmpty());
    }

    // -- Immutability ----------------------------------------------------------

    @Test
    @DisplayName("getUrlPathPatterns() returns an unmodifiable list")
    void urlPathPatterns_isUnmodifiable() {
        EmailTemplate t = new EmailTemplate("t", "", Arrays.asList("validateToken"),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        assertThrows(UnsupportedOperationException.class,
                () -> t.getUrlPathPatterns().add("injected"));
    }

    @Test
    @DisplayName("getExcludePatterns() returns an unmodifiable list")
    void excludePatterns_isUnmodifiable() {
        EmailTemplate t = new EmailTemplate("t", "", Collections.<String>emptyList(),
                Arrays.asList("amp;"), Collections.<String>emptyList());
        assertThrows(UnsupportedOperationException.class,
                () -> t.getExcludePatterns().add("injected"));
    }

    @Test
    @DisplayName("getMasks() returns an unmodifiable list")
    void masks_isUnmodifiable() {
        EmailTemplate t = new EmailTemplate("t", "", Collections.<String>emptyList(),
                Collections.<String>emptyList(), Arrays.asList("trim"));
        assertThrows(UnsupportedOperationException.class,
                () -> t.getMasks().add("injected"));
    }

    @Test
    @DisplayName("Mutating original list does not affect template after construction")
    void originalListMutation_doesNotAffectTemplate() {
        List<String> original = new java.util.ArrayList<String>(Arrays.asList("validateToken"));
        EmailTemplate t = new EmailTemplate("t", "", original,
                Collections.<String>emptyList(), Collections.<String>emptyList());
        original.add("injected");
        assertEquals(1, t.getUrlPathPatterns().size());
    }
}
