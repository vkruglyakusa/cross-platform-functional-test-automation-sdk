package com.test.automation.sdk.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserStackSupportTest {

    @Test
    void verifyActive_throwsIllegalStateException_whenNoJavaagentAttached() {
        // No BrowserStack javaagent is attached in unit tests, so verifyActive()
        // must fail fast rather than silently proceeding.
        assertThrows(IllegalStateException.class, BrowserStackSupport::verifyActive);
    }
}
