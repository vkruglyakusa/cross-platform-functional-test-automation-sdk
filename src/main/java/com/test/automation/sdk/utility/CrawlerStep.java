package com.test.automation.sdk.utility;

/**
 * CrawlerStep -- a single interaction within a {@link CrawlerScenario} or test case flow.
 *
 * LOCATOR MODES
 * -------------
 * Two ways to target an element:
 *
 *  1. Raw XPath  -- exact locator, used when you know the XPath up front.
 *       CrawlerStep.select("//mat-select[@formcontrolname='caseType']", "Complaint")
 *
 *  2. Semantic   -- human-readable hint resolved live from the DOM by {@link ElementSearchEngine}.
 *     The crawler finds the element the same way a tester would describe it.
 *       CrawlerStep.selectByLabel("Category", "Complaint")
 *       CrawlerStep.typeByPlaceholder("Enter description", "My description")
 *       CrawlerStep.clickByText("Submit")
 *       CrawlerStep.clickByAriaLabel("Close dialog")
 *       CrawlerStep.typeByFormControlName("streetAddress", "123 Main St")
 *
 * ACTIONS
 * -------
 *   SELECT  -- select dropdown option by visible text (native select or mat-select / custom)
 *   TYPE    -- clear + type text into an input or textarea
 *   CLICK   -- click any element
 *   CLEAR   -- clear an input field
 *   WAIT    -- fixed pause in milliseconds (for async rendering, animations)
 *
 * DESCRIPTION
 * -----------
 * Every step carries an optional human-readable description (ideally copied directly
 * from the ADO test case step). This description is used to tag discovered elements
 * in the generated Page Object:
 *   // [Step 3: Select Category = Noise]
 *
 * @author vkruglyak
 */
public class CrawlerStep {

    // -- Action ----------------------------------------------------------------

    public enum Action { SELECT, TYPE, CLICK, WAIT, CLEAR }

    // -- Locator resolution mode -----------------------------------------------

    public enum LocatorMode {
        /** Caller-supplied raw XPath -- used as-is. */
        XPATH,
        /** Resolved by ElementSearchEngine from a visible label / mat-label. */
        BY_LABEL,
        /** Resolved by ElementSearchEngine from @placeholder attribute. */
        BY_PLACEHOLDER,
        /** Resolved by ElementSearchEngine from visible element text (buttons, links). */
        BY_TEXT,
        /** Resolved by ElementSearchEngine from @aria-label attribute. */
        BY_ARIA_LABEL,
        /** Resolved by ElementSearchEngine from @formcontrolname attribute. */
        BY_FORM_CONTROL_NAME
    }

    // -- Fields ----------------------------------------------------------------

    private final Action      action;
    private final LocatorMode locatorMode;

    /** Raw XPath (when mode=XPATH) or semantic hint string (all other modes). */
    private final String locator;

    /** Value: option text for SELECT, typed text for TYPE, millis (as string) for WAIT. */
    private final String value;

    /**
     * Human-readable description from the test case step.
     * Copied directly from ADO or written by the test author.
     * Used to tag elements in the generated Page Object.
     */
    private String description = "";

    // =========================================================================
    // Raw XPath factories
    // =========================================================================

    /** Select a dropdown option using a raw XPath locator. */
    public static CrawlerStep select(String xpath, String visibleText) {
        return new CrawlerStep(Action.SELECT, LocatorMode.XPATH, xpath, visibleText);
    }

    /** Type into an element using a raw XPath locator. */
    public static CrawlerStep type(String xpath, String value) {
        return new CrawlerStep(Action.TYPE, LocatorMode.XPATH, xpath, value);
    }

    /** Click an element using a raw XPath locator. */
    public static CrawlerStep click(String xpath) {
        return new CrawlerStep(Action.CLICK, LocatorMode.XPATH, xpath, "");
    }

    /** Clear an input using a raw XPath locator. */
    public static CrawlerStep clear(String xpath) {
        return new CrawlerStep(Action.CLEAR, LocatorMode.XPATH, xpath, "");
    }

