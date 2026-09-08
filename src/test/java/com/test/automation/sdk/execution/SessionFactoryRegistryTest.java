package com.test.automation.sdk.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SessionFactoryRegistryTest {

    @AfterEach
    void clearRegistry() {
        SessionFactoryRegistry.clear();
    }

    private static SessionFactory fakeFactory(Platform platform, RunMode runMode, WebDriver toReturn) {
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
    void register_twiceForSameKey_lastOneWins() {
        SessionFactory first = fakeFactory(Platform.IOS, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactory second = fakeFactory(Platform.IOS, RunMode.LOCAL, mock(WebDriver.class));
        SessionFactoryRegistry.register(first);
        SessionFactoryRegistry.register(second);

        assertSame(second, SessionFactoryRegistry.resolve(Platform.IOS, RunMode.LOCAL));
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
}
