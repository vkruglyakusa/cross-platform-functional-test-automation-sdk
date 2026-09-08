# Unified Cross-Platform Execution Architecture

Status: PROPOSED (not yet implemented)
Author: Copilot CLI (architecture pass), for @vkruglyak_NYC
Scope: `com.test.automation.sdk` — driver lifecycle, config, test base, execution strategy

---

## 1. The Actual Problem

The repository is the *physical* merge of two SDKs (web + mobile), not an
*architectural* one. Evidence, side by side:

| Concern | Web (existing) | Mobile (merged in) | Problem |
|---|---|---|---|
| Test base class | `testbase.TestBase` (~2000 lines, static `WebDriver driver`) | `mobile.testbase.MobileTestBase` (instance `AppiumDriver driver`) | Two unrelated lifecycles. No shared contract (setup/teardown, waits, reporting hooks). |
| Driver factory | `testbase.WebDriverFactory` (switches on browser name) | `mobile.driver.MobileDriverFactory` (delegates to a strategy) | Only mobile got a proper Strategy pattern. Web never got a "local vs. remote (Selenium Grid/BrowserStack Automate)" abstraction at all. |
| Config directory resolver | `testbase.SdkConfig` | `mobile.testbase.MobileSdkConfig` | Byte-for-byte duplicate logic (same `-D`/env/default resolution order), different system property names (`sdk.config.dir` vs `mobile.sdk.config.dir`), so a consumer running both web and mobile tests must configure two independent config roots. |
| YAML reader | `utility.YamlConfigReader` (full nested parser, drives `sdk-config.yaml`, already handles `accessibility.*`, `reporting.*`, `proxy.*`) | `mobile.config.MobileConfigReader` (near-identical hand-rolled parser, drives a **second** file `mobile-config.yaml`) | Two config files, two parsers, no single source of truth. A consumer project must maintain `sdk-config.yaml` *and* `mobile-config.yaml` *and* `browserstack.yml`. |
| Remote/cloud execution | None — web has no BrowserStack/Grid concept | `mobile.execution.{ExecutionTarget, MobileExecutionStrategy, MobileExecutionStrategyFactory, LocalExecutionStrategy, BrowserStackExecutionStrategy, MobileSessionRequest}` | The one genuinely good pattern here (pluggable execution target) is mobile-only. Web has no equivalent, so "run this Selenium suite on BrowserStack Automate" isn't a first-class concept anywhere in the SDK. |
| Page objects | None (correctly — web SDK has zero page objects) | `mobile.uiActions.*` (7 concrete 311-app screens: `HomePage`, `NewServiceRequestPage`, `PermissionControllerPopUp`, `TermsOfUsePage`, `UserDataPolicyPage`, ...) | App-specific screens baked into the reusable SDK jar. Violates the SDK's own contract that it ships zero app knowledge. |

Root cause: the mobile SDK was ported from a working standalone project
(`311_Mobile_Automation`, referenced directly in code comments) by translating
its package names, not by asking "what does this concept become when Web and
Mobile share one execution model?" Every class above answers the same
question — *"how do I get a driver, read config, and run a test?"* — twice,
with no shared interface between the answers.

---

## 2. Design Principle

> **One SDK models one execution lifecycle. "Web" and "Mobile" are platform
> *variants* of that lifecycle, not separate frameworks living in the same repo.**

Concretely, this means:

- One enum spans **platform × run-mode**, not a mobile-only `ExecutionTarget`.
- One `ExecutionStrategy` interface is implemented once per platform/run-mode
  combination — web-local, web-browserstack, android-local,
  android-browserstack, ios-local, ios-browserstack, mobile-web (hybrid
  WebView), with room for future providers (Sauce Labs, LambdaTest, Perfecto,
  Selenium Grid) added the same way mobile already does it well.
- One driver type at the public API boundary: **`WebDriver`**. This isn't a
  compromise — `io.appium.java_client.AppiumDriver` already `implements
  WebDriver`. A single `TestBase` can hold `protected WebDriver driver` and
  work for both Selenium and Appium sessions; platform-specific gesture APIs
  (tap/swipe) are opt-in helpers, not a reason to fork the base class.