    /**
     * Pause for a fixed number of milliseconds.
     * Use after steps that trigger async DOM rendering.
     */
    public static CrawlerStep wait(int millis) {
        return new CrawlerStep(Action.WAIT, LocatorMode.XPATH, "", String.valueOf(millis));
    }

    // =========================================================================
    // Semantic factories -- resolved live by ElementSearchEngine
    // =========================================================================

    /** Select a dropdown option by finding the field via its visible label text. */
    public static CrawlerStep selectByLabel(String labelText, String optionValue) {
        return new CrawlerStep(Action.SELECT, LocatorMode.BY_LABEL, labelText, optionValue);
    }

    /** Type into a field by finding it via its @placeholder text. */
    public static CrawlerStep typeByPlaceholder(String placeholder, String value) {
        return new CrawlerStep(Action.TYPE, LocatorMode.BY_PLACEHOLDER, placeholder, value);
    }

    /** Type into a field by finding it via its @formcontrolname attribute. */
    public static CrawlerStep typeByFormControlName(String formControlName, String value) {
        return new CrawlerStep(Action.TYPE, LocatorMode.BY_FORM_CONTROL_NAME, formControlName, value);
    }

    /** Click an element by finding it via its visible text. */
    public static CrawlerStep clickByText(String visibleText) {
        return new CrawlerStep(Action.CLICK, LocatorMode.BY_TEXT, visibleText, "");
    }

    /** Click an element by finding it via its @aria-label. */
    public static CrawlerStep clickByAriaLabel(String ariaLabel) {
        return new CrawlerStep(Action.CLICK, LocatorMode.BY_ARIA_LABEL, ariaLabel, "");
    }

    /** Select a dropdown by finding it via its @aria-label. */
    public static CrawlerStep selectByAriaLabel(String ariaLabel, String optionValue) {
        return new CrawlerStep(Action.SELECT, LocatorMode.BY_ARIA_LABEL, ariaLabel, optionValue);
    }

    /** Select a dropdown by finding it via its @formcontrolname attribute. */
    public static CrawlerStep selectByFormControlName(String formControlName, String optionValue) {
        return new CrawlerStep(Action.SELECT, LocatorMode.BY_FORM_CONTROL_NAME, formControlName, optionValue);
    }

    /** Click a field found via its visible label text (e.g. a labelled checkbox). */
    public static CrawlerStep clickByLabel(String labelText) {
        return new CrawlerStep(Action.CLICK, LocatorMode.BY_LABEL, labelText, "");
    }

    // =========================================================================
    // Description fluent setter
    // =========================================================================

    /**
     * Attach a human-readable description (from the test case step).
     * Returns {@code this} for chaining.
     *
     * Example:
     *   CrawlerStep.selectByLabel("Category", "Noise").describe("Step 1: Select complaint category")
     */
    public CrawlerStep describe(String description) {
        this.description = description;
        return this;
    }

    // =========================================================================
    // Private constructor
    // =========================================================================

    private CrawlerStep(Action action, LocatorMode locatorMode, String locator, String value) {
        this.action      = action;
        this.locatorMode = locatorMode;
        this.locator     = locator;
        this.value       = value;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public Action      getAction()      { return action;      }
    public LocatorMode getLocatorMode() { return locatorMode; }
    public String      getLocator()     { return locator;     }
    public String      getValue()       { return value;       }
    public String      getDescription() { return description; }

    /** True when this step uses a semantic hint that needs live DOM resolution. */
    public boolean isSemantic() { return locatorMode != LocatorMode.XPATH; }

    /** Short label used in logs and Page Object comments. */
    public String label() {
        if (!description.isEmpty()) return description;
        switch (action) {
            case SELECT: return "Select [" + locator + "] = \"" + value + "\"";
            case TYPE:   return "Type   [" + locator + "] = \"" + value + "\"";
            case CLICK:  return "Click  [" + locator + "]";
            case CLEAR:  return "Clear  [" + locator + "]";
            case WAIT:   return "Wait   " + value + "ms";
            default:     return action + " [" + locator + "]";
        }
    }

    @Override
    public String toString() {
        return "[" + action + "/" + locatorMode + "] " + locator
            + (value.isEmpty() ? "" : " = \"" + value + "\"")
            + (description.isEmpty() ? "" : "  // " + description);
    }
}
