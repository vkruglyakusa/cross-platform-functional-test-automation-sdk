package com.test.automation.sdk.session.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("SeleniumSession -- AutomationSession wrapping a plain WebDriver")
class SeleniumSessionTest {

    @Test
    @DisplayName("constructor rejects a null driver")
    void constructor_rejectsNullDriver() {
        assertThrows(IllegalArgumentException.class, () -> new SeleniumSession(null));
    }

    @Test
    @DisplayName("navigate() delegates to WebDriver.get(url)")
    void navigate_delegatesToGet() {
        WebDriver driver = mock(WebDriver.class);
        SeleniumSession session = new SeleniumSession(driver);
        session.navigate("https://example.com");
        verify(driver).get("https://example.com");
    }

    @Test
    @DisplayName("quit() delegates to WebDriver.quit()")
    void quit_delegatesToQuit() {
        WebDriver driver = mock(WebDriver.class);
        SeleniumSession session = new SeleniumSession(driver);
        session.quit();
        verify(driver).quit();
    }

    @Test
    @DisplayName("unwrap(WebDriver.class) returns the underlying driver")
    void unwrap_returnsUnderlyingDriver() {
        WebDriver driver = mock(WebDriver.class);
        SeleniumSession session = new SeleniumSession(driver);
        assertSame(driver, session.unwrap(WebDriver.class));
    }

    @Test
    @DisplayName("unwrap() throws for a technology type the driver is not an instance of")
    void unwrap_throwsForMismatchedType() {
        WebDriver driver = mock(WebDriver.class);
        SeleniumSession session = new SeleniumSession(driver);
        assertThrows(IllegalArgumentException.class, () -> session.unwrap(String.class));
    }
}
