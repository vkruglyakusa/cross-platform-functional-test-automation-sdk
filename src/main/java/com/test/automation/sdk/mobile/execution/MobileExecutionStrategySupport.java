package com.test.automation.sdk.mobile.execution;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.test.automation.sdk.mobile.config.MobileConfigReader;

/**
 * Shared helpers used by every {@link MobileExecutionStrategy} implementation. Kept as
 * plain static utilities (composition) rather than an abstract base class, since the two
 * current strategies share only a couple of small helpers, not behavior worth an
 * inheritance hierarchy.
 */
final class MobileExecutionStrategySupport {

    private MobileExecutionStrategySupport() {}

    static String resolveAppPath(String configuredPath) {
        File f = new File(configuredPath);
        return f.isAbsolute() ? f.getAbsolutePath()
                : new File(System.getProperty("user.dir"), configuredPath).getAbsolutePath();
    }

    /**
     * Every strategy (local or cloud) connects to this same local Appium URL. For
     * {@link ExecutionTarget#BROWSERSTACK}, the BrowserStack Java SDK javaagent
     * transparently intercepts this call and reroutes the session to App Automate using
     * browserstack.yml -- strategies do not need to know or care that this happens.
     */
    static URL localAppiumUrl() {
        String urlString = MobileConfigReader.get("appium.localUrl", "http://127.0.0.1:4723/");
        try {
            return new URI(urlString).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Invalid appium.localUrl in mobile-config.yaml: " + urlString, e);
        }
    }
}
