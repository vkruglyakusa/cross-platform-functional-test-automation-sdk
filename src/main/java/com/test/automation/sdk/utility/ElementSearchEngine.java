package com.test.automation.sdk.utility;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * ElementSearchEngine -- live DOM semantic element resolver.
 *
 * Resolves human-readable hints (label text, placeholder, aria-label, visible text,
 * formcontrolname) to a concrete {@link WebElement} using the same strategy priority
 * order as {@link ElementCrawler}.
 *
 * Used by {@link DataDrivenCrawler} to execute {@link CrawlerStep}s that are defined
 * with semantic locators rather than raw XPaths -- enabling test case steps to be
 * written in plain language:
 *
 *   CrawlerStep.selectByLabel("Category", "Noise")
 *   CrawlerStep.typeByPlaceholder("Enter description", "text")
 *   CrawlerStep.clickByText("Submit")
 *
 * RESOLUTION STRATEGY PER MODE
 * -----------------------------
 *
 * BY_LABEL:
 *   1. mat-form-field containing mat-label with matching text -> child input/mat-select/textarea
 *   2. <label for="X"> with matching text -> input[@id='X']
 *   3. <label> wrapping an input directly
 *   4. input with @aria-labelledby pointing to element with matching text
 *   5. Proximity: //label[.='text']/following-sibling::*[self::input or self::mat-select][1]
 *
 * BY_PLACEHOLDER:
 *   1. //input[@placeholder='text']
 *   2. //textarea[@placeholder='text']
 *   3. contains() fallback
 *
 * BY_TEXT (for buttons, links, tab headers):
 *   1. //button[normalize-space(.)='text']
 *   2. //a[normalize-space(.)='text']
 *   3. //*[@role='button' and normalize-space(.)='text']
 *   4. //*[normalize-space(.)='text'] -- broadest fallback
 *
 * BY_ARIA_LABEL:
 *   1. //*[@aria-label='text']
 *   2. //*[contains(@aria-label,'text')]
 *
 * BY_FORM_CONTROL_NAME:
 *   1. //*[@formcontrolname='name']
 *
 * @author vkruglyak
 */
public class ElementSearchEngine {

    private static final Logger log = LogManager.getLogger(ElementSearchEngine.class.getName());

    private static final List<String> INPUT_TAGS =
        Arrays.asList("input", "textarea", "select", "mat-select");

    private final WebDriver driver;

    public ElementSearchEngine(WebDriver driver) {
        this.driver = driver;
    }

    // =========================================================================
    // Main resolve entry point
    // =========================================================================

    /**
     * Resolve a {@link CrawlerStep} semantic hint to a live {@link WebElement}.
     *
     * @param step  the step whose locator needs resolution
     * @return the found WebElement, or {@code null} if not found
     */
    public WebElement resolve(CrawlerStep step) {
        String hint = step.getLocator();
        switch (step.getLocatorMode()) {
            case XPATH:               return findByXPath(hint);
            case BY_LABEL:            return findByLabel(hint);
            case BY_PLACEHOLDER:      return findByPlaceholder(hint);
            case BY_TEXT:             return findByText(hint);
            case BY_ARIA_LABEL:       return findByAriaLabel(hint);
            case BY_FORM_CONTROL_NAME:return findByFormControlName(hint);
            default:
                log.warn("Unknown locator mode: " + step.getLocatorMode());
                return null;
        }
    }

    // =========================================================================
    // BY_LABEL
    // =========================================================================

    public WebElement findByLabel(String labelText) {

        // 1. Angular Material mat-form-field containing mat-label
        for (String inputTag : INPUT_TAGS) {
            WebElement el = first(
                "//mat-form-field[.//mat-label[normalize-space(.)='"
                + labelText + "']]//" + inputTag);
            if (el != null) { log.info("findByLabel[mat-label] '" + labelText + "' -> " + inputTag); return el; }
        }

        // 2. <label for="X"> explicit association
        WebElement label = first("//label[normalize-space(.)='" + labelText + "' and @for]");
        if (label != null) {
            String forId = attr(label, "for");
            if (!forId.isEmpty()) {
                for (String inputTag : INPUT_TAGS) {
                    WebElement el = first("//" + inputTag + "[@id='" + forId + "']");
                    if (el != null) { log.info("findByLabel[for] '" + labelText + "' -> " + inputTag + "[@id='" + forId + "']"); return el; }
                }
            }
        }

        // 3. aria-labelledby: find element whose aria-labelledby points to label with this text
        WebElement labelEl = first("//*[normalize-space(.)='" + labelText + "' and @id]");
        if (labelEl != null) {
            String labelId = attr(labelEl, "id");
            if (!labelId.isEmpty()) {
                for (String inputTag : INPUT_TAGS) {
                    WebElement el = first("//" + inputTag + "[@aria-labelledby='" + labelId + "']");
                    if (el != null) { log.info("findByLabel[aria-labelledby] '" + labelText + "' -> " + el.getTagName()); return el; }
                }
            }
        }

        // 4. Proximity: label followed by input sibling
        for (String inputTag : INPUT_TAGS) {
            WebElement el = first(
                "//label[normalize-space(.)='" + labelText + "']/following-sibling::" + inputTag + "[1]");
            if (el != null) { log.info("findByLabel[proximity-sibling] '" + labelText + "' -> " + inputTag); return el; }
        }

        // 5. Label wrapping an input (label > input)
        for (String inputTag : INPUT_TAGS) {
            WebElement el = first(
                "//label[normalize-space(.)='" + labelText + "']//" + inputTag);
            if (el != null) { log.info("findByLabel[wrapping] '" + labelText + "' -> " + inputTag); return el; }
        }

        log.warn("findByLabel: not found for label='" + labelText + "'");
        return null;
    }

