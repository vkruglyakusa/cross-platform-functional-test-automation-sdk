package com.test.automation.sdk.utility;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.WebDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.web.DataDrivenCrawler}. This facade delegates to
 * the new package while preserving the legacy source-level API.
 */
@Deprecated
public class DataDrivenCrawler {

    private final com.test.automation.sdk.tools.crawler.web.DataDrivenCrawler delegate;

    public DataDrivenCrawler(WebDriver driver) {
        this.delegate = new com.test.automation.sdk.tools.crawler.web.DataDrivenCrawler(driver);
    }

    public DataDrivenCrawler addScenario(CrawlerScenario scenario) {
        delegate.addScenario(scenario.unwrap());
        return this;
    }

    public DataDrivenCrawler setDiffOnlyMode(boolean diffOnly) {
        delegate.setDiffOnlyMode(diffOnly);
        return this;
    }

    public DataDrivenCrawler setStateDeduplication(boolean enabled) {
        delegate.setStateDeduplication(enabled);
        return this;
    }

    public List<ElementCrawler.ElementInfo> crawlTestCase(String url, String testCaseName, List<CrawlerStep> steps) {
        List<com.test.automation.sdk.tools.crawler.web.CrawlerStep> converted =
                new ArrayList<com.test.automation.sdk.tools.crawler.web.CrawlerStep>();
        for (CrawlerStep step : steps) {
            converted.add(step.unwrap());
        }
        return castElementInfos(delegate.crawlTestCase(url, testCaseName, converted));
    }

    public List<ElementCrawler.ElementInfo> crawl(String url) {
        return castElementInfos(delegate.crawl(url));
    }

    com.test.automation.sdk.tools.crawler.web.DataDrivenCrawler unwrap() {
        return delegate;
    }

    @SuppressWarnings("unchecked")
    private static List<ElementCrawler.ElementInfo> castElementInfos(
            List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo> infos) {
        return (List<ElementCrawler.ElementInfo>) (List<?>) infos;
    }
}
