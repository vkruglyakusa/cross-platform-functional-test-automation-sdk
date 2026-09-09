# Unified SDK Implementation Review — Findings and Remaining Architecture Work

**Review date:** 2026-09-09  
**Reviewed implementation:** `cross-platform-functional-test-automation-sdk`  
**Architecture baseline:** `sdk-structure-cleanup-assessment-updated-v4.md`  
**Supporting implementation record:** `CHANGELOG.md`  
**Status (2026-09-09): COMPLETE — all 6 priorities in Section 16 implemented, tested, documented,
released as v1.1.0, and frozen for this iteration.** See Section 17 for the final
Definition-of-Done mapping and Section 18 for the final assessment.

## 1. Executive Summary

The current implementation is in good shape and is substantially aligned with the v4 architecture guideline. The major architectural direction is correct and does not need to be redesigned.

The implementation correctly establishes:

- one platform-neutral execution/session-resolution path;
- a deliberately lightweight `AutomationSession` boundary;
- Selenium and Appium session wrappers;
- public Appium `NATIVE_APP` / `WEBVIEW` context switching;
- normalized Web/Mobile crawler output contracts;
- versioned AI prompts, skills, and schemas without prematurely introducing an AI runtime;
- improved accessibility abstraction;
- removal of significant global static driver/session state;
- gradual package cleanup without unnecessarily rewriting mature crawler algorithms.

> **Update (2026-09-09, post-implementation): RESOLVED.** All 6 items below
> were implemented, tested, and committed this iteration (see Section 16 for
> per-item detail and `CHANGELOG.md` for the full record):
>
> 1. `TestBase`/`MobileTestBase` now route session lifecycle through
>    `ExecutionContext -> AutomationSessionFactory -> DriverManager ->
>    SessionFactoryRegistry` (Priority 1).
> 2. Configuration unification is complete: `ConfigurationManager` is the
>    single precedence-resolution engine; `MobileConfigReader` is a typed
>    view over it, including a precedence bugfix so system-property/env
>    overrides win over the standalone `mobile-config.yaml` (Priority 2 +
>    final pre-release cleanup).
> 3. Tooling/crawler package consolidation is complete under
>    `tools.crawler.web`, `tools.crawler.mobile`, `tools.pageobject`, and
>    `tools.locator`, with deprecated compatibility facades at every old FQN
>    (Priority 3).
> 4. Mutable static Mobile execution state (`mobileOsName`/`deviceName`) was
>    converted to instance state, with an explicit mixed Web/Mobile isolation
>    test (Priority 4).
> 5. `execution.AutomationTechnology` reserves the `SELENIUM`/`APPIUM`
>    dimension in `SessionFactory`/`ExecutionContext`/`SessionFactoryRegistry`
>    without redesigning `TestBase` (Priority 5).
> 6. Stray compiled `.class` artifacts were removed, the stale
>    `unified-execution-architecture.md` status header was corrected, and
>    doc FQN references were synchronized to the Priority 3 package moves
>    (Priority 6).
>
> The overall alignment assessment below (85%) is the **pre-implementation**
> baseline this document originally scored against; the post-implementation
> state is tracked in Section 17's Definition-of-Done table, which is now
> fully COMPLETE or explicitly DEFERRED BY ARCHITECTURE (no open items).

The architecture cleanup was **not yet considered 100% complete at review time**.

The most important remaining work at review time was:

1. integrate `TestBase` with the new `ExecutionContext -> AutomationSessionFactory -> DriverManager` lifecycle;
2. finish true configuration unification rather than only relocating `YamlConfigReader`;
3. complete the required tooling/crawler package consolidation;
4. remove remaining mutable static Mobile execution state and validate mixed Web/Mobile isolation;
5. reserve automation technology as an execution-selection dimension so Selenium can later coexist with or be replaced by another browser/user-mimic technology;
6. clean stale documentation and generated build artifacts from the source/review package.

Overall assessment at review time: **approximately 85% aligned with the intended completed v4 architecture** (now superseded — see the "Update" note above and Section 17).

---

## 2. Implementation Areas That Are Correct

### 2.1 Execution and Session Resolution

The previous duplicate Mobile execution strategy layer has been removed.

The implementation now uses `SessionFactoryRegistry` as the common `(Platform, RunMode)` session-resolution mechanism, with `DriverManager` acting as the platform-neutral acquisition entry point.

This is the correct direction:

```text
ExecutionContext
      |
DriverManager
      |
SessionFactoryRegistry
      |
+-----+-------------------------+
|                               |
Web SessionFactory        Mobile SessionFactory
```

There should not be a second Mobile-only strategy/factory/resolver hierarchy.

