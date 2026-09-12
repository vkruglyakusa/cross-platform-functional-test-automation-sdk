package com.test.automation.sdk.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SessionFactoryRegistryTest {

    @BeforeEach
    void startWithEmptyRegistry() {
        SessionFactoryRegistry.clear();
    }

    @AfterEach
    void clearRegistry() {
        SessionFactoryRegistry.clear();
    }

    private static SessionFactory fakeFactory(Platform platform, RunMode runMode, WebDriver toReturn) {
        return fakeFactory(platform, runMode, null, toReturn);
    }

    private static SessionFactory fakeFactory(Platform platform, RunMode runMode,
            ProviderId providerId, WebDriver toReturn) {
        return new SessionFactory() {
            @Override
            public Platform getPlatform() {
                return platform;
            }

            @Override
            public RunMode getRunMode() {
                return runMode;
            }

            @Override
            public ProviderId getProviderId() {
                return providerId;
            }

            @Override
            public WebDriver createDriver(ExecutionContext context) {
                return toReturn;
            }
        };
    }

    @Test
    void resolve_throwsWhenNothingRegistered() {
        assertThrows(IllegalStateException.class, () -> SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL));
    }

    @Test
    void register_thenResolve_returnsSameFactory() {
        WebDriver driver = mock(WebDriver.class);
        SessionFactory factory = fakeFactory(Platform.WEB, RunMode.LOCAL, driver);
        SessionFactoryRegistry.register(factory);

        SessionFactory resolved = SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL);
        assertSame(factory, resolved);
        assertSame(driver, resolved.createDriver(null));
    }

    @Test
    void resolve_byExecutionContext_matchesPlatformAndRunMode() {
        SessionFactory factory = fakeFactory(Platform.ANDROID, RunMode.BROWSERSTACK, mock(WebDriver.class));
        SessionFactoryRegistry.register(factory);

        ExecutionContext ctx = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6", RunMode.BROWSERSTACK);
        assertSame(factory, SessionFactoryRegistry.resolve(ctx));
    }

    @Test
    void register_twiceForSameKey_isRejected() {
        SessionFactory first = fakeFactory(Platform.IOS, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactory second = fakeFactory(Platform.IOS, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactoryRegistry.register(first);

        assertThrows(IllegalStateException.class, () -> SessionFactoryRegistry.register(second));
        assertSame(first, SessionFactoryRegistry.resolve(Platform.IOS, RunMode.LOCAL));
    }

    @Test
    void differentPlatformsOrRunModes_doNotCollide() {
        SessionFactory webLocal = fakeFactory(Platform.WEB, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactory webCloud = fakeFactory(Platform.WEB, RunMode.BROWSERSTACK, mock(WebDriver.class));
        SessionFactoryRegistry.register(webLocal);
        SessionFactoryRegistry.register(webCloud);

        assertEquals(webLocal, SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL));
        assertEquals(webCloud, SessionFactoryRegistry.resolve(Platform.WEB, RunMode.BROWSERSTACK));
    }

    /**
     * Verifies Priority 5 of the Unified SDK Implementation Review
     * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
     * section 11): {@link AutomationTechnology} is reserved as a third
     * resolution dimension without breaking any existing 2-arg
     * {@code resolve(Platform, RunMode)} caller -- a {@link SessionFactory}
     * that never overrides {@link SessionFactory#getAutomationTechnology()}
     * still resolves via the platform-implied default (SELENIUM for WEB,
     * APPIUM for ANDROID/IOS).
     */
    @Test
    void resolve_twoArgOverload_stillWorks_usingPlatformImpliedTechnologyDefault() {
        SessionFactory factory = fakeFactory(Platform.WEB, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactoryRegistry.register(factory);

        assertSame(factory, SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL));
        assertSame(factory, SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL, AutomationTechnology.SELENIUM));
    }

    @Test
    void resolve_byExecutionContext_usesPlatformImpliedTechnologyByDefault() {
        SessionFactory factory = fakeFactory(Platform.ANDROID, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactoryRegistry.register(factory);

        ExecutionContext ctx = ExecutionContext.forMobile(Platform.ANDROID, "Pixel_6", RunMode.LOCAL);
        assertEquals(AutomationTechnology.APPIUM, ctx.getAutomationTechnology());
        assertSame(factory, SessionFactoryRegistry.resolve(ctx));
    }

    @Test
    void resolve_withExplicitTechnology_doesNotMatchADifferentTechnologyFactory() {
        SessionFactory seleniumFactory = new SessionFactory() {
            @Override
            public Platform getPlatform() {
                return Platform.WEB;
            }

            @Override
            public RunMode getRunMode() {
                return RunMode.LOCAL;
            }

            @Override
            public AutomationTechnology getAutomationTechnology() {
                return AutomationTechnology.SELENIUM;
            }

            @Override
            public WebDriver createDriver(ExecutionContext context) {
                return mock(WebDriver.class);
            }
        };
        SessionFactoryRegistry.register(seleniumFactory);

        // No factory registered for (WEB, LOCAL, APPIUM) -- an explicit, non-default
        // technology must not silently fall back to the SELENIUM registration.
        assertThrows(IllegalStateException.class,
                () -> SessionFactoryRegistry.resolve(Platform.WEB, RunMode.LOCAL, AutomationTechnology.APPIUM));
    }

    @Test
    void providerAwareFactories_resolveIndependently() {
        ProviderId browserStack = new ProviderId("browserstack");
        ProviderId sauceLabs = new ProviderId("saucelabs");
        SessionFactory browserStackFactory = fakeFactory(
                Platform.WEB, RunMode.REMOTE, browserStack, mock(WebDriver.class));
        SessionFactory sauceLabsFactory = fakeFactory(
                Platform.WEB, RunMode.REMOTE, sauceLabs, mock(WebDriver.class));
        SessionFactoryRegistry.register(browserStackFactory);
        SessionFactoryRegistry.register(sauceLabsFactory);

        assertSame(browserStackFactory, SessionFactoryRegistry.resolve(
                Platform.WEB, RunMode.REMOTE, AutomationTechnology.SELENIUM, browserStack));
        assertSame(sauceLabsFactory, SessionFactoryRegistry.resolve(
                Platform.WEB, RunMode.REMOTE, AutomationTechnology.SELENIUM, sauceLabs));
    }

    @Test
    void resolveByContext_includesProviderDimension() {
        ProviderId provider = new ProviderId("custom-grid");
        SessionFactory factory = fakeFactory(
                Platform.WEB, RunMode.REMOTE, provider, mock(WebDriver.class));
        SessionFactoryRegistry.register(factory);

        ExecutionContext context = ExecutionContext.forWebWithProvider("firefox", RunMode.REMOTE, provider);
        assertSame(factory, SessionFactoryRegistry.resolve(context));
    }

    @Test
    void remoteFactory_requiresProviderAndLocalFactoryRejectsProvider() {
        assertThrows(IllegalArgumentException.class, () -> SessionFactoryRegistry.register(
                fakeFactory(Platform.WEB, RunMode.REMOTE, mock(WebDriver.class))));
        assertThrows(IllegalArgumentException.class, () -> SessionFactoryRegistry.register(
                fakeFactory(Platform.WEB, RunMode.LOCAL, new ProviderId("custom"), mock(WebDriver.class))));
    }
}
