package com.test.automation.sdk.tools.locator;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbstractMobileLocatorInvestigator")
class AbstractMobileLocatorInvestigatorTest {

    /** Minimal concrete subclass -- exercises the abstract contract without an Appium session. */
    private static class TestInvestigator extends AbstractMobileLocatorInvestigator {
        @Override
        protected void performLogin(String username, String password) {
            // no-op for unit test
        }

        @Override
        protected boolean isSessionAlive() {
            return true;
        }

        @Override
        protected void defineCrawlSteps() {
            // no-op for unit test
        }
    }

    @AfterEach
    void clearFilterProperty() {
        System.clearProperty("inv.page");
    }

    @Test
    @DisplayName("extends MobileTestBase, mirroring AbstractLocatorInvestigator extending TestBase")
    void extendsMobileTestBase() {
        assertTrue(MobileTestBase.class.isAssignableFrom(AbstractMobileLocatorInvestigator.class));
    }

    @Test
    @DisplayName("shouldSkip returns false for every screen when -Dinv.page is unset (defaults to 'all')")
    void shouldSkipDefaultsToCrawlingEverything() {
        TestInvestigator investigator = new TestInvestigator();
        assertFalse(investigator.shouldSkip("dashboard"));
        assertFalse(investigator.shouldSkip("login"));
    }

    @Test
    @DisplayName("shouldSkip honors the -Dinv.page filter, same semantics as the web investigator")
    void shouldSkipHonorsPageFilter() {
        System.setProperty("inv.page", "login");
        TestInvestigator investigator = new TestInvestigator();

        assertFalse(investigator.shouldSkip("login"));
        assertTrue(investigator.shouldSkip("dashboard"));
    }

    @Test
    @DisplayName("registerRole/loginAs blacklist an unregistered role without throwing")
    void loginAsUnregisteredRoleFailsFast() {
        TestInvestigator investigator = new TestInvestigator();
        assertFalse(investigator.loginAs("no-such-role"));
    }
}
