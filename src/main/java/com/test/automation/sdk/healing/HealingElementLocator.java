package com.test.automation.sdk.healing;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.ElementLocator;

import com.test.automation.sdk.reporting.ExecutionReporting;

/**
 * Runtime self-healing {@link ElementLocator}.
 *
 * <p>Wraps the {@code @FindBy} locator generated for a single page-object field.
 * When the primary locator fails to resolve any element, it attempts a small set
 * of progressively relaxed XPath candidates (see {@link LocatorRelaxationEngine}),
 * generated purely from the primary locator itself -- no pre-crawled fingerprint
 * data or external service is required. Healing is only attempted for XPath
 * locators (the SDK's mandated locator strategy, see
 * {@code locator-strategy.instructions.md}).
 *
 * <p>A relaxed candidate is only trusted if it resolves to <b>exactly one</b>
 * element in the live DOM -- the same uniqueness bar the SDK's crawler enforces
 * at design time. Matching more than one element is treated as "still broken",
 * never silently guessed.
 *
 * <p>Every heal attempt (success or exhaustion) is published through
 * {@link ExecutionReporting} so it shows up in the log/Allure/Extent report
 * trail -- never a silent side effect that could mask a real product regression.
 *
 * <p>Never caches the resolved element between lookups: a page that needed
 * healing once is, by definition, not stable enough to trust a cached reference.
 */
public class HealingElementLocator implements ElementLocator {

    private static final Logger log = LogManager.getLogger(HealingElementLocator.class.getName());
    private static final String BY_XPATH_PREFIX = "By.xpath: ";

    private final SearchContext context;
    private final By primaryBy;
    private final String fieldDescription;

    public HealingElementLocator(SearchContext context, By primaryBy, String fieldDescription) {
        this.context = context;
        this.primaryBy = primaryBy;
        this.fieldDescription = fieldDescription == null ? "" : fieldDescription;
    }

    public By getPrimaryBy() {
        return primaryBy;
    }

    @Override
    public WebElement findElement() {
        try {
            return context.findElement(primaryBy);
        } catch (NoSuchElementException primaryFailure) {
            WebElement healed = tryHeal(primaryFailure);
            if (healed != null) {
                return healed;
            }
            throw primaryFailure;
        }
    }

    @Override
    public List<WebElement> findElements() {
        List<WebElement> found = context.findElements(primaryBy);
        if (!found.isEmpty()) {
            return found;
        }
        WebElement healed = tryHeal(null);
        if (healed == null) {
            return found;
        }
        List<WebElement> result = new ArrayList<>();
        result.add(healed);
        return result;
    }

    private WebElement tryHeal(NoSuchElementException cause) {
        String xpath = extractXPath(primaryBy);
        if (xpath == null) {
            recordExhausted(cause);
            return null;
        }

        long start = System.currentTimeMillis();
        for (String candidate : LocatorRelaxationEngine.relax(xpath)) {
            WebElement match = tryCandidate(candidate);
            if (match != null) {
                long duration = System.currentTimeMillis() - start;
                log.warn("[SELF-HEAL] " + fieldDescription + " primary locator failed (" + primaryBy
                        + ") -- healed via relaxed xpath: " + candidate);
                ExecutionReporting.actionCompleted("LOCATOR_HEALED", primaryBy.toString(),
                        fieldDescription + " healed -> //" + candidate, duration);
                return match;
            }
        }

        recordExhausted(cause);
        return null;
    }

    private WebElement tryCandidate(String candidate) {
        try {
            List<WebElement> matches = context.findElements(By.xpath(candidate));
            return matches.size() == 1 ? matches.get(0) : null;
        } catch (Exception invalidCandidate) {
            return null;
        }
    }

    private void recordExhausted(NoSuchElementException cause) {
        log.warn("[SELF-HEAL] " + fieldDescription + " primary locator failed and no relaxed candidate "
                + "resolved to a unique element: " + primaryBy);
        ExecutionReporting.actionFailed("LOCATOR_HEALED", primaryBy.toString(),
                fieldDescription + " healing exhausted -- no unique relaxed candidate found", cause, null);
    }

    private static String extractXPath(By by) {
        if (by == null) {
            return null;
        }
        String s = by.toString();
        return s.startsWith(BY_XPATH_PREFIX) ? s.substring(BY_XPATH_PREFIX.length()) : null;
    }
}