**Assessment: COMPLETE / GOOD**

---

### 2.2 Lightweight `AutomationSession` Boundary

The new session architecture correctly introduces:

```text
session/
├── AutomationSession
├── AutomationSessionFactory
└── internal/
    ├── SeleniumSession
    └── AppiumSession
```

The deliberately small API is appropriate:

```java
navigate(...)
quit()
unwrap(Class<T>)
```

This follows the v4 decision not to recreate Selenium or Appium behind a large proprietary wrapper.

The controlled `unwrap()` escape hatch is especially important because technology-specific functionality will continue to exist.

The implementation also correctly defers:

```text
AutomationElement
Locator
```

until crawler/Page Object portability creates a concrete need.

**Assessment: COMPLETE / GOOD**

---

## 3. Appium Native / WebView Hybrid Interaction

The Appium context-switching implementation is one of the strongest completed pieces.

The previously crawler-internal `SupportsContextSwitching` behavior is now exposed through reusable Mobile APIs.

Supported operations include concepts such as:

```text
getAvailableContexts()
getCurrentContext()
isInWebViewContext()
switchToNativeContext()
switchToContext(...)
switchToWebViewContext(...)
```

with `MobileTestBase` convenience wrappers such as:

```text
mobileSwitchToNative()
mobileSwitchToWebView()
mobileSwitchToWebView(String)
```

This supports the concrete hybrid use case discussed during architecture design:

```text
Mobile Web Application
        |
        | WebView interaction
        v
    Click Attach
        |
        v
 Native OS File Picker
        |
        | Appium NATIVE_APP
        v
  Select Attachment
        |
        v
 Switch to WEBVIEW
        |
        v
 Continue Web Test
```

The crawler was also refactored to use the same shared Mobile context-switching mechanism rather than maintaining its own private implementation.

That is exactly the desired architectural behavior: **one supported context-switching mechanism**.

**Assessment: COMPLETE / GOOD**

---

## 4. Discovery / Crawler Normalization

The common discovery model is well designed.

The implementation introduces:

```text
discovery/
├── ElementDiscoveryService
├── DiscoveryResult
├── DiscoveredElement
├── LocatorCandidate
└── WebElementDiscoveryAdapter
```

with the Mobile adapter integrated with the Mobile crawler.

This is consistent with the architecture principle:

> Normalize crawler outputs, not crawler algorithms.

The Web and Mobile crawlers legitimately require different algorithms.

Web discovery may depend on:

```text
DOM
ARIA
CSS
XPath
test IDs
text
browser properties
```

while Mobile discovery may depend on:

```text
Appium page source
accessibility ID
resource-id
UiAutomator
iOS predicate
class chain
native hierarchy
WebView
XPath
```

Those algorithms should remain specialized.

The normalized result contract provides the shared boundary needed by:

- Page Object generation;
- locator analysis;
- accessibility tools;
- AI-agent skills;
- regression-maintenance tools;
- evidence analysis.

**Assessment: COMPLETE / GOOD**

---

## 5. AI Prompts, Skills, and Schemas

The AI implementation is at the correct level of maturity.

The SDK now contains versioned resources under the conceptual structure:

```text
src/main/resources/ai/
├── prompts/
├── skills/
└── schemas/
```

A concrete element-discovery example connects:

```text
DiscoveryResult
      |
AI Prompt
      |
AI Skill
      |
Output Schema
```

The implementation correctly does **not** introduce speculative runtime infrastructure such as:

```text
AgentToolRegistry
AgentContextBuilder
Agent orchestration runtime
full Java-side LLM runtime
```

Those remain deferred until a concrete AI-agent consumer exists.

This matches the agreed principle:

> Prompts and skills are versioned SDK assets now; AI orchestration infrastructure is introduced only when a real consumer requires it.

**Assessment: COMPLETE / GOOD**

---

## 6. Accessibility Architecture

The accessibility implementation also follows the intended extensibility pattern.

The SDK has introduced concepts such as:

```text
AccessibilityEngine
AccessibilityFinding
AxeCoreEngine
NativeMobileEngine
```

This provides an engine-neutral framework boundary while retaining mature underlying implementations.

The same general architecture philosophy applies here as with browser automation:

```text
SDK Contract
     |
+----+----------------+
|                     |
axe-core         Native Mobile
```

The framework can own orchestration, normalized findings, reporting, and evidence without permanently coupling the public architecture to one evaluation engine.

**Assessment: GOOD**

---

# 7. Major Remaining Gap — `TestBase` Does Not Yet Own the New Session Architecture

This is the most important remaining architecture item.

The target v4 architecture requires:

