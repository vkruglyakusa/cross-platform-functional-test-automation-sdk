# Unified SDK Structure Cleanup — Senior Architecture Assessment & Target Design

## Status

**Architecture update / refactoring plan — implementation should proceed in controlled phases.**

**Adopted as the formal architecture document for this SDK (2026-09-09), superseding `sdk-structure-cleanup-assessment-updated-v3.md` (which itself superseded v2).**

### Phase 1 — Remove Real Duplication: ✅ COMPLETE (2026-09-09)

- `mobile.execution.MobileExecutionStrategy` / `MobileExecutionStrategyFactory` /
  `LocalExecutionStrategy` / `BrowserStackExecutionStrategy` /
  `MobileExecutionStrategySupport` / `ExecutionTarget` / `MobileSessionRequest`
  deleted entirely (`ExecutionTarget` needed no migration -- `execution.RunMode`
  already fully superseded it, including its legacy-flag fallback resolution).
- `mobile.driver.MobileDriverFactory` now owns the real local/BrowserStack
  Appium driver-creation logic directly (mirrors how `testbase.WebDriverFactory`
  already owns the equivalent web logic), exposing `getLocalDriver` /
  `getBrowserStackDriver`, with `getDriver` switching on `RunMode.resolve()`.
- All 4 `driver.mobile.*SessionFactory` classes now delegate straight to
  `MobileDriverFactory`, so `execution.SessionFactoryRegistry` is the only
  (platform, runMode) → session resolution mechanism left in the SDK.
- `MobileTestBase.isRunningInCloud()` switched from `ExecutionTarget.resolve()`
  to `RunMode.resolve()`.
- Validated: `mvn test` → 441/441 passing, `BUILD SUCCESS`; additionally
  verified with a real local Appium server + Android emulator
  (`MobileDriverFactory.getLocalDriver("android", "emulator-5554")` created a
  live session, confirmed via `driver.getSessionId()`, then cleanly quit).

### Phase 2 — Configuration Unification: ✅ COMPLETE (2026-09-09)

- Real YAML parser/singleton moved from `utility.YamlConfigReader` to
  `config.YamlConfigReader`; `utility.YamlConfigReader` is now a `@Deprecated`
  thin compatibility facade. Validated: `mvn test` → 442/442 passing.

### Phase 3 (Lightweight) — `AutomationSession`/`AutomationSessionFactory`: ✅ COMPLETE (2026-09-09)

- New `session` package: `AutomationSession` interface (`navigate`, `quit`,
  `unwrap(Class<T>)`), `session.internal.SeleniumSession`/`AppiumSession`,
  `session.AutomationSessionFactory` (`create(ExecutionContext)` delegates to
  `driver.DriverManager.acquire()`; `wrap(WebDriver, Platform)`).
  `AutomationElement`/`Locator` remain deferred per scope. Validated:
  `mvn test` → 459/459 passing.

### Appium WebView/Native Context Switching (Priority & Sequencing item 4): ✅ COMPLETE (2026-09-09)

- Promoted the proven `SupportsContextSwitching` pattern out of
  `MobileElementCrawler` into public `mobile.actions.MobileActions` helpers
  (`getAvailableContexts`, `getCurrentContext`, `isInWebViewContext`,
  `switchToNativeContext`, `switchToContext`, `switchToWebViewContext`) and
  `mobile.testbase.MobileTestBase` wrappers (`mobileSwitchToNative()`,
  `mobileSwitchToWebView()`, etc.). `MobileElementCrawler` refactored to reuse
  the same helpers. Validated: full suite green.

### Phase 6 — Discovery/Crawler Normalization: ✅ COMPLETE (2026-09-09)

- New `discovery` package (`LocatorCandidate`, `DiscoveredElement`,
  `DiscoveryResult`, `ElementDiscoveryService`) plus two adapters --
  `utility.WebElementDiscoveryAdapter` and
  `mobile.crawler.MobileElementDiscoveryAdapter` -- that normalize each
  crawler's existing output into the common contract without changing either
  crawler's own crawling/uniqueness-detection algorithm. Validated: full suite
  green (16 new unit tests covering the pure, driver-free conversion logic).

### Priority Item 6 / Phase 10 — AI Prompt/Skill Asset Standardization: ✅ COMPLETE (2026-09-09)

- New `src/main/resources/ai/{prompts,skills,schemas}` asset tree (sections
  26.1/26.2/29), additive to and clearly distinguished from the existing
  `sdk-prompts/`/`InstructionExtractor` mechanism (untouched). One concrete
  example shipped end-to-end for Phase 6's `ElementDiscoveryService`:
  `ai/schemas/discovery-result-v1.schema.json`,
  `ai/schemas/locator-recommendation-v1.schema.json` (`$ref`-linked),
  `ai/prompts/element-discovery/analyze-locator-candidates.md`, and
  `ai/skills/element-discovery/element-discovery.skill.yaml`. `ai/README.md`
  documents the convention. Per Guardrail #23, the Java-side agent
  orchestration runtime (tool registry/context builder) remains deferred
  until a concrete agent consumer exists -- only the asset files were added.
  `SdkResourcesTest` extended with a packaging-gate block for all 5 new
  files. Validated: full suite green.

### Phase 7 — Session Isolation / Parallel Safety: ✅ COMPLETE (2026-09-09)

