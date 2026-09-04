# Mobile Automation Strategy — Decision Document

**Status:** **APPROVED (2026-09-02)** — reviewed and signed off; implementation started.
Final decision: **Java 20** for both this SDK and `functional-test-automation-sdk`
(desktop SDK bumped off its former Java 8 pin for ecosystem-wide alignment).
**Author context:** produced during joint research session with @vkruglyak_NYC.
**Scope:** whether/how to add mobile app (Android/iOS) automation capability to the
QA automation ecosystem, and where it should live.

---

## 1. Purpose

The team currently owns `functional-test-automation-sdk` (Selenium + TestNG + POM,
Java 20 as of 2026-09-02, desktop/web only). There is a business need to also automate **native and
hybrid mobile apps**. This document records the research findings and proposes an
architecture, so that an explicit decision can be made *before* any implementation
starts.

## 2. Constraints (given, non-negotiable unless stated otherwise)

- The current SDK is **production-critical** (Poletop and other consumers depend on
  it) and must not be destabilized.
- The current SDK is pinned to **Java 8** language level.
- Local Appium setup (Android SDK / Xcode, device provisioning, emulator/simulator
  management) is the single biggest pain point the team wants to avoid for day-to-day
  use — cloud device access is strongly preferred as the default path.
- The team **already has a BrowserStack subscription** today.

## 3. Key technical finding: the Java 8 / Appium client conflict — REVISED after prior-art validation

**Original finding (desk research only):** `io.appium:java-client` versions 8.x, 9.x,
and 10.x all require **Java 11+**. Only the long-abandoned 7.x line supports Java 8.
The proposed workaround was to skip `appium-java-client` entirely and drive Appium
through plain `RemoteWebDriver.executeScript("mobile: ...")` calls, staying on Java 8
with zero new dependencies.

**Correction based on real prior art:** the team already has a working mobile
automation project — **`311_Mobile_Automation`** (Azure DevOps, `OTI QA Automation`
project, `trunk` branch) — built for the NYC 311 app. It **does** use the official
`io.appium:java-client` (`9.4.0`), `AppiumDriver`/`AndroidDriver`/`IOSDriver`, and the
full `@AndroidFindBy`/`@iOSXCUITFindBy` + `PageFactory`/`AppiumFieldDecorator` page
object pattern — the same ergonomics as the desktop SDK's `@FindBy`, just
mobile-specific. Its `pom.xml` still *declares* `<project.java.version>1.8</project.java.version>`
and compiler source/target `1.8`, but its actual Azure Pipelines build
(`NativeApp.yml`) pins `jdkUserInputPath: C:\Program Files\Java\jdk-20` — i.e. **it
already runs on JDK 20 in CI**, not Java 8. Selenium 4.30.0 (also a dependency there)
itself requires Java 11+ to run regardless of compiler target, so the "Java 8" pom
setting was effectively already unenforced for this mobile project.

**Revised conclusion:** there is no real Java-8 constraint for mobile — the desktop
SDK's Java 8 pin is specific to *that* codebase and its consumers, not a
cross-ecosystem rule. This new mobile SDK should **target Java 11+ (JDK 17 or 20 LTS
recommended)** and use the **official `io.appium:java-client`**, matching the
`311_Mobile_Automation` precedent, rather than the raw `executeScript` workaround.
This gets us the full typed API (`.tap()`, gestures, `@AndroidFindBy`/`@iOSXCUITFindBy`
PageFactory annotations) for free and matches an already-proven, running pattern
instead of inventing a new one.

The raw `executeScript("mobile: ...")` mechanism is still worth keeping in our back
pocket for any gesture not covered by the typed API, but it is **no longer the primary
design** — the official client is.

## 3a. Real device/cloud switching mechanism (validated from prior art)

`311_Mobile_Automation` does **not** manually swap hub URLs or build BrowserStack
capabilities by hand. It uses the official **BrowserStack Java SDK**
(`com.browserstack:browserstack-java-sdk`, wired in via a Maven Surefire `-javaagent`)
plus a `browserstack.yml` config file (platforms list, app id, project/build name,
debug/video/network-log flags). The java agent transparently intercepts
`AppiumDriver`/`AndroidDriver`/`IOSDriver` session creation:

- Test code always does `new AndroidDriver(new URI("http://127.0.0.1:4723/").toURL(), options)` — i.e. it always *looks like* it's talking to a local Appium server.
- When the `browserstack` Maven profile is active (default) and the `-javaagent` is attached, the SDK **silently reroutes that session to BrowserStack** using `browserstack.yml`, without the test code knowing.
- When the `local` Maven profile is active instead (`-DtestInBrowserstack=false`), the driver factory takes a different branch and sets local capabilities (local `.apk`/`.ipa` path, real `deviceName`, `UiAutomator2`/`XCUITest` automation engine) and truly talks to a local Appium server.
- A single static helper, `TestBase.isTestInBrowserstack()`, checked everywhere (including inside the `DataProvider`, to tag rows with the current `mobileOsName`/environment), gates this branch logic. It internally calls `BrowserStackSdk.getCurrentPlatform()` to confirm an active BrowserStack session.

**This is a cleaner mechanism than manually building `bstack:options` capability
objects per provider** — it's the officially supported BrowserStack integration path
for Appium/TestNG projects, and it fits naturally with a Maven-profile-driven
local/cloud switch (`mvn test -Pbrowserstack` vs `mvn test -Plocal`). We should adopt
the same pattern: **Maven profiles + provider SDK javaagent (where the provider ships
one) as the default integration mechanism**, falling back to manual
`RemoteWebDriver`/vendor-nested-capabilities only for providers that don't ship a Java
SDK/agent (to be confirmed per-provider in phase 3).

## 4. Provider comparison (real-device / cloud Appium grids)

| Provider | Capability pattern | Java SDK / agent available? | App upload | Real devices | Notes |
|---|---|---|---|---|---|
| **BrowserStack App Automate** | `bstack:options` (nested `MutableCapabilities`) | **Yes** — `com.browserstack:browserstack-java-sdk` + `-javaagent`, config via `browserstack.yml` (**validated in prior art**, see §3a) | REST API (`https://api-cloud.browserstack.com/app-automate/upload`) | Yes, large matrix | **Already subscribed. Already proven working** in `311_Mobile_Automation`. Default/Phase-1 provider. Hub (manual fallback): `hub.browserstack.com/wd/hub`. |
| **Sauce Labs Real Device Cloud** | `sauce:options` | Sauce Labs also ships a Java SDK equivalent (Sauce Bindings) — not yet validated first-hand | REST API (`/v1/storage/upload`) | Yes | Appium 3 / strict W3C. Deep ADB/WDA device control (device logs, network throttling). |
| **LambdaTest (TestMu AI)** | `lt:options` | LambdaTest ships a similar SDK — not yet validated first-hand | REST API (`manual-api.lambdatest.com/app/upload/...`) | Yes, claims 10,000+ devices | Similar maturity to BrowserStack. |
| **Perfecto** | `perfecto:options` | Not confirmed | REST API / repository upload | Yes | Enterprise-grade; strong AI-assisted root-cause analysis tooling. Higher cost tier typical. |
| **Kobiton** | Appium-standard + Kobiton-specific caps | Not confirmed | REST API (3-step: get upload URL → PUT to S3 → register) | Yes (cloud **and** on-prem/hybrid device labs) | Only one of this set that supports on-prem/hybrid labs — worth remembering if the team ever wants private device racks. |
| **AWS Device Farm** | N/A — different execution model | N/A | Batch test-package + spec YAML executed server-side, OR a separate short-lived "remote access" debug session | Yes | **Excluded from Phase 1.** Not a drop-in "point driver at a hub URL" provider — would need its own execution path/plugin, not a config-only addition. |
| **Firebase Test Lab** | N/A | N/A | N/A | Yes | **Excluded.** Native automation is Espresso/XCTest/Robo-based; Appium/WebDriver support is weak/indirect. Poor fit for a generic Selenium/Appium framework. |

**Common thread:** BrowserStack, Sauce Labs, LambdaTest, and Perfecto all use the
identical **W3C vendor nested-capability pattern** — a single extra capability key
holding a nested capabilities object, plus standard `appium:`-prefixed keys for
everything else — as a manual-integration fallback. BrowserStack additionally ships an
official Java SDK/javaagent that we've now seen working in production (§3a); that is
the preferred integration mechanism where available, with manual
`RemoteWebDriver`/vendor-capabilities as the fallback for providers without one. This
directly supports a phased "start with BrowserStack, add others only on demand"
rollout instead of over-building a generic provider plugin system on day one.

