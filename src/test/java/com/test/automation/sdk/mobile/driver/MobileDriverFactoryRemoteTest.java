package com.test.automation.sdk.mobile.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MobileDriverFactoryRemoteTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("mobile.appium.url");
        System.clearProperty("mobile.appium.allowInsecureHttp");
        System.clearProperty("appium.localUrl");
        System.clearProperty("mobile.android.appPath");
        System.clearProperty("android.appPath");
        System.clearProperty("android.uiautomator2ServerLaunchTimeoutMs");
    }

    @Test
    void remoteUrlAndAppReferenceRemainRemote() {
        System.setProperty("mobile.appium.url", "https://mac.example.test:4723/wd/hub");
        System.setProperty("mobile.android.appPath", "storage:filename=sample.apk");
        assertEquals("https://mac.example.test:4723/wd/hub", MobileDriverFactory.appiumUrl(true).toString());
        assertEquals("storage:filename=sample.apk",
                MobileDriverFactory.androidOptions("Pixel 8", true).getCapability("app"));
    }

    @Test
    void localRelativeAppPathIsResolved() {
        System.setProperty("mobile.android.appPath", "uploads/sample.apk");
        assertEquals(new File(System.getProperty("user.dir"), "uploads/sample.apk").getAbsolutePath(),
                MobileDriverFactory.androidOptions("Pixel 8", false).getCapability("app"));
    }

    @Test
    void invalidRemoteUrlIsRejected() {
        System.setProperty("mobile.appium.url", "file:///tmp/appium.sock");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
    }

    @Test
    void missingRemoteUrlDoesNotFallBackToLocalAppium() {
        System.setProperty("appium.localUrl", "http://127.0.0.1:4723/");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
    }

    @Test
    void remoteUrlRejectsCredentialsQueryAndFragment() {
        System.setProperty("mobile.appium.url", "https://user:secret@mac.example.test/wd/hub");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
        System.setProperty("mobile.appium.url", "https://mac.example.test/wd/hub?token=secret");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
        System.setProperty("mobile.appium.url", "https://mac.example.test/wd/hub#fragment");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
    }

    @Test
    void remoteHttpRequiresExplicitTrustedServerOptIn() {
        System.setProperty("mobile.appium.url", "http://mac.example.test:4723/wd/hub");
        assertThrows(IllegalStateException.class, () -> MobileDriverFactory.appiumUrl(true));
        System.setProperty("mobile.appium.allowInsecureHttp", "true");
        assertEquals("http://mac.example.test:4723/wd/hub", MobileDriverFactory.appiumUrl(true).toString());
    }

    @Test
    void localAppiumKeepsSafeLoopbackDefault() {
        assertEquals("http://127.0.0.1:4723/", MobileDriverFactory.appiumUrl(false).toString());
    }

    @Test
    void androidOptionsUseNinetySecondServerLaunchTimeoutByDefault() {
        assertEquals(90000L, ((Number) MobileDriverFactory.androidOptions("Synthetic Device", false)
                .getCapability("appium:uiautomator2ServerLaunchTimeout")).longValue());
    }

    @Test
    void androidOptionsHonorConfiguredServerLaunchTimeout() {
        System.setProperty("android.uiautomator2ServerLaunchTimeoutMs", "120000");
        assertEquals(120000L, ((Number) MobileDriverFactory.androidOptions("Synthetic Device", false)
                .getCapability("appium:uiautomator2ServerLaunchTimeout")).longValue());
    }

    @Test
    void androidOptionsRejectNonPositiveServerLaunchTimeout() {
        System.setProperty("android.uiautomator2ServerLaunchTimeoutMs", "0");
        assertThrows(IllegalArgumentException.class,
                () -> MobileDriverFactory.androidOptions("Synthetic Device", false));
        System.setProperty("android.uiautomator2ServerLaunchTimeoutMs", "-1");
        assertThrows(IllegalArgumentException.class,
                () -> MobileDriverFactory.androidOptions("Synthetic Device", false));
    }
}
