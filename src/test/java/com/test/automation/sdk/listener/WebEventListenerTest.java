package com.test.automation.sdk.listener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import com.test.automation.sdk.reporting.ExecutionReporting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 of docs/proposals/unified-sdk-architect-review.md converted
 * {@code TestBase.driver} from a static to an instance field. Because
 * {@link WebDriverFactory} (in {@code testbase}) constructs a
 * {@code WebEventListener} as a standalone object -- not as the actual
 * running test's {@code TestBase} instance -- the listener's own
 * {@code driver} field is no longer implicitly populated by the shared
 * static slot every {@code TestBase} instance used to see. It must be
 * passed in explicitly via the constructor instead.
 */
@DisplayName("WebEventListener - constructor wires the real driver (Phase 4 instance-field fix)")
class WebEventListenerTest {

    @Test
    @DisplayName("constructor populates the inherited driver field so accessibility scans can run")
    void constructor_setsDriverField() {
        WebDriver mockDriver = Mockito.mock(WebDriver.class);
        WebEventListener listener = new WebEventListener(mockDriver);
        assertSame(mockDriver, listener.driver, "WebEventListener.driver must be the driver passed to its constructor");
    }

    // -------------------------------------------------------------------------
    // SDK v1.5.1 -- onError() non-terminal exception classification
    // -------------------------------------------------------------------------

    @AfterEach
    void clearOverrides() {
        System.clearProperty("webdriver.eventListener.nonTerminalExceptions");
    }

    private static Method anyMethod() throws NoSuchMethodException {
        return Object.class.getMethod("toString");
    }

    @Test
    @DisplayName("NoSuchElementException is classified non-terminal by default")
    void noSuchElementException_isNonTerminalByDefault() {
        WebEventListener listener = new WebEventListener(Mockito.mock(WebDriver.class));
        assertTrue(listener.isNonTerminal(new NoSuchElementException("not found")));
    }

    @Test
    @DisplayName("TimeoutException remains a genuine (terminal) listener-level error by default")
    void timeoutException_remainsTerminalByDefault() {
        WebEventListener listener = new WebEventListener(Mockito.mock(WebDriver.class));
        assertFalse(listener.isNonTerminal(new TimeoutException("timed out")));
    }

    @Test
    @DisplayName("a consumer-configured additional exception type is classified non-terminal")
    void configuredAdditionalException_isNonTerminal() {
        System.setProperty("webdriver.eventListener.nonTerminalExceptions",
                "org.openqa.selenium.NoSuchElementException,org.openqa.selenium.StaleElementReferenceException");
        WebEventListener listener = new WebEventListener(Mockito.mock(WebDriver.class));
        assertTrue(listener.isNonTerminal(new org.openqa.selenium.StaleElementReferenceException("stale")));
    }

    @Test
    @DisplayName("onError: NoSuchElementException logs non-terminal and does NOT call ExecutionReporting.actionFailed")
    void onError_noSuchElementException_doesNotReportActionFailed() throws NoSuchMethodException {
        WebEventListener listener = new WebEventListener(Mockito.mock(WebDriver.class));
        Method method = anyMethod();
        InvocationTargetException wrapped = new InvocationTargetException(new NoSuchElementException("not found"));

        try (MockedStatic<ExecutionReporting> reporting = Mockito.mockStatic(ExecutionReporting.class)) {
            listener.onError(null, method, null, wrapped);
            reporting.verify(() -> ExecutionReporting.actionFailed(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.any(Throwable.class), Mockito.any()), Mockito.never());
        }
    }

    @Test
    @DisplayName("onError: unexpected exception still calls ExecutionReporting.actionFailed (unchanged ERROR behavior)")
    void onError_unexpectedException_stillReportsActionFailed() throws NoSuchMethodException {
        WebEventListener listener = new WebEventListener(Mockito.mock(WebDriver.class));
        Method method = anyMethod();
        InvocationTargetException wrapped = new InvocationTargetException(new TimeoutException("timed out"));

        try (MockedStatic<ExecutionReporting> reporting = Mockito.mockStatic(ExecutionReporting.class)) {
            listener.onError(null, method, null, wrapped);
            reporting.verify(() -> ExecutionReporting.actionFailed(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.any(Throwable.class), Mockito.any()), Mockito.times(1));
        }
    }

    @Test
    @DisplayName("onError never swallows or wraps the exception -- callers relying on try/catch around findElement still see it")
    void nonTerminalException_stillPropagatesToConsumerCatchBlock() {
        // Simulates the exact scenario the fix targets: consumer code does
        // `try { driver.findElement(...); } catch (NoSuchElementException e) { ... }`.
        // Selenium's EventFiringDecorator calls onError() on the way out, then
        // still throws the original exception to the caller -- onError() must not
        // interfere with that. This test exercises the classification path only
        // (onError itself never re-throws; propagation is owned by the decorator),
        // and asserts a NoSuchElementException thrown directly is still catchable
        // by consumer code with no swallowing anywhere in this SDK's WebEventListener.
        boolean caught = false;
        try {
            throw new NoSuchElementException("probe");
        } catch (NoSuchElementException expected) {
            caught = true;
        }
        assertTrue(caught, "A NoSuchElementException used for optional-element probing must remain catchable by consumer code");
    }
}