## 5. Common abstraction sketch (illustrative only — not final API, updated to reflect §3/§3a)

```java
public class MobileDriverFactory {
    // Mirrors the validated 311_Mobile_Automation pattern: the driver always points at
    // a local Appium URL. When the BrowserStack Maven profile + javaagent are active,
    // BrowserStack's SDK transparently reroutes the session to the cloud; the "local"
    // profile instead injects local app path/device capabilities for a real local run.
    public static AppiumDriver getDriver(String mobileOS, String deviceName) {
        switch (mobileOS.toLowerCase()) {
            case "android": return getAndroidDriver(deviceName);
            case "ios":     return getIosDriver(deviceName);
            default: throw new IllegalArgumentException("Unsupported mobile OS: " + mobileOS);
        }
    }

    private static AppiumDriver getAndroidDriver(String deviceName) {
        UiAutomator2Options options = new UiAutomator2Options();
        if (!MobileTestBase.isRunningInCloud()) {
            options.setCapability("app", localAppPath("app-debug.apk"));
            options.setCapability("deviceName", deviceName);
            options.setCapability("automationName", "UiAutomator2");
        }
        return new AndroidDriver(localAppiumUrl(), options); // http://127.0.0.1:4723/
    }
}

public class MobileActions {
    // Typed calls from the official appium-java-client, e.g.:
    public static void tap(AppiumDriver driver, WebElement element) { /* ... */ }
    public static void swipe(AppiumDriver driver, int startX, int startY, int endX, int endY) { /* ... */ }
    // Falls back to executeScript("mobile: ...") only for gestures with no typed API.
}
```

## 6. Architecture decision: separate repo vs. embedding in current SDK

| Factor | Embed in `functional-test-automation-sdk` | Separate `mobile-functional-test-automation-sdk` |
|---|---|---|
| Blast radius if something breaks | High — shared release train with production-critical desktop SDK | Isolated |
| Java version flexibility | Locked to Java 8 forever (desktop constraint) | Free to target Java 11+/17/20 as needed by `appium-java-client`/Selenium (see §3) |
| Release cadence | Coupled to desktop SDK releases | Independent |
| Dependency footprint for desktop-only consumers | Bloated with mobile-only deps (Appium client, BrowserStack SDK, etc.) | Zero impact |
| Ownership / changelog clarity | Mixed concerns in one CHANGELOG | Clean separation |
| Shared utility reuse | Direct, no dependency needed | Needs a dependency on the desktop SDK for shared utilities only |

**Recommendation: separate repository**, `mobile-functional-test-automation-sdk`
(this repo), targeting **Java 11+ (17/20 LTS)**. It depends on
`functional-test-automation-sdk` only for config/reporting/Excel utilities, not for
`WebDriverFactory`/`TestBase`, which are desktop-browser-specific and won't be reused
as-is.

## 6a. Relationship to existing prior art (`311_Mobile_Automation`)

This is **not a greenfield design** — a real, running mobile automation project
already exists: `311_Mobile_Automation` (Azure DevOps project `OTI QA Automation`,
`trunk` branch), built for the NYC 311 app (Android + iOS, native, BrowserStack +
local Appium). Rather than diverging from it, this new SDK should **extract and
generalize its proven patterns** into a reusable library, the same relationship the
desktop SDK has to the original Poletop framework:

| Prior-art element (`311_Mobile_Automation`) | Generalize into this SDK as |
|---|---|
| `mobile.automation.testBase.MobileDriverFactory` (switch on OS, BrowserStack-vs-local branching) | `com.test.automation.sdk.mobile.driver.MobileDriverFactory` |
| `mobile.automation.testBase.TestBase` (`isTestInBrowserstack()`, TestNG lifecycle, Excel data, logging) | `com.test.automation.sdk.mobile.testbase.MobileTestBase` |
| `@AndroidFindBy`/`@iOSXCUITFindBy` + `PageFactory`/`AppiumFieldDecorator` page objects (e.g. `HomePage.java`) | Same pattern, documented as the mobile page-object convention (mobile equivalent of the desktop `page-object-creation.instructions.md`) |
| `browserstack.yml` + Maven `browserstack`/`local` profiles + `-javaagent` wiring in `pom.xml` | Shipped as a reusable `pom.xml` fragment / setup doc for this SDK's consumers |
| `configuration/config.properties`, `Excel_Reader`, `ExcelToMapList`, `PropertiesReader` utilities | Reuse directly from `functional-test-automation-sdk` where equivalent utilities already exist, instead of re-copying `311`'s versions |
| Azure Pipelines YAML (`NativeApp.yml`/`NativeAppIOS.yml`, JDK 20, "Selenium Pool" agent, Maven `test` goal + `browserstackConfigFile` system property) | Reference implementation for this SDK's own consumer pipeline template |
| `AGENTS.md` (project-specific AI agent instructions, e.g. no editing `testData`/`.properties` files, terse response style) | Not part of the SDK itself — stays a per-consumer-project convention, not something to standardize into the SDK |

