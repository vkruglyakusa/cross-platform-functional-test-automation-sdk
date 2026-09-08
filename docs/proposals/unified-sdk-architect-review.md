# Senior Architect Review — Unified Web + Mobile Automation SDK

Status: REVIEW COMPLETE — no code changed as part of this document.
Scope reviewed: `cross-platform-functional-test-automation-sdk` (`src/main/java/com/test/automation/sdk/**`), plus both consumer templates (`functional-automation-consumer-template`, `mobile-functional-automation-consumer-template`) to verify real-world usage before recommending any move/removal.

---

## A. Executive Summary

The repository is the result of a **physical merge**, not an **architectural
unification**, of two previously separate SDKs. Every framework-level concern
— test lifecycle, driver acquisition, configuration resolution, YAML parsing,
execution-target selection — exists **twice**: once under
`com.test.automation.sdk.testbase`/`utility` (web, mature, ~2000-line
`TestBase`) and once under `com.test.automation.sdk.mobile.*` (ported near
verbatim from a prior standalone project, `311_Mobile_Automation`, per its own
code comments). Additionally, seven concrete 311-app page objects
(`uiActions/*`) were committed into the SDK jar itself and are **actively
imported by the mobile consumer template's test class**, which means removal
is not a pure cleanup — it is a real (small) migration.

The one genuinely good architectural pattern already in the codebase — a
pluggable execution-target/strategy model — exists **only for mobile**
(`ExecutionTarget`, `MobileExecutionStrategy`). Web has no equivalent, so
"run this Selenium suite on BrowserStack Automate" isn't a first-class SDK
concept anywhere.

**Recommended direction:** generalize the mobile strategy pattern (it is the
correct shape) across both platforms, unify the four duplicate
config/driver-factory pairs into one implementation each, and unify
`TestBase`/`MobileTestBase` into one lifecycle class with technology-specific
internals — without rewriting the ~2000 lines of already-working Selenium
helper methods in `TestBase`, and without breaking either consumer template.
This is achievable in 5 phases, each independently testable and committable,
with deprecated compatibility shims carrying old call sites through the
transition.

---

## B. Current Architecture (as verified in source)

```
com.test.automation.sdk
├── testbase/
│   ├── TestBase.java            Web lifecycle. static WebDriver driver. ~2000 lines:
│   │                             DataProvider/Excel helpers, waits, clicks, screenshots,
│   │                             Extent+Allure reporting hooks, mailinator, accessibility
│   │                             hooks (AccessibilityChecker/A11ySessionManager), TestNG
│   │                             @BeforeClass/@AfterClass.
│   ├── WebDriverFactory.java    switch(browserName) -> Chrome/Firefox/Edge. Static driver
│   │                             field. Proxy + headless + WebDriverManager resolution,
│   │                             all read via YamlConfigReader. Wraps in WebEventListener
│   │                             via EventFiringDecorator. NO remote/BrowserStack concept.
│   └── SdkConfig.java           Config-dir resolver: -Dsdk.config.dir > SDK_CONFIG_DIR env
│                                 > "configuration" default. Exposes CONFIG_PROPERTIES,
│                                 LOG4J_PROPERTIES, LOG4J2_XML, YAML_CONFIG paths.
├── utility/
│   ├── YamlConfigReader.java    Full flat-map YAML parser (2-level nesting), singleton,
│   │                             reads sdk-config.yaml, bridges accessibility.* keys into
│   │                             system properties. Backing store for ~all web config.
│   ├── ElementCrawler.java      Mature web crawler; ElementInfo/LocatorCandidate model,
│   │                             XPath uniqueness validation, shadow-DOM + map-widget
│   │                             detection (per locator-strategy instructions).
│   ├── PageObjectGenerator.java, DataDrivenCrawler.java, reports/, mailinator/, Excel_Reader,
│   │                             PropertiesReader, PageContext, GapReportWriter, etc.
├── listener/
│   └── WebEventListener.java    WebDriverListener (Selenium 4 decorator). Central hook:
│                                 logs every interaction AND triggers per-element
│                                 AccessibilityChecker scans when enabled. Ties TestBase +
│                                 AccessibilityChecker + PageContext together for web.
├── accessibility/               Unified this session: AccessibilityFinding model,
│                                 AccessibilityEngine interface, AxeCoreEngine (web),
│                                 A11ySessionManager, AccessibilityExcelReporter, etc.
│                                 (mobile.accessibility.NativeMobileEngine implements the
│                                 same AccessibilityEngine interface — this is the ONE
│                                 place web/mobile already share a real abstraction.)
└── mobile/
    ├── testbase/
    │   ├── MobileTestBase.java  Instance AppiumDriver driver (not static — already better
    │   │                         than web here). isRunningInCloud()/getCurrentPlatformOS()/
    │   │                         verifyIfDeviceIphone(). @BeforeClass/@AfterClass via
    │   │                         MobileDriverFactory. elementClick/clickOnElement/sleep
    │   │                         helpers duplicate small pieces of TestBase's web helpers.
    │   └── MobileSdkConfig.java Byte-for-byte duplicate of SdkConfig's resolution algorithm,
    │                             different property name (-Dmobile.sdk.config.dir), points
    │                             at a SEPARATE file: mobile-config.yaml (not sdk-config.yaml).
    ├── config/
    │   └── MobileConfigReader.java  Near-identical hand-rolled parser to YamlConfigReader,
    │                             reading mobile-config.yaml instead of sdk-config.yaml.
    ├── driver/
    │   └── MobileDriverFactory.java  Thin facade: resolves ExecutionTarget -> delegates to
    │                             MobileExecutionStrategy. Good design, mobile-only.
    ├── execution/
    │   ├── ExecutionTarget.java (enum LOCAL|BROWSERSTACK, -Dmobile.execution.target with
    │   │                         legacy -DtestInBrowserstack fallback — clean precedence
    │   │                         chain, no web equivalent exists)
    │   ├── MobileExecutionStrategy.java (interface: getTarget()/createDriver()/isRemote())
    │   ├── MobileExecutionStrategyFactory.java (target -> strategy resolution, single switch)
    │   ├── LocalExecutionStrategy.java (android+ios branches, one class, local Appium URL)
    │   ├── BrowserStackExecutionStrategy.java (android+ios branches, one class, BrowserStack
    │   │                         Java SDK javaagent verification)
    │   └── MobileSessionRequest.java, MobileExecutionStrategySupport.java
    ├── actions/MobileActions.java     tap/swipe/waitForElementPresent gesture helpers
    ├── accessibility/NativeAccessibilityChecker.java, NativeAccessibilityIssue.java,
    │                  NativeMobileEngine.java  (correctly implements the shared
    │                  AccessibilityEngine interface — proof the pattern works)
    ├── crawler/        MobileElementCrawler, MobileElementInfo, MobileLocatorCandidate,
    │                   MobileDataDrivenCrawler, MobilePageObjectGenerator,
    │                   MobileCrawlerReportWriter, MobileScreenSnapshot, MobileCrawlerStep.
    │                   MobileElementInfo's own Javadoc says it "mirrors ElementCrawler's
    │                   ElementInfo" — conceptually parallel, structurally independent code.
    └── uiActions/      HomePage, NavigationUtility, NewServiceRequestPage, NotificationsPage,
                        PermissionControllerPopUp, TermsOfUsePage, UserDataPolicyPage
                        (7 concrete 311-app screens — see section C.6).
```