```text
                    TestBase
                       |
              ExecutionContext
                       |
           AutomationSessionFactory
                       |
                 DriverManager
                       |
            SessionFactoryRegistry
                 /           \
               Web           Mobile
```

The implementation currently has the new abstraction, but the primary test lifecycle still follows the older technology-specific paths.

Conceptually, Web remains:

```text
TestBase
   |
initialization()
   |
WebDriverFactory
```

and Mobile remains:

```text
MobileTestBase
   |
setUpDriver()
   |
MobileDriverFactory
```

Therefore:

```text
AutomationSessionFactory
```

currently exists **beside** the main lifecycle rather than being the lifecycle's session-creation boundary.

## Required Change

`TestBase` should resolve an `ExecutionContext` and acquire its session through the common session architecture.

Target:

```text
TestBase
   |
resolveExecutionContext()
   |
createAutomationSession()
   |
AutomationSessionFactory
   |
DriverManager
   |
SessionFactoryRegistry
```

`TestBase` should remain responsible for:

- configuration loading;
- execution-context creation;
- session lifecycle;
- setup and teardown;
- logging;
- reporting;
- artifacts;
- failure capture;
- test metadata.

It should **not** directly own:

- Chrome option construction;
- Appium desired capabilities;
- BrowserStack-specific capability creation;
- Android/iOS driver construction;
- Selenium-specific internals.

`MobileTestBase` can remain temporarily for compatibility, but it should eventually become a very thin Mobile convenience layer over the common lifecycle.

**Assessment: NOT COMPLETE — HIGH PRIORITY**

---

# 8. Configuration Unification Is Only Partially Complete

Moving the real `YamlConfigReader` from `utility` to `config` was correct.

However, the architecture requirement was broader than package relocation.

The intended precedence is:

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

The important requirement is:

> Resolution logic must be implemented once.

The current implementation still leaves configuration-resolution responsibility distributed across components.

`MobileConfigReader` also retains Mobile-specific configuration parsing/fallback behavior rather than being purely a typed view over one centralized configuration system.

Conceptually, the target should be:

```text
              ConfigurationManager
                      |
            ConfigurationResolver
                      |
        +-------------+-------------+
        |             |             |
      Common          Web         Mobile
        |
     Provider
```

rather than:

```text
YamlConfigReader
       +
Mobile-specific parser/resolution
       +
component-specific override logic
```

## Required Change

Move toward one configuration-resolution engine.

`MobileConfigReader` should become only a typed Mobile view, for example:

```text
ConfigurationManager
   |
   +-- getCommonConfig()
   +-- getWebConfig()
   +-- getMobileConfig()
   +-- getProviderConfig()
```

Compatibility with `mobile-config.yaml` may remain temporarily, but it should not become a permanent second configuration architecture unless explicitly decided.

Documentation and templates must also reflect the final decision consistently.

**Assessment: PARTIAL — HIGH PRIORITY**

---

# 9. Tooling / Crawler Package Consolidation Is Not Finished

The v4 architecture explicitly treats tools and crawlers as first-class SDK capabilities.

Current implementation still contains crawler/tool responsibilities distributed roughly across:

```text
utility/
    ElementCrawler
    DataDrivenCrawler
    PageObjectGenerator
    CrawlerStep
    ...

mobile/crawler/
    MobileElementCrawler
    MobileDataDrivenCrawler
    MobilePageObjectGenerator
    ...

tools/
    AbstractLocatorInvestigator
```

The discovery contracts are normalized, which is excellent, but the broader tooling organization is not yet complete.

## Recommended Target

```text
tools/
├── crawler/
│   ├── web/
│   └── mobile/
├── discovery/
├── locator/
├── pageobject/
├── accessibility/
├── evidence/
└── analysis/
```

This does **not** require rewriting mature crawler algorithms.

The objective is responsibility ownership and discoverability.

For example:

```text
tools/crawler/web/
    WebElementCrawler
    WebDataDrivenCrawler

tools/crawler/mobile/
    MobileElementCrawler
    MobileDataDrivenCrawler

tools/pageobject/
    PageObjectGenerator
    WebPageObjectGenerator
    MobilePageObjectGenerator

tools/locator/
    LocatorInvestigator
    LocatorScorer
```

Do not create empty abstractions or packages merely to match a diagram. Move responsibilities only where concrete classes already justify them.

**Assessment: PARTIAL — REQUIRED**

---

# 10. Session Isolation / Parallel Safety Still Needs Work

The removal of global static Web session state is a strong improvement.

The implementation converted important `TestBase` fields from shared static state to instance state and removed dead static session/reporting fields.

However, Mobile execution state still requires attention.

Mutable values such as:

```java
protected static String mobileOsName;
protected static String deviceName;
```