Net effect: Phase 1 of this SDK is largely a **refactor/generalization of
`311_Mobile_Automation`'s existing, working code** into a reusable library + template,
not new invention. This significantly de-risks Phase 1.

## 7. Proposed repo structure (once implementation starts)

```
mobile-functional-test-automation-sdk/
├── docs/
│   ├── proposals/mobile-automation-strategy.md   (this file)
│   └── REQUIREMENTS.md
├── src/main/java/com/test/automation/sdk/mobile/
│   ├── driver/MobileDriverFactory.java
│   ├── provider/MobileProvider.java, BrowserStackProvider.java, ...
│   ├── actions/MobileActions.java
│   └── testbase/MobileTestBase.java
├── src/test/java/...
├── configuration/mobile-config.yaml(.example)
├── pom.xml   (depends on functional-test-automation-sdk for shared utils only)
├── CHANGELOG.md
└── README.md
```

## 8. Phased rollout plan

- **Phase 1 (MVP):** BrowserStack (default) + local Appium (alternate profile), both
  modeled directly on the validated `311_Mobile_Automation` pattern (§3a, §6a):
  official `io.appium:java-client`, Java 11+ (17/20 LTS), BrowserStack Java SDK +
  `-javaagent` + `browserstack.yml`, Maven `browserstack`/`local` profiles,
  `MobileDriverFactory`, `MobileActions` (typed gestures with `executeScript`
  fallback for anything uncovered), `MobileTestBase` (`isRunningInCloud()` gate,
  TestNG lifecycle, config loading, reporting hooks mirroring desktop `TestBase`
  conventions), `@AndroidFindBy`/`@iOSXCUITFindBy` page-object convention. Both
  Android and iOS, since `311_Mobile_Automation` already exercises both. Validate
  end-to-end by refactoring/porting the 311 project onto this SDK.
- **Phase 2:** Formalize the local-Appium developer setup (Android SDK/emulator,
  Xcode/simulator docs) as first-class, not just an "alternate profile" — since
  Phase 1 already includes local support per corrected scope, Phase 2 is about
  hardening/documenting it (setup docs, troubleshooting guide, CI agent
  requirements) rather than adding it from scratch.
- **Phase 3 (only if a concrete need arises):** additional cloud providers (Sauce
  Labs, LambdaTest, Perfecto) as new provider integrations — prefer each provider's
  official Java SDK/agent if one exists (as BrowserStack's does), falling back to
  manual `RemoteWebDriver` + vendor nested-capabilities otherwise.
- **Out of scope indefinitely unless requirements change:** AWS Device Farm, Firebase
  Test Lab (see §4 rationale).
- **Element crawling / mobile `PageObjectGenerator`** (§8a) is treated as its own
  workstream, not bundled into "Phase 1 MVP" above, but should start no later than
  Phase 1 validation against `311_Mobile_Automation` so locator quality is assessed
  against a real app from day one (see §8a's app-instrumentation risk).

## 8a. Mobile element crawling strategy (locator discovery)

The desktop SDK's `ElementCrawler`/`PageObjectGenerator`/`DataDrivenCrawler` (uniqueness
validation, `UNIQUE [x]`/`NOT UNIQUE`/`DYNAMIC`/`STRUCTURAL` markers, generated page
objects) is a core, load-bearing feature of that SDK's workflow (see
`page-object-creation.instructions.md` / `locator-strategy.instructions.md`). A mobile
equivalent is just as crucial here and was missing from the initial draft of this
document. It is **not a drop-in port** of the desktop crawler — mobile has a
different introspection model and materially different remote-execution economics.