- One config directory resolver, one YAML file, one parser.
- Zero app-specific code (page objects, flows, test data) inside the SDK.
  That belongs exclusively in consumer projects
  (`*-functional-automation-consumer-template`).

---

## 3. Proposed Package Layout

```
com.test.automation.sdk
├── config/
│   ├── SdkConfig.java                  // unified config-dir resolver (was testbase.SdkConfig + mobile.testbase.MobileSdkConfig)
│   └── YamlConfigReader.java           // unified parser (was utility.YamlConfigReader + mobile.config.MobileConfigReader)
│                                        // sdk-config.yaml gains `android:` / `ios:` sections; mobile-config.yaml retired
├── execution/
│   ├── Platform.java                   // WEB | ANDROID | IOS  (replaces implicit "which factory did you call")
│   ├── RunMode.java                    // LOCAL | BROWSERSTACK  (replaces mobile-only ExecutionTarget)
│   ├── DriverSessionRequest.java        // was MobileSessionRequest, generalized (platform, runMode, browserOrOS, deviceName)
│   ├── ExecutionStrategy.java          // was MobileExecutionStrategy, generalized; createDriver(...) returns WebDriver
│   ├── ExecutionStrategyFactory.java    // was MobileExecutionStrategyFactory; resolves by (Platform, RunMode)
│   ├── WebLocalStrategy.java            // NEW — absorbs today's WebDriverFactory browser switch
│   ├── WebBrowserStackStrategy.java     // NEW — first-class Selenium Automate support (parity with mobile)
│   ├── AndroidLocalStrategy.java         // was mobile.execution.LocalExecutionStrategy (android half)
│   ├── AndroidBrowserStackStrategy.java  // was mobile.execution.BrowserStackExecutionStrategy (android half)
│   ├── IosLocalStrategy.java             // was mobile.execution.LocalExecutionStrategy (ios half)
│   ├── IosBrowserStackStrategy.java      // was mobile.execution.BrowserStackExecutionStrategy (ios half)
│   └── MobileWebStrategy.java           // NEW — WebView/hybrid-app context switch, addresses "mobile crawler must handle hybrid apps"
├── driver/
│   └── DriverFactory.java              // was WebDriverFactory + mobile.driver.MobileDriverFactory, single entry point
├── testbase/
│   ├── TestBase.java                   // unified base; protected WebDriver driver; existing web helpers unchanged
│   └── MobileActions.java              // was mobile.actions.MobileActions; tap/swipe helpers, used only when driver instanceof AppiumDriver
├── crawler/                            // ElementCrawler (web) + MobileElementCrawler folded under one crawler package,
│                                        // sharing ElementInfo/LocatorCandidate shape; MobileElementCrawler already
│                                        // needs "crawl web inside a WebView" per your hybrid-app requirement, which
│                                        // is far easier once both crawlers share one report/locator model
├── accessibility/                      // already unified this session: AccessibilityEngine + AxeCoreEngine + NativeMobileEngine
└── utility/                            // unchanged (Excel_Reader, reports, mailinator, etc.)
```

`uiActions/*` (7 files) is **deleted from the SDK entirely** — it has no
architectural home here at all. It moves to
`mobile-functional-automation-consumer-template` if it's still useful as a
reference implementation, or is discarded if it was crawler-testing scaffolding.

---

## 4. What Changes for Callers