should not remain global mutable test/session state.

Two concurrently executing Mobile test classes could otherwise interfere with each other's device/platform state, particularly around retries.

Preferred:

```java
protected String mobileOsName;
protected String deviceName;
```

or ultimately have these values come from the current `ExecutionContext`.

## Required Validation

Add explicit architecture-level validation for:

```text
Web test A
      |
      +---- own driver/session

Mobile test B
      |
      +---- own Appium driver/session
```

running concurrently without state leakage.

A mocked concurrency test is sufficient for this architecture validation; it does not need to launch a real browser/device for every unit test.

Real Appium/BrowserStack smoke validation should remain part of integration validation.

**Assessment: PARTIAL**

---

# 11. Selenium Replacement Boundary Needs One More Execution Dimension

The new `AutomationSession` abstraction creates a good technology boundary:

```text
AutomationSession
      |
      +-- SeleniumSession
      +-- AppiumSession
```

However, the execution-selection model currently resolves primarily by:

```text
Platform + RunMode
```

That works today because Web effectively implies Selenium and Mobile effectively implies Appium.

It becomes ambiguous when another Web technology is introduced.

Example:

```text
WEB + LOCAL + SELENIUM
WEB + LOCAL + PLAYWRIGHT
```

Both would otherwise resolve to:

```text
WEB + LOCAL
```

## Recommended Future-Proofing

Reserve a technology dimension:

```java
enum AutomationTechnology {
    SELENIUM,
    APPIUM
}
```

with future additions such as:

```text
PLAYWRIGHT
USER_MIMIC
```

Then session/provider resolution can evolve toward:

```text
Platform
   +
RunMode
   +
AutomationTechnology
```

Examples:

```text
WEB + LOCAL + SELENIUM
WEB + LOCAL + PLAYWRIGHT
WEB + REMOTE + USER_MIMIC

ANDROID + LOCAL + APPIUM
IOS + BROWSERSTACK + APPIUM
```

This does **not** mean Playwright or another engine should be implemented now.

It simply prevents today's execution model from assuming forever that:

```text
WEB == SELENIUM
MOBILE == APPIUM
```

**Assessment: ARCHITECTURAL SEAM SHOULD BE RESERVED**

---

# 12. Multi-Session Architecture Remains Correctly Deferred

The architecture should continue to permit future tests such as:

```text
Test Execution
      |
 SessionContext
      |
 +----+----------------+
 |                     |
Web Session        Mobile Session
 |                     |
Selenium             Appium
```

but a full `SessionContext` / named-session runtime does not need to be implemented until a real independent multi-session consumer exists.

This remains distinct from Appium context switching.

```text
SessionContext
    = multiple independent automation sessions

Appium context switching
    = multiple interaction contexts within one Appium session
```

For the attachment example:

```text
WEBVIEW
   |
click attachment
   |
NATIVE_APP
   |
select file
   |
WEBVIEW
```

the same Appium session should normally be used.

Do not create an unnecessary second Appium session simply to access native UI.

**Assessment: CORRECTLY DEFERRED**

---

# 13. Source / Distribution Hygiene

The reviewed archive contains compiled `.class` artifacts under `src/main/java`.

Examples observed during review included compiled artifacts corresponding to classes such as:

```text
SdkConfig
CrawlerStep
InstructionExtractor
PageContext
Email
```

Compiled classes should not live in the Java source tree.

They may be ignored by Git, but they should still be deleted from the working source tree and excluded from SDK review/distribution archives.

A clean source/review archive should normally exclude:

```text
.git/
target/
log/
*.class
```

The Maven build should recreate all generated artifacts from source.

**Assessment: CLEANUP REQUIRED**

---

# 14. Documentation Consistency

The architecture document contains current status information showing that several phases are complete.

However, older review sections still contain statements describing some now-completed functionality as not implemented.

Examples include historical statements around:

```text
AutomationSession not yet implemented
Mobile context switching not yet publicly exposed
```

while the current status section correctly reports those capabilities as complete.

## Required Change

Either:

1. remove obsolete implementation-status statements; or
2. clearly label them as historical review findings.

The architecture document should have one authoritative current-state section.

The same consistency check should be applied to:

```text
README
SDK-USER-GUIDE
GETTING-STARTED
Mobile documentation
configuration examples
CHANGELOG
architecture proposal
```

**Assessment: CLEANUP REQUIRED**