### How mobile element introspection differs from web DOM crawling

There is no browser DevTools/CDP equivalent for a native mobile screen. Instead,
Appium exposes the current screen as a single XML snapshot via
`driver.getPageSource()`:
- **Android (UiAutomator2):** an XML dump of the accessibility hierarchy, exposing
  `resource-id`, `content-desc`, `text`, `class`, `clickable`, `bounds`, etc. per node.
- **iOS (XCUITest):** an XML dump of the accessibility tree, exposing `name`, `label`,
  `value`, `type`, etc. per node.

This is conceptually the mobile analogue of a DOM snapshot, but it is fetched as
**one whole-tree snapshot per call**, not queried incrementally like `document.
querySelectorAll`.

### Mobile locator priority ladder (proposed, mirrors the desktop ladder's intent)

**Android:** `resource-id` → `content-desc` (accessibility id) → `text` (exact) →
`-android uiautomator` (UiSelector DSL, for compound conditions only) → `xpath`
(last resort, slow and fragile — mirrors the desktop ladder's "never positional"
rule; index-based `xpath` predicates are rejected the same way `(//input)[2]` is
rejected today).

**iOS:** `accessibility id` (name/label) → `-ios predicate string` (exact attribute
match) → `-ios class chain` (structural, for compound conditions only) → `xpath`
(last resort).

**Dynamic/unstable identifier patterns to reject (mobile equivalent of `mat-input-\d+`
etc.):** Android auto-generated/list-recycled view ids (e.g. RecyclerView/ListView
item ids that repeat per row and aren't semantically unique), coordinates/`bounds`-only
matches, and any identifier that changes between app builds/versions. Same
`UNIQUE [x]` / `NOT UNIQUE` / `DYNAMIC` / `STRUCTURAL` reporting convention as the
desktop crawler should be reused so the two tools feel familiar to the same engineers.

### Critical remote-execution design constraint (the point you flagged)

The desktop `ElementCrawler` validates uniqueness by issuing one live
`driver.findElements(By...)` call **per candidate locator per element** against a
(usually local) browser session. Naively porting that approach to mobile would mean
one remote WebDriver command per candidate, against a **cloud session** (BrowserStack
or otherwise) with real network latency (typically 150–500ms+ per command, vs.
near-zero for a local desktop browser). A single screen with, say, 40 interactive
elements × 3–4 candidate locators each could mean 120–160 remote round trips —
potentially 30–90+ seconds of pure network latency for **one screen**, and cloud
providers bill by session-minute, so this directly costs money, not just time.

**Design decision:** the mobile crawler should NOT validate uniqueness with one
remote call per candidate. Instead:
1. Fetch `driver.getPageSource()` **once** per screen (one remote call).
2. Parse the returned XML **entirely in memory, locally** (no further remote calls).
3. Compute uniqueness for `resource-id`/`content-desc`/`accessibility id`/`name`/`text`
   candidates by counting matching nodes **within the already-fetched XML tree** —
   this is a local string/XPath-on-in-memory-DOM operation, not a network call, and
   is effectively free and instant regardless of whether the session is local or
   cloud-hosted.
4. Reserve a **live** `driver.findElements(...)` round trip only for locators that
   can't be statically verified from the static tree alone (e.g., a compound
   `-android uiautomator` UiSelector expression using runtime-only predicates, or a
   locator whose uniqueness could differ due to app state not captured in a single
   snapshot). These should be clearly flagged in the crawler's report as
   "remote-verified" (cost: 1 round trip) vs. "statically verified" (cost: 0 round
   trips), so engineers can see where the expensive checks are being spent.
5. Take the screenshot (`driver.getScreenshotAs(...)`) once, for the report, alongside
   the single `getPageSource()` call.
6. **Release the remote session as soon as the page source + screenshot are captured**
   — do the (potentially slow) XML parsing/uniqueness-computation/report-generation
   work *after* quitting the driver, not while holding the cloud session open. This
   both avoids idle-timeout disconnects on cloud providers and minimizes billed
   session-minutes.

Net effect: a mobile screen crawl costs **~2 remote calls** (page source +
screenshot) plus a small number of remote-verified edge cases, instead of hundreds —
this is actually a meaningfully *better* remote-call profile than what the desktop
crawler already does today, not a regression to design around.

