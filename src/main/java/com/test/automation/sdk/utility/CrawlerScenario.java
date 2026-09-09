package com.test.automation.sdk.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.web.CrawlerScenario}. This facade keeps the
 * legacy {@code com.test.automation.sdk.utility.CrawlerScenario} FQN source-compatible.
 */
@Deprecated
public class CrawlerScenario {

    private final com.test.automation.sdk.tools.crawler.web.CrawlerScenario delegate;

    public CrawlerScenario(String name) {
        this(new com.test.automation.sdk.tools.crawler.web.CrawlerScenario(name));
    }

    private CrawlerScenario(com.test.automation.sdk.tools.crawler.web.CrawlerScenario delegate) {
        this.delegate = delegate;
    }

    public static CrawlerScenario fromTestCase(String testCaseName, List<CrawlerStep> steps) {
        List<com.test.automation.sdk.tools.crawler.web.CrawlerStep> converted =
                new ArrayList<com.test.automation.sdk.tools.crawler.web.CrawlerStep>();
        for (CrawlerStep step : steps) {
            converted.add(step.unwrap());
        }
        return new CrawlerScenario(
                com.test.automation.sdk.tools.crawler.web.CrawlerScenario.fromTestCase(testCaseName, converted));
    }

    public static CrawlerScenario fromTestCase(String testCaseName, CrawlerStep... steps) {
        return fromTestCase(testCaseName, Arrays.asList(steps));
    }

    public CrawlerScenario addStep(CrawlerStep step) {
        delegate.addStep(step.unwrap());
        return this;
    }

    public CrawlerScenario snapshotAfterEachStep() {
        delegate.snapshotAfterEachStep();
        return this;
    }

    public CrawlerScenario resetPageBeforeRun() {
        delegate.resetPageBeforeRun();
        return this;
    }

    public String getName() {
        return delegate.getName();
    }

    public List<CrawlerStep> getSteps() {
        List<CrawlerStep> converted = new ArrayList<CrawlerStep>();
        for (com.test.automation.sdk.tools.crawler.web.CrawlerStep step : delegate.getSteps()) {
            converted.add(new CrawlerStep(step));
        }
        return Collections.unmodifiableList(converted);
    }

    public boolean isSnapshotAfterEachStep() {
        return delegate.isSnapshotAfterEachStep();
    }

    public boolean isResetPageBeforeRun() {
        return delegate.isResetPageBeforeRun();
    }

    com.test.automation.sdk.tools.crawler.web.CrawlerScenario unwrap() {
        return delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