> **Update (2026-09-09, post-implementation): RESOLVED.** Priority 6
> (Section 16) removed the stale `AutomationSession not yet implemented` /
> `Mobile context switching not yet publicly exposed` style statements:
> `docs/proposals/unified-execution-architecture.md`'s header now reads
> `Status: IMPLEMENTED`, pointing to Section 16 of this document as the
> single authoritative current-state reference. `README.md`,
> `SDK-USER-GUIDE.md`, `TESTBASE-API.md`, `CHANGELOG.md` (and their
> `src/main/resources/` mirrors) were also corrected for stale/incorrect
> `functional-test-automation-sdk` Maven-coordinate references (the repo's
> actual artifactId is `cross-platform-functional-test-automation-sdk`) — a
> related documentation-consistency defect found and fixed after the initial
> six priorities, during v1.1.0 release-pipeline validation. `GETTING-STARTED.md`
> did not exist at review time and was not introduced by this cleanup.
> `docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`'s Section 33
> review block and "34. Final Recommendation" list were annotated to mark
> AutomationSession/context-switching items as implemented rather than
> rewritten, preserving the original historical review text alongside the
> update.

---

# 15. Validation Status

The supplied implementation contains build/test evidence indicating a green suite and prior real Appium validation.

During this review, a fresh `mvn clean test` could not be independently executed because Maven was not available in the review environment.

The packaged Surefire reports showed a green test set with no reported failures/errors.

Before declaring the architecture work complete, validation should include:

```text
mvn clean test
```

plus:

- Web local smoke;
- Android local Appium smoke;
- BrowserStack Web smoke where available;
- BrowserStack Mobile smoke where available;
- Appium WEBVIEW -> NATIVE_APP -> WEBVIEW scenario;
- mixed Web/Mobile isolation test;
- retry lifecycle validation;
- configuration precedence tests.

> **Update (2026-09-09, post-implementation):** `mvn clean test` was run
> repeatedly throughout this iteration (after each priority, after final
> cleanup, and again after the v1.1.0 release-pipeline fix), most recently
> with a clean `BUILD SUCCESS` and **523/523 tests passed, 0 failures, 0
> errors, 0 skipped**. Configuration precedence, retry lifecycle, and mixed
> Web/Mobile isolation are covered by unit/mocked tests (see Priority 2's
> `ConfigurationManagerTest`/`MobileConfigReaderTest`, Priority 4's
> `MixedWebMobileIsolationTest`). The SDK was also deployed end-to-end as
> v1.1.0 to Azure Artifacts and the local `maven-repository`, and the
> release git history was pushed to `origin/master` — this exercises the
> full build/package/deploy pipeline, not just `mvn test`.
>
> **Still not validated in this environment** (unchanged from review time):
> Web local smoke against a real browser, Android local Appium smoke,
> BrowserStack Web/Mobile smoke, and a real Appium WEBVIEW -> NATIVE_APP ->
> WEBVIEW device/emulator scenario. These require infrastructure (a real
> browser/device/BrowserStack account) not available in this environment and
> remain **BLOCKED** pending a future integration-validation pass, not
> something this iteration claims to have proven.

---

# 16. Recommended Remaining Work — Priority Order

## Priority 1 — Complete TestBase Session Integration

> **Status: COMPLETE** (see `CHANGELOG.md`).
> `TestBase.initialization(String, String)` and `MobileTestBase.setUpDriver(String, String)`
> now build an `ExecutionContext` and call `AutomationSessionFactory.create(context)`
> (which delegates to `DriverManager` -> `SessionFactoryRegistry`) instead of calling
> `WebDriverFactory`/`MobileDriverFactory` directly. `TestBase.closeBrowser()` quits
> through the resulting `AutomationSession`, falling back to `driver.quit()` only when
> a subclass sets `driver` without going through `initialization()`.
> `MobileTestBase`'s retry path in `beforeMethod(Method)` also re-acquires its session
> through the same path. Proven by
> `src/test/java/com/test/automation/sdk/testbase/TestBaseSessionIntegrationTest.java`
> (registers a fake `SessionFactory`, asserts `automationSession`/`driver` are both set
> and that `closeBrowser()` quits through the session, with a fallback-path test too).
> Validated: targeted tests pass and the full `mvn test` suite passes with no
> regressions. Not validated: real browser/Appium/BrowserStack execution (unit/mocked
> only in this environment).

Make the primary lifecycle use:

```text
ExecutionContext
      ->
AutomationSessionFactory
      ->
DriverManager
      ->
SessionFactoryRegistry
```

Do not leave `AutomationSessionFactory` as an unused parallel abstraction.

---

## Priority 2 — Finish Configuration Unification

