package com.test.automation.sdk.driver.web;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.testbase.WebDriverFactory;

/**
 * {@link SessionFactory} for {@link Platform#WEB} + {@link RunMode#LOCAL}.
 *
 * Deliberately delegates to the existing, already-tested
 * {@link WebDriverFactory#getWebDriver(String)} rather than duplicating its
 * Chrome/Firefox/Edge option-building logic -- see
 * {@code docs/proposals/unified-sdk-architect-review.md} Phase 2, which calls
 * for driver ACQUISITION to be unified without rewriting the mature
 * browser-specific creation code underneath it.
 */
public final class WebLocalSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.WEB;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.LOCAL;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        return WebDriverFactory.getWebDriver(context.getBrowserName());
    }
}