| Old | New | Compatibility |
|---|---|---|
| `MobileTestBase` | `TestBase` (unified) | `MobileTestBase` kept as a deprecated subclass of `TestBase` for one release, forwarding `driver` to the inherited field. |
| `WebDriverFactory.getWebDriver(browser)` | `DriverFactory.create(Platform.WEB, RunMode.resolve(), browser)` | Old method kept, delegates internally to the new factory. |
| `MobileDriverFactory.getDriver(os, device)` | `DriverFactory.create(Platform.ANDROID/IOS, RunMode.resolve(), os, device)` | Old method kept, delegates internally. |
| `ExecutionTarget` (mobile-only) | `RunMode` (platform-agnostic) | `ExecutionTarget.resolve()` kept, delegates to `RunMode.resolve()`. |
| `SdkConfig` / `MobileSdkConfig` | `config.SdkConfig` (single resolver, single `-Dsdk.config.dir`) | Old classes kept as deprecated aliases; `-Dmobile.sdk.config.dir` still honored as a secondary override for one release. |
| `mobile-config.yaml` | `android:` / `ios:` sections inside `sdk-config.yaml` | `MobileConfigReader.get(...)` kept, reads from the unified `YamlConfigReader` under the hood; still falls back to a standalone `mobile-config.yaml` if one is present, so existing consumer projects don't break immediately. |

No consumer-facing method signature is removed in phase 1. Deprecated
classes get `@Deprecated` + Javadoc pointing at the replacement, and are
slated for removal only in a documented future major version.

---

## 5. Migration Plan (phased, testable at each step)

1. **`execution` package** — introduce `Platform`, `RunMode`,
   `DriverSessionRequest`, `ExecutionStrategy`, `ExecutionStrategyFactory`.
   Port the two existing mobile strategies into `AndroidLocalStrategy` /
   `AndroidBrowserStackStrategy` / `IosLocalStrategy` /
   `IosBrowserStackStrategy` (splitting `LocalExecutionStrategy` /
   `BrowserStackExecutionStrategy`'s android/ios branches, which today live
   in one class each). Add `WebLocalStrategy` (wraps today's
   `WebDriverFactory` browser switch) and stub `WebBrowserStackStrategy`.
   *No behavior change yet — old classes untouched, new ones unused.*
2. **`config` package** — unify `SdkConfig`/`MobileSdkConfig` and
   `YamlConfigReader`/`MobileConfigReader`. Old classes become thin
   deprecated delegators.
3. **`driver.DriverFactory`** — single entry point calling
   `ExecutionStrategyFactory`. `WebDriverFactory`/`MobileDriverFactory`
   become deprecated delegators.
4. **`testbase.TestBase`** — merge `MobileTestBase`'s lifecycle
   (`@BeforeClass`/`@AfterClass`, `isRunningInCloud()`,
   `getCurrentPlatformOS()`) into `TestBase`, gated so web-only consumers see
   no behavior change (mobile `@Parameters` are `@Optional`, unused for web
   tests). `MobileTestBase extends TestBase` kept as deprecated compatibility
   shim exposing the same protected methods it does today.
5. **Remove `uiActions`** from the SDK; relocate to the mobile consumer
   template or delete, per your call.
6. **Crawler unification** (larger, separate follow-up) — only after 1-5 are
   stable and merged, since it's the piece tied to your hybrid-app
   requirement and deserves its own design pass.

Each phase: implement → `mvn clean test` (currently 396/396) → commit →
report back before starting the next phase, exactly like the accessibility
work this session.

---

## 6. Non-Goals / Explicitly Deferred

- Rewriting `TestBase`'s ~2000 lines of existing web helper methods — those
  are untouched; only the lifecycle/driver-acquisition surface is unified.
- Selenium Grid support — `RunMode` is designed to accept it later
  (`RunMode.GRID`) but it is not implemented in phase 1.
- Full crawler unification (item 6 above) — flagged for a dedicated design
  doc once the execution-layer unification lands, since "mobile crawler
  should also crawl WebView/hybrid content" is a meaningfully different
  problem (context switching between native and web DOM) from the
  config/driver duplication fixed here.

---

## 7. Why Not Just Move Folders

Renaming `mobile.execution.MobileExecutionStrategy` to
`execution.ExecutionStrategy` without generalizing `Platform`/`RunMode` would
leave web permanently second-class (no BrowserStack Automate support, no
shared driver-acquisition entry point) and would not remove the duplicate
config resolvers/parsers, which is the duplication most likely to bite a
consumer project first (two YAML files, two `-D` flags, easy to configure
one and forget the other).