> **Status: COMPLETE** (see `CHANGELOG.md`).
> Added `config.ConfigurationManager` as the single precedence-resolution
> engine (system property > env var > project YAML via `YamlConfigReader` >
> default), with typed `getCommonConfig()`/`getWebConfig()` views.
> `mobile.config.MobileConfigReader.get(...)` now delegates to it for every
> key not present in an optional standalone `mobile-config.yaml` (retained
> only as a temporary migration path), and gained a typed `getMobileConfig()`
> view. This closes the gap where Mobile configuration had no
> system-property/env override at all. Proven by
> `config.ConfigurationManagerTest` and the extended
> `mobile.config.MobileConfigReaderTest`. Validated: targeted tests and the
> full `mvn test` suite pass with no regressions.

Implement one precedence/resolution mechanism and make Mobile configuration a typed view over it.

Retain compatibility fallbacks only as temporary migration mechanisms.

---

## Priority 3 — Complete Tooling Consolidation

> **Status: COMPLETE** (see `CHANGELOG.md`).
> Web tooling was consolidated under `tools.crawler.web`
> (`ElementCrawler`, `DataDrivenCrawler`, `CrawlerStep`, `CrawlerScenario`,
> `ElementSearchEngine`); mobile tooling was consolidated under
> `tools.crawler.mobile` (`MobileElementCrawler`,
> `MobileDataDrivenCrawler`, `MobileCrawlerReportWriter`,
> `MobileCrawlerStep`, `MobileElementInfo`, `MobileLocatorCandidate`,
> `MobileScreenSnapshot`, `MobileElementDiscoveryAdapter`);
> page-object generators moved to `tools.pageobject`
> (`PageObjectGenerator`, `MobilePageObjectGenerator`); and
> `tools.AbstractLocatorInvestigator` moved to
> `tools.locator.AbstractLocatorInvestigator`. The old FQNs remain in place
> as deprecated compatibility facades (inheritance where safe, delegation
> wrappers where constructors/finality/generic signatures made direct
> subclassing unsafe), matching the compatibility pattern already used by
> `utility.YamlConfigReader` and the earlier
> `utility.WebElementDiscoveryAdapter` move. Proven by relocated moved-class
> tests plus dedicated facade-compatibility tests
> (`utility.ToolingCompatibilityFacadeTest`,
> `mobile.crawler.MobileToolingCompatibilityFacadeTest`,
> `tools.locator.AbstractLocatorInvestigatorCompatibilityTest`).
> Validated: `mvn compile test-compile`, targeted moved-class tests, and the
> full `mvn test` suite all pass with no regressions. Not validated in this
> environment: live browser/Appium crawler execution (unit/mocked/package
> structure validation only).

Promote existing crawlers, Page Object generators, locator investigation, accessibility tools, and evidence tools into the formal tooling architecture.

Do not rewrite mature algorithms.

---

## Priority 4 — Finish Session Isolation

> **Status: COMPLETE** (see `CHANGELOG.md`).
> `MobileTestBase.mobileOsName`/`deviceName` converted from `protected
> static` to instance fields; `getCurrentPlatformOS()` is now an instance
> method (it reads `mobileOsName`). Proven by
> `mobile.testbase.MixedWebMobileIsolationTest`, which runs a Web `TestBase`
> test and a Mobile `MobileTestBase` test concurrently, and two concurrent
> Mobile tests with different device names, asserting no session/device
> state leaks between them. Validated: targeted tests and the full `mvn
> test` suite pass with no regressions. Real mixed Web/Mobile
> BrowserStack/Appium concurrency was not validated in this environment
> (mocked `SessionFactory`s only).

Remove remaining mutable static Mobile execution state.

Add explicit mixed Web/Mobile concurrency/isolation validation.

---

## Priority 5 — Reserve Automation Technology in Execution Selection

> **Status: COMPLETE**
> Added `execution.AutomationTechnology` (`SELENIUM`, `APPIUM` only —
> `PLAYWRIGHT`/`USER_MIMIC` intentionally not implemented). `SessionFactory`
> gained `default getAutomationTechnology()` inferring the platform-implied
> value, so all 6 built-in factories resolve unchanged without modification.
> `ExecutionContext` gained `getAutomationTechnology()` plus new 3-arg
> `forWeb(...)`/`forMobile(...)` overloads accepting an explicit technology;
> existing 2-arg overloads are unchanged and default identically.
> `SessionFactoryRegistry` now keys on `(platform, runMode, technology)`; the
> existing 2-arg `resolve(Platform, RunMode)` is preserved and defaults the
> technology the same way, so `DriverManager` and all existing callers/tests
> needed no changes. Tests: `execution.ExecutionContextTest`,
> `execution.SessionFactoryRegistryTest` (new technology-defaulting/override/
> mismatch cases). Targeted and full `mvn test` suites pass. No new
> technology (e.g. Playwright) was implemented, per this section's explicit
> scope boundary.

