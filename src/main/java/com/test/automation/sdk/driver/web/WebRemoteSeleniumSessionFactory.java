package com.test.automation.sdk.driver.web;

import java.net.URI;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.config.ConfigurationManager;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.Platform;
import com.test.automation.sdk.execution.ProviderId;
import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.execution.SessionFactory;
import com.test.automation.sdk.testbase.WebDriverFactory;

/** Selenium Grid-compatible Web execution through the built-in custom provider. */
public final class WebRemoteSeleniumSessionFactory implements SessionFactory {

    private static final ProviderId PROVIDER = new ProviderId("custom");

    @Override
    public Platform getPlatform() {
        return Platform.WEB;
    }

    @Override
    public RunMode getRunMode() {
        return RunMode.REMOTE;
    }

    @Override
    public ProviderId getProviderId() {
        return PROVIDER;
    }

    @Override
    public WebDriver createDriver(ExecutionContext context) {
        URI hubUri = ConfigurationManager.getRemoteProviderConfig(PROVIDER.value()).hubUri();
        return WebDriverFactory.getRemoteWebDriver(context.getBrowserName(), hubUri);
    }
}
