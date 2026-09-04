package com.test.automation.sdk.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CrawlerScenario -- a named sequence of {@link CrawlerStep}s that expose
 * a specific dynamic page state for the {@link DataDrivenCrawler} to snapshot.
 *
 * Each scenario represents one "path" through a dynamic form:
 *   - Select "Complaint" from the Case Type dropdown -> new fields appear
 *   - Select "Request"   from the Case Type dropdown -> different fields appear
 *
 * Elements discovered exclusively in one scenario are tagged with that
 * scenario's name in the generated Page Object and report.
 *
 * TEST CASE BUILDER
 * -----------------
 * Use {@link #fromTestCase(String, List)} or {@link #fromTestCase(String, CrawlerStep...)}
 * to build a scenario directly from test case steps.  Each step is executed in order;
 * after every step the DOM is diffed and new elements are captured automatically.
 *
 * Example:
 * <pre>
 *   CrawlerScenario tc = CrawlerScenario.fromTestCase("TC-311 Noise Complaint",
 *       CrawlerStep.selectByLabel("Complaint Type", "Noise")
 *                  .describe("Step 1: Select Complaint Type = Noise"),
 *       CrawlerStep.selectByLabel("Noise Category", "Music")
 *                  .describe("Step 2: Select Noise Category = Music"),
 *       CrawlerStep.typeByPlaceholder("Describe the issue", "Loud music at night")
 *                  .describe("Step 3: Enter description"),
 *       CrawlerStep.clickByText("Next")
 *                  .describe("Step 4: Click Next")
 *   );
 * </pre>
 *
 * @author vkruglyak
 */
public class CrawlerScenario {

    /** Human-readable name shown in the Page Object comments and discovery report. */
    private final String name;

    /** Steps to execute in order before taking the DOM snapshot for this scenario. */
    private final List<CrawlerStep> steps = new ArrayList<>();

    /**
     * When true, the crawler takes an intermediate DOM snapshot after each step
     * (not just at the end of all steps). Useful for multi-level cascading forms
     * where each selection reveals a new dependent dropdown.
     *
     * Default: false -- snapshot only after all steps complete.
     * Always true when built via {@link #fromTestCase}.
     */
    private boolean snapshotAfterEachStep = false;

    /**
     * When true, the page is reloaded before executing this scenario's steps.
     * Use when scenarios are independent (each needs a fresh form state).
     *
     * Default: false -- continue from current page state.
     */
    private boolean resetPageBeforeRun = false;

    // =========================================================================
    // Constructors
    // =========================================================================

    public CrawlerScenario(String name) {
        this.name = name;
    }

    // =========================================================================
    // Test case builder -- preferred entry point for test-case-driven crawling
    // =========================================================================

    /**
     * Build a scenario from a list of test case steps.
     * Snapshot-after-each-step is enabled automatically so every DOM change
     * is captured as the crawler follows the complete test case flow.
     *
     * @param testCaseName human-readable test case name (e.g. "TC-311: Noise Complaint")
     * @param steps        ordered list of steps from the test case
     */
    public static CrawlerScenario fromTestCase(String testCaseName, List<CrawlerStep> steps) {
        CrawlerScenario s = new CrawlerScenario(testCaseName);
        s.steps.addAll(steps);
        s.snapshotAfterEachStep = true;
        return s;
    }

    /**
     * Build a scenario from test case steps (varargs overload).
     *
     * @param testCaseName human-readable test case name
     * @param steps        one or more steps from the test case
     */
    public static CrawlerScenario fromTestCase(String testCaseName, CrawlerStep... steps) {
        return fromTestCase(testCaseName, Arrays.asList(steps));
    }

    // =========================================================================
    // Fluent builder methods
    // =========================================================================

    /** Add a step and return {@code this} for chaining. */
    public CrawlerScenario addStep(CrawlerStep step) {
        steps.add(step);
        return this;
    }

    /**
     * Enable intermediate snapshots after every step.
     * Use for deeply cascading forms (each step may reveal new elements).
     */
    public CrawlerScenario snapshotAfterEachStep() {
        this.snapshotAfterEachStep = true;
        return this;
    }

    /**
     * Reload the page before running this scenario.
     * Use when scenarios are independent (each requires a fresh form).
     */
    public CrawlerScenario resetPageBeforeRun() {
        this.resetPageBeforeRun = true;
        return this;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String            getName()                 { return name;                  }
    public List<CrawlerStep> getSteps()                { return Collections.unmodifiableList(steps); }
    public boolean           isSnapshotAfterEachStep() { return snapshotAfterEachStep; }
    public boolean           isResetPageBeforeRun()    { return resetPageBeforeRun;    }

    @Override
    public String toString() {
        return "CrawlerScenario[\"" + name + "\", " + steps.size() + " step(s)"
            + (snapshotAfterEachStep ? ", snapshotPerStep" : "") + "]";
    }
}
