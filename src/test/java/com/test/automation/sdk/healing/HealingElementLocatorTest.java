package com.test.automation.sdk.healing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealingElementLocator}. Uses a mocked {@link SearchContext}
 * so no real WebDriver/browser is needed.
 */
@DisplayName("HealingElementLocator -- runtime self-healing behavior")
class HealingElementLocatorTest {

    @Test
    @DisplayName("primary locator resolves normally -- no healing attempted")
    void primaryLocatorSucceeds_noHealingAttempted() {
        SearchContext context = mock(SearchContext.class);
        WebElement primaryElement = mock(WebElement.class);
        By primaryBy = By.xpath("//input[@id='email']");
        when(context.findElement(primaryBy)).thenReturn(primaryElement);

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        assertSame(primaryElement, locator.findElement());
        verify(context, never()).findElements(any(By.class));
    }

    @Test
    @DisplayName("primary locator fails but a relaxed candidate resolves to exactly one element -- heals")
    void primaryFails_relaxedCandidateResolvesUniquely_heals() {
        SearchContext context = mock(SearchContext.class);
        WebElement healedElement = mock(WebElement.class);
        By primaryBy = By.xpath("//input[@id='email' and @type='text']");

        when(context.findElement(primaryBy)).thenThrow(new NoSuchElementException("not found"));
        // The engine will try, among other candidates, dropping "@type='text'".
        when(context.findElements(By.xpath("//input[@id='email']")))
                .thenReturn(Collections.singletonList(healedElement));
        // Any other candidate resolves to nothing.
        when(context.findElements(argThat(by -> by != null && !by.equals(By.xpath("//input[@id='email']")))))
                .thenReturn(Collections.emptyList());

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        assertSame(healedElement, locator.findElement());
    }

    @Test
    @DisplayName("primary fails and every relaxed candidate is ambiguous or missing -- rethrows original exception")
    void primaryFails_allCandidatesFail_rethrowsOriginal() {
        SearchContext context = mock(SearchContext.class);
        By primaryBy = By.xpath("//input[@id='email' and @type='text']");
        NoSuchElementException original = new NoSuchElementException("not found");

        when(context.findElement(primaryBy)).thenThrow(original);
        when(context.findElements(any(By.class))).thenReturn(Collections.emptyList());

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class, locator::findElement);
        assertSame(original, thrown);
    }

    @Test
    @DisplayName("a relaxed candidate matching MORE than one element is not trusted")
    void ambiguousRelaxedCandidateIsNotTrusted() {
        SearchContext context = mock(SearchContext.class);
        By primaryBy = By.xpath("//input[@id='email' and @type='text']");
        WebElement a = mock(WebElement.class);
        WebElement b = mock(WebElement.class);

        when(context.findElement(primaryBy)).thenThrow(new NoSuchElementException("not found"));
        // Every relaxed candidate ambiguously matches two elements.
        when(context.findElements(any(By.class))).thenReturn(List.of(a, b));

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        assertThrows(NoSuchElementException.class, locator::findElement);
    }

    @Test
    @DisplayName("non-XPath locators are never healed -- original exception propagates immediately")
    void nonXPathLocatorIsNeverHealed() {
        SearchContext context = mock(SearchContext.class);
        By primaryBy = By.id("email");
        NoSuchElementException original = new NoSuchElementException("not found");
        when(context.findElement(primaryBy)).thenThrow(original);

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        assertThrows(NoSuchElementException.class, locator::findElement);
        verify(context, never()).findElements(any(By.class));
    }

    @Test
    @DisplayName("findElements(): primary returns empty but a relaxed candidate is unique -- returns healed singleton list")
    void findElements_primaryEmpty_relaxedUnique_returnsHealedList() {
        SearchContext context = mock(SearchContext.class);
        WebElement healedElement = mock(WebElement.class);
        By primaryBy = By.xpath("//input[@id='email' and @type='text']");

        when(context.findElements(primaryBy)).thenReturn(Collections.emptyList());
        when(context.findElements(By.xpath("//input[@id='email']")))
                .thenReturn(Collections.singletonList(healedElement));
        when(context.findElements(argThat(by -> by != null && !by.equals(primaryBy) && !by.equals(By.xpath("//input[@id='email']")))))
                .thenReturn(Collections.emptyList());

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        List<WebElement> result = locator.findElements();
        assertEquals(1, result.size());
        assertSame(healedElement, result.get(0));
    }

    @Test
    @DisplayName("findElements(): primary returns results directly -- no healing attempted")
    void findElements_primarySucceeds_noHealingAttempted() {
        SearchContext context = mock(SearchContext.class);
        WebElement element = mock(WebElement.class);
        By primaryBy = By.xpath("//input[@id='email']");
        when(context.findElements(primaryBy)).thenReturn(Collections.singletonList(element));

        HealingElementLocator locator = new HealingElementLocator(context, primaryBy, "LoginPage.emailField");

        List<WebElement> result = locator.findElements();
        assertEquals(1, result.size());
        assertSame(element, result.get(0));
        verify(context, times(1)).findElements(any(By.class));
    }
}
