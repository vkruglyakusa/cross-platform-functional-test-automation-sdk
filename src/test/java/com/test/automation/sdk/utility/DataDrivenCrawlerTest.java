package com.test.automation.sdk.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataDrivenCrawler public API contract.
 *
 * DataDrivenCrawler is driven almost entirely through a live WebDriver/JS executor
 * (page navigation, MutationObserver injection, step execution), so these tests focus
 * on the public, WebDriver-independent surface -- fluent builder methods and their
 * signatures -- rather than full behavioral coverage, which belongs in an
 * integration/e2e suite against a real browser.
 */
@DisplayName("DataDrivenCrawler - public API contract")
class DataDrivenCrawlerTest {

    @Test
    @DisplayName("DataDrivenCrawler class is public")
    void classIsPublic() {
        assertTrue(Modifier.isPublic(DataDrivenCrawler.class.getModifiers()),
            "DataDrivenCrawler must be a public class");
    }

    @Test
    @DisplayName("setDiffOnlyMode(boolean) is public and fluent (returns DataDrivenCrawler)")
    void setDiffOnlyModeIsPublicAndFluent() throws NoSuchMethodException {
        Method m = DataDrivenCrawler.class.getMethod("setDiffOnlyMode", boolean.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "setDiffOnlyMode must be public");
        assertEquals(DataDrivenCrawler.class, m.getReturnType(), "setDiffOnlyMode must be fluent");
    }

    @Test
    @DisplayName("setStateDeduplication(boolean) is public and fluent (new, state-fingerprint dedup)")
    void setStateDeduplicationIsPublicAndFluent() throws NoSuchMethodException {
        Method m = DataDrivenCrawler.class.getMethod("setStateDeduplication", boolean.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "setStateDeduplication must be public");
        assertEquals(DataDrivenCrawler.class, m.getReturnType(), "setStateDeduplication must be fluent");
    }

    @Test
    @DisplayName("addScenario(CrawlerScenario) is public and fluent")
    void addScenarioIsPublicAndFluent() throws NoSuchMethodException {
        Method m = DataDrivenCrawler.class.getMethod("addScenario", CrawlerScenario.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "addScenario must be public");
        assertEquals(DataDrivenCrawler.class, m.getReturnType(), "addScenario must be fluent");
    }

    @Test
    @DisplayName("crawlTestCase(String, String, List) is public")
    void crawlTestCaseIsPublic() throws NoSuchMethodException {
        Method m = DataDrivenCrawler.class.getMethod("crawlTestCase",
                String.class, String.class, java.util.List.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "crawlTestCase must be public");
    }

    @Test
    @DisplayName("crawl(String) is public")
    void crawlIsPublic() throws NoSuchMethodException {
        Method m = DataDrivenCrawler.class.getMethod("crawl", String.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "crawl must be public");
    }
}