Prepare the execution model so a future browser engine can coexist with Selenium:

```text
Platform + RunMode + AutomationTechnology
```

Do not implement another technology until needed.

---

## Priority 6 — Documentation and Repository Hygiene

> **Status: COMPLETE** (see `CHANGELOG.md`).
> Removed 11 stray compiled `.class` artifacts from the `src/main/java`
> source tree (already gitignored, but still physically present -- matches
> this document's section 13 finding exactly). Updated
> `docs/proposals/unified-execution-architecture.md`'s header from `Status:
> PROPOSED (not yet implemented)` to `Status: IMPLEMENTED`, pointing to this
> document's section 16 as the single authoritative current-state
> reference. Synchronized `README.md`, `SDK-USER-GUIDE.md`, and their
> `src/main/resources/` bundled copies so documented tooling FQNs match the
> Priority 3 package moves (`utility.*` -> `tools.crawler.web.*` /
> `tools.pageobject.*` / `tools.locator.*`). `GETTING-STARTED.md` does not
> exist in this repository. Validated: full `mvn test` suite passes with no
> regressions (doc-only/source-tree-cleanup changes).

Remove stale architecture statements and generated source-tree artifacts.

Ensure configuration examples and user guides describe the same architecture that the SDK actually implements.

---

# 17. Definition of Done for the Current Architecture Cleanup

The current cleanup can be considered complete when:

- `TestBase` uses the common session-creation architecture;
- `ExecutionContext` drives session selection;
- Selenium/Appium creation details remain behind providers/factories;
- configuration precedence is implemented once;
- Mobile configuration is not a separate resolver architecture;
- existing crawlers are formal first-class SDK tools;
- Web and Mobile crawler outputs use normalized discovery contracts;
- Appium WebView/native switching is supported through public SDK APIs;
- mutable global Mobile session/execution state is removed;
- mixed Web/Mobile execution is isolation-tested;
- `AutomationSession` remains the small technology-neutral session boundary;
- future automation technology can be selected without redesigning `TestBase`;
- AI prompts, skills, and schemas remain versioned assets;
- full AI runtime remains deferred until a concrete consumer;
- true independent multi-session runtime remains deferred until a concrete consumer;
- source trees contain source files rather than generated `.class` artifacts;
- documentation accurately reflects the implemented state;
- clean Maven tests and required integration smoke tests pass.

> **Update (2026-09-09, post-implementation): Definition-of-Done mapping.**
>
> | Item | Status | Evidence |
> |---|---|---|
> | `TestBase` uses the common session-creation architecture | COMPLETE | `TestBaseSessionIntegrationTest`; Priority 1 |
> | `ExecutionContext` drives session selection | COMPLETE | `ExecutionContextTest`, `SessionFactoryRegistryTest`; Priority 1 & 5 |
> | Selenium/Appium creation details remain behind providers/factories | ALREADY COMPLETE | `SessionFactory` implementations, `SessionFactoryRegistry`; Section 2.1 |
> | Configuration precedence implemented once | COMPLETE | `config.ConfigurationManager`; `ConfigurationManagerTest`; Priority 2 |
> | Mobile configuration is not a separate resolver architecture | COMPLETE | `MobileConfigReader` delegates to `ConfigurationManager`; precedence bugfix in final cleanup |
> | Existing crawlers are formal first-class SDK tools | COMPLETE | `tools.crawler.web.*`, `tools.crawler.mobile.*`; Priority 3 |
> | Web and Mobile crawler outputs use normalized discovery contracts | ALREADY COMPLETE | Section 4 (pre-existing `DiscoveryResult`/`LocatorCandidate`) |
> | Appium WebView/native switching supported through public SDK APIs | ALREADY COMPLETE | `MobileTestBase.mobileSwitchToNative()`/`mobileSwitchToWebView()`; Section 3 |
> | Mutable global Mobile session/execution state removed | COMPLETE | `mobileOsName`/`deviceName` instance fields; Priority 4 |
> | Mixed Web/Mobile execution is isolation-tested | COMPLETE | `MixedWebMobileIsolationTest`; Priority 4 |
> | `AutomationSession` remains the small technology-neutral session boundary | ALREADY COMPLETE / DEFERRED BY ARCHITECTURE | Section 2.2; no `AutomationElement`/full `Locator` abstraction added, per scope |
> | Future automation technology selectable without redesigning `TestBase` | COMPLETE | `execution.AutomationTechnology`; Priority 5 |
> | AI prompts, skills, and schemas remain versioned assets | ALREADY COMPLETE | Section 5; untouched by this iteration, per scope |
> | Full AI runtime remains deferred until a concrete consumer | DEFERRED BY ARCHITECTURE | Explicit non-goal of this iteration |
> | True independent multi-session runtime remains deferred | DEFERRED BY ARCHITECTURE | Explicit non-goal of this iteration |
> | Source trees contain source files, not generated `.class` artifacts | COMPLETE | 11 stray `.class` files removed; Priority 6 |
> | Documentation accurately reflects the implemented state | COMPLETE | Priority 6 + Section 14 update + v1.1.0 release-pipeline coordinate fixes |
> | Clean Maven tests pass | COMPLETE | `mvn clean test`: 523/523 passed, `BUILD SUCCESS` |
> | Required integration smoke tests pass | BLOCKED (environment) | No real browser/Appium/BrowserStack available in this environment; unit/mocked coverage only — see Section 15 |
>
> **No item is open/in-progress.** Every row is COMPLETE, ALREADY COMPLETE, or
> explicitly DEFERRED BY ARCHITECTURE / BLOCKED by environment limitations
> that were true at review time and remain true today (real device/browser
> infrastructure was never available in this environment, in any iteration).
> **This iteration is considered closed and v1.1.0 is frozen** — no further
> architecture work from this document is planned unless a concrete new
> requirement (e.g. a second Web/user-mimic technology, or real
> integration-lab access) reopens it.

---

# 18. Final Architecture Assessment

The implementation has a strong foundation.

The completed work should be preserved rather than redesigned:

```text
SessionFactoryRegistry
AutomationSession
SeleniumSession / AppiumSession
Appium context switching
DiscoveryResult / LocatorCandidate
ElementDiscoveryService
AI prompt/skill/schema assets
AccessibilityEngine
instance-scoped TestBase driver state
```

The main architectural issue is now **integration and completion**, not a wrong design.

The desired near-term target is:

```text
                         TestBase
                            |
                     ExecutionContext
                            |
                 AutomationSessionFactory
                            |
                      DriverManager
                            |
                 SessionFactoryRegistry
                            |
                +-----------+-----------+
                |                       |
             Selenium                  Appium
                |                       |
              Web                WEBVIEW / NATIVE
```

surrounded by:

```text
Common Configuration
Common Reporting
Common Logging
Common Accessibility
Common Discovery Contracts
First-Class SDK Tools
Versioned AI Prompts / Skills / Schemas
```

The guiding principle remains:

> **Selenium and Appium are implementation technologies, not the architecture of the SDK. Crawlers and automation tools are first-class reusable SDK capabilities. Prompts and skills are versioned AI assets. New abstraction layers should be introduced only when they solve a demonstrated requirement.**

Once the remaining lifecycle, configuration, tooling, and isolation items are completed, this architecture will provide a clean baseline for increasing SDK functionality while preserving a practical path to hybrid Web/Mobile execution, AI-agent integration, multiple sessions when required, and future replacement or coexistence of Selenium with another user-interaction technology.

---

# 19. Iteration Closed — v1.1.0 Frozen (2026-09-09)

All six priorities in Section 16 are implemented, unit/mocked-tested, documented,
and released. Final verdict for this iteration:

| Area | Verdict |
|---|---|
| Production architecture/code | ✅ COMPLETE |
| Testing | ✅ COMPLETE (`mvn clean test`: 523/523 passed, `BUILD SUCCESS`) |
| Configuration cleanup | ✅ COMPLETE |
| Repository/source hygiene | ✅ COMPLETE |
| Documentation status synchronization | ✅ COMPLETE (this section, plus Sections 1, 14, 15, 17) |

Release record:
- `pom.xml` version `1.1.0`; git tag `v1.1.0` on `origin/master`
  (`cross-platform-functional-test-automation-sdk`).
- Deployed to Azure Artifacts and installed into the local file-based
  `maven-repository`.
- A follow-up fix (`67b22f1`) corrected `scripts/release.ps1`'s step 7,
  which had hard-coded the legacy `functional-test-automation-sdk`
  artifactId and could otherwise have bumped an unrelated SDK dependency in
  a consumer template; it now reads its own artifactId at runtime and skips
  step 7 (with a warning) for templates that don't depend on it.

**Explicitly out of scope for this iteration** (unchanged from the original
architecture constraints, not gaps): `AutomationElement`/full technology-neutral
`Locator` abstraction, `SessionContext`/true multi-session runtime, the AI agent
runtime/`AgentToolRegistry`/orchestration infrastructure, and any second
Web/user-mimic automation technology (e.g. Playwright) — `AutomationTechnology`
only reserves the dimension for that future work.

**This iteration is closed. v1.1.0 is frozen.** Future architecture work
resumes only against a new concrete requirement, tracked in a new proposal
document rather than by reopening this one.
