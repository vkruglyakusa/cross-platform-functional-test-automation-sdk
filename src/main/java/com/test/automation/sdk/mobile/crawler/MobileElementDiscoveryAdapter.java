package com.test.automation.sdk.mobile.crawler;

import com.test.automation.sdk.discovery.DiscoveredElement;
import com.test.automation.sdk.discovery.DiscoveryResult;
import com.test.automation.sdk.discovery.ElementDiscoveryService;

import io.appium.java_client.AppiumDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter}. This facade
 * preserves the legacy FQN and forwards to the new package.
 */
@Deprecated
public class MobileElementDiscoveryAdapter implements ElementDiscoveryService {

    private final com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter delegate;

    public MobileElementDiscoveryAdapter(AppiumDriver driver) {
        this.delegate = new com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter(driver);
    }

    public MobileElementDiscoveryAdapter(MobileElementCrawler crawler) {
        this.delegate = new com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter(crawler.unwrap());
    }

    @Override
    public DiscoveryResult discoverCurrent() {
        return delegate.discoverCurrent();
    }

    public static DiscoveryResult toDiscoveryResult(MobileScreenSnapshot snapshot) {
        return com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter.toDiscoveryResult(
                snapshot.unwrap());
    }

    public static DiscoveredElement toDiscoveredElement(MobileElementInfo info) {
        return com.test.automation.sdk.tools.crawler.mobile.MobileElementDiscoveryAdapter.toDiscoveredElement(
                info.unwrap());
    }
}
