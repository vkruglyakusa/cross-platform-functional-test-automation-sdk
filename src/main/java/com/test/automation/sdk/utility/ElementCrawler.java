package com.test.automation.sdk.utility;

import org.openqa.selenium.WebDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real crawler implementation to
 * {@link com.test.automation.sdk.tools.crawler.web.ElementCrawler}. This class is a
 * compatibility facade so existing consumers using the legacy
 * {@code com.test.automation.sdk.utility.ElementCrawler} FQN continue to compile.
 */
@Deprecated
public class ElementCrawler extends com.test.automation.sdk.tools.crawler.web.ElementCrawler {

    public ElementCrawler(WebDriver driver) {
        super(driver);
    }
}
