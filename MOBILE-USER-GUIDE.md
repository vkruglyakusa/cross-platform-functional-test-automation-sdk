# Mobile User Guide

This guide covers the **native mobile** side of the SDK (Android/iOS via Appium).
For the web side, see `SDK-USER-GUIDE.md`. For initial setup of either track, see
`GETTING-STARTED.md`.

---

## 1. Core Classes

| Class | Package | Purpose |
|---|---|---|
| `MobileTestBase` | `com.test.automation.sdk.mobile.testbase` | Base class for mobile test classes; owns the `AppiumDriver` lifecycle (`@BeforeClass`/`@AfterClass`) and common wait/click helpers. Full API: [`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md). |
| `MobileDriverFactory` | `com.test.automation.sdk.mobile.driver` | Builds an `AndroidDriver`/`IOSDriver` from `mobile-config.yaml` (local Appium) or `browserstack.yml.example.mobile` (BrowserStack App Automate), selected via `RunMode`. |
| `RunMode` | `com.test.automation.sdk.execution` | Enum + `resolve()` strategy (shared by web and mobile) that picks `LOCAL` vs. `BROWSERSTACK` execution based on `-Drun.mode` (or the legacy `-Dmobile.execution.target`/`-DtestInBrowserstack` flags). |
| `MobileActions` | `com.test.automation.sdk.mobile.actions` | Static gesture/action helpers (tap, longPress, swipe, scrollToElement, hideKeyboard) that `MobileTestBase` and page objects call into. |
| `MobileElementCrawler` | `com.test.automation.sdk.mobile.crawler` | Crawls the current screen (native XML tree) into locator candidates; also detects and delegates any active WebView content (see Section 4, Hybrid App Testing). |
| `MobileScreenSnapshot` | `com.test.automation.sdk.mobile.crawler` | Result of one crawl: native elements (`getNativeElements()`) + per-context WebView DOM elements (`getWebViewElements()`). |
| `MobilePageObjectGenerator` | `com.test.automation.sdk.mobile.crawler` | Emits a starter `uiActions` Java file from a `MobileScreenSnapshot`, mirroring the desktop `PageObjectGenerator`. |
| `MobileDataDrivenCrawler` | `com.test.automation.sdk.mobile.crawler` | Mobile analogue of the desktop `DataDrivenCrawler` -- re-crawls after each simulated step for dynamic native forms. |
| `NativeAccessibilityChecker` | `com.test.automation.sdk.mobile.accessibility` | Free, page-source-based accessibility audit for native (non-WebView) Android/iOS screens (see Section 7, Accessibility Testing). |

---

`MobileTestBase` inherits the same SDK-owned business-step API as the web base:
prefer `step("...", () -> { ... })` so one mobile execution story flows
consistently into logs, Allure, Extent, and failure evidence.

## 2. Writing a Mobile Test

```java
public class Test_Login extends MobileTestBase {

    @Test
    public void loginWithValidCredentials() {
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

Mobile page objects follow the same conventions as web page objects (see
`.github/instructions/page-object-creation.instructions.md`): extend the base,
use `@FindBy` (Appium's `AppiumFieldDecorator` supports `-android uiautomator`
and accessibility-id locators via the same annotation), and never hardcode
`Thread.sleep()` -- use the wait helpers on `MobileTestBase`.

---

## 3. Locator Strategy (Mobile)

Same non-negotiable uniqueness rule as web (see
`.github/instructions/locator-strategy.instructions.md`): a locator is only
acceptable if it resolves to exactly one element, as verified by
`MobileElementCrawler`.

Priority ladder for native elements:

```
1. resource-id (Android) / name or accessibility-id (iOS) -- if not auto-generated
2. content-desc / accessibility-id
3. -android uiautomator (compound: className + text/description) when a single
   attribute isn't unique
4. text (exact) for buttons/labels
5. class + index -- last resort, never as primary
```

`MobileElementCrawler` rejects known auto-generated/list-recycled identifier
patterns automatically (e.g. `.../item_12`, trailing numeric ids, trailing hex
hashes) regardless of whether they're unique in a single snapshot, because
they are not stable across app builds or list re-renders.

---

## 4. Hybrid App Testing (Native + WebView)

### 4.1 What "hybrid" means here

A hybrid screen is a native Android/iOS screen that embeds a `WebView`
rendering real HTML/DOM content (as opposed to a screen that merely launches
an external browser app, which is still 100% native from the automation
driver's point of view). Appium exposes this as multiple **contexts**:
`NATIVE_APP` plus one `WEBVIEW_<package>` context per active WebView.

### 4.2 How `MobileElementCrawler` handles it

`MobileElementCrawler.crawlCurrentScreen()` always does both in one pass:

1. Captures native `getPageSource()` + a screenshot (2 remote calls total for
   the native side, regardless of native element count).
2. Calls `crawlWebViewsIfPresent()`, which:
   - Checks whether the driver `instanceof SupportsContextSwitching`.
   - Calls `getContextHandles()` and finds any context starting with
     `"WEBVIEW"`.
   - Switches into that context (`contextDriver.context(name)`).
   - Delegates the DOM crawl to the **desktop SDK's `ElementCrawler`**
     directly (an `AppiumDriver` in a WebView context *is* a `WebDriver`, so
     no separate DOM-crawling logic is duplicated for mobile).
   - Always restores `NATIVE_APP` afterward, even on failure.
3. Returns a `MobileScreenSnapshot` with both `getNativeElements()` (native
   XML tree) and `getWebViewElements()` (a `Map<contextName, List<ElementInfo>>`
   of desktop-crawler results, one entry per WebView context found).

```java
MobileElementCrawler crawler = new MobileElementCrawler(driver);
MobileScreenSnapshot snapshot = crawler.crawlCurrentScreen();

snapshot.getNativeElements();      // native XML-tree elements on screen
snapshot.getWebViewElements();     // Map<"WEBVIEW_...", List<ElementCrawler.ElementInfo>>
```

### 4.3 Live validation (not just unit tests)

This capability has unit-test coverage (`MobileElementCrawlerTest`), but was
also **exercised live against a real hybrid app** to confirm the context
switch and DOM delegation genuinely work end-to-end on a device/emulator, not
just in a mocked test:

- **Target app**: Appium's own open-source sample app, `io.appium.android.apis`
  ("ApiDemos"), activity `.view.WebView1` -- a real native Activity hosting an
  embedded `android.webkit.WebView` with local sandbox HTML. Installed via
  `adb install ApiDemos-debug.apk` on the local emulator.
- **Result**: `getContextHandles()` returned
  `[NATIVE_APP, WEBVIEW_io.appium.android.apis]`. `crawlCurrentScreen()`
  detected the WebView context, switched into it, and the desktop
  `ElementCrawler` found **3 real DOM elements** inside it:

  | Tag | Verified unique locator |
  |---|---|
  | `h1` | `//h1[normalize-space(.)='This page is a Selenium sandbox']` |
  | `input` | `//input[@id='i_am_a_textbox']` |
  | `a` | `//a[@id='i am a link']` |

  The context was correctly restored to `NATIVE_APP` afterward, and the
  native pass on the same screen still found its 12 native elements
  independently -- confirming the two passes don't interfere with each other.

### 4.4 Known environment gotcha: ChromeDriver/WebView version mismatch

Switching into a `WEBVIEW_*` context requires Appium to attach a matching
**ChromeDriver** for the device's system WebView build. If the emulator/device
image ships a newer system WebView than any published ChromeDriver supports,
you'll see:

```
io.appium.java_client.NoSuchContextException: No Chromedriver found that can
automate Chrome '<version>'. You could also try to enable automated
chromedrivers download as a possible workaround.
```

This happened during the live validation above (system WebView was `151.x`,
newer than any released ChromeDriver). Fix by adding these capabilities and
pinning a close-version ChromeDriver binary explicitly:

```java
options.setCapability("appium:chromedriverAutodownload", true);
options.setCapability("appium:chromedriverDisableBuildCheck", true);
options.setCapability("appium:chromedriverExecutable", "C:/path/to/chromedriver.exe");
```

Download a matching (or closest-available) ChromeDriver build from the
[Chrome for Testing](https://googlechromelabs.github.io/chrome-for-testing/)
JSON endpoints if `chromedriverAutodownload` alone can't resolve one for your
device's exact WebView version. This is an **Appium/device environment
constraint**, not a bug in `MobileElementCrawler` -- the crawler correctly
detects the context either way; it just can't delegate into a WebView that
Appium itself cannot attach ChromeDriver to.

### 4.5 Limitations

- Requires `chromedriver` on the machine running the Appium server (or
  autodownload access) for every distinct WebView/Chrome build under test.
- iOS hybrid apps use `WKWebView`/`WebInspector` instead of ChromeDriver;
  context switching works the same way through `SupportsContextSwitching`,
  but no live validation of the iOS path has been performed yet (Android-only
  so far).
- `crawlWebViewsIfPresent()` crawls **all** open WebView contexts, so a screen
  with multiple simultaneous WebViews returns one map entry per context --
  keep this in mind when writing page objects for such screens.

---

## 5. Execution Targets

| Target | Config file | Notes |
|---|---|---|
| Local Appium | `configuration/mobile-config.yaml` | Requires a running Appium server (`appium`) and a connected device/emulator (`adb devices`). |
| BrowserStack App Automate | `configuration/browserstack.yml.example.mobile` | Requires `BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY` and an uploaded app URL (`bs://...`). |

See `GETTING-STARTED.md` Track B / Track C for the exact setup steps for each.

---

## 6. Accessibility Testing

Mobile accessibility splits into two completely different mechanisms depending on
whether the current screen is native or WebView-hosted -- there is no single tool
that covers both.

| Context | Mechanism | Class | Coverage |
|---|---|---|---|
| Hybrid-app **WebView** content | axe-core (same engine as web) | `com.test.automation.sdk.accessibility.AccessibilityChecker` | Full axe-core ruleset (contrast, ARIA, labels, etc.) -- works because an `AppiumDriver` switched into a `WEBVIEW_*` context *is* a `WebDriver`/`JavascriptExecutor`, so no separate implementation is needed (see Section 4). |
| **Native** Android/iOS screens | Page-source attribute analysis (no axe-core -- no DOM to inject into) | `com.test.automation.sdk.mobile.accessibility.NativeAccessibilityChecker` | Missing accessible names, unlabeled text-entry fields, duplicate accessible names, undersized touch targets. **No color-contrast check** -- the page source carries no rendering/paint information. |

`A11ySessionManager.shouldScan()` automatically **skips** any scan attempted
against a native (non-`WEBVIEW_*`) Appium context, so enabling
`accessibility.checking.enabled=true` for a mobile suite never throws against a
native screen -- it simply does nothing there. Call `NativeAccessibilityChecker`
explicitly to get native-screen coverage:

```java
List<NativeAccessibilityIssue> issues = NativeAccessibilityChecker.check(driver, "LoginScreen");
for (NativeAccessibilityIssue issue : issues) {
    log.warn(issue.toString());
}
```

Or, for unit testing against a captured `getPageSource()` fixture with no live
device (the same pattern `NativeAccessibilityCheckerTest` uses):

```java
String pageSource = ...; // captured getPageSource() XML
List<NativeAccessibilityIssue> issues =
        NativeAccessibilityChecker.check(pageSource, "android", "LoginScreen");
```

### 6.1 Known limitations

- **Touch-target sizing uses raw device pixels**, not density-independent
  units. Appium's `bounds`/`frame` attributes are raw pixels, so this check
  under-reports violations on higher-density displays -- treat a "pass" here
  as inconclusive on an unknown-density device, not a guarantee.
- **No color-contrast check** -- would require rendering/paint data the page
  source does not carry.
- For deeper Android-only coverage, Google's open-source [Accessibility Test
  Framework (ATF)](https://github.com/google/Accessibility-Test-Framework-for-Android)
  has a much larger ruleset (including contrast), but consumes a live `View`/
  `AccessibilityNodeInfo` tree from an Espresso/instrumentation test -- it is
  not a drop-in for an Appium-based suite and has no iOS equivalent, so it
  would only ever extend Android coverage, never replace
  `NativeAccessibilityChecker` for iOS.

---

## 7. Related Docs

- [`GETTING-STARTED.md`](GETTING-STARTED.md) -- setup steps for all three tracks (Web / Mobile-Local / Mobile-BrowserStack).
- [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md) -- web/desktop equivalent of this guide.
- [`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md) -- full `MobileTestBase`/`MobileActions` API reference (mobile analogue of `TESTBASE-API.md`).
- [`TESTBASE-API.md`](TESTBASE-API.md) -- full API reference (web).
- [`README.md`](README.md) -- top-level navigation hub for both products.
- `.github/instructions/locator-strategy.instructions.md` -- locator uniqueness rules shared by both web and mobile crawlers, including the map-widget and shadow-DOM sections.
