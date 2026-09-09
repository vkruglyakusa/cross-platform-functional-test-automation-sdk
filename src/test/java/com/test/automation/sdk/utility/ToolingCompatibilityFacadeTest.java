package com.test.automation.sdk.utility;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Legacy web tooling compatibility facades")
class ToolingCompatibilityFacadeTest {

    @Test
    @DisplayName("legacy CrawlerStep and CrawlerScenario delegate to the new web tooling package")
    void crawlerStepAndScenarioRemainUsable() {
        CrawlerStep step = CrawlerStep.selectByLabel("Category", "Noise").describe("Step 1");
        CrawlerScenario scenario = CrawlerScenario.fromTestCase("TC-1", Arrays.asList(step));

        assertEquals(CrawlerStep.Action.SELECT, step.getAction());
        assertEquals(CrawlerStep.LocatorMode.BY_LABEL, step.getLocatorMode());
        assertEquals(1, scenario.getSteps().size());
        assertTrue(scenario.isSnapshotAfterEachStep());
        assertEquals("Step 1", scenario.getSteps().get(0).getDescription());
    }

    @Test
    @DisplayName("legacy DataDrivenCrawler fluent mutators still return the legacy facade")
    void dataDrivenCrawlerFluentMethodsReturnFacade() {
        WebDriver driver = mock(WebDriver.class);
        DataDrivenCrawler crawler = new DataDrivenCrawler(driver);

        assertSame(crawler, crawler.setDiffOnlyMode(true));
        assertSame(crawler, crawler.setStateDeduplication(false));
        assertSame(crawler, crawler.addScenario(new CrawlerScenario("Scenario A")));
    }

    @Test
    @DisplayName("legacy ElementCrawler and PageObjectGenerator extend the new implementations")
    void classHierarchyPreservesCompatibility() {
        assertTrue(com.test.automation.sdk.tools.crawler.web.ElementCrawler.class.isAssignableFrom(ElementCrawler.class));
        assertTrue(com.test.automation.sdk.tools.pageobject.PageObjectGenerator.class.isAssignableFrom(PageObjectGenerator.class));
        assertTrue(com.test.automation.sdk.tools.locator.AbstractLocatorInvestigator.class
                .isAssignableFrom(com.test.automation.sdk.tools.AbstractLocatorInvestigator.class));
    }
}