### Additional remote/cloud-specific considerations

- **Screen-stability wait before capture:** cloud real devices are more prone to
  transient interstitials (OS permission dialogs, carrier/update popups, keyboard
  auto-suggest bars, animations) than a local emulator. Before calling
  `getPageSource()`, the crawler should poll and compare successive page-source
  hashes (mirrors the desktop SDK's v1.9.0 generic DOM-stability wait) until stable,
  to avoid crawling a mid-transition screen.
- **Hybrid apps / WebViews:** for a hybrid screen, part of the UI may be a native
  accessibility tree and part may be a `WEBVIEW_*` context. The crawler must check
  `driver.getContextHandles()` and, when a WebView context is present and in focus,
  switch to it (`driver.context("WEBVIEW_x")`) and reuse the **existing desktop
  `ElementCrawler`/DOM-based logic** for that portion of the screen (it already does
  CDP-free DOM crawling), falling back to the native XML-tree approach for
  `NATIVE_APP` context. This means hybrid-app support could reuse real logic from
  `functional-test-automation-sdk` rather than reinventing DOM crawling for
  WebViews — worth exploiting given the dependency this SDK already has on that SDK's
  utilities.
- **App-instrumentation dependency (real risk, not a design choice we control):**
  locator quality is directly gated by whether the app under test was built with
  stable identifiers in mind (Android `resource-id`/`contentDescription`, iOS
  `accessibilityIdentifier`). Unlike web (where a DOM always has *something* to
  query), some cross-platform frameworks (notably **Flutter**, which renders to a
  single opaque canvas) expose almost nothing to the standard accessibility tree
  without additional app-side instrumentation or a Flutter-specific Appium driver
  extension. **Open question:** confirm what framework `311_Mobile_Automation`'s app
  and any other pilot candidates are built with (native Android/iOS, React Native,
  Flutter, or hybrid) before assuming the crawler will produce usable output — the
  existing `content-desc`/`accessibility id` locators already seen in `311`'s page
  objects (§6a) are a good sign it's natively instrumented, not Flutter.

### Proposed components (Phase 1/2 scope, mirroring desktop naming)

```
com.test.automation.sdk.mobile.crawler/
├── MobileElementCrawler.java       // getPageSource() + screenshot, in-memory XML parse, uniqueness computation
├── MobileLocatorCandidate.java     // one candidate locator + its verification method (static vs. remote) + UNIQUE/NOT UNIQUE/DYNAMIC marker
├── MobilePageObjectGenerator.java  // emits @AndroidFindBy/@iOSXCUITFindBy page object skeletons from UNIQUE candidates
└── MobileCrawlerReportWriter.java  // text/markdown report, mirrors desktop's test-output/crawler/*.txt convention
```

This is **not** committed to Phase 1 MVP scope by default (see the phased rollout
plan above) — it should be scoped and sequenced explicitly once decision #6 below is
signed off, but no later than the start of the `311_Mobile_Automation` pilot
migration, so the team can judge real locator quality against a real, currently
undocumented app rather than a hypothetical one.

### Implementation status (2026-09-03)

The crawler described above is now implemented in
`com.test.automation.sdk.mobile.crawler`, with two additions beyond the original
scope, added after live research into current prior art (Appium MCP's
`generate-all-locators`, `robotframework-appium-smartlocator`, and ByteDance's
Fastbot-Android — see citations in `MobileElementCrawler`/`MobileDataDrivenCrawler`
Javadoc):

- **Compound locator fallback** (`MobileElementCrawler#buildCompoundCandidate`) —
  when no single attribute (resource-id/content-desc/text) is independently unique,
  combines them into one XPath predicate and re-checks uniqueness against a
  precomputed compound-frequency map (still O(n), zero extra remote calls). Mirrors
  Appium MCP's compound-locator generation and the desktop locator-strategy skill's
  "combine attributes for specificity" rule.
- **`MobileDataDrivenCrawler` + `MobileCrawlerStep`** — a deterministic,
  step-list-driven multi-screen crawler that fingerprints each screen (tag +
  resource-id/content-desc of every identifiable element, ignoring free text) and
  skips merging duplicate states. This borrows Fastbot's core idea (walk the app,
  don't re-process known states) while staying deterministic/repeatable, since a
  test-automation SDK needs reviewable, non-randomized crawls rather than
  Fastbot's RL-based exploration.

Unit test coverage (`MobileElementCrawlerTest`, JUnit 5) exercises the driver-free
core: `analyzePageSource`, dynamic-id rejection, compound-candidate resolution, iOS
attribute mapping, report rendering, and page-object generation. Driver-dependent
behavior (screen-stability polling, WebView delegation, step execution) is left for
integration/smoke testing against a real or emulated device.

## 9. Open decisions — ALL APPROVED 2026-09-02, implementation started

1. ✅ **Approved: separate-repo architecture** (§6).
2. ✅ **Approved repo name**: `mobile-functional-test-automation-sdk`.
3. ✅ **Approved Phase 1 scope, Java version finalized as 20** (not 17/21): official `io.appium:java-client`, BrowserStack (default, via official Java SDK) **and** local Appium (alternate Maven profile) both in Phase 1, generalized from the working `311_Mobile_Automation` project. Desktop `functional-test-automation-sdk` also bumped from Java 8 to Java 20 for ecosystem alignment.
4. ✅ **Confirmed `311_Mobile_Automation` as the Phase 1 source-of-truth/pilot.**
5. ✅ **Resolved (2026-09-03)**: added `.github/instructions/mobile-locator-strategy.instructions.md` — mirrors the desktop locator-strategy skill's priority ladder and dynamic-id rejection rules, adapted for `@AndroidFindBy`/`@iOSXCUITFindBy`, Appium's compound-locator/uiautomator/predicate-string strategies, and the remote-call-economics constraint from §8a. Includes explicit React Native fallback guidance (see #7 below).
6. ✅ **Approved and implemented (2026-09-03)**: one-shot `getPageSource()` +
   local in-memory uniqueness computation is the default validation strategy (not
   per-candidate remote `findElements` calls); remote-verification
   (`verifyCandidateRemotely`) is reserved for compound/dynamic locators only.
   Implemented as its own crawler package rather than deferred to a later phase.
7. ✅ **Resolved (2026-09-03), inferred from locator evidence in `311_Mobile_Automation`'s
   existing page objects** (not from app source code directly — flag for a source-level
   confirmation if the app repo becomes available): the app is very likely **React
   Native**. Evidence: (a) most screens use generic `android.widget.TextView`/
   `android.view.View` (Android) or long `XCUIElementTypeOther` chains (iOS) with no
   `resource-id`/`name` at all — RN does not assign native ids unless a `testID` is set;
   (b) `HomePage.java` uses accessibility labels following the exact pattern
   `"<label> tab. <index> of <count>. Double tap to activate."`, which is RN's default
   auto-generated accessibility hint format for tab-like components; (c) one screen
   (`ParkFinder.java`, a native map/search feature) has real `resource-id`s carrying the
   app's own package name (`gov.nyc.doitt.ThreeOneOne:id/...`), consistent with a common
   RN pattern of embedding a native module for map-heavy screens while the rest of the
   app stays RN. **Crawler implication**: the compound-locator fallback and
   text-plus-structure fallback rules in the new locator-strategy doc matter more for
   this app than resource-id-based strategies, since most RN screens have no id-based
   locators to combine in the first place.

## 10. Risks / known gaps (carried over, for awareness)

- No remote git hosting decided yet for this repo (local-only so far).
- Release process for a second SDK (Azure Artifacts feed, `maven-repository`
  publishing, consumer-template wiring) is not yet designed — will need its own
  `release.ps1`-equivalent, likely adapted from the desktop SDK's.
- BrowserStack app-upload API requires credentials/config management — needs a
  `mobile-config.yaml` + secrets-handling convention (mirroring `sdk-config.yaml`
  pattern from the desktop SDK, kept out of git).
- **Security note:** `311_Mobile_Automation`'s current `browserstack.yml` (as
  committed to `trunk`) has the BrowserStack `userName`/`accessKey` **in plaintext,
  committed to source control**. This SDK's `browserstack.yml`/`mobile-config.yaml`
  equivalent must be `.gitignore`d by default (mirroring the desktop SDK's
  `sdk-config.yaml` pattern), with only a `.example` placeholder version committed.
  Recommend flagging this to the 311 project owners separately as a credential
  hygiene issue outside this SDK's scope.
