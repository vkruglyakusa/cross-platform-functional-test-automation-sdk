# Mobile TestBase API Reference

Every method on `MobileTestBase` and its companion helper `MobileActions`
(`com.test.automation.sdk.mobile.actions.MobileActions`), with usage guidance.
This is the mobile analogue of [`TESTBASE-API.md`](TESTBASE-API.md) (web).

For setup, see [`GETTING-STARTED.md`](GETTING-STARTED.md) Track B/C.
For conventions and the Hybrid App Testing feature, see [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md).

---

## Table of Contents

1. [Lifecycle & Setup](#1-lifecycle--setup)
2. [Platform Detection](#2-platform-detection)
3. [Waiting](#3-waiting)
4. [Clicks & Taps](#4-clicks--taps)
5. [Gestures (via MobileActions)](#5-gestures-via-mobileactions)
6. [Keyboard](#6-keyboard)
7. [Misc](#7-misc)

---

## 1. Lifecycle & Setup

### `setUpDriver(String mobileOS, String device)`
`@BeforeClass` hook. Reads the TestNG `<parameter>` values `mobileOS`
(defaults to `"android"`) and `deviceName` (defaults to `""`), stores them on
instance fields, then obtains a driver through the unified execution path
`ExecutionContext -> AutomationSessionFactory -> DriverManager -> SessionFactoryRegistry`
(which delegates to the platform-specific session factory and ultimately the mobile
driver implementation). Called automatically -- you do not call this yourself.

```xml
<parameter name="mobileOS" value="android"/>
<parameter name="deviceName" value="Pixel_6_API_34"/>
```

### `tearDownDriver()`
`@AfterClass` hook. Calls `driver.quit()`, swallowing and logging any
exception so cleanup never masks the real test failure. Called automatically.

### `driver`
Protected `AppiumDriver` field. Use it directly in page object constructors,
same pattern as web's `driver` field on `TestBase`.

---

## 2. Platform Detection

### `isRunningInCloud()`
`static boolean`. True only when `RunMode.resolve()` returns
`BROWSERSTACK` **and** the BrowserStack Java SDK javaagent confirms an active
platform. Throws `IllegalStateException` if the target is `BROWSERSTACK` but
the javaagent/`browserstack.yml` isn't wired up correctly -- this is a fail-fast
guard, not a silent `false`.

### `getCurrentPlatformOS()`
`String` (instance method as of Unified SDK Review Priority 4 -- previously
`static`; `mobileOsName`/`deviceName` are now per-instance state so
concurrent Mobile test classes never leak each other's device/platform
selection). Returns `"android"`/`"ios"` -- read from BrowserStack when
`isRunningInCloud()`, otherwise from the `-Dmobile.os`/`mobileOS` parameter
set in `setUpDriver`. Throws `IllegalStateException` if neither source is set.

### `verifyIfDeviceIphone()`
`protected boolean`. `true` when `getCurrentPlatformOS()` is `"ios"` or
`"iphone"`. Use this to branch flows that differ between Android and iOS
(e.g. an Android-only permissions dialog).

```java
if (verifyIfDeviceIphone()) {
    // iOS-specific step
} else {
    new PermissionControllerPopUp(driver).allow();
}
```

---

## 3. Waiting

### `waitForElementPresent(WebElement element)`
Waits (default 30s) for visibility via `ExpectedConditions.visibilityOf`.
Call before reading text or asserting on an element's state.

### `waitForElementPresent(WebElement element, int timeoutSeconds)`
Same, with a caller-specified timeout -- use for slower screens (e.g. after a
network call) instead of adding a raw sleep.

Both delegate to the equivalent static overloads on `MobileActions`, so page
objects that don't extend `MobileTestBase` (rare) can call
`MobileActions.waitForElementPresent(driver, element)` directly.

---

## 4. Clicks & Taps

### `elementClick(String key, WebElement element)`
Primary click helper. Waits for the element, logs the action under `key`
(for reporting/traceability -- pass the field name or a description), then
calls `element.click()`. **Throws** on failure -- use this as the default,
and reserve `clickOnElement` for elements known to resist a plain click.

```java
elementClick("submitButton", submitButton);
```

### `clickOnElement(WebElement element)`
Fallback strategy: tries `element.click()` first; on any exception, falls
back to a gesture-based tap via `MobileActions.tap(driver, element)`. Use
this for elements that are flaky with a plain click (e.g. overlapping native
views, custom-drawn buttons).

---

## 5. Gestures (via `MobileActions`)

These are static methods on `MobileActions`, layered on top of Appium's
typed API and the W3C "Execute Method" extension (`mobile: ...` scripts) for
gestures without a typed equivalent.

### `tap(AppiumDriver driver, WebElement element)`
Waits for visibility, then clicks. Used internally by
`MobileTestBase.clickOnElement` as the fallback tap.

### `tap(AppiumDriver driver, int x, int y)`
Taps at raw screen coordinates via `mobile: tap` -- for cases with no element
to click (e.g. canvas-drawn widgets, custom map pins). Pair with an assertion
on the resulting side effect, not on the tapped target (same guidance as the
desktop map-widget helper).

### `longPress(AppiumDriver driver, WebElement element, Duration duration)`
Long-press gesture via `mobile: longClickGesture` for the given duration.

### `swipe(AppiumDriver driver, int startX, int startY, int endX, int endY, Duration duration)`
Point-to-point swipe via `mobile: swipeGesture`.

### `scrollToElement(AppiumDriver driver, String strategy, String selector)`
Scrolls until an element matching `strategy`/`selector` (e.g.
`"accessibility id"` / `"-android uiautomator"`) is in view, via
`mobile: scroll`.

---

## 6. Keyboard

### `hideKeyboard(AppiumDriver driver)`
Dismisses the on-screen keyboard via `mobile: hideKeyboard`. Call after a
text-entry step if a subsequent element is likely to be obscured by the
keyboard.

---

## 7. Misc

### `sleep(int seconds)`
Plain `Thread.sleep` wrapper. Documented here for completeness but should be
treated as a **last resort** -- prefer `waitForElementPresent` or a
purpose-built stability wait. Only use where no explicit wait condition
exists yet.

---

## Quick Reference -- Mobile Test Class Cheat Sheet

```java
public class Test_MobileLogin extends MobileTestBase {

    @Parameters({"mobileOS", "deviceName"})
    @Test
    public void loginWithValidCredentials() throws Exception {
        LoginPage login = new LoginPage(driver);
        step("Enter valid mobile credentials", () -> {
            login.enterUsername("user@example.com");
            login.enterPassword("Passw0rd!");
        });
        step("Tap Login", login::tapLogin);
        step("Verify home page is displayed", () ->
            Assert.assertTrue(new HomePage(driver).isDisplayed()));
    }
}
```

```java
public class LoginPage extends MobileTestBase {

    @FindBy(xpath = "//android.widget.EditText[@resource-id='com.app:id/username']")
    public WebElement usernameField;

    public LoginPage(AppiumDriver driver) {
        this.driver = driver;
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    public void enterUsername(String value) {
        waitForElementPresent(usernameField);
        usernameField.clear();
        usernameField.sendKeys(value);
    }
}
```

---

## Related Docs

- [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md) -- conventions, locator strategy, execution targets, Hybrid App Testing.
- [`TESTBASE-API.md`](TESTBASE-API.md) -- web equivalent of this reference.
- [`GETTING-STARTED.md`](GETTING-STARTED.md) -- setup steps.
- [`README.md`](README.md) -- top-level navigation hub for both products.
