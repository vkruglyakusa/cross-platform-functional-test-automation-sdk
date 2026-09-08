package com.test.automation.sdk.driver.web;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.driver.BrowserStackSupport;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.testbase.WebDriverFactory;

/**
 * {@link SessionFactory} for {@link Platform#WEB} + {@link RunMode#BROWSERSTACK}.
 *
 * NEW capability -- prior to this, the SDK had no first-class "run this
 * Selenium suite on BrowserStack Automate" concept at all (see
 * {@code docs/proposals/unified-sdk-architect-review.md} section C.4). Follows
 * the same pattern already proven by the mobile BrowserStack strategy: create
 * a normal local browser session via {@link WebDriverFactory}, and rely on the
 * BrowserStack Java SDK javaagent (attached via the "browserstack" Maven
 * profile's surefire {@code -javaagent} argLine) to transparently reroute it
 * to Automate using {@code browserstack.yml}. {@link BrowserStackSupport#verifyActive()}
 * fails fast if that javaagent/config isn't actually wired up, instead of
 * silently running against a real local browser by accident.
 */
public final class WebBrowserStackSessionFactory implements SessionFactory {

    @Override
    public Platform getPlatform() {
        return Platform.WEB;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.BROWSERSTACK;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        BrowserStackSupport.verifyActive();
        return WebDriverFactory.getWebDriver(context.getBrowserName());
    }
}
