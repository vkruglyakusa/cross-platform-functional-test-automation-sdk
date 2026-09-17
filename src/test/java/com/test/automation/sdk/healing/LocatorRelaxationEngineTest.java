package com.test.automation.sdk.healing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocatorRelaxationEngine}. Pure string/XPath logic -- no
 * WebDriver or browser required.
 */
@DisplayName("LocatorRelaxationEngine -- XPath relaxation candidate generation")
class LocatorRelaxationEngineTest {

    @Test
    @DisplayName("compound 'and' predicate produces drop-one and keep-one-alone candidates")
    void compoundPredicateProducesDropOneAndKeepOneCandidates() {
        String xpath = "//input[@id='email' and @type='text']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        assertTrue(candidates.contains("//input[@id='email']"),
                "Should keep @id alone (also equals drop-@type)");
        assertTrue(candidates.contains("//input[@type='text']"),
                "Should keep @type alone (also equals drop-@id)");
    }

    @Test
    @DisplayName("three-operand predicate produces every drop-one and keep-one-alone combination")
    void threeOperandPredicateProducesAllCombinations() {
        String xpath = "//button[@name='submit' and @type='button' and normalize-space(.)='Submit']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        assertTrue(candidates.contains("//button[@type='button' and normalize-space(.)='Submit']"));
        assertTrue(candidates.contains("//button[@name='submit' and normalize-space(.)='Submit']"));
        assertTrue(candidates.contains("//button[@name='submit' and @type='button']"));
        assertTrue(candidates.contains("//button[@name='submit']"));
        assertTrue(candidates.contains("//button[@type='button']"));
        assertTrue(candidates.contains("//button[normalize-space(.)='Submit']"));
    }

    @Test
    @DisplayName("exact attribute match is offered as a contains() variant")
    void exactAttributeProducesContainsVariant() {
        String xpath = "//input[@id='mat-input-3']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        assertTrue(candidates.contains("//input[contains(@id,'mat-input-3')]"));
    }

    @Test
    @DisplayName("exact normalize-space(.) text match is offered as a contains() variant")
    void exactTextProducesContainsVariant() {
        String xpath = "//button[normalize-space(.)='Submit Now']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        assertTrue(candidates.contains("//button[contains(normalize-space(.),'Submit Now')]"));
    }

    @Test
    @DisplayName("exact text() match is offered as a contains() variant")
    void exactTextFunctionProducesContainsVariant() {
        String xpath = "//a[text()='Home']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        assertTrue(candidates.contains("//a[contains(text(),'Home')]"));
    }

    @Test
    @DisplayName("a single-predicate, non-compound xpath yields no drop-one candidates")
    void singlePredicateHasNoDropOneCandidates() {
        String xpath = "//div[@id='panel']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);

        // Only the contains() variant should appear -- no drop-one candidate possible.
        assertEquals(1, candidates.size());
        assertEquals("//div[contains(@id,'panel')]", candidates.get(0));
    }

    @Test
    @DisplayName("an xpath with no predicates at all yields no candidates")
    void noPredicateYieldsNoCandidates() {
        assertTrue(LocatorRelaxationEngine.relax("//div").isEmpty());
    }

    @Test
    @DisplayName("null or blank input yields no candidates, never throws")
    void nullOrBlankInputIsHandledSafely() {
        assertTrue(LocatorRelaxationEngine.relax(null).isEmpty());
        assertTrue(LocatorRelaxationEngine.relax("").isEmpty());
        assertTrue(LocatorRelaxationEngine.relax("   ").isEmpty());
    }

    @Test
    @DisplayName("the original xpath is never returned as one of its own candidates")
    void originalXPathNeverIncludedInCandidates() {
        String xpath = "//input[@id='email' and @type='text']";
        assertFalse(LocatorRelaxationEngine.relax(xpath).contains(xpath));
    }

    @Test
    @DisplayName("candidate list is de-duplicated")
    void candidatesAreDeduplicated() {
        // '@id' exact-match predicate alone: drop-one/keep-one produces the same
        // string as the untouched predicate bracket, and contains() is distinct --
        // ensure no duplicate entries regardless.
        String xpath = "//input[@id='email' and @id='email']";
        List<String> candidates = LocatorRelaxationEngine.relax(xpath);
        assertEquals(candidates.size(), candidates.stream().distinct().count());
    }
}