- Removed remaining global static session state from `TestBase`: `baseURL`,
  `testRetryCount`, `parentWindow` converted `public static` -> instance
  fields (same pattern already established for `driver` in Phase 4 -- an
  instance field is correct and sufficient given TestNG's one-instance-per-
  class model, no `ThreadLocal` required). Removed two genuinely dead static
  fields outright: `TestBase.extent`/`TestBase.test` (unused
  `ExtentReports`/`ExtentTest` -- reporting already goes through the
  thread-keyed `ExtentTestManager`) and `WebDriverFactory.driver` (unused
  static `WebDriver`). Cleaned up the now-ineffective
  `testRetryCount = 0;` reset in `Listener.onFinish()` (was resetting a
  different object's copy once the field stopped being a shared static).
  Confirmed no other static session-holding fields exist in `mobile`,
  `driver`, `session`, or `execution` packages (only static factory
  *methods*). Validated: full suite green.

### Phase 8 — Package Cleanup (first gradual step): ✅ COMPLETE (2026-09-09)

- Moved `utility.WebElementDiscoveryAdapter` -> `discovery.WebElementDiscoveryAdapter`
  (+ its test to `discovery.WebElementDiscoveryAdapterTest`) so the `discovery`
  package's `ElementDiscoveryService` contract and its web implementation live
  together, mirroring where `MobileElementDiscoveryAdapter` already lives
  relative to its own crawler package. Updated the one internal caller and
  doc cross-references. Confirmed Class Migration Map item 1
  (`utility.YamlConfigReader` -> `config.YamlConfigReader`) was already done
  in Phase 2; the deprecated `utility.YamlConfigReader` facade is
  intentionally left in place (roadmap rule: remove compatibility wrappers
  only after consumer repos are migrated, not yet confirmed). This is a
  first, low-risk step of the "gradual cleanup" called for in Phase 8 --
  the broader `utility` package still holds legitimately-utility classes
  (`ElementCrawler`, `Excel_Reader`, `PageObjectGenerator`, etc.) that are
  not being moved speculatively. Validated: full suite green.

### Section 33 (Multi-Session and Hybrid Web + Mobile Driver Architecture) — Reviewed Against Current Code (2026-09-09)

> **Update (2026-09-09, later same day): items 1 and 2 below are now
> implemented and this block's "not yet"/"NOT yet buildable" wording is
> historical, not current state.** Public `mobileSwitchToNative()` /
> `mobileSwitchToWebView(...)` / `getAvailableContexts()` now exist on
> `MobileTestBase` (see `mobile.testbase.MobileTestBase`), and
> `AutomationSession`/`AutomationSessionFactory` are implemented and wired
> into `TestBase`/`MobileTestBase` (see "Phase 3" status note at the top of
> this document and `docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`
> section 16, Priority 1). The `SessionContext`/multi-session model described
> in item 2 remains correctly deferred (no concrete consumer need yet) --
> only the "NOT yet buildable because AutomationSession doesn't exist" premise
> is now outdated, not the deferral decision itself.

Section 33 was appended after section 34 ("Final Recommendation") without a
phase number, so as written it is **not yet sequenced into the Phase 1–12
roadmap** (section 20/30). Reviewing it against the current codebase found:

1. **The NATIVE_APP/WEBVIEW context-switch mechanism section 33 calls for
   already exists and works today** -- `mobile.crawler.MobileElementCrawler`
   already uses `io.appium.java_client.remote.SupportsContextSwitching`
   (`getContextHandles()` / `context(...)`) to hop into a WebView context,
   delegate to the desktop `ElementCrawler`, and restore `NATIVE_APP`
   afterward. It is currently private to the crawler, not exposed to test
   authors via `MobileTestBase`/`MobileActions` (no public
   `mobileSwitchToNative()`/`mobileSwitchToWebView()` yet).
2. **The `SessionContext`/`SessionManager`/multi-session `SessionRequest`
   model (33.1–33.4) is NOT yet buildable** -- it explicitly depends on the
   `AutomationSession`/`AutomationSessionFactory` abstraction from sections
   5–8, which is still just proposed (originally scheduled as Phase 3 in
   section 20) and has not been implemented. `TestBase.driver` today is a
   per-instance field (fixed away from `static` in Phase 4 of the prior
   `unified-sdk-architect-review.md`), but still **singular** -- one driver
   per test class, not a named-session map. Building `SessionContext` before
   `AutomationSession` exists would mean building the session-storage layer
   twice.
3. **Recommended sequencing fix** -- split section 33 into two tracks rather
   than treating it as one atomic phase:
   - **Near-term, low-risk, no architecture prerequisites**: promote the
     already-proven `SupportsContextSwitching` pattern from
     `MobileElementCrawler` into public `MobileTestBase`/`MobileActions`
     helpers (`mobileSwitchToNative()`, `mobileSwitchToWebView(...)`,
     `getAvailableContexts()`). This alone delivers the most common concrete
     hybrid scenario named in 33 (native file picker/permission dialog from a
     WebView) with no `SessionContext` work at all.
   - **Deferred until after Phase 3 (`AutomationSession`) exists, AND gated
     on a real consumer need** -- true independent multi-session support
     (Web + Mobile in the same test, `SessionContext`, `SessionManager`,
     multi-session YAML) should not be built speculatively; this follows the
     document's own Guardrail #10 ("Do not add layers without a concrete
     second implementation or responsibility"). No current consumer project
     (`311-Automation-SDK`, `mobile-functional-automation-consumer-template`)
     has a hybrid-session test case today.
4. This section's roadmap numbering (Phase 1–12 in sections 20/30) should be
   updated to insert multi-session work explicitly once Phase 3 is underway,
   rather than leaving section 33 as an unsequenced addendum.

### Priority & Sequencing Adjustments — Revised After Architecture Review (2026-09-09)

The prior feedback correctly identified the need to avoid speculative framework layers, but the roadmap is adjusted here to preserve the SDK's stated long-term goals without over-engineering.

1. **Structure Cleanup Phases 1–2 remain firm, near-term work.**
   - Phase 1 is complete and validated.
   - Phase 2 (configuration unification / `YamlConfigReader` relocation and precedence cleanup) remains required.

2. **A lightweight technology boundary is REQUIRED, but it must stay intentionally small.**
   The SDK already supports two real automation technologies — Selenium and Appium — and the architecture must remain capable of introducing a future browser/user-mimic technology without redesigning `TestBase`, configuration, reporting, or tool contracts.

   Therefore:
   - `AutomationSession` is a required architecture boundary.
   - `AutomationSessionFactory` / session-provider resolution is required as the common entry point over existing driver/session creation.
   - `AutomationElement` and a fully technology-neutral `Locator` are **deferred** until crawler/Page Object portability creates a concrete need.
   - Do not recreate Selenium/Appium APIs behind new wrappers.

3. **Multi-session support is an architectural requirement but not an immediate implementation requirement.**
   The design must permit one test execution to own more than one session, but `SessionContext` / named-session storage should be implemented only when the first real Web+Mobile (or multiple-device) consumer appears.
   This avoids redesign later without forcing speculative runtime complexity now.

4. **Appium WebView/native context switching is REQUIRED NOW.**
   The code already contains a proven `SupportsContextSwitching` implementation in `MobileElementCrawler`. Promote that behavior into supported SDK APIs such as:
   - `mobileSwitchToNative()`
   - `mobileSwitchToWebView(...)`
   - `getAvailableContexts()`

   This directly enables real hybrid mobile-Web cases such as native file pickers, permission dialogs, camera/gallery menus, and OS-native attachment flows.

5. **Existing crawlers/tools are REQUIRED SDK capabilities now.**
   Web crawlers, Mobile crawlers, locator investigation, Page Object generation, accessibility tools, and evidence collection already exist and are concrete SDK functionality. They should be organized as first-class tools rather than left as miscellaneous `utility` classes.

   Required now:
   - formal tooling package organization
   - crawler/discovery package consistency
   - common normalized result contracts where they provide immediate value
   - preservation of mature Web/Mobile algorithms

6. **Prompts and skills should be versioned assets now; AI runtime infrastructure remains conditional.**
   Prompt/skill resources are inexpensive, reusable architectural assets and should be standardized now under:
   - `src/main/resources/ai/prompts`
   - `src/main/resources/ai/skills`
   - `src/main/resources/ai/schemas`

   Deferred until the first concrete agent consumer:
   - Java-side `AgentToolRegistry`
   - `AgentContextBuilder`
   - agent orchestration runtime
   - agent output validators beyond current concrete needs

7. **Keep `TestBase` in the existing `testbase` package.**
   Renaming the package provides no meaningful architectural benefit and creates unnecessary consumer breakage.

8. **Phase naming remains disambiguated.**
   Use the prefix **"Structure Cleanup Phase N"** in commits, CHANGELOG entries, and design references.

**Net effect on the roadmap:**
- Required near-term: Structure Cleanup Phases 1–2, Appium public context switching, tooling/crawler organization, prompt/skill asset standardization, and the minimal `AutomationSession` boundary.
- Deferred until a concrete consumer: full `AutomationElement`/`Locator` abstraction, named multi-session runtime, full AI tool registry/orchestration infrastructure.

---

# 1. Executive Summary

The merged SDK is already significantly cleaner than two independent SDKs, but the current structure still exposes several legacy implementation concepts directly:

- `WebDriverFactory`
- `MobileDriverFactory`
- Web/mobile lifecycle specialization
- duplicated mobile execution strategy selection
- configuration implementation split between `config` and `utility`
- crawler/discovery implementations with no common contract
- framework APIs strongly tied to Selenium/Appium driver types

The immediate duplication issue is the parallel session-selection mechanism in `mobile.execution`, but stopping there would produce a structurally cleaner SDK without giving us a strong long-term extension boundary.

The recommended target is:

```text
                    Unified Automation SDK
                             |
                      Framework Core
                             |
          +------------------+------------------+
          |                                     |
   Automation Session                    Common Services
          |                                     |
   +------+------+                    +----------+----------+
   |             |                    |                     |
Selenium       Appium             Config/Execution    Reporting/Secrets/
Session        Session             /Lifecycle         Accessibility/etc.
   |             |
 Web          Mobile
```

The key design decision is:

> **Selenium and Appium are implementation technologies, not the public architecture of the SDK.**

Ordinary tests and Page Objects should depend primarily on stable framework concepts such as:

- `AutomationSession`
- `AutomationElement`
- `Locator`
- `ExecutionContext`
- `TestBase`

This provides a realistic path to introduce another user-interaction technology later — for example Playwright or another browser/user-mimic execution engine — without redesigning the full test framework.

This does **not** mean recreating every Selenium/Appium API. Technology-specific functionality remains available through specialized capabilities or a controlled escape hatch.

---

# 2. Current Structure — Findings From the Existing Assessment

The current package structure includes:

```text
com.test.automation.sdk
├── accessibility/
├── config/
├── driver/
│   ├── mobile/
│   └── web/
├── execution/
├── listener/
├── mobile/
│   ├── accessibility/
│   ├── actions/
│   ├── config/
│   ├── crawler/
│   ├── driver/
│   ├── execution/
│   └── testbase/
├── testbase/
├── tools/
└── utility/
```

The most important existing findings remain valid:

1. `config.YamlConfigReader` is only a facade while the real implementation is under `utility`.
2. Mobile has a second execution/session-selection abstraction:
   `MobileExecutionStrategy` / `MobileExecutionStrategyFactory`.
3. Web and mobile crawlers are legitimately different implementations but have no common discovery contract.
4. Accessibility already demonstrates a good shared-interface pattern.
5. `MobileTestBase extends TestBase`, so lifecycle duplication is already smaller than originally expected.
6. Reporting/listeners/secrets/shared utilities are already mostly common.

These findings are preserved from the original assessment. The new recommendations below extend the architecture beyond simple duplication cleanup so future capabilities can be added cleanly.

---

# 3. Architecture Principles

The refactoring should follow these rules.

## 3.1 One SDK, one framework lifecycle

There must be one primary test lifecycle.

Web and Mobile should not expose separate framework architectures.

## 3.2 Technology and execution target are separate concerns

These must not be mixed:

```text
Automation technology:
- Selenium
- Appium
- future Playwright / another user-interaction engine

Platform:
- WEB
- ANDROID
- IOS
- MOBILE_WEB

Execution target:
- LOCAL
- BROWSERSTACK
- future GRID / SAUCE / LAMBDATEST / PERFECTO
```

## 3.3 Keep the public framework API small

Do not build a deep abstraction stack.

Avoid:

```text
Test
 -> API
 -> Session
 -> Adapter
 -> Strategy
 -> Resolver
 -> Provider
 -> Capability Manager
 -> Driver Factory
```

Prefer:

```text
Test
  |
TestBase
  |
ExecutionContext
  |
AutomationSessionFactory
  |
+-- SeleniumSession
+-- AppiumSession
+-- FutureSession
```

## 3.4 Technology-specific code stays behind an implementation boundary

Ordinary tests should not need to know how Selenium/Appium sessions are created.

## 3.5 Do not recreate Selenium/Appium

The framework should abstract stable business-level operations only.

Technology-specific features remain available when needed.

---

# 4. Recommended Target Package Structure

```text
com.test.automation.sdk
│
├── testbase/               ← unchanged from today; NOT renamed to base/ (see
│   └── TestBase              "Priority & Sequencing Adjustments" note above)
│
├── config/
│   ├── AutomationConfig
│   ├── ConfigurationManager
│   ├── ConfigurationResolver
│   ├── SdkConfig
│   ├── YamlConfigReader
│   ├── WebConfig
│   ├── MobileConfig
│   └── ProviderConfig
│
├── execution/
│   ├── ExecutionContext
│   ├── ExecutionTarget
│   ├── Platform
│   ├── RunMode
│   └── AutomationTechnology
│
├── session/
│   ├── AutomationSession
│   ├── AutomationSessionFactory
│   ├── SessionManager
│   └── internal/
│       ├── SeleniumSession
│       ├── AppiumSession
│       ├── SeleniumSessionProvider
│       └── AppiumSessionProvider
│
├── element/
│   ├── AutomationElement
│   └── Locator
│
├── web/
│   ├── driver/
│   │   ├── WebLocalSessionProvider
│   │   └── WebBrowserStackSessionProvider
│   ├── discovery/
│   │   └── WebElementDiscoveryService
│   └── support/
│       ├── BrowserOptionsSupport
│       └── WebTechnologySupport
│
├── mobile/
│   ├── driver/
│   │   ├── AndroidLocalSessionProvider
│   │   ├── AndroidBrowserStackSessionProvider
│   │   ├── IosLocalSessionProvider
│   │   └── IosBrowserStackSessionProvider
│   ├── discovery/
│   │   └── MobileElementDiscoveryService
│   ├── actions/
│   │   └── MobileActions
│   ├── context/
│   │   └── MobileContextSupport
│   ├── accessibility/
│   └── support/
│       └── MobileTechnologySupport
│
├── discovery/
│   ├── ElementDiscoveryService
│   ├── DiscoveryContext
│   ├── DiscoveryResult
│   └── LocatorCandidate
│
├── accessibility/
├── reporting/
├── logging/
├── artifacts/
├── listener/
├── secrets/
├── tools/
└── utility/
```

This tree is a target logical structure, not a requirement to create empty packages. A package should exist only when it owns real responsibility.

---

# 5. Public Technology-Neutral API

## 5.1 `AutomationSession`

```java
public interface AutomationSession {

    AutomationElement find(Locator locator);

    void navigate(String url);

    void quit();

    <T> T unwrap(Class<T> technologyType);
}
```

Purpose:

- stable session contract for ordinary tests
- hides Selenium/Appium from the majority of test code
- enables future technology replacement
- provides controlled access to the underlying technology when necessary

The `unwrap()` method is intentionally an escape hatch. It prevents us from bloating the common API to cover every Selenium/Appium-specific operation.

---

## 5.2 `AutomationElement`

```java
public interface AutomationElement {

    void click();

    void type(String text);

    String getText();

    boolean isDisplayed();

    <T> T unwrap(Class<T> technologyType);
}
```

Only broadly stable operations should be placed here.

Do not attempt to reproduce every `WebElement` or Appium element method.

---

## 5.3 `Locator`

Page Objects should not normally depend directly on Selenium `By` or Appium `AppiumBy`.

Example:

```java
private final Locator username = Locator.id("username");
private final Locator submit = Locator.accessibilityId("Submit");
```

Each session implementation converts the common locator to the underlying technology.

This becomes especially important for crawler-generated Page Objects.

---

# 6. Replaceable Technology Boundary

The architecture should allow a future engine to be introduced like this:

```text
AutomationSession
      |
      +-- SeleniumSession
      |
      +-- AppiumSession
      |
      +-- PlaywrightSession       (future)
      |
      +-- UserMimicSession        (future)
```

Tests written against the common API remain stable where they use common capabilities.

Example:

```java
public class LoginPage {

    private final AutomationSession session;

    private final Locator username = Locator.id("username");
    private final Locator password = Locator.id("password");
    private final Locator submit = Locator.id("submit");

    public void login(String user, String pass) {
        session.find(username).type(user);
        session.find(password).type(pass);
        session.find(submit).click();
    }
}
```

Changing the underlying browser technology should require primarily:

- a new `AutomationSession` implementation
- locator mapping
- session provider/configuration
- technology-specific support

The Page Object above should not need redesign.

Important limitation:

> Tests that use `unwrap()` or specialized Selenium/Appium capabilities may require migration when technology changes.

That is acceptable and much more realistic than attempting complete vendor independence.

---

# 7. Unified Session Creation

The current architecture has both:

- shared `SessionFactory` / `SessionFactoryRegistry`
- mobile-specific `MobileExecutionStrategy*`

That duplication should be removed.

The target flow should be:

```text
TestBase
   |
ExecutionContext
   |
AutomationSessionFactory
   |
Session Provider Resolution
   |
+-- WEB + LOCAL                -> WebLocalSessionProvider
+-- WEB + BROWSERSTACK         -> WebBrowserStackSessionProvider
+-- ANDROID + LOCAL            -> AndroidLocalSessionProvider
+-- ANDROID + BROWSERSTACK     -> AndroidBrowserStackSessionProvider
+-- IOS + LOCAL                -> IosLocalSessionProvider
+-- IOS + BROWSERSTACK         -> IosBrowserStackSessionProvider
```

The registry/provider implementation may reuse the existing `SessionFactoryRegistry` internally.

There should be only **one** resolution mechanism.

---

# 8. Driver Factories — Recommended Change

The current public concepts:

```text
WebDriverFactory
MobileDriverFactory
```

should not remain primary public framework concepts.

They are technology implementation details.

Recommended migration:

```text
WebDriverFactory
    -> deprecated compatibility facade
    -> delegates to AutomationSessionFactory / Selenium provider

MobileDriverFactory
    -> deprecated compatibility facade
    -> delegates to AutomationSessionFactory / Appium provider
```

After consumer migration, they can be removed.

This is more extensible than moving both implementations directly into `TestBase`.

---

# 9. TestBase Design

The current inheritance:

```text
TestBase
  └── MobileTestBase
```

is already better than duplicate lifecycle implementations, but the final target should reduce lifecycle specialization further.

Preferred flow:

```text
                    TestBase
                       |
              resolveExecutionContext()
                       |
              createAutomationSession()
                       |
                  run lifecycle
```

`TestBase` owns:

- configuration loading
- execution-context creation
- session lifecycle
- setup/teardown
- logging
- reporting
- artifacts
- failure capture
- test metadata

It should **not** contain:

- Chrome option construction
- Android capabilities
- iOS capabilities
- BrowserStack-specific desired capabilities
- Appium context switching
- Selenium-specific browser internals

Those belong to technology/provider support classes.

`MobileTestBase` can initially remain as a deprecated or thin compatibility layer:

```java
@Deprecated
public abstract class MobileTestBase extends TestBase {
}
```

New tests should use `TestBase` with platform configuration.

---

# 10. Unified Configuration Architecture

Configuration should become a first-class common capability.

## 10.1 Target configuration model

```text
AutomationConfig
|
+-- CommonConfig
+-- WebConfig
+-- MobileConfig
+-- ProviderConfig
```

Example:

```yaml
automation:
  environment: stg
  platform: web
  executionTarget: local
  timeoutSeconds: 30

web:
  browser: chrome
  headless: true

mobile:
  platformName: android
  deviceName: emulator-5554
  automationName: UiAutomator2

provider:
  browserstack:
    enabled: false

reporting:
  enabled: true

logging:
  level: INFO
```

## 10.2 Precedence

Use exactly one precedence model:

```text
System / Maven property
        >
Environment variable
        >
Project YAML
        >
Provider YAML/default
        >
SDK default
```

The resolution logic must be implemented once.

---

# 11. Configuration Cleanup Actions

## Required

### Move the real `YamlConfigReader`

Current:

```text
utility.YamlConfigReader   <- real implementation
config.YamlConfigReader    <- facade
```

Target:

```text
config.YamlConfigReader    <- real implementation
```

Temporarily:

```text
utility.YamlConfigReader
    -> @Deprecated facade
    -> delegates to config.YamlConfigReader
```

Remove after consumer migration.

### Keep `SdkConfig`

`SdkConfig` remains the single source of truth for SDK configuration paths.

### Refactor `MobileConfigReader`

`MobileConfigReader` should not become a second configuration engine.

It should only expose typed mobile-specific values from the unified configuration system.

Longer-term target:

```text
ConfigurationManager
   |
   +-- getCommonConfig()
   +-- getWebConfig()
   +-- getMobileConfig()
   +-- getProviderConfig()
```

---

# 12. Execution Context

Use one execution descriptor.

Example:

```java
public class ExecutionContext {

    private Platform platform;
    private ExecutionTarget target;
    private AutomationTechnology technology;

    private String environment;

    private String browser;
    private String device;
    private String appReference;

    private Map<String, Object> options;
}
```

Examples:

```text
WEB + LOCAL + SELENIUM
WEB + BROWSERSTACK + SELENIUM
ANDROID + LOCAL + APPIUM
IOS + BROWSERSTACK + APPIUM
WEB + LOCAL + PLAYWRIGHT            future
WEB + REMOTE + USER_MIMIC_ENGINE    future
```

This separation prevents provider and technology concerns from becoming coupled.

---

# 13. Session Ownership and Parallel Execution

The framework should not rely on one global static driver.

Target:

```text
SessionManager
    |
ThreadLocal<AutomationSession>
```

or another scoped-session mechanism.

This enables:

- parallel Web tests
- parallel Mobile tests
- Web + Mobile execution in the same JVM
- multiple devices
- multiple BrowserStack sessions
- future mixed-technology execution

The actual implementation should be introduced only after verifying how the current `DriverManager` stores sessions.

---

# 14. Web and Mobile Package Consistency

Equivalent responsibilities should use equivalent structure.

Target example:

```text
web/
├── driver/
├── discovery/
├── support/
└── accessibility/

mobile/
├── driver/
├── discovery/
├── support/
├── actions/
├── context/
└── accessibility/
```

Avoid inconsistent vocabulary such as:

```text
web.driver.Factory
mobile.execution.Strategy
mobile.driver.Factory
```

for the same responsibility.

Use one architectural vocabulary.

---

# 15. Crawler / Discovery Architecture

The current Web and Mobile crawler implementations are legitimately different.

Do **not** merge their internal algorithms.

Create only a common result/contract layer:

```java
public interface ElementDiscoveryService {

    DiscoveryResult discover(
        AutomationSession session,
        DiscoveryContext context
    );
}
```

Implementations:

```text
WebElementDiscoveryService
MobileElementDiscoveryService
```

Both can return:

```text
DiscoveryResult
  |
  +-- LocatorCandidate[]
```

Example `LocatorCandidate`:

```text
type
value
score
source
stable
platform
context
```

Web keeps mature DOM heuristics.

Mobile keeps Appium native/XML-specific heuristics.

This allows:

- one reporting pipeline
- one Page Object generation model
- one gap-analysis model
- future discovery engines

without weakening Web behavior.

---

# 16. Accessibility

The existing use of `AccessibilityEngine` by mobile is a good architecture pattern and should be preserved.

Improve the result boundary by using one normalized finding model where practical.

Target:

```text
AccessibilityEngine
      |
      +-- WebAccessibilityEngine
      +-- NativeMobileEngine
      +-- FutureEngine
```

Normalized output:

```text
AccessibilityFinding
```

Technology-specific evidence may be attached to the common finding.

---

# 17. Reporting, Logging, Evidence, Secrets

These should remain framework-core services.

Do not create Web and Mobile variants unless the underlying technology requires a specific evidence collector.

Common services:

```text
ReportingService
LoggingService
ArtifactService
SecretService
TestMetadata
```

Technology-specific collectors may feed them:

```text
WebEvidenceCollector
MobileEvidenceCollector
```

but results should flow to the same reporting architecture.

---

# 18. Utility Cleanup

The existing generic `utility` package is too broad for long-term maintainability.

Do not move everything immediately, but gradually classify utilities by responsibility.

Example target:

```text
config/
reporting/
data/
mail/
discovery/
artifacts/
common/
```

Avoid a permanent dumping-ground `utility` package.

Classes should move only when touched by planned refactoring to reduce migration risk.

---

# 19. Class Migration Map

| Current Class / Area | Action | Target |
|---|---|---|
| `utility.YamlConfigReader` | MOVE implementation | `config.YamlConfigReader` |
| `config.YamlConfigReader` facade | REPLACE with real implementation | `config.YamlConfigReader` |
| old `utility.YamlConfigReader` API | DEPRECATE temporarily if externally used | compatibility facade |
| `mobile.execution.ExecutionTarget` | MOVE | `execution.ExecutionTarget` |
| `MobileExecutionStrategy*` | REMOVE after migration | logic handled by one session/provider registry |
| `MobileDriverFactory` | DEPRECATE as public architecture | delegate to unified session factory |
| `WebDriverFactory` | DEPRECATE as public architecture | delegate to unified session factory |
| `MobileTestBase` | KEEP temporarily / reduce to thin compatibility class | `TestBase` becomes primary lifecycle |
| `ExecutionContext` | KEEP and EXPAND carefully | common execution descriptor |
| `SessionFactoryRegistry` | KEEP / evolve internally | one session resolution mechanism |
| Web crawler classes | KEEP implementation | adapt to `ElementDiscoveryService` |
| Mobile crawler classes | KEEP implementation | adapt to `ElementDiscoveryService` |
| duplicated crawler step/result models | NORMALIZE where useful | common discovery result model |
| accessibility engines | KEEP | common engine/result contract |
| broad `utility` package | GRADUAL CLEANUP | responsibility-specific packages |

---

# 20. Refactoring Roadmap

## Phase 1 — Remove Real Duplication

- move `ExecutionTarget` to common `execution`
- remove `MobileExecutionStrategy*`
- make `SessionFactoryRegistry` / unified session selection the only resolution mechanism
- keep all existing tests green

Validation:

- Maven test suite
- real Android local smoke
- BrowserStack smoke
- iOS path validation

---

## Phase 2 — Configuration Unification

- move real `YamlConfigReader` to `config`
- centralize precedence
- make `MobileConfigReader` a typed mobile view, not a second resolver
- preserve compatibility facade temporarily
- document unified YAML layout

Validation:

- Web configuration tests
- Mobile configuration tests
- Maven/system property precedence
- environment variable precedence
- BrowserStack configuration

---

## Phase 3 — Lightweight Unified Session Boundary

This phase is **required**, but intentionally limited in scope.

Introduce:

```text
AutomationSession
AutomationSessionFactory
```

Implement technology-specific session wrappers/providers:

```text
SeleniumSession
AppiumSession
```

The purpose is to create one stable SDK session boundary over the existing Selenium/Appium implementations without recreating either API.

`AutomationElement` and a complete SDK `Locator` abstraction are **not required in this phase**. They remain optional later additions when crawler/Page Object portability justifies them.

Implementation requirements:

- preserve existing Selenium/Appium behavior
- preserve `SessionFactoryRegistry` as the single session-selection mechanism or evolve it behind `AutomationSessionFactory`
- existing `WebDriverFactory` / `MobileDriverFactory` may remain as compatibility/internal facades during migration
- provide a controlled `unwrap(Class<T>)` escape hatch where direct Selenium/Appium capabilities are required
- do not migrate every existing Page Object in this phase

---

## Phase 4 — TestBase Consolidation

After the lightweight session boundary is in place:

- `TestBase` becomes the only primary lifecycle
- session determined from `ExecutionContext`
- `MobileTestBase` becomes thin compatibility layer
- Web/Appium setup details move to session/provider implementations

---

## Phase 5 — Page Object / Locator Migration (CONDITIONAL)

New Page Objects use:

```text
AutomationSession
AutomationElement
Locator
```

Existing Selenium Page Objects remain compatible initially.

Migrate incrementally when touched.

---

## Phase 6 — Discovery / Crawler Normalization (REQUIRED)

Introduce common:

```text
ElementDiscoveryService
DiscoveryResult
LocatorCandidate
```

Adapt both crawler implementations without rewriting their algorithms.

---

## Phase 7 — Session Isolation / Parallel Safety

- remove global static session state where applicable
- introduce scoped or ThreadLocal session ownership
- validate mixed Web + Mobile parallel execution

---

## Phase 8 — Package Cleanup

Gradually move broad `utility` contents into responsibility-specific packages.

Remove deprecated compatibility wrappers only after consumer repositories are migrated.

---

# 21. Extension Scenario — Replacing Selenium Later

The target design should support this future scenario:

Today:

```text
AutomationSession
      |
SeleniumSession
```

Future:

```text
AutomationSession
      |
PlaywrightSession
```

or:

```text
AutomationSession
      |
UserMimicSession
```

The framework should not require redesign of:

- TestBase lifecycle
- configuration system
- reporting
- logging
- secrets
- test metadata
- ordinary Page Objects using common operations
- execution target/provider architecture

Only the technology implementation and technology-specific tests should change.

This is the primary reason to introduce the small session/element/locator abstraction.

---

# 22. Guardrails

1. One SDK.
2. One primary TestBase lifecycle.
3. One configuration-resolution mechanism.
4. One session-selection mechanism.
5. Web and Mobile use the same architectural vocabulary.
6. Selenium/Appium remain implementation technologies.
7. Do not expose Selenium/Appium types in new ordinary Page Objects unless needed.
8. Keep `unwrap()` / specialized capabilities for advanced technology-specific needs.
9. Do not recreate the complete Selenium/Appium API.
10. Do not add layers without a concrete second implementation or responsibility.
11. Preserve working Web behavior.
12. Preserve working Mobile behavior.
13. Keep BrowserStack/local provider logic separate from Selenium/Appium technology.
14. Make session ownership parallel-safe.
15. Keep mature crawler algorithms platform-specific.
16. Normalize crawler outputs, not algorithms.
17. Keep reporting/logging/secrets common.
18. Remove obsolete mobile-SDK artifacts after consumer-impact verification.
19. Use deprecation wrappers for controlled migration.
20. Every refactoring phase must compile and pass regression validation.
21. Keep the technology boundary intentionally small: `AutomationSession` is required; broader `AutomationElement` / `Locator`, named multi-session runtime, and full AI agent infrastructure are implemented only when a concrete consumer justifies them.
22. Existing crawlers/tools are first-class SDK capabilities and must not be deferred as speculative architecture.
23. Prompts and skills are versioned SDK assets; the LLM/agent runtime may remain external or conditional.

---

# 23. Definition of Done

The architecture cleanup is complete when:

- Web and Mobile clearly belong to one framework architecture
- no duplicate execution-selection logic remains
- no duplicate configuration-resolution logic remains
- one common execution context describes Web and Mobile runs
- one primary lifecycle owns Web and Mobile execution
- driver factories are no longer exposed as separate framework architectures
- the SDK exposes a stable technology-neutral `AutomationSession` boundary; broader element/locator abstraction is added only when justified
- Selenium can theoretically be replaced by implementing a new session technology without redesigning the whole SDK
- provider selection is independent of automation technology
- Web and Mobile discovery share a common result contract
- reporting, logging, accessibility results, artifacts, and secrets use common framework services
- session state supports safe parallel execution
- existing Web and Mobile tests remain operational or have a documented compatibility path

---

# 25. Tools, Crawlers, Prompts, and AI Agent Skills as First-Class SDK Capabilities

The SDK must treat **tools** as a required architectural layer, not as miscellaneous utilities.

This includes:

- Web crawlers
- Mobile crawlers
- locator investigators
- Page Object generators
- accessibility analyzers
- evidence collectors
- test-data analyzers
- reporting helpers
- AI-agent callable tools
- prompts
- reusable AI-agent skills

These capabilities are part of the SDK itself because they support test generation, maintenance, diagnosis, discovery, accessibility, RCA, and AI-assisted automation workflows.

The target should therefore evolve from:

```text
Unified Automation SDK
|
+-- Core Runtime
+-- Web
+-- Mobile
```

to:

```text
                        Unified Automation SDK
                                 |
       +-------------------------+-------------------------+
       |                         |                         |
   Runtime Core               Tooling Layer            AI Layer
       |                         |                         |
  Test execution          Crawlers / Generators      Prompts / Skills
  Sessions                Investigators              Agent Contracts
  Configuration           Evidence Tools             Tool Definitions
  Reporting               Accessibility Tools        AI Orchestration Support
       |
   +---+---+
   |       |
  Web    Mobile
```

The tooling and AI layers must use the same common framework contracts rather than creating a second architecture.

---

## 25.1 Tooling Package

Create a dedicated top-level package for SDK tooling.

Recommended structure:

```text
tools/
|
+-- discovery/
|   +-- ElementDiscoveryService
|   +-- DiscoveryContext
|   +-- DiscoveryResult
|   +-- LocatorCandidate
|
+-- crawler/
|   +-- web/
|   |   +-- WebElementCrawler
|   |   +-- WebDataDrivenCrawler
|   |
|   +-- mobile/
|       +-- MobileElementCrawler
|       +-- MobileDataDrivenCrawler
|
+-- pageobject/
|   +-- PageObjectGenerator
|   +-- WebPageObjectGenerator
|   +-- MobilePageObjectGenerator
|
+-- locator/
|   +-- LocatorInvestigator
|   +-- LocatorScorer
|   +-- LocatorNormalizer
|
+-- accessibility/
|   +-- AccessibilityTool
|   +-- AccessibilityEvidenceCollector
|
+-- evidence/
|   +-- EvidenceCollector
|   +-- ScreenshotCollector
|   +-- DomEvidenceCollector
|   +-- MobilePageSourceCollector
|
+-- analysis/
    +-- TestAnalysisTool
    +-- FailureAnalysisTool
    +-- GapAnalysisTool
```

Do not create empty abstractions. The package structure should be driven by existing crawler, generator, locator, accessibility, and evidence classes already present in the SDK.

The important architectural change is that these classes are recognized as **SDK tools**, not generic `utility` classes.

---

## 25.2 Crawlers Are Required SDK Components

The Web and Mobile crawlers are not optional helper code.

They are core SDK capabilities and should be treated as such.

They support:

- element discovery
- locator generation
- Page Object generation
- automation gap analysis
- AI-assisted test generation
- UI structure analysis
- regression maintenance
- accessibility evidence collection
- troubleshooting
- agent-based automation workflows

The crawler architecture should therefore be explicit:

```text
ElementDiscoveryService
        |
        +-- WebElementDiscoveryService
        |       |
        |       +-- WebElementCrawler
        |
        +-- MobileElementDiscoveryService
                |
                +-- MobileElementCrawler
```

The internal discovery techniques remain platform-specific.

Web:

```text
DOM
attributes
ARIA
labels
forms
ancestor/sibling relationships
XPath/CSS candidates
test IDs
visible text
```

Mobile:

```text
Appium page source
accessibility ID
resource-id
UiAutomator
iOS predicate
iOS class chain
native hierarchy
WebView context
XPath fallback
```

The output should be normalized through common models:

```text
DiscoveryResult
LocatorCandidate
PageElementDefinition
```

This is important because AI agents, Page Object generators, reporting, and future automation technologies should consume a stable model rather than Selenium/Appium internals.

---

## 25.3 Tools Must Be Technology-Aware but Framework-Neutral

A tool should receive SDK-level abstractions wherever practical:

```java
public interface AutomationTool<I, O> {
    O execute(AutomationSession session, I input);
}
```

Do not require every tool to implement this interface if it does not add value, but use this concept as the design principle.

Example:

```java
public interface ElementDiscoveryService {
    DiscoveryResult discover(
        AutomationSession session,
        DiscoveryContext context
    );
}
```

The Web implementation can internally unwrap Selenium when required:

```java
WebDriver driver = session.unwrap(WebDriver.class);
```

The Mobile implementation can unwrap Appium:

```java
AppiumDriver driver = session.unwrap(AppiumDriver.class);
```

This allows tooling to stay attached to the SDK architecture while still using technology-specific capabilities.

---

# 26. AI Agent Support as a Native SDK Capability

The SDK should include a dedicated AI-support asset area. Prompts, skills, and schemas are required versioned assets; runtime agent infrastructure is introduced only as concrete consumers require it.

AI integration should not be scattered across test code or individual automation projects.

Recommended structure:

```text
ai/
|
+-- prompts/
|   +-- test-generation/
|   +-- locator-analysis/
|   +-- failure-analysis/
|   +-- accessibility/
|   +-- page-object-generation/
|   +-- rca/
|
+-- skills/
|   +-- element-discovery/
|   +-- test-generation/
|   +-- page-object-generation/
|   +-- failure-analysis/
|   +-- accessibility-analysis/
|   +-- rca/
|
+-- contracts/
|   +-- AgentRequest
|   +-- AgentResponse
|   +-- ToolResult
|   +-- PromptContext
|
+-- tools/
|   +-- AgentTool
|   +-- ToolRegistry
|   +-- ToolDescriptor
|
+-- context/
|   +-- AgentContextBuilder
|   +-- EvidenceContextBuilder
|
+-- validation/
    +-- AgentOutputValidator
    +-- StructuredResponseValidator
```

This does not mean the SDK must contain an LLM runtime.

The SDK should contain the **contracts, prompts, skills, schemas, and callable tools** required by AI agents.

The actual agent or LLM can run externally.

---

## 26.1 Prompts Must Be Versioned Assets

Prompts should not be hardcoded inside Java classes.

Recommended layout:

```text
src/main/resources/ai/prompts/
|
+-- test-generation/
|   +-- generate-test.md
|
+-- locator-analysis/
|   +-- analyze-locator.md
|
+-- page-object/
|   +-- generate-page-object.md
|
+-- failure-analysis/
|   +-- analyze-failure.md
|
+-- accessibility/
|   +-- analyze-accessibility-findings.md
|
+-- rca/
    +-- root-cause-analysis.md
```

Each prompt should contain metadata, either in YAML front matter or a sidecar descriptor.

Example:

```yaml
name: generate-page-object
version: 1.0
capability: page-object-generation
inputSchema: page-object-input-v1
outputSchema: page-object-output-v1
requiredTools:
  - element-discovery
  - locator-analysis
```

This gives us:

- version control
- auditability
- predictable agent behavior
- backward compatibility
- prompt testing
- easier rollback
- environment-independent reuse

---

## 26.2 Skills Must Be Reusable Agent Capabilities

A skill is broader than a prompt.

A skill defines:

- purpose
- inputs
- required tools
- prompt(s)
- workflow
- validation rules
- expected output
- failure behavior

Example:

```text
Skill: Generate Page Object
|
+-- collect UI evidence
+-- run crawler
+-- normalize locator candidates
+-- score candidate stability
+-- invoke generation prompt
+-- validate generated source
+-- return Page Object proposal
```

Recommended skill descriptor:

```yaml
name: page-object-generation
version: 1.0

inputs:
  - executionContext
  - pageName

tools:
  - element-discovery
  - locator-analysis

prompts:
  - generate-page-object

validation:
  - java-syntax
  - locator-policy
  - sdk-api-usage

outputs:
  - generatedSource
  - locatorEvidence
  - warnings
```

Skills should be independent from a specific LLM vendor.

---

## 26.3 AI Tools Registry

AI agents need a safe, discoverable list of SDK capabilities.

Introduce a lightweight registry concept:

```text
Agent Tool Registry
|
+-- element-discovery
+-- locator-analysis
+-- page-source-capture
+-- screenshot-capture
+-- accessibility-scan
+-- test-data-read
+-- failure-evidence
+-- page-object-generation
```

Each tool should expose metadata such as:

```text
name
description
input schema
output schema
supported platforms
required session capability
side effects
version
```

Example:

```yaml
name: element-discovery
version: 1.0
supportedPlatforms:
  - WEB
  - ANDROID
  - IOS
requiresSession: true
readOnly: true
```

This enables future AI agents to discover and use SDK functionality consistently.

---

# 27. AI-Agent Architecture Boundary

The architecture should separate:

```text
AI Agent
    |
    v
AI Skill
    |
    v
SDK Tool Contract
    |
    v
SDK Runtime
    |
    +-- SeleniumSession
    +-- AppiumSession
    +-- future technology
```

The AI agent must not directly control Selenium/Appium where an SDK tool already exists.

For example, instead of:

```text
Agent -> raw Selenium WebDriver -> DOM
```

prefer:

```text
Agent
  -> element-discovery skill
  -> ElementDiscoveryService
  -> AutomationSession
  -> SeleniumSession
```

This preserves SDK rules, locator standards, logging, evidence collection, and future technology portability.

---

# 28. Prompt and Skill Configuration

Prompts and skills should use the same centralized configuration architecture as the rest of the SDK.

Example:

```yaml
ai:
  enabled: true

  prompts:
    root: classpath:ai/prompts

  skills:
    root: classpath:ai/skills

  validation:
    strict: true

  maxContextChars: 24000

  evidence:
    includeScreenshot: true
    includeDom: true
    includePageSource: true
```

AI configuration must not introduce another independent configuration reader.

It should be exposed through the same `ConfigurationManager`.

---

# 29. Proposed Final Top-Level SDK Structure

The more complete target becomes:

```text
com.test.automation.sdk
|
+-- testbase/    (unchanged; not renamed to base/)
|
+-- config/
|
+-- execution/
|
+-- session/
|
+-- element/
|
+-- web/
|
+-- mobile/
|
+-- tools/
|   +-- discovery/
|   +-- crawler/
|   +-- locator/
|   +-- pageobject/
|   +-- accessibility/
|   +-- evidence/
|   +-- analysis/
|
+-- ai/
|   +-- contracts/
|   +-- tools/
|   +-- context/
|   +-- validation/
|
+-- accessibility/
|
+-- reporting/
|
+-- logging/
|
+-- artifacts/
|
+-- listener/
|
+-- secrets/
|
+-- data/
|
+-- common/
```

Resources:

```text
src/main/resources/
|
+-- sdk-config.yaml
|
+-- ai/
|   +-- prompts/
|   +-- skills/
|   +-- schemas/
|
+-- reporting/
|
+-- accessibility/
```

This makes the SDK more than an execution library.

It becomes a complete automation platform containing:

```text
Execution Runtime
+
Automation Tools
+
AI-Agent Capabilities
```

---

# 30. Additional Migration Work

Add the following work to the refactoring roadmap.

## Phase 9 — Tooling Consolidation (REQUIRED)

This phase is required because crawlers, locator investigation, Page Object generation, accessibility analysis, and evidence collection already exist as concrete SDK capabilities.

- move crawler-related classes out of generic `utility`
- introduce `tools.discovery`
- introduce normalized `DiscoveryResult`
- introduce normalized `LocatorCandidate`
- preserve existing Web crawler algorithms
- preserve existing Mobile crawler algorithms
- consolidate Page Object generation contracts
- move locator investigator functionality into the tools architecture

## Phase 10 — AI Asset Structure (REQUIRED ASSET STANDARDIZATION)

- create `src/main/resources/ai/prompts`
- create `src/main/resources/ai/skills`
- define prompt metadata/versioning
- define skill descriptor format
- define input/output schemas
- ensure prompts are not hardcoded in implementation classes

## Phase 11 — AI Tool Contracts (CONDITIONAL)

- create agent tool descriptors
- expose crawler/discovery capabilities through SDK tool contracts
- expose evidence collectors
- expose accessibility scan
- expose Page Object generation
- expose failure-analysis evidence
- implement a lightweight tool registry

## Phase 12 — Agent Runtime Validation (CONDITIONAL)

- validate AI-generated Java source
- validate locator policy
- validate SDK API usage
- validate structured agent responses
- record prompt/skill/tool versions in generated artifacts and reports

---

# 31. Updated Definition of Done

In addition to the prior architecture requirements, the SDK cleanup is complete only when:

- crawlers are treated as formal SDK tools, not miscellaneous utilities
- Web and Mobile crawlers expose a common discovery/result contract
- Page Object generation is part of the SDK tooling layer
- locator investigation is part of the SDK tooling layer
- AI agents can consume SDK capabilities through stable tool contracts
- prompts are versioned resources
- skills are versioned reusable workflows
- prompts/skills are technology-neutral where possible
- AI configuration uses the same configuration system
- AI agents do not need direct Selenium/Appium dependencies for standard tasks
- generated outputs can record which prompt, skill, and tool versions were used
- future agents can add capabilities without modifying the execution core
- future browser technology replacement does not require rewriting prompts or skills that operate on normalized SDK contracts

---

# 32. Final Platform View

The intended SDK architecture is:

```text
                         AUTOMATION SDK
                              |
          +-------------------+-------------------+
          |                   |                   |
       Runtime              Tools               AI
          |                   |                   |
   Test lifecycle         Crawlers            Prompts
   Configuration          Discovery           Skills
   Sessions               Locator tools       Agent contracts
   Execution context      Page Objects         Tool registry
   Reporting              Evidence            Validation
          |
     +----+----+
     |         |
    Web      Mobile
     |         |
 Selenium   Appium
     |
 Future Technology
```

The SDK should therefore be designed as a reusable **automation platform**, not only as a Web/Mobile driver wrapper.

The final architectural principle becomes:

> **Execution technologies are replaceable implementation details; crawlers and automation tools are reusable platform capabilities; prompts and skills are versioned AI assets that consume those capabilities through stable SDK contracts.**

# 33. Multi-Session and Hybrid Web + Mobile Driver Architecture

### 33.0 Sequencing Decision

This architecture is **approved as the target model**, but implementation is split into two levels:

**Required now**
- expose Appium `NATIVE_APP` / `WEBVIEW` switching through supported SDK APIs
- keep session/provider APIs compatible with future multiple-session ownership
- ensure reporting/evidence metadata can carry session identity when needed

**Deferred until the first real independent multi-session consumer**
- `SessionContext`
- named-session maps
- multi-session YAML
- multiple concurrent Selenium/Appium sessions in one test

The design must not block multi-session execution, but the runtime storage layer should not be built before an actual Web+Mobile or multiple-device test requires it.


The SDK must support test cases where a single test execution needs more than one automation technology or interaction context.

A test must **not** be modeled as permanently bound to exactly one driver technology for its entire lifecycle.

Required execution patterns include:

```text
Web-only
Mobile-only
Mobile Web + Native
Web + Mobile
Multiple Mobile sessions/devices
Future mixed-technology scenarios
```

A common example is a Web application running on a mobile device where the test performs normal Web interactions but must temporarily interact with a native Android/iOS UI.

For example:

```text
Mobile Web Application
        |
        +-- Web/WebView interaction
        |
        +-- click Attach
        |
        +-- Native file picker opens
        |
        +-- Appium controls native menu/file picker
        |
        +-- return to WebView
        |
        +-- continue Web application test
```

Selenium alone cannot control native operating-system UI such as:

- native file picker
- permission dialog
- camera/gallery chooser
- native menus
- OS-level dialogs

For this type of scenario, the preferred architecture is to create an Appium session that can move between Web and native contexts:

```text
Appium AutomationSession
        |
        +-- WEBVIEW / Browser Context
        |       |
        |       +-- interact with Web application
        |
        +-- NATIVE_APP Context
                |
                +-- file picker
                +-- permissions
                +-- native menu
                +-- camera/gallery
```

Typical execution:

```text
WEBVIEW
   |
click Attach
   |
switch to NATIVE_APP
   |
select file
   |
switch to WEBVIEW
   |
continue Web test
```

The SDK should expose explicit mobile-context operations such as:

```java
mobileSwitchToNative();
mobileSwitchToWebView();

mobileNative().find(...);
mobileWeb().find(...);
```

These may internally use Appium context APIs while keeping the test-facing SDK API consistent.

---

## 33.1 Multiple Independent Sessions in One Test

The SDK must also support a scenario where a test genuinely needs multiple independent sessions.

Example:

```text
Test Execution
      |
      +-- Web Session
      |      |
      |      +-- Selenium
      |
      +-- Mobile Session
             |
             +-- Appium
```

Future examples may include:

```text
Web + Mobile
Web + API
two Mobile devices
Selenium + future user-mimic technology
multiple browser sessions
```

Therefore the architecture should not use:

```java
ThreadLocal<AutomationSession>
```

as the complete execution model.

Use a test-scoped session container instead:

```java
ThreadLocal<SessionContext>
```

Conceptual API:

```java
public final class SessionContext {

    private final Map<String, AutomationSession> sessions;

    public AutomationSession primary();

    public AutomationSession get(String name);

    public void register(String name, AutomationSession session);

    public void close(String name);

    public void closeAll();
}
```

The lifecycle remains owned by `TestBase`, while `SessionManager` manages the sessions belonging to the current test execution.

```text
TestBase
   |
SessionManager
   |
SessionContext
   |
   +-- primary session
   +-- web session
   +-- mobile session
   +-- additional named sessions
```

Convenience methods may expose common cases:

```java
webSession();
mobileSession();
primarySession();
session("secondary-mobile");
```

---

## 33.2 SessionRequest

A single global execution descriptor is insufficient for a multi-session test.

Introduce a small `SessionRequest` model describing one requested session.

Example:

```java
public final class SessionRequest {

    private String name;
    private Platform platform;
    private AutomationTechnology technology;
    private ExecutionTarget target;

    private String browser;
    private String deviceName;
    private String appReference;

    private Map<String, Object> options;
}
```

The overall test execution context can contain one or more requests:

```text
TestExecutionContext
       |
       +-- SessionRequest: web
       |       platform = WEB
       |       technology = SELENIUM
       |       target = LOCAL
       |
       +-- SessionRequest: mobile
               platform = ANDROID
               technology = APPIUM
               target = BROWSERSTACK
```

This separates:

- test execution
- session identity
- platform
- automation technology
- execution provider/target

and avoids coupling one test to one driver.

---

## 33.3 Multi-Session Configuration

Configuration must support both simple single-session tests and advanced multi-session tests.

A normal test can remain simple:

```yaml
execution:
  platform: web
  technology: selenium
  target: local
```

Internally this becomes the primary `SessionRequest`.

For hybrid tests:

```yaml
sessions:

  web:
    platform: web
    technology: selenium
    target: local
    browser: chrome

  mobile:
    platform: android
    technology: appium
    target: browserstack
    deviceName: Samsung Galaxy S25
```

The same configuration system and precedence rules must resolve every session.

Do not introduce a separate mobile-session configuration engine.

---

## 33.4 Session Manager Responsibilities

`SessionManager` should be responsible for:

- creating sessions from `SessionRequest`
- registering named sessions
- returning the primary session
- retrieving sessions by name
- tracking session ownership
- closing one session
- closing all sessions at test teardown
- preventing session leakage between tests
- supporting ThreadLocal/test-scoped isolation
- providing session metadata to reporting
- coordinating failure evidence from all active sessions

It should **not** contain Selenium/Appium creation details.

Those remain in technology/provider implementations.

Example:

```text
SessionManager
      |
AutomationSessionFactory
      |
      +-- SeleniumSessionProvider
      |
      +-- AppiumSessionProvider
      |
      +-- FutureSessionProvider
```

---

## 33.5 Reporting and Evidence for Multi-Session Tests

Reporting must understand that one test may own multiple sessions.

A failure report should be able to contain:

```text
Test Result
   |
   +-- Web Session Evidence
   |      browser
   |      URL
   |      screenshot
   |      DOM
   |
   +-- Mobile Session Evidence
          device
          platform
          current context
          screenshot
          page source
```

Each artifact should contain a session identifier.

Example metadata:

```text
testId
sessionName
platform
technology
executionTarget
context
timestamp
artifactType
```

This is particularly important for AI RCA agents because they must know which session produced each piece of evidence.

---

## 33.6 AI Tools and Hybrid Sessions

AI-agent tools must also be session-aware.

An agent tool should be able to explicitly target:

```text
primary
web
mobile
secondary-mobile
```

Example:

```text
element-discovery(session="web")
screenshot(session="mobile")
page-source(session="mobile")
locator-analysis(session="web")
```

The agent should not assume there is only one active driver.

`AgentContextBuilder` should include available session metadata so an AI skill can determine which tool/session is appropriate.

This allows future skills such as:

```text
Hybrid Attachment Validation Skill

1. inspect Web element
2. activate attachment
3. detect native context
4. inspect native file picker
5. select attachment
6. return to Web context
7. validate Web application state
8. collect evidence from both contexts
```

---

## 33.7 Updated Session Architecture

The target runtime architecture becomes:

```text
                         TestBase
                            |
                     SessionManager
                            |
                     SessionContext
                            |
             +--------------+--------------+
             |                             |
       Web Session                    Mobile Session
             |                             |
      SeleniumSession                 AppiumSession
             |                             |
       Web Context                 +-------+-------+
                                   |               |
                                WEBVIEW        NATIVE_APP
```

Future technologies can participate without changing `TestBase`:

```text
SessionContext
     |
     +-- SeleniumSession
     +-- AppiumSession
     +-- PlaywrightSession
     +-- UserMimicSession
```

---

## 33.8 Architecture Rule

Replace the earlier assumption:

> One test execution owns one AutomationSession.

with:

> **One test execution owns one SessionContext containing one or more AutomationSessions.**

Also establish:

> **One AutomationSession may expose multiple interaction contexts when the underlying technology supports them.**

For Appium this includes, at minimum:

```text
NATIVE_APP
WEBVIEW / mobile browser
```

This distinction is important:

```text
SessionContext
    = multiple independent automation sessions

Appium context switching
    = multiple interaction contexts inside one Appium session
```

Do not create a second Appium session merely to move between WebView and native UI when the same Appium session can correctly own both contexts.

---

## 33.9 Updated Definition of Done for Session Architecture

The session architecture is complete when:

- a normal Web test can use one primary Web session
- a normal Mobile test can use one primary Appium session
- an Appium mobile-Web test can switch between WebView and native UI
- a hybrid test can own both Selenium and Appium sessions
- a test can own multiple named sessions
- configuration can describe one or multiple sessions
- reporting identifies evidence by session
- AI tools can explicitly target a session
- teardown reliably closes all sessions
- parallel tests cannot access another test's sessions
- adding another automation technology does not require redesigning `TestBase`

# 34. Final Recommendation

The SDK should proceed with a balanced architecture: establish the boundaries that protect long-term extensibility now, while postponing expensive abstractions until they have a concrete consumer.

The recommended target is:

```text
                        Unified Automation SDK
                                 |
            +--------------------+--------------------+
            |                    |                    |
         Runtime               Tools                 AI Assets
            |                    |                    |
      TestBase lifecycle     Crawlers/Discovery   Prompts/Skills/Schemas
      Configuration          Locator tools        (runtime conditional)
      Reporting              Page Object tools
      Session selection      Evidence/Accessibility
            |
      AutomationSession
            |
       +----+----+
       |         |
   Selenium    Appium
       |         |
   future      WEBVIEW /
 technology    NATIVE_APP
```

The architectural priorities are:

1. **Complete configuration and structural cleanup.** — ✅ implemented; see `docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md` section 16, Priorities 1-2.
2. **Promote current Web/Mobile crawlers and related utilities into a formal SDK tooling layer.** — ✅ implemented; see the same document, Priority 3.
3. **Expose proven Appium native/WebView context switching as a supported SDK capability.** — ✅ implemented: `MobileTestBase.mobileSwitchToNative()` / `mobileSwitchToWebView(...)` / `getAvailableContexts()`.
4. **Introduce only the lightweight `AutomationSession` boundary needed to keep Selenium/Appium replaceable behind the SDK.** — ✅ implemented; see the "Phase 3" status note at the top of this document and Priority 1/5 of the same review document.
5. **Keep full element/locator abstraction conditional until Page Object/crawler portability requires it.** — still correctly deferred; no concrete consumer need yet.
6. **Keep true named multi-session runtime conditional until the first real Web+Mobile or multiple-device test appears, while preserving it as an approved target architecture.** — still correctly deferred; no concrete consumer need yet.
7. **Version prompts and AI skills now, but defer agent registry/orchestration infrastructure until an actual AI consumer requires it.** — prompts/skills remain versioned assets under `src/main/resources/ai/*`; agent runtime remains deferred by architecture.

The central architectural rule is:

> **Tests and SDK tools should depend on stable framework contracts where those contracts already provide concrete value. Selenium and Appium remain implementation technologies. Existing crawlers/tools are first-class SDK capabilities. Prompts and skills are versioned AI assets. New abstraction layers are added only when they solve a demonstrated problem.**

This preserves the clean, robust structure required today and still gives the SDK a practical path to add new capabilities, hybrid Web/Mobile scenarios, AI-agent workflows, and a future Selenium replacement without forcing speculative complexity into the current implementation.
