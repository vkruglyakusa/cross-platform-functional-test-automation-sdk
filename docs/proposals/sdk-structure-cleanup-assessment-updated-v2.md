# Unified SDK Structure Cleanup — Senior Architecture Assessment & Target Design

## Status

**Architecture update / refactoring plan — implementation should proceed in controlled phases.**

**Adopted as the formal architecture document for this SDK (2026-09-09).**

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

This document updates the previous cleanup assessment with a stronger long-term target: one clean Web + Mobile SDK that is easy to extend, avoids duplicated framework concepts, and keeps Selenium/Appium behind replaceable implementation boundaries.

The current repository baseline documented in the assessment is:

- Repo: `cross-platform-functional-test-automation-sdk`
- Revision reviewed: `a108c36`
- `SdkConfig` / `MobileSdkConfig` shim cleanup already completed
- 441/441 tests passing at the time of the assessment

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
├── base/
│   └── TestBase
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

## Phase 3 — Unified Session Boundary

Introduce:

```text
AutomationSession
AutomationElement
Locator
AutomationSessionFactory
SessionManager
```

Implement:

```text
SeleniumSession
AppiumSession
```

Do this behind compatibility facades so existing tests continue to run.

Do not convert every existing Page Object in one change.

---

## Phase 4 — TestBase Consolidation

- `TestBase` becomes the only primary lifecycle
- session determined from `ExecutionContext`
- `MobileTestBase` becomes thin compatibility layer
- Web/Appium setup details move to session/provider implementations

---

## Phase 5 — Page Object / Locator Migration

New Page Objects use:

```text
AutomationSession
AutomationElement
Locator
```

Existing Selenium Page Objects remain compatible initially.

Migrate incrementally when touched.

---

## Phase 6 — Discovery Normalization

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

---

# 23. Definition of Done

The architecture cleanup is complete when:

- Web and Mobile clearly belong to one framework architecture
- no duplicate execution-selection logic remains
- no duplicate configuration-resolution logic remains
- one common execution context describes Web and Mobile runs
- one primary lifecycle owns Web and Mobile execution
- driver factories are no longer exposed as separate framework architectures
- common tests/Page Objects can work through technology-neutral session/element/locator concepts
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

The SDK should include a dedicated AI-support area.

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
+-- base/
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

## Phase 9 — Tooling Consolidation

- move crawler-related classes out of generic `utility`
- introduce `tools.discovery`
- introduce normalized `DiscoveryResult`
- introduce normalized `LocatorCandidate`
- preserve existing Web crawler algorithms
- preserve existing Mobile crawler algorithms
- consolidate Page Object generation contracts
- move locator investigator functionality into the tools architecture

## Phase 10 — AI Asset Structure

- create `src/main/resources/ai/prompts`
- create `src/main/resources/ai/skills`
- define prompt metadata/versioning
- define skill descriptor format
- define input/output schemas
- ensure prompts are not hardcoded in implementation classes

## Phase 11 — AI Tool Contracts

- create agent tool descriptors
- expose crawler/discovery capabilities through SDK tool contracts
- expose evidence collectors
- expose accessibility scan
- expose Page Object generation
- expose failure-analysis evidence
- implement a lightweight tool registry

## Phase 12 — Agent Validation

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

# 33. Final Recommendation

The previous assessment correctly identified that the current code has only one major immediate duplication problem: the parallel mobile execution strategy.

However, for a **clean, robust, extensible SDK**, the architecture should not stop at removing that duplication.

The recommended long-term target is:

```text
                       Unified Automation SDK
                                |
                         Common Framework
                                |
                  +-------------+-------------+
                  |                           |
           AutomationSession             Common Services
                  |                           |
          +-------+-------+        Config / Reporting / Logging /
          |               |        Secrets / Accessibility / Artifacts
   SeleniumSession   AppiumSession
          |
     Future replacement
   (Playwright / UserMimic /
    another interaction engine)
```

The most important architectural rule is:

> **Tests should depend on the SDK's stable automation concepts, while Selenium and Appium remain replaceable implementation details.**

This gives the project a clean structure today and a practical path to add new functionality, new providers, new platforms, or a future Selenium replacement without rebuilding the entire framework.
