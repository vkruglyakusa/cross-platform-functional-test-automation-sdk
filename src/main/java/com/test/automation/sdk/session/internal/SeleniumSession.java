package com.test.automation.sdk.session.internal;

import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.session.AutomationSession;

/**
 * {@link AutomationSession} implementation wrapping a plain Selenium
 * {@link WebDriver} (web/desktop browser sessions). Package-private
 * implementation detail -- obtained only via
 * {@code com.test.automation.sdk.session.AutomationSessionFactory}.
 */
public final class SeleniumSession implements AutomationSession {

    private final WebDriver driver;

    public SeleniumSession(WebDriver driver) {
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
