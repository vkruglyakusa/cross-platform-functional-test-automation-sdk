package com.test.automation.sdk.session;

/**
 * Technology-neutral session contract -- Structure Cleanup Phase 3
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md, sections
 * 5.1/7/20). Intentionally minimal: only the lifecycle operations that are
 * genuinely common to every current and future automation technology
 * (Selenium, Appium, and any future engine).
 *
 * <p>Per the adopted architecture's scope decision for this phase,
 * {@code AutomationElement}/{@code Locator} are deliberately NOT introduced
 * yet -- they are deferred until crawler/Page Object portability creates a
 * concrete need for them (see the "Priority &amp; Sequencing Adjustments"
 * note in the architecture document). Existing Page Objects continue to use
 * {@code WebElement}/Appium types directly; this interface only replaces
 * {@code WebDriverFactory}/{@code MobileDriverFactory} as the technology-
 * neutral session handle, unifying how a session is created and torn down.
 *
 * <p>{@link #unwrap(Class)} is the intentional escape hatch for any
 * technology-specific capability that does not belong in this common
 * surface (per Guardrail #8/#9 -- do not recreate the complete Selenium/
 * Appium API).
 */
public interface AutomationSession {

    /** Navigates the underlying session to the given URL (web) or deep link (mobile web view). */
    void navigate(String url);

    /** Ends the underlying session, releasing any browser/device resources. */
    void quit();

    /**
     * Returns the underlying technology-specific driver instance (e.g.
     * {@code WebDriver}, {@code AppiumDriver}, {@code AndroidDriver},
     * {@code IOSDriver}) cast to the requested type.
     *
     * @throws IllegalArgumentException if the underlying driver is not an instance of {@code technologyType}
     */
    <T> T unwrap(Class<T> technologyType);
}
