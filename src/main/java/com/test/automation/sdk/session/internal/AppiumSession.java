package com.test.automation.sdk.session.internal;

import io.appium.java_client.AppiumDriver;

import com.test.automation.sdk.session.AutomationSession;

/**
 * {@link AutomationSession} implementation wrapping an Appium
 * {@link AppiumDriver} (Android/iOS sessions). Package-private
 * implementation detail -- obtained only via
 * {@code com.test.automation.sdk.session.AutomationSessionFactory}.
 *
 * <p>Kept as a distinct implementation from {@link SeleniumSession} (rather
 * than reusing it, even though {@code AppiumDriver} already {@code implements
 * WebDriver}) so that Appium-specific behavior -- e.g. the NATIVE_APP/WEBVIEW
 * context-switch helpers planned immediately after this phase -- has a
 * natural home without changing the shared {@link AutomationSession}
 * contract.
 */
public final class AppiumSession implements AutomationSession {

    private final AppiumDriver driver;

    public AppiumSession(AppiumDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("driver must not be null");
        }
        this.driver = driver;
    }

    @Override
    public void navigate(String url) {
        driver.get(url);
    }

    @Override
    public void quit() {
        driver.quit();
    }

    @Override
    public <T> T unwrap(Class<T> technologyType) {
        if (technologyType != null && technologyType.isInstance(driver)) {
            return technologyType.cast(driver);
        }
        throw new IllegalArgumentException(
                "Underlying driver (" + driver.getClass().getName()
                        + ") is not an instance of " + technologyType);
    }
}
