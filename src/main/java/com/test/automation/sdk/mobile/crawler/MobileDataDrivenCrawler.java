package com.test.automation.sdk.mobile.crawler;

import java.util.ArrayList;
import java.util.List;

import io.appium.java_client.AppiumDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileDataDrivenCrawler}. This facade
 * delegates to the new package while preserving the legacy source-level API.
 */
@Deprecated
public class MobileDataDrivenCrawler {

    private final com.test.automation.sdk.tools.crawler.mobile.MobileDataDrivenCrawler delegate;

    public MobileDataDrivenCrawler(AppiumDriver driver) {
        this.delegate = new com.test.automation.sdk.tools.crawler.mobile.MobileDataDrivenCrawler(driver);
    }

    public MobileDataDrivenCrawler setStateDeduplication(boolean enabled) {
        delegate.setStateDeduplication(enabled);
        return this;
    }

    public List<MobileElementInfo> crawlFlow(String flowName, List<MobileCrawlerStep> steps) {
        List<com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep> convertedSteps =
                new ArrayList<com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerStep>();
        for (MobileCrawlerStep step : steps) {
            convertedSteps.add(step.unwrap());
        }
        List<MobileElementInfo> converted = new ArrayList<MobileElementInfo>();
        for (com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo info :
                delegate.crawlFlow(flowName, convertedSteps)) {
            converted.add(new MobileElementInfo(info));
        }
        return converted;
    }
}
