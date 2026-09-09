package com.test.automation.sdk.tools;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.locator.AbstractLocatorInvestigator}. This abstract facade
 * preserves the legacy {@code com.test.automation.sdk.tools.AbstractLocatorInvestigator} FQN for
 * consumer test suites that already extend it.
 */
@Deprecated
public abstract class AbstractLocatorInvestigator
        extends com.test.automation.sdk.tools.locator.AbstractLocatorInvestigator {
}
