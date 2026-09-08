package com.test.automation.sdk.driver;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.driver.mobile.AndroidBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.AndroidLocalSessionFactory;
import com.test.automation.sdk.driver.mobile.IosBrowserStackSessionFactory;
import com.test.automation.sdk.driver.mobile.IosLocalSessionFactory;
import com.test.automation.sdk.driver.web.WebBrowserStackSessionFactory;
import com.test.automation.sdk.driver.web.WebLocalSessionFactory;
import com.test.automation.sdk.execution.ExecutionContext;
import com.test.automation.sdk.execution.SessionFactoryRegistry;

/**
 * Single public entry point for acquiring a driver session for any
 * {@link ExecutionContext}, replacing the previous two independent entry
 * points ({@code testbase.WebDriverFactory} and
 * {@code mobile.driver.MobileDriverFactory}).
 *
 * Registers all six built-in {@code SessionFactory} implementations
 * (web local/BrowserStack, Android local/BrowserStack, iOS
 * local/BrowserStack) with {@link SessionFactoryRegistry} on class
 * initialization, so callers only ever need
 * {@code DriverManager.acquire(context)}.
 *
 * {@code WebDriverFactory}/{@code MobileDriverFactory} are NOT modified by
 * this class -- they remain the underlying implementation that the web/
 * Android/iOS local factories delegate to, and continue to work unchanged
 * for any existing caller. This class is additive; nothing is wired to it
 * yet (see Phase 4 of {@code docs/proposals/unified-sdk-architect-review.md}
 * for the planned {@code TestBase} integration).
 */
public final class DriverManager {

    static {
        SessionFactoryRegistry.register(new WebLocalSessionFactory());
        SessionFactoryRegistry.register(new WebBrowserStackSessionFactory());
        SessionFactoryRegistry.register(new AndroidLocalSessionFactory());
        SessionFactoryRegistry.register(new AndroidBrowserStackSessionFactory());
        SessionFactoryRegistry.register(new IosLocalSessionFactory());
        SessionFactoryRegistry.register(new IosBrowserStackSessionFactory());
    }

    private DriverManager() {}

    /** Acquires a driver session for the given context, resolving the matching registered {@code SessionFactory}. */
    public static WebDriver acquire(ExecutionContext context) {
        return SessionFactoryRegistry.resolve(context).createDriver(context);
    }
}