    // =========================================================================
    // BY_PLACEHOLDER
    // =========================================================================

    public WebElement findByPlaceholder(String placeholder) {
        for (String tag : new String[]{"input", "textarea"}) {
            WebElement el = first("//" + tag + "[@placeholder='" + placeholder + "']");
            if (el != null) { log.info("findByPlaceholder[exact] '" + placeholder + "' -> " + tag); return el; }
        }
        // contains() fallback
        for (String tag : new String[]{"input", "textarea"}) {
            WebElement el = first("//" + tag + "[contains(@placeholder,'" + placeholder + "')]");
            if (el != null) { log.info("findByPlaceholder[contains] '" + placeholder + "' -> " + tag); return el; }
        }
        log.warn("findByPlaceholder: not found for placeholder='" + placeholder + "'");
        return null;
    }

    // =========================================================================
    // BY_TEXT (buttons, links, tabs, any clickable)
    // =========================================================================

    public WebElement findByText(String visibleText) {
        // Specific clickable tags first
        for (String tag : new String[]{"button", "a", "span", "div"}) {
            WebElement el = first("//" + tag + "[normalize-space(.)='" + visibleText + "']");
            if (el != null) { log.info("findByText[exact/" + tag + "] '" + visibleText + "'"); return el; }
        }
        // Role=button
        WebElement el = first("//*[@role='button' and normalize-space(.)='" + visibleText + "']");
        if (el != null) { log.info("findByText[role=button] '" + visibleText + "'"); return el; }

        // Any element -- broadest fallback
        el = first("//*[normalize-space(.)='" + visibleText + "']");
        if (el != null) { log.info("findByText[any] '" + visibleText + "'"); return el; }

        log.warn("findByText: not found for text='" + visibleText + "'");
        return null;
    }

    // =========================================================================
    // BY_ARIA_LABEL
    // =========================================================================

    public WebElement findByAriaLabel(String ariaLabel) {
        WebElement el = first("//*[@aria-label='" + ariaLabel + "']");
        if (el != null) { log.info("findByAriaLabel[exact] '" + ariaLabel + "'"); return el; }

        el = first("//*[contains(@aria-label,'" + ariaLabel + "')]");
        if (el != null) { log.info("findByAriaLabel[contains] '" + ariaLabel + "'"); return el; }

        log.warn("findByAriaLabel: not found for aria-label='" + ariaLabel + "'");
        return null;
    }

    // =========================================================================
    // BY_FORM_CONTROL_NAME
    // =========================================================================

    public WebElement findByFormControlName(String formControlName) {
        WebElement el = first("//*[@formcontrolname='" + formControlName + "']");
        if (el != null) { log.info("findByFormControlName '" + formControlName + "' -> " + el.getTagName()); return el; }

        log.warn("findByFormControlName: not found for formcontrolname='" + formControlName + "'");
        return null;
    }

    // =========================================================================
    // Raw XPath
    // =========================================================================

    private WebElement findByXPath(String xpath) {
        return first(xpath);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the first matching element or null -- never throws. */
    private WebElement first(String xpath) {
        try {
            List<WebElement> els = driver.findElements(By.xpath(xpath));
            for (WebElement el : els) {
                try {
                    if (el.isDisplayed()) return el;
                } catch (Exception ignored) {}
            }
            // If none displayed, return first found (may be hidden but interactable)
            return els.isEmpty() ? null : els.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String attr(WebElement el, String name) {
        try {
            String v = el.getAttribute(name);
            return v != null ? v.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
