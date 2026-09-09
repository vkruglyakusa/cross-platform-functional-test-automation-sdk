package com.test.automation.sdk.session.internal;

import io.appium.java_client.AppiumDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AppiumSession -- AutomationSession wrapping an AppiumDriver")
class AppiumSessionTest {

    @Test
    @DisplayName("constructor rejects a null driver")
    void constructor_rejectsNullDriver() {
        assertThrows(IllegalArgumentException.class, () -> new AppiumSession(null));
    }

    @Test
    @DisplayName("navigate() delegates to AppiumDriver.get(url)")
    void navigate_delegatesToGet() {
        AppiumDriver driver = mock(AppiumDriver.class);
        AppiumSession session = new AppiumSession(driver);
        session.navigate("https://example.com");
        verify(driver).get("https://example.com");
    }

    @Test
    @DisplayName("quit() delegates to AppiumDriver.quit()")
    void quit_delegatesToQuit() {
        AppiumDriver driver = mock(AppiumDriver.class);
        AppiumSession session = new AppiumSession(driver);
        session.quit();
        verify(driver).quit();
    }

    @Test
    @DisplayName("unwrap(AppiumDriver.class) returns the underlying driver")
    void unwrap_returnsUnderlyingDriver() {
        AppiumDriver driver = mock(AppiumDriver.class);
        AppiumSession session = new AppiumSession(driver);
        assertSame(driver, session.unwrap(AppiumDriver.class));
    }

    @Test
    @DisplayName("unwrap() throws for a technology type the driver is not an instance of")
    void unwrap_throwsForMismatchedType() {
        AppiumDriver driver = mock(AppiumDriver.class);
        AppiumSession session = new AppiumSession(driver);
        assertThrows(IllegalArgumentException.class, () -> session.unwrap(String.class));
    }
}