**How Web and Mobile interact today: they don't.** There is no shared
lifecycle, no shared driver-acquisition entry point, no shared config file.
The only real integration point is `accessibility/AccessibilityEngine`
(added this session), which both `AxeCoreEngine` (web) and `NativeMobileEngine`
(mobile) implement — proof that a shared interface *can* work cleanly across
both platforms without forcing identical internals.

**Consumer template verification (why this matters for section M):**
- `functional-automation-consumer-template`'s `Test_Example extends
  com.test.automation.sdk.testbase.TestBase` — standard web usage.
- `mobile-functional-automation-consumer-template`'s
  `OnboardingAndHomeSmokeTest extends com.test.automation.sdk.mobile.testbase.MobileTestBase`
  **and directly imports `com.test.automation.sdk.mobile.uiActions.NavigationUtility`
  and `NewServiceRequestPage` from the SDK jar.** This means `uiActions/*` is
  not dead scaffolding — it is a live dependency of the one existing mobile
  consumer template test. Removing it requires first porting those two (at
  minimum) page objects into the consumer template's own `uiActions` package
  and updating its imports, not a bare deletion.

---

## C. Architectural Problems

### C.1 Duplicate lifecycle owners
`TestBase` (web) and `MobileTestBase` (mobile) both own `@BeforeClass`/
`@AfterClass` driver setup/teardown, both own simple click/wait helpers, and
neither knows the other exists. A consumer project mixing web and mobile
tests in one suite has two unrelated base classes with two unrelated
reporting/logging integration points.

### C.2 Duplicate config resolution (2 classes, identical algorithm)
`SdkConfig` and `MobileSdkConfig` implement the *exact same* precedence chain
(`-D` flag > env var > `"configuration"` default) with only the property
name changed. This is duplication with zero technical justification — the
resolution algorithm has nothing platform-specific about it.

### C.3 Duplicate YAML parsing (2 classes, 2 files)
`YamlConfigReader` (full-featured, drives `sdk-config.yaml`, bridges
accessibility keys to system properties, has defaults for ~40 keys) and
`MobileConfigReader` (a smaller hand-rolled parser, drives a second file
`mobile-config.yaml`) solve the same problem independently. A consumer
project running both web and mobile tests must maintain and keep in sync
**two** YAML files with **two** different flattening/nesting conventions.

### C.4 Web has no execution-target abstraction; mobile's is not reusable
`WebDriverFactory` only ever creates a local browser — there is no concept of
"web on BrowserStack Automate" or "web on Grid" anywhere in the SDK, even
though `MobileExecutionStrategy` already proves the right shape for exactly
this problem. The mobile strategy classes are also not directly reusable for
web because `MobileSessionRequest`/`MobileExecutionStrategy.createDriver()`
return `AppiumDriver`, not the more general `WebDriver` supertype they could
return.

### C.5 Static vs. instance driver state (real thread-safety gap)
`WebDriverFactory.driver` and `TestBase.driver` are **static**, explicitly
documented as "single-threaded execution only" in `WebDriverFactory`'s own
Javadoc. `MobileTestBase.driver` is an **instance field** — already correct
for parallel TestNG execution. Unifying the two lifecycles is an opportunity
to fix this real bug for web, not just a stylistic cleanup.

### C.6 App-specific code inside the SDK (`uiActions/*`)
Seven concrete 311-app page objects live in the SDK's own `mobile.uiActions`
package and are directly imported by the mobile consumer template's only
test class. This violates the SDK's own contract (confirmed by the web SDK,
which correctly has *zero* page objects) and creates an actual—if small—
migration dependency once removed (see C's consumer-template note above and
section M).

### C.7 Parallel-but-unlinked element-discovery models
`ElementCrawler.ElementInfo` (web) and `MobileElementCrawler`'s
`MobileElementInfo` (mobile) are independently coded but intentionally
"mirror" each other per `MobileElementInfo`'s own Javadoc. This is exactly
the kind of duplication a shared `ElementDiscoveryService` contract (not a
merged implementation — the two crawlers use genuinely different techniques:
DOM/XPath vs. Appium page-source/accessibility-id/UiAutomator/iOS predicate)
should normalize, without weakening either mature crawler.

### C.8 Naming/package inconsistency as a symptom, not the root cause
`mobile.testbase`, `mobile.driver`, `mobile.config`, `mobile.execution` mirror
`testbase`/`utility` 1:1 by design intent, but because each mobile class was
translated rather than integrated, the packages look parallel while the code
underneath has no shared interface. Renaming packages alone (what the first
draft of this review risked doing) would not fix C.1–C.5.

---

## D. Target Architecture

```
                    Unified Automation SDK
                              |
                     Common Framework Core
        (TestBase lifecycle, AutomationConfig, ExecutionContext,
         DriverManager, Reporting, Logging, Accessibility, Discovery)
                              |
              +---------------+---------------+
              |                               |
        Web Capability                  Mobile Capability
              |                               |
      Selenium WebDriver               Appium AppiumDriver
      (Chrome/Firefox/Edge,             (Android/iOS, native +
       local + BrowserStack              WebView context switch,
       Automate)                         local + BrowserStack
                                          App Automate)
```

```
Test
  |
TestBase                         <- ONE class, coordinates lifecycle only
  |
ExecutionContext (Platform, RunMode, resolved config)
  |
DriverManager.acquire(ExecutionContext)   <- ONE entry point
  |
  +-- Platform.WEB    -> WebSessionFactory    -> Selenium WebDriver
  |
  +-- Platform.ANDROID/IOS -> MobileSessionFactory -> Appium AppiumDriver
```

`TestBase` stays a coordinator, not a god-class: it owns setup/teardown,
test metadata, and delegates all technology-specific driver creation to
`DriverManager`, which delegates to one `WebSessionFactory` or
`MobileSessionFactory` chosen by `ExecutionContext`. Because
`AppiumDriver implements WebDriver`, `TestBase` can hold a single
`protected WebDriver driver` field usable by both platforms; mobile-only
gesture operations are namespaced (`mobileTap()`, `mobileSwipe()`,
`mobileSwitchToWebView()`) rather than added to the base API surface, and
are only valid to call when the underlying driver is actually an
`AppiumDriver` (guarded, clear exception otherwise — same pattern already
used in this session's `NativeMobileEngine`).

---

## E. Target Package Structure

```
com.test.automation.sdk
├── testbase/
│   └── TestBase.java                 // unified lifecycle coordinator (was TestBase + MobileTestBase)
├── config/
│   ├── SdkConfig.java                 // unified config-dir resolver (was SdkConfig + MobileSdkConfig)
│   └── YamlConfigReader.java          // unified parser; sdk-config.yaml gains android:/ios: sections
├── execution/
│   ├── Platform.java                  // WEB | ANDROID | IOS
│   ├── RunMode.java                   // LOCAL | BROWSERSTACK  (was mobile-only ExecutionTarget)
│   ├── ExecutionContext.java          // resolved (Platform, RunMode, browser/OS, device) for one test
│   ├── SessionFactory.java            // was MobileExecutionStrategy, generalized; createDriver() -> WebDriver
│   └── SessionFactoryRegistry.java    // was MobileExecutionStrategyFactory, resolves by (Platform, RunMode)
├── driver/
│   ├── DriverManager.java             // ONE entry point (was WebDriverFactory + MobileDriverFactory)
│   ├── web/
│   │   ├── WebLocalSessionFactory.java        // today's WebDriverFactory Chrome/Firefox/Edge switch
│   │   └── WebBrowserStackSessionFactory.java // NEW — parity with mobile's BrowserStack support
│   └── mobile/
│       ├── AndroidLocalSessionFactory.java     // was LocalExecutionStrategy (android half)
│       ├── AndroidBrowserStackSessionFactory.java // was BrowserStackExecutionStrategy (android half)
│       ├── IosLocalSessionFactory.java          // was LocalExecutionStrategy (ios half)
│       ├── IosBrowserStackSessionFactory.java   // was BrowserStackExecutionStrategy (ios half)
│       └── MobileActions.java                  // tap/swipe/context-switch gesture helpers
├── discovery/
│   ├── ElementDiscoveryService.java   // shared contract (find/report shape only)
│   ├── web/ElementCrawler.java        // unchanged, mature — moved under discovery/web for symmetry only
│   └── mobile/MobileElementCrawler.java // unchanged, moved under discovery/mobile for symmetry only
├── accessibility/                     // already unified this session — no further structural change needed
├── listener/                          // WebEventListener unchanged; a MobileEventListener equivalent is FUTURE, not required
├── reporting/                         // was utility/reports — unchanged content, name matches guardrail #4 more literally (optional)
└── utility/                           // unchanged (Excel_Reader, mailinator, PageContext, generators, etc.)
```

`uiActions/*` does not appear anywhere in this tree — it has no
architectural home in the SDK (see F and M).

---

## F. Class-Level Changes

| Current Class | Current Responsibility | Recommendation | Target Location/Class | Reason |
|---|---|---|---|---|
| `testbase.TestBase` | Web lifecycle + ~2000 lines of Selenium helpers | **KEEP + REFACTOR** (lifecycle only) | `testbase.TestBase` | Becomes the one lifecycle owner for both platforms; existing helper methods untouched. |
| `mobile.testbase.MobileTestBase` | Mobile lifecycle, small helper duplicates | **MERGE** into `TestBase`, then **DEPRECATE** | `testbase.TestBase` (mobile lifecycle absorbed) | Same lifecycle shape (`@BeforeClass`/`@AfterClass`, driver field) as web; no technical reason for a separate class. |
| `testbase.WebDriverFactory` | Local browser creation, static driver | **REFACTOR** into `driver.web.WebLocalSessionFactory`, **DEPRECATE** old class as delegator | `driver.web.WebLocalSessionFactory` | Becomes one `SessionFactory` implementation instead of the only web driver-creation path; also fixes static-field thread-safety gap. |
| `mobile.driver.MobileDriverFactory` | Facade delegating to strategy | **MERGE** into `driver.DriverManager`, **DEPRECATE** old class as delegator | `driver.DriverManager` | Its resolve-then-delegate pattern is exactly right and becomes the model for the unified factory. |
| `testbase.SdkConfig` | Config-dir resolution (web) | **MERGE** with `MobileSdkConfig` | `config.SdkConfig` | Identical algorithm, no platform-specific logic; duplication has zero justification. |
| `mobile.testbase.MobileSdkConfig` | Config-dir resolution (mobile) | **MERGE**, then **DEPRECATE** as delegator (`-Dmobile.sdk.config.dir` honored as secondary override for one release) | `config.SdkConfig` | Same as above. |
| `utility.YamlConfigReader` | sdk-config.yaml parser | **KEEP + EXTEND** (add `android.*`/`ios.*` section support) | `config.YamlConfigReader` | Already the more complete parser; extending it removes the need for a second file. |
| `mobile.config.MobileConfigReader` | mobile-config.yaml parser | **MERGE**, then **DEPRECATE** as delegator (fallback to standalone `mobile-config.yaml` for one release if present) | `config.YamlConfigReader` | Same problem solved twice; consumer projects should configure one file. |
| `mobile.execution.ExecutionTarget` | LOCAL/BROWSERSTACK enum, mobile-only | **REFACTOR** (generalize) | `execution.RunMode` | The precedence-chain design is correct and should apply to web too. |
| `mobile.execution.MobileExecutionStrategy` | Strategy interface, `AppiumDriver`-typed | **REFACTOR** (generalize return type to `WebDriver`) | `execution.SessionFactory` | Same interface shape works for web once the return type is generalized. |
| `mobile.execution.MobileExecutionStrategyFactory` | target -> strategy resolution | **REFACTOR** (generalize to `(Platform, RunMode)`) | `execution.SessionFactoryRegistry` | Same resolution pattern, now keyed by two axes instead of one. |
| `mobile.execution.LocalExecutionStrategy` | android+ios local driver creation, one class | **REFACTOR** (split by platform) | `driver.mobile.AndroidLocalSessionFactory`, `driver.mobile.IosLocalSessionFactory` | Splitting clarifies that Android/iOS are the platform axis, not a switch inside one class; also matches web's per-browser split already used in `WebDriverFactory`. |
| `mobile.execution.BrowserStackExecutionStrategy` | android+ios BrowserStack driver creation | **REFACTOR** (split by platform) | `driver.mobile.AndroidBrowserStackSessionFactory`, `driver.mobile.IosBrowserStackSessionFactory` | Same reasoning as above. |
| `mobile.execution.MobileSessionRequest` | (mobileOS, deviceName) value object | **REFACTOR** (generalize) | `execution.ExecutionContext` | Becomes the one context object carrying platform/runMode/browser-or-OS/device for either platform. |
| *(none — gap)* | Web has no BrowserStack Automate support | **NEW** | `driver.web.WebBrowserStackSessionFactory` | Parity with mobile; currently a real capability gap, not duplication. |
| `mobile.actions.MobileActions` | tap/swipe/wait helpers | **KEEP** (already correctly isolated) | `driver.mobile.MobileActions` | Genuinely mobile-specific; no change needed beyond package location. |
| `mobile.uiActions.*` (7 classes) | Concrete 311-app page objects | **REMOVE** from SDK; **MOVE** the ≥2 classes the consumer template imports into that template's own `uiActions` package first | *(none in SDK)* | No architectural justification for app code inside a reusable SDK; see section M for the required migration step before deletion. |
| `mobile.crawler.MobileElementInfo` / `utility.ElementCrawler.ElementInfo` | Parallel discovery-result models | **KEEP** both implementations; **NEW** thin shared contract | `discovery.ElementDiscoveryService` (interface only) | Preserves each crawler's mature, technology-specific technique while giving reporting/Page-Object-generation one common result shape to target. Larger unification is a FUTURE item, not required now. |
| `accessibility.AccessibilityEngine` / `AxeCoreEngine` / `mobile.accessibility.NativeMobileEngine` | Already unified this session | **KEEP** | unchanged | Already demonstrates the correct pattern; used as the template for `SessionFactory` above. |
| `listener.WebEventListener` | Web interaction logging + inline a11y scan hook | **KEEP** | unchanged | Genuinely web-specific (Selenium 4 `WebDriverListener` decorator); an Appium equivalent is a FUTURE item, not required. |

---

## G. TestBase Design

```java
public class TestBase {
    protected WebDriver driver;                 // instance field (fixes C.5); AppiumDriver IS-A WebDriver
    protected ExecutionContext executionContext; // resolved Platform + RunMode for this test

    @Parameters({"browser", "mobileOS", "deviceName"})
    @BeforeClass(alwaysRun = true)
    public void setUp(@Optional String browser, @Optional String mobileOS, @Optional String deviceName) {
        executionContext = ExecutionContext.resolve(browser, mobileOS, deviceName);
        driver = DriverManager.acquire(executionContext);
        // existing web lifecycle: Extent/Allure test start, log4j context, etc. — unchanged
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        // existing screenshot-on-failure / report close / driver.quit() — unchanged
    }

    // Mobile-only capabilities, namespaced and guarded:
    protected void mobileTap(WebElement element) {
        requireAppiumDriver().tap(element); // delegates to driver/mobile/MobileActions
    }
    protected void mobileSwitchToWebView() { ... }

    private AppiumDriver requireAppiumDriver() {
        if (!(driver instanceof AppiumDriver)) {
            throw new IllegalStateException("mobile-only operation called on a non-mobile driver");
        }
        return (AppiumDriver) driver;
    }

    // All existing web helper methods (waitForElementPresent, elementClick, Excel
    // DataProvider support, screenshot/report hooks, accessibility hooks) are
    // UNCHANGED — TestBase does not become a "giant class" because none of that
    // code is new; only driver ACQUISITION is being unified.
}
```

Key points:
- `ExecutionContext.resolve(...)` is the **only** new decision logic in
  `TestBase`; it inspects which of `browser`/`mobileOS` was supplied (or an
  explicit `-Dplatform=WEB|ANDROID|IOS` override) to determine `Platform`,
  then applies the existing `RunMode` precedence chain.
- `MobileTestBase` becomes a deprecated subclass:
  `public class MobileTestBase extends TestBase` — existing mobile consumer
  test classes that `extends MobileTestBase` keep compiling unchanged for one
  release, while new mobile tests are written directly against `TestBase`.
- No existing web method signature changes. No existing web test needs any
  code change at all — only the internal `driver` field goes from `static`
  to instance (transparent to callers using `this.driver`/inherited access).

---

## H. Driver / Session Architecture

```
DriverManager.acquire(ExecutionContext ctx)
        |
        +-- ctx.platform == WEB
        |        |
        |        +-- ctx.runMode == LOCAL        -> WebLocalSessionFactory       -> ChromeDriver/FirefoxDriver/EdgeDriver
        |        +-- ctx.runMode == BROWSERSTACK  -> WebBrowserStackSessionFactory -> RemoteWebDriver (BrowserStack Automate)
        |
        +-- ctx.platform == ANDROID
        |        +-- LOCAL        -> AndroidLocalSessionFactory        -> AndroidDriver (local Appium)
        |        +-- BROWSERSTACK -> AndroidBrowserStackSessionFactory -> AndroidDriver (BrowserStack javaagent reroute)
        |
        +-- ctx.platform == IOS
                 +-- LOCAL        -> IosLocalSessionFactory        -> IOSDriver (local Appium)
                 +-- BROWSERSTACK -> IosBrowserStackSessionFactory -> IOSDriver (BrowserStack javaagent reroute)
```

**Thread safety / parallel execution:** the current SDK is inconsistent —
web is static (single-threaded only, explicitly documented as such), mobile
is already instance-based. The unified design uses an **instance field on
`TestBase`**, which combined with TestNG's per-instance test class model
(`parallel="classes"` or `"methods"` with `@Test(dataProvider=...)` giving
each thread its own `TestBase` instance in the mobile-tested pattern already)
gives correct parallel isolation for both platforms without a `ThreadLocal`.
If any single consumer test class is later run with
`parallel="methods"` *and* shares one `TestBase` instance across methods,
`ThreadLocal<WebDriver>` should be introduced then — flagged as a
RECOMMENDED follow-up (section O), not required for the phase 1–5 unification
itself, since today's web tests already run single-threaded by design and
today's mobile tests already get correct isolation from the instance field.

BrowserStack integration for mobile is unchanged (javaagent + `browserstack.yml`
+ `verifyBrowserStackSdkActive()` check, ported as-is into
`AndroidBrowserStackSessionFactory`/`IosBrowserStackSessionFactory`). Web
BrowserStack support is new (`WebBrowserStackSessionFactory`) and should
follow the same "fail fast if the provider isn't actually active" pattern.

---

## I. Configuration Architecture

```
AutomationConfig                       (conceptual grouping; not necessarily one new class —
 |                                      YamlConfigReader's existing flat-map already supports
 +-- CommonConfig     (logging.*, reporting.*, proxy.*, accessibility.*)   this via dotted keys)
 +-- WebConfig        (browser.*)
 +-- MobileConfig     (android.*, ios.*  <- NEW sections merged from mobile-config.yaml)
 +-- ProviderConfig   (execution target/runMode, BrowserStack device matrices via browserstack.yml)
```

Precedence (unchanged from today's web behavior, extended to mobile config
which currently lacks an explicit `-D` override layer of its own beyond the
config-dir path):

```
System / Maven property (-D flag)
        >
Environment variable
        >
Project sdk-config.yaml (single file, gains android:/ios: sections)
        >
SDK built-in default
```

`browserstack.yml` remains a separate file **by necessity** — it is not an
SDK convention but a hard requirement of the BrowserStack Java SDK javaagent,
which looks for that exact filename at the project root. This is correctly
already the case for mobile and should also be reused (not reinvented) for
the new `WebBrowserStackSessionFactory`.

Credentials/secrets: none are currently hardcoded anywhere in the reviewed
config classes — `YamlConfigReader`/`MobileConfigReader` read only paths,
booleans, and non-sensitive settings. BrowserStack credentials are handled
by the BrowserStack SDK itself via `BROWSERSTACK_USERNAME`/
`BROWSERSTACK_ACCESS_KEY` environment variables (consistent with your stated
preference to pass credentials as env vars, not config files). No change
needed here — flag as **KEEP** in section F terms, not called out as a
separate table row since no class implements secret storage today.

---

## J. Web-Specific Components (must remain isolated)

- `driver.web.WebLocalSessionFactory` / `WebBrowserStackSessionFactory` — Chrome/Firefox/Edge options, proxy config, WebDriverManager resolution.
- `discovery.web.ElementCrawler` — DOM/XPath/ARIA/shadow-DOM/map-widget detection; must not be weakened.
- `listener.WebEventListener` — Selenium 4 `WebDriverListener` decorator; no Appium equivalent needed unless a future need arises (FUTURE, not required).
- Web-specific Selenium types (`ChromeOptions`, `By`, `Select`, `Actions`, etc.) used throughout `TestBase`'s existing helper methods.

## K. Mobile-Specific Components (must remain isolated)

- `driver.mobile.AndroidLocalSessionFactory` / `IosLocalSessionFactory` / `AndroidBrowserStackSessionFactory` / `IosBrowserStackSessionFactory` — capability sets, BrowserStack javaagent verification.
- `driver.mobile.MobileActions` — tap/swipe/gesture primitives; `mobileTap()`/`mobileSwipe()`/`mobileSwitchToWebView()`/`mobileSwitchToNative()` namespaced methods on `TestBase`.
- `discovery.mobile.MobileElementCrawler` — Appium page-source, accessibility-id, Android resource-id, UiAutomator, iOS predicate/class-chain, native/WebView context awareness (and per your stated hybrid-app requirement, must also be able to crawl WebView-hosted web content — a FUTURE item building on this same package, not solved by this review).
- `mobile.accessibility.NativeAccessibilityChecker`/`NativeAccessibilityIssue` — native-control accessibility properties with no web equivalent.

## L. Common Components (shared across both)

- `testbase.TestBase` — unified lifecycle.
- `config.SdkConfig` / `config.YamlConfigReader` — one config-dir resolver, one YAML file/parser.
- `execution.Platform` / `RunMode` / `ExecutionContext` / `SessionFactory` / `SessionFactoryRegistry` — one execution model.
- `driver.DriverManager` — one acquisition entry point.
- `accessibility.AccessibilityEngine` (+ `AccessibilityFinding`) — already unified this session; both `AxeCoreEngine` and `NativeMobileEngine` implement it.
- `discovery.ElementDiscoveryService` — thin shared contract only (see F); implementations stay separate and technology-specific.
- `utility.reports.*` (Extent/Allure), `utility.mailinator.*`, `utility.Excel_Reader`, `utility.PageContext` — already platform-agnostic, no change needed.

---

## M. Backward Compatibility

Verified usage before recommending any move/removal (per required process):

| Class | Usages found | Consumer-template dependency | Migration impact |
|---|---|---|---|
| `testbase.TestBase` | `functional-automation-consumer-template`'s `Test_Example` | Yes (`extends TestBase`) | None — public API unchanged, internal `driver` field goes static→instance transparently. |
| `mobile.testbase.MobileTestBase` | `mobile-functional-automation-consumer-template`'s `OnboardingAndHomeSmokeTest`, `WikipediaSearchTest` | Yes (`extends MobileTestBase`) | Kept as a deprecated subclass of the unified `TestBase` for one release — zero code change required in the consumer template. |
| `mobile.uiActions.NavigationUtility`, `NewServiceRequestPage` | Directly imported by `OnboardingAndHomeSmokeTest` | **Yes — active, non-optional dependency** | **Must** be copied into the consumer template's own `uiActions` package and the two imports updated *before* the SDK-side classes are deleted. This is the one item in this whole plan that requires a coordinated two-repo change, not just a deprecation shim. |
| `WebDriverFactory`, `MobileDriverFactory` | Called internally by `TestBase`/`MobileTestBase` only (no direct consumer-template usage found) | No direct consumer-template calls found | Kept as deprecated delegators regardless, since they are `public` API and a consumer project could call them directly even without today's templates doing so. |
| `SdkConfig`, `MobileSdkConfig`, `YamlConfigReader`, `MobileConfigReader` | Internal SDK use; `mobile-config.yaml`/`mobile-config.yaml.template` present in the mobile consumer template's `configuration/` directory | Yes (config files, not code) | `MobileConfigReader` keeps reading a standalone `mobile-config.yaml` as a fallback for one release if present, so the mobile consumer template's existing config file keeps working without an immediate edit; new consumers configure `android:`/`ios:` sections directly in `sdk-config.yaml`. |
| `ExecutionTarget`, `MobileExecutionStrategy`, `MobileExecutionStrategyFactory` | Internal SDK use only | No | Kept as deprecated delegators to `RunMode`/`SessionFactory`/`SessionFactoryRegistry`. |

General rule applied throughout sections F–M: **every renamed/merged class
keeps a deprecated, delegating version of its old name and public methods for
one release.** Nothing is deleted in phase 1–4; deletions (if any) happen
only in phase 5, and only for `uiActions/*` after its consumer-template
dependency is resolved.

---

## N. Refactoring Roadmap

### PHASE 1 — Structural cleanup
- Classes affected: none moved yet; add new `execution.Platform`, `execution.RunMode` (generalizing `ExecutionTarget`), `execution.ExecutionContext` (generalizing `MobileSessionRequest`), `execution.SessionFactory`/`SessionFactoryRegistry` (generalizing the mobile strategy interface/factory) as **new, unused-by-default** classes alongside the existing ones.
- Expected behavior change: none — nothing is wired up yet.
- Migration risk: none.
- Regression testing: `mvn clean test` only (no behavior touched).

### PHASE 2 — Driver/session unification
- Classes affected: introduce `driver.DriverManager`, `driver.web.WebLocalSessionFactory`, `driver.web.WebBrowserStackSessionFactory` (new capability), `driver.mobile.{Android,Ios}{Local,BrowserStack}SessionFactory` (split from today's two combined-platform classes). `WebDriverFactory`/`MobileDriverFactory` become deprecated delegators calling `DriverManager`.
- Expected behavior change: none for existing callers; new capability (`WebBrowserStackSessionFactory`) is opt-in only.
- Migration risk: low — thread-safety fix (static→instance) is isolated to this phase and worth its own test focus.
- Regression testing: full suite + a manual/CI smoke run of both an existing local web test and an existing local Appium test, since driver acquisition is the exact code path touched.

### PHASE 3 — Configuration unification
- Classes affected: `config.SdkConfig` (merge of `SdkConfig`+`MobileSdkConfig`), `config.YamlConfigReader` (extended with `android.*`/`ios.*`), `MobileConfigReader` becomes a deprecated delegator with standalone-file fallback.
- Expected behavior change: none if `sdk-config.yaml`/`mobile-config.yaml` are both present; a consumer relying only on `mobile-config.yaml` keeps working via the fallback.
- Migration risk: low-medium — the YAML section-nesting rules differ slightly between the two existing parsers (`YamlConfigReader` supports 2-level nesting via indent counting; `MobileConfigReader` supports 1-level via a "current section" pointer) and must be reconciled carefully so `android.appPath`-style keys parse identically either way.
- Regression testing: full suite + explicit unit tests asserting both old key names and new merged-file key names resolve correctly.

### PHASE 4 — Lifecycle unification (TestBase)
- Classes affected: `testbase.TestBase` gains `ExecutionContext`-based setup and namespaced mobile helpers (`mobileTap`, etc.); `mobile.testbase.MobileTestBase` becomes `extends TestBase`, deprecated.
- Expected behavior change: none for existing web or mobile tests (verified against both consumer templates in section M).
- Migration risk: medium — this is the highest-blast-radius phase since `TestBase` is the most-used class in the SDK; must be validated against **both** consumer templates, not just unit tests.
- Regression testing: full suite + `mvn test` in both `functional-automation-consumer-template` and `mobile-functional-automation-consumer-template` (per your existing "always validate consumer template before pushing" convention).

### PHASE 5 — Compatibility cleanup
- Classes affected: `uiActions/*` — after porting `NavigationUtility`/`NewServiceRequestPage` (and any other actually-used classes) into the mobile consumer template, remove `uiActions/*` from the SDK. Optionally begin removing deprecated delegators from phases 2–4 in a documented future major version (not part of this round).
- Expected behavior change: mobile consumer template gains its own local copies of two page objects; no behavior change to test outcomes.
- Migration risk: low, but **cross-repo** — requires a coordinated PR in `mobile-functional-automation-consumer-template` merged before (or atomically with) the SDK-side deletion.
- Regression testing: full SDK suite + mobile consumer template test run confirming `OnboardingAndHomeSmokeTest`/`WikipediaSearchTest` still pass with the relocated page objects.

Each phase: implement → `mvn clean test` (currently 396/396) → commit →
report back before starting the next phase — same working pattern used for
the accessibility roadmap this session.

---

## O. Final Recommendation

**REQUIRED**
1. Unify `SdkConfig`/`MobileSdkConfig` and `YamlConfigReader`/`MobileConfigReader` (Phase 3) — highest-value, lowest-risk fix; removes the two-config-file trap for any consumer using both platforms.
2. Generalize the mobile execution-strategy pattern into `Platform`×`RunMode`/`SessionFactory` and unify driver acquisition behind `DriverManager` (Phase 2) — fixes the real thread-safety bug (C.5) and closes the web-BrowserStack capability gap (C.4).
3. Unify `TestBase`/`MobileTestBase` into one lifecycle class with deprecated compatibility subclassing (Phase 4) — the architectural centerpiece the rest of the roadmap serves.
4. Migrate `uiActions/*` out of the SDK, coordinated with the mobile consumer template (Phase 5) — closes a real SDK-boundary violation flagged directly by you.

**RECOMMENDED**
5. Introduce a thin `discovery.ElementDiscoveryService` contract over the existing (unchanged) web/mobile crawlers, to give reporting and Page-Object generation one normalized result shape without weakening either crawler (C.7).
6. Add `ThreadLocal<WebDriver>` support to `TestBase` if/when any consumer project needs `parallel="methods"` with a shared test-class instance — not needed today given TestNG's per-instance model, but cheap to add once actually required (H).
7. Extract a `MobileEventListener` mirroring `WebEventListener`'s interaction-logging (not necessarily its inline accessibility-scan hook) once a concrete need arises — currently no consumer test has asked for it.

**FUTURE**
8. Extend `MobileElementCrawler` to crawl WebView-hosted web content using the same shared-context-switch model as `mobileSwitchToWebView()`, directly addressing your stated hybrid-app requirement — deserves its own design pass once phases 1–5 are stable, since it is a genuinely new capability, not a refactor.
9. Additional `RunMode`/`SessionFactory` implementations (Selenium Grid, Sauce Labs, LambdaTest, Perfecto) — the design supports this by construction (one enum value + one factory class each) but none should be built until a concrete need exists.
10. Consider a small technology-neutral `AutomationElement`/`Locator` facade only if a genuine cross-technology Page Object need emerges — not recommended preemptively, since it would recreate Selenium/Appium API surface for no current benefit (per the "no abstraction without a demonstrated responsibility" guardrail).

---

## Guardrail Compliance Checklist

| Guardrail | Status |
|---|---|
| ONE SDK | ✅ Target architecture (D) |
| ONE primary TestBase lifecycle | ✅ Section G |
| ONE configuration approach | ✅ Section I |
| ONE reporting architecture | ✅ unchanged, already shared (utility.reports) |
| ONE logging architecture | ✅ unchanged, already shared (log4j2.xml) |
| Web/Mobile are capabilities, not frameworks | ✅ Sections D, J, K, L |
| Selenium/Appium details isolated | ✅ Sections J, K |
| Common code not duplicated | ✅ Section F merges all 4 duplicate pairs |
| No abstraction without demonstrated responsibility | ✅ `ElementDiscoveryService` kept thin (F, O.5); no `AutomationElement` facade proposed (O.10) |
| Backward compatibility preserved where practical | ✅ Section M; deprecated delegators throughout |
| Mature Web implementation preserved | ✅ `TestBase`'s ~2000 lines, `ElementCrawler` — untouched |
| Mobile follows established SDK organization, not a second architecture | ✅ Section E package tree |
