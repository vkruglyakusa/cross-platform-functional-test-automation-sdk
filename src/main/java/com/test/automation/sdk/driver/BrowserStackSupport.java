package com.test.automation.sdk.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.browserstack.BrowserStackSdk;

/**
 * Shared helper for any {@code SessionFactory} that runs against BrowserStack
 * via the official BrowserStack Java SDK javaagent (Automate for web, App
 * Automate for mobile). Mirrors the verification previously inlined in
 * {@code mobile.execution.BrowserStackExecutionStrategy} /
 * {@code mobile.testbase.MobileTestBase.isRunningInCloud()}, generalized so
 * the new web BrowserStack factory can reuse the same fail-fast check
 * instead of duplicating it.
 */
public final class BrowserStackSupport {

    private static final Logger log = LogManager.getLogger(BrowserStackSupport.class);

    private BrowserStackSupport() {}

    /**
     * Confirms the BrowserStack Java SDK javaagent actually rerouted the
     * about-to-be-created local session, rather than silently falling back
     * to a real local Selenium/Appium server.
     *
     * @throws IllegalStateException if the javaagent/{@code browserstack.yml}
     *                                are not correctly wired up.
     */
    public static void verifyActive() {
        try {
            if (BrowserStackSdk.getCurrentPlatform().isEmpty()) {
                throw new IllegalStateException("BrowserStack SDK reports no active platform");
            }
        } catch (Exception e) {
            log.error("Execution target is BROWSERSTACK but the BrowserStack SDK javaagent/config is not "
                    + "wired up correctly -- check pom.xml (-javaagent) and browserstack.yml.", e);
            throw new IllegalStateException(
                    "BrowserStack session could not be confirmed; see log for details.", e);
        }
    }
}
