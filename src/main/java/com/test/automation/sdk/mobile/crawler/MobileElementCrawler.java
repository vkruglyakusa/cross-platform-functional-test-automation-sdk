package com.test.automation.sdk.mobile.crawler;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;

import io.appium.java_client.AppiumDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler}. This facade preserves
 * the legacy FQN while delegating to the new package.
 */
@Deprecated
public class MobileElementCrawler {

    private final com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler delegate;

    public MobileElementCrawler(AppiumDriver driver) {
        this.delegate = new com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler(driver);
    }

    MobileElementCrawler(com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler delegate) {
        this.delegate = delegate;
    }

    public MobileScreenSnapshot crawlCurrentScreen() {
        return new MobileScreenSnapshot(delegate.crawlCurrentScreen());
    }

    public void verifyCandidateRemotely(MobileLocatorCandidate candidate, By by) {
        delegate.verifyCandidateRemotely(candidate.unwrap(), by);
    }

    public static List<MobileElementInfo> analyzePageSource(String pageSourceXml, String platform) {
        List<MobileElementInfo> converted = new ArrayList<MobileElementInfo>();
        for (com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo info :
                com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler.analyzePageSource(pageSourceXml, platform)) {
            converted.add(new MobileElementInfo(info));
        }
        return converted;
    }

    com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler unwrap() {
        return delegate;
    }
}
