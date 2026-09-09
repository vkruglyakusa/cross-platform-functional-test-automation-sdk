package com.test.automation.sdk.utility;

import org.openqa.selenium.WebDriver;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.pageobject.PageObjectGenerator}. This compatibility facade
 * preserves the legacy {@code com.test.automation.sdk.utility.PageObjectGenerator} FQN.
 */
@Deprecated
public class PageObjectGenerator extends com.test.automation.sdk.tools.pageobject.PageObjectGenerator {

    public PageObjectGenerator(WebDriver driver) {
        super(driver);
    }
}
