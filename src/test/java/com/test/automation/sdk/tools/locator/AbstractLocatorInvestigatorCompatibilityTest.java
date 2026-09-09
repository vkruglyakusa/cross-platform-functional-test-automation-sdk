package com.test.automation.sdk.tools.locator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbstractLocatorInvestigator package move compatibility")
class AbstractLocatorInvestigatorCompatibilityTest {

    @Test
    @DisplayName("legacy tools.AbstractLocatorInvestigator remains assignable to the new locator package base class")
    void legacyFacadeRemainsAssignable() {
        assertTrue(AbstractLocatorInvestigator.class
                .isAssignableFrom(com.test.automation.sdk.tools.AbstractLocatorInvestigator.class));
    }
}
