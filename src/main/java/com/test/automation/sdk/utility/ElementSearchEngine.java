package com.test.automation.sdk.utility;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.web.ElementSearchEngine}. This class extends the
 * new implementation and adds a legacy overload accepting the old {@link CrawlerStep} facade.
 */
@Deprecated
public class ElementSearchEngine extends com.test.automation.sdk.tools.crawler.web.ElementSearchEngine {

    public ElementSearchEngine(WebDriver driver) {
        super(driver);
    }

    public WebElement resolve(CrawlerStep step) {
        return super.resolve(step.unwrap());
    }
}
