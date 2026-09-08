package com.test.automation.sdk.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertSame;

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
}
