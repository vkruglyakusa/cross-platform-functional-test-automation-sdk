# Changelog -- Framework Automation SDK

All notable changes to `com.test.automation:cross-platform-functional-test-automation-sdk` are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

| Increment | When |
|-----------|------|
| `PATCH` | Bug fixes, no API change |
| `MINOR` | New helpers, backward-compatible additions |
| `MAJOR` | Breaking changes -- package rename, method removal, signature change |

> [!]? **Rule: every SDK change must have a changelog entry before deployment.**
> Write the entry first, then run `mvn deploy`.

---

## [Unreleased]
<!-- Add entries here during development; move to a version heading on release -->

### Fixed
- **`scripts/release.ps1` could be accidentally re-run for a version that was
  already released**, producing a duplicate `docs: release` commit and a
  failed re-deploy (Azure Artifacts rejects re-uploading an existing version).
  Added a Step 0 guard that aborts immediately -- before any tests, doc edits,
  or deploy attempt -- if a `v<version>` git tag already exists (local or
  origin) or `CHANGELOG.md` already has a heading for that version. Also fixed
  the `[Unreleased]` doc-gate check to ignore the standing HTML-comment
  placeholder so an already-promoted (genuinely empty) section is no longer
  miscounted as "has content".
- **`RunMode.resolve()` defaulted to `BROWSERSTACK` when no execution-target
  property was configured** (OBS-1, Poletop consumer-validation finding). A
  local-only consumer upgrading the SDK -- or simply forgetting to set
  `-Drun.mode`/`-DtestInBrowserstack` -- could silently attempt a remote
  BrowserStack session instead of running locally. Unspecified execution now
  resolves to `RunMode.LOCAL`; remote/provider execution must be explicit via
  `-Drun.mode=BROWSERSTACK`, the legacy `-Dmobile.execution.target`, or the
  legacy `-DtestInBrowserstack=true` flag, all of which are unchanged.
  Invalid `-Drun.mode`/`-Dmobile.execution.target` values now raise an
  `IllegalArgumentException` naming the offending property and its accepted
  values, instead of the raw `Enum.valueOf` message.
- **Standard failure screenshots were written to a hidden, double-nested
  `<reporting.screenshotsDir>/screenshots/` folder** instead of directly to
  `reporting.screenshotsDir` (OBS-3, Poletop consumer-validation finding),
  while the paired DOM dump was written one level up in the correct,
  configured directory -- so a failure's screenshot and DOM evidence ended up
  in different folders. `TestBase.getScreenShot(WebDriver, ITestResult)` is
  now the standard failure-capture API and writes directly to
  `reporting.screenshotsDir`, matching the DOM dump. The old
  `getScreenShot(WebDriver, ITestResult, String folderName)` overload is
  `@Deprecated` and now ignores `folderName` (rather than nesting it) for
  source/binary compatibility.
- **`Excel_Reader.getDataFromSheet` threw a raw `NegativeArraySizeException(-1)`**
  for a missing sheet or a sheet with no data rows below the header (OBS-6,
  Poletop consumer-validation finding), instead of describing the actual
  test-data problem. It now validates the sheet exists and has at least one
  data row and one column before allocating the result array, and raises a
  descriptive `IllegalStateException` naming the workbook, sheet, and the
  row/column counts found.

### Documentation
- **Added a "Known migration gotchas" subsection** (SDK-USER-GUIDE.md Section 4a)
  covering two recurring consumer-migration issues surfaced by the Poletop
  consumer-validation pass: (1) a `@Deprecated` top-level facade class cannot
  preserve imports of its nested types (Java requires the canonical declaring
  class for nested-type imports -- e.g. `ElementCrawler.ElementInfo` must be
  imported from `com.test.automation.sdk.tools.crawler.web.ElementCrawler`,
  not a deprecated facade), and (2) `TestBase.driver` is instance-scoped, so
  consumer static helper/Page Object methods that touch the driver must be
  converted to instance methods. Both are documentation-only; no SDK code
  changes accompany them since neither is a bug to fix in the SDK.

---

## [1.1.1] — 2026-09-09
<!-- Add entries here during development; move to a version heading on release -->

### Fixed
- **`scripts/release.ps1` step 7 (consumer template update) was hard-coded to
  the legacy `functional-test-automation-sdk` artifactId.** When releasing
  *this* SDK (`cross-platform-functional-test-automation-sdk`), it could bump
  an unrelated SDK dependency's version in a consumer template to match this
  release -- a silent downgrade/corruption bug (caught only because the
  template's own `mvn compile test-compile` validation failed). The script now
  reads its own `artifactId` from `pom.xml` at runtime, uses it in every
  version-bump regex (its own docs *and* the template's), and skips step 7
  entirely with a clear warning if the target template does not actually
  depend on that artifactId. Added a `-SkipTemplate` switch to opt out of step
  7 explicitly.
- **Stale/incorrect Maven coordinates in this SDK's own docs**: `README.md`,
  `SDK-USER-GUIDE.md`, `TESTBASE-API.md`, and `CHANGELOG.md` (plus their
  `src/main/resources/` mirrors) still referenced the old
  `functional-test-automation-sdk` artifactId/coordinate in dependency
  snippets, verification commands, and footers -- corrected to
  `cross-platform-functional-test-automation-sdk` (Azure Artifacts feed name
  and server/repository `<id>` references were left unchanged since that feed
  is intentionally shared infrastructure, not an artifactId).

### Documentation
- **Synchronized `docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`**
  to the final post-implementation state: Section 1 (Executive Summary),
  Section 14 (Documentation Consistency), and Section 15 (Validation Status)
  now carry "Update (2026-09-09, post-implementation)" notes pointing at the
  completed work instead of the original pre-implementation findings; Section
  17 (Definition of Done) now has a full item-by-item COMPLETE / ALREADY
  COMPLETE / DEFERRED BY ARCHITECTURE / BLOCKED mapping table with evidence;
  a new Section 19 ("Iteration Closed — v1.1.0 Frozen") records the final
  verdict and release record. This closes the review's own "documentation
  status synchronization" follow-up item -- the historical review text is
  preserved (not deleted), each stale section is annotated rather than
  rewritten.

---

## [1.1.0] — 2026-09-09
<!-- Add entries here during development; move to a version heading on release -->

### Added
- **Final cleanup for the v1.1.0 iteration (pre-release)**: fixed
  `MobileConfigReader.get(...)` precedence so a system property/environment
  variable override (via `ConfigurationManager.resolveOverride(...)`) is now
  checked *before* an optional standalone `mobile-config.yaml`, matching the
  documented "system property > env var > project YAML > default" chain --
  previously a value present in `mobile-config.yaml` incorrectly took
  precedence over `-Dandroid.appPath=...`/the equivalent env var. No second
  Mobile-specific resolver was introduced; the fix only reorders the existing
  calls to `ConfigurationManager.resolveOverride(...)`/`resolve(...)`. Added
  a package-private `MobileConfigReader.resetForTests()` test hook and a new
  regression test (`systemProperty_overridesStandaloneMobileConfigYamlValue`
  in `MobileConfigReaderTest`) that writes a real standalone
  `mobile-config.yaml`, and proves both that its value wins over the
  project-YAML default and that a system property still wins over it. Added
  an "Mobile (Appium / Android / iOS) Settings" section (`appium.localUrl`,
  `android.appPath`/`automationName`, `ios.appPath`/`automationName`) to
  `sdk-defaults/sdk-config.yaml.template` as the new recommended location for
  Mobile configuration. Marked `configuration/mobile-config.yaml.example`
  with an explicit DEPRECATED/legacy-compatibility banner pointing at the
  unified `sdk-config.yaml` template instead (not removed, since no
  confirmation exists that all consumer projects have migrated). Fixed
  stale "not yet implemented"/"NOT yet buildable" wording in
  `docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`'s Section
  33 review block and its "34. Final Recommendation" priority list, which
  predated this session's `AutomationSession`/`AutomationSessionFactory` and
  public `mobileSwitchToNative()`/`mobileSwitchToWebView()` work -- both are
  now annotated as implemented rather than left reading as still-pending.
  Validated: full `mvn clean test` suite passes with no regressions; clean
  `git status`.
- **Unified SDK Review Priority 6 -- Documentation and Repository Hygiene**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  sections 13, 14, and 16): removed 11 stray compiled `.class` artifacts
  from the `src/main/java` source tree (`SdkConfig`, `CrawlerStep` +
  inner-class variants, `InstructionExtractor`, `PageContext`, `Email` +
  related mailinator classes) -- they were already gitignored but were
  still physically present in the working tree, exactly matching the
  review's section 13 finding. Updated the stale
  `docs/proposals/unified-execution-architecture.md` header (previously
  `Status: PROPOSED (not yet implemented)`, now `Status: IMPLEMENTED`) to
  point to `Unified_SDK_Implementation_Review_Findings_2026-09-09.md`
  section 16 as the single authoritative current-state reference, per the
  review's "one authoritative current-state section" requirement (section
  14). Synchronized `README.md`, `SDK-USER-GUIDE.md`, and their
  `src/main/resources/` bundled copies so the documented tooling FQNs match
  the Priority 3 package moves (`sdk.utility.ElementCrawler` ->
  `sdk.tools.crawler.web.ElementCrawler`, `sdk.utility.PageObjectGenerator`
  -> `sdk.tools.pageobject.PageObjectGenerator`,
  `sdk.utility.DataDrivenCrawler`/`CrawlerStep`/`CrawlerScenario`/
  `ElementSearchEngine` -> `sdk.tools.crawler.web.*`,
  `sdk.tools.AbstractLocatorInvestigator` ->
  `sdk.tools.locator.AbstractLocatorInvestigator`), including the
  `mvn exec:java` `PageObjectGenerator` example command. `GETTING-STARTED.md`
  does not exist in this repository, so there was nothing to sync there.
  Validated: full `mvn test` suite passes with no regressions (doc-only and
  source-tree-cleanup changes; no production code changed).
- **Unified SDK Review Priority 3 — Complete Tooling Consolidation**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  sections 9 and 16): completed the package-only tooling consolidation under
  `tools/` without rewriting crawler/generator logic. Web tooling moved from
  `utility.*` to `tools.crawler.web.*`
  (`ElementCrawler`, `DataDrivenCrawler`, `CrawlerStep`, `CrawlerScenario`,
  `ElementSearchEngine`); mobile tooling moved from `mobile.crawler.*` to
  `tools.crawler.mobile.*` (`MobileElementCrawler`,
  `MobileDataDrivenCrawler`, `MobileCrawlerReportWriter`,
  `MobileCrawlerStep`, `MobileElementInfo`, `MobileLocatorCandidate`,
  `MobileScreenSnapshot`, `MobileElementDiscoveryAdapter`);
  page-object generators moved to `tools.pageobject.*`
  (`PageObjectGenerator`, `MobilePageObjectGenerator`); and
  `tools.AbstractLocatorInvestigator` moved to
  `tools.locator.AbstractLocatorInvestigator`. Per the repo's existing
  backward-compatibility pattern (`utility.YamlConfigReader`,
  `utility.WebElementDiscoveryAdapter`), every old FQN now remains as a
  deprecated compatibility facade: inheritance-based facades where safe
  (`ElementCrawler`, `ElementSearchEngine`, `PageObjectGenerator`,
  `AbstractLocatorInvestigator`) and delegation/wrapper facades where
  constructors/finality/generic signatures made direct subclassing unsafe
  (`CrawlerStep`, `CrawlerScenario`, `DataDrivenCrawler`,
  `MobileElementCrawler`, `MobileDataDrivenCrawler`,
  `MobileCrawlerReportWriter`, `MobileCrawlerStep`, `MobileElementInfo`,
  `MobileLocatorCandidate`, `MobileScreenSnapshot`,
  `MobileElementDiscoveryAdapter`, `MobilePageObjectGenerator`). Internal
  callers, tests, and bundled docs/resources were updated to reference the
  new packages directly. Added compatibility tests
  `utility.ToolingCompatibilityFacadeTest`,
  `mobile.crawler.MobileToolingCompatibilityFacadeTest`, and
  `tools.locator.AbstractLocatorInvestigatorCompatibilityTest`; relocated
  moved-class tests under `tools/crawler/web`, `tools/crawler/mobile`, and
  `tools/pageobject`. Also made `utility.MapWidgetHelper.isAncestor(...)`
  public so the moved web crawler can keep using the same helper without
  duplicating logic. Validated: `mvn compile test-compile`, targeted moved
  class tests (124 tests), and full `mvn test` all pass with no regressions.
  Not validated in this environment: live browser/Appium crawler execution;
  validation here remained unit/mocked/package-structure focused.
- **Unified SDK Review Priority 5 -- Reserve `AutomationTechnology` in Execution Selection**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  section 11): execution selection was strictly `Platform + RunMode`, with no
  way to express that a platform could one day be served by more than one
  automation engine (e.g. Web via Selenium today, Playwright later). Added a
  new `execution.AutomationTechnology` enum (`SELENIUM`, `APPIUM` only --
  `PLAYWRIGHT`/`USER_MIMIC` are explicitly *not* implemented, only the seam is
  reserved). `SessionFactory` gained a `default getAutomationTechnology()`
  method that infers the platform-implied value (`SELENIUM` for `WEB`,
  `APPIUM` for `ANDROID`/`IOS`), so all 6 existing built-in factories compile
  and resolve unchanged without overriding it. `ExecutionContext` gained a
  matching `getAutomationTechnology()` plus new 3-arg
  `forWeb(browserName, runMode, technology)` /
  `forMobile(platform, deviceName, runMode, technology)` overloads (the
  existing 2-arg overloads are unchanged and now delegate with `technology =
  null`, defaulting the same way). `SessionFactoryRegistry` now keys
  registrations on `(platform, runMode, technology)`; the existing 2-arg
  `resolve(Platform, RunMode)` overload is preserved and defaults the
  technology identically, so no existing caller (`DriverManager`, tests)
  needed to change. New/updated tests: `execution.ExecutionContextTest`
  (technology defaulting/override), `execution.SessionFactoryRegistryTest`
  (technology-aware resolution, explicit-technology mismatch does not
  silently fall back). No new technology implementation (e.g. Playwright) was
  added, per the review's explicit scope boundary.
- **Unified SDK Review Priority 4 — Finish Session Isolation (Mobile static state removal)**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  section 10): `MobileTestBase.mobileOsName`/`deviceName` were `protected
  static` fields shared across all Mobile test instances -- two concurrently
  executing Mobile test classes (or the same class run in parallel) could
  leak each other's device/platform selection, especially around retries.
  Converted both to instance fields; `getCurrentPlatformOS()` (which reads
  `mobileOsName`) is now an instance method instead of `static` (no other
  behavior change -- `isRunningInCloud()` remains `static`, it never read
  either field). New test: `mobile.testbase.MixedWebMobileIsolationTest`,
  which runs a Web `TestBase` test and a Mobile `MobileTestBase` test
  concurrently (fake `SessionFactory`s, no real browser/Appium session) and
  asserts neither driver/session leaks into the other, plus runs two
  concurrent Mobile test instances with different `deviceName`s and asserts
  each retains its own value -- proving the fields are genuine per-instance
  state. A mocked concurrency test is sufficient for this architecture-level
  validation per the review's own guidance; real mixed Web/Mobile
  BrowserStack/Appium concurrency was not validated in this environment.
  Validated: targeted tests and full `mvn test` suite pass with no
  regressions.
- **Unified SDK Review Priority 2 — Finish Configuration Unification**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  section 8): added `config.ConfigurationManager`, the SDK's single
  configuration-resolution engine implementing the full precedence chain
  (system property > environment variable > project YAML via
  `YamlConfigReader` > caller-supplied default) in exactly one place, plus
  small typed `CommonConfig`/`WebConfig` views (`getCommonConfig()`,
  `getWebConfig()`) over the handful of keys already in use. Refactored
  `mobile.config.MobileConfigReader.get(String, String)` to delegate to
  `ConfigurationManager.resolve(...)` for any key not present in an optional
  standalone `mobile-config.yaml` (still the only Mobile-specific behavior it
  owns, retained as a temporary migration path per the review), so Mobile
  configuration is now subject to the exact same precedence rules as
  Web/common configuration instead of reading `YamlConfigReader` directly
  with no system-property/env override at all -- previously a real gap
  (e.g. `-Dandroid.appPath=...` had no effect). Added
  `MobileConfigReader.getMobileConfig()` returning a typed `MobileConfig`
  view (`androidAppPath()`, `androidAutomationName()`, `iosAppPath()`,
  `iosAutomationName()`, `appiumLocalUrl()`) so call sites can move off raw
  dotted-key strings incrementally; existing `MobileConfigReader.get(...)`
  call sites in `MobileDriverFactory`/crawler classes are unchanged and keep
  working. New tests: `config.ConfigurationManagerTest`,
  extended `mobile.config.MobileConfigReaderTest` (system-property override
  precedence, typed view). Validated: targeted tests and full `mvn test`
  suite pass with no regressions.
- **Unified SDK Review Priority 1 — Complete TestBase Session Integration**
  (`docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md`,
  section 16): `TestBase.initialization(String, String)` now builds an
  `ExecutionContext.forWeb(...)` and acquires its `WebDriver` via
  `AutomationSessionFactory.create(context)` (which delegates to
  `DriverManager` -> `SessionFactoryRegistry`) instead of calling
  `WebDriverFactory` directly; the resulting `AutomationSession` is stored on
  the new `TestBase.automationSession` field. `TestBase.closeBrowser()` now
  quits through `automationSession` when set, falling back to `driver.quit()`
  for subclasses that assign `driver` directly. `MobileTestBase.setUpDriver`
  and its retry path in `beforeMethod(Method)` were updated the same way,
  building `ExecutionContext.forMobile(...)` and acquiring/re-acquiring
  through `AutomationSessionFactory` instead of calling `MobileDriverFactory`
  directly (a new private `resolvePlatform(String)` helper maps the existing
  `mobileOS`/`device` strings to `Platform`). `AutomationSessionFactory`,
  `DriverManager`, and `SessionFactoryRegistry` themselves were already
  correct and unchanged -- this closes the gap where they existed but were
  never called from the primary test lifecycle. New test:
  `testbase.TestBaseSessionIntegrationTest` (registers a fake `SessionFactory`
  for `(WEB, LOCAL)`, same pattern as `session.AutomationSessionFactoryTest`).
  Validated: targeted tests and full `mvn test` suite pass with no
  regressions. Not validated in this environment: real
  browser/Appium/BrowserStack execution.
- **Structure Cleanup Phase 8 — Package Cleanup (first gradual step)**
  (`docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`): moved
  `utility.WebElementDiscoveryAdapter` to `discovery.WebElementDiscoveryAdapter`
  (and its test to `discovery.WebElementDiscoveryAdapterTest`) so the web
  implementation of the `discovery` package's `ElementDiscoveryService`
  contract lives next to the contract it implements, mirroring
  `mobile.crawler.MobileElementDiscoveryAdapter`'s location alongside its own
  crawler package. Updated the one caller (`MobileElementDiscoveryAdapter`'s
  import) and doc cross-references (`ElementDiscoveryService` javadoc,
  `ai/schemas/discovery-result-v1.schema.json` description). Confirmed
  Class Migration Map item 1 (`utility.YamlConfigReader` -> real
  `config.YamlConfigReader`) was already completed in Phase 2 -- the
  `utility.YamlConfigReader` compatibility facade remains in place per the
  roadmap's explicit rule to remove deprecated wrappers only after consumer
  repositories are migrated (not yet confirmed), so it is intentionally left
  untouched. Validated: full suite green.
- **Structure Cleanup Phase 7 — Session Isolation / Parallel Safety**
  (`docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`): removed
  remaining global static session state from `TestBase` -- `baseURL`,
  `testRetryCount`, and `parentWindow` converted from `public static` to
  instance fields (same rationale/pattern as the `driver` field fixed in
  Phase 4: TestNG gives each test class its own `TestBase` instance, so an
  instance field is correct and sufficient without a `ThreadLocal`, and a
  shared static would leak one test class's base URL/retry count/window
  handle into another running on a different thread). Removed two dead
  static fields entirely rather than migrating them, since nothing read
  them: `TestBase.extent`/`TestBase.test` (unused `ExtentReports`/`ExtentTest`
  fields -- reporting already goes through the thread-keyed
  `ExtentTestManager`) and `WebDriverFactory.driver` (unused static
  `WebDriver` field). Removed the now-ineffective `testRetryCount = 0;` reset
  in `Listener.onFinish()` (a no-op on a different object instance now that
  the field is per-`TestBase`-instance, not a shared static) with an
  explanatory comment. No remaining global mutable static session state was
  found elsewhere (`mobile`, `driver`, `session`, `execution` packages
  already use only static factory *methods*, no static session-holding
  fields). Validated: full suite green.
- **Structure Cleanup — AI Prompt/Skill Asset Standardization** (Priority item 6 /
  Phase 10, `docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`
  sections 26.1/26.2/29): new `src/main/resources/ai/{prompts,skills,schemas}`
  asset tree for a future LLM *agent* that drives the SDK's tool contracts
  programmatically -- distinct from, and additive to, the existing
  `sdk-prompts/`/`InstructionExtractor` mechanism (human-facing Copilot CLI
  `#slash-command` templates), which is untouched. Ships one concrete,
  end-to-end example tied to Phase 6's `ElementDiscoveryService`: JSON Schemas
  `ai/schemas/discovery-result-v1.schema.json` (mirrors `DiscoveryResult`/
  `DiscoveredElement`/`LocatorCandidate`) and `ai/schemas/locator-recommendation-v1.schema.json`
  (the prompt's output contract, `$ref`-linked to the discovery-result schema);
  prompt `ai/prompts/element-discovery/analyze-locator-candidates.md`
  (versioned YAML front matter -- name/version/capability/inputSchema/
  outputSchema/requiredTools) instructing an agent to pick the best `UNIQUE`
  locator candidate from a `DiscoveryResult`; and skill descriptor
  `ai/skills/element-discovery/element-discovery.skill.yaml` tying the
  `ElementDiscoveryService` tool call, the prompt, and output-schema
  validation together. `ai/README.md` documents the convention and explicitly
  distinguishes it from `sdk-prompts/`. Per the roadmap's Priority &
  Sequencing Adjustments item 6, only the asset files are added now -- the
  Java-side `AgentToolRegistry`/`AgentContextBuilder`/orchestration runtime
  remains deferred until a concrete agent consumer exists (Guardrail #23).
  `SdkResourcesTest` gained a packaging-gate block asserting all 5 new `ai/*`
  files are present and non-empty on the classpath, plus a front-matter
  content check on the new prompt.
- **Structure Cleanup Phase 6 — Discovery/Crawler Normalization**
  (`docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`): new
  `discovery` package with a common, crawler-neutral result contract --
  `LocatorCandidate` (strategy label, value, `Marker` enum
  `UNIQUE`/`NOT_UNIQUE`/`DYNAMIC`/`STALE`/`STRUCTURAL`/`OTHER`, match count),
  `DiscoveredElement` (platform, tag/type, text, candidates, plus
  `unwrap(Class<T>)` back to the original platform-specific object -- mirrors
  the `AutomationSession#unwrap(Class)` pattern from Phase 3), `DiscoveryResult`
  (element list + source label + `resolvedCount()`), and the
  `ElementDiscoveryService` interface (`discoverCurrent()`). Two new adapters
  implement it without changing either crawler's own algorithm (Guardrails
  #15/#16 -- normalize outputs, not algorithms): `utility.WebElementDiscoveryAdapter`
  (wraps `ElementCrawler`, parses its existing `allXpaths` composite-key
  labels via its own public `LABEL_*` constants so normalization can never
  drift out of sync) and `mobile.crawler.MobileElementDiscoveryAdapter` (wraps
  `MobileElementCrawler`, converts native `MobileElementInfo`/`MobileLocatorCandidate`
  and reuses `WebElementDiscoveryAdapter.toDiscoveredElement` for delegated
  WebView elements -- there is now only one `ElementCrawler.ElementInfo`-to-
  `DiscoveredElement` conversion in the whole SDK). Additive, non-breaking
  change; neither crawler's crawl/uniqueness-detection algorithm was modified.
  New unit tests (`WebElementDiscoveryAdapterTest`, 10 cases;
  `MobileElementDiscoveryAdapterTest`, 6 cases) cover the pure, driver-free
  conversion logic only (`discoverCurrent()` requires a live session and is
  out of unit-test scope, consistent with existing crawler test conventions).
  Full suite green after this change.
- **Structure Cleanup Phase 3 — Lightweight Unified Session Boundary**
  (`docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`): new
  `session.AutomationSession` interface (`navigate(url)`, `quit()`,
  `unwrap(Class<T>)`) with two implementations,
  `session.internal.SeleniumSession` (wraps `WebDriver`) and
  `session.internal.AppiumSession` (wraps `AppiumDriver`), plus
  `session.AutomationSessionFactory` as the public entry point --
  `create(ExecutionContext)` and `wrap(WebDriver, Platform)`. Per this
  phase's deliberately narrow scope, `AutomationElement`/`Locator` are NOT
  introduced yet (deferred until crawler/Page Object portability creates a
  concrete need). `AutomationSessionFactory` does not add a second
  session-resolution mechanism -- it delegates entirely to the existing
  `driver.DriverManager.acquire(ExecutionContext)` (itself backed by
  `execution.SessionFactoryRegistry`, established in Phase 1) and only wraps
  the resulting driver in the technology-appropriate `AutomationSession`.
  `testbase.WebDriverFactory`/`mobile.driver.MobileDriverFactory` are
  unaffected and remain valid entry points; `TestBase` consolidation onto
  this factory is deferred to Phase 4. Additive, non-breaking change. New
  unit tests (`AutomationSessionFactoryTest`, `SeleniumSessionTest`,
  `AppiumSessionTest`) use mocked `WebDriver`/`AppiumDriver` -- no real
  browser/Appium session is launched. 459/459 tests passing, `BUILD SUCCESS`.
- **Mobile context switching helpers** (`mobileSwitchToNative()` /
  `mobileSwitchToWebView()`), per the same v4 roadmap's "Priority &
  Sequencing Adjustments" item 4: promoted the proven NATIVE_APP/WEBVIEW
  switching pattern out of `MobileElementCrawler.crawlWebViewsIfPresent()`
  (previously private/internal to the crawler) into public, reusable helpers.
  New static methods on `mobile.actions.MobileActions`:
  `getAvailableContexts`, `getCurrentContext`, `isInWebViewContext`,
  `switchToNativeContext`, `switchToContext(driver, name)`,
  `switchToWebViewContext(driver)` (first available WebView), and
  `switchToWebViewContext(driver, nameContains)` (for apps with more than one
  active WebView). New protected wrappers on
  `mobile.testbase.MobileTestBase`: `mobileSwitchToNative()`,
  `mobileSwitchToWebView()`, `mobileSwitchToWebView(String)`,
  `isInWebViewContext()`, `getAvailableContexts()` -- for direct use from
  test classes/page objects. `MobileElementCrawler` itself was refactored to
  delegate to these same `MobileActions` helpers instead of calling
  `SupportsContextSwitching` directly, so the crawler and general
  page-object/test code now share one context-switching mechanism (Guardrail
  #4: one session/context-selection mechanism). Additive, non-breaking
  change. New unit tests (`MobileActionsTest`, 10 cases) mock
  `AppiumDriver`/`SupportsContextSwitching` -- no real Appium session is
  launched. Full suite green after this change.

### Changed
- **Structure Cleanup Phase 2 — Configuration Unification**
  (`docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`): the real,
  tested YAML parser/singleton moved from `utility.YamlConfigReader` to
  `config.YamlConfigReader`, which is now the single source of truth.
  `utility.YamlConfigReader` is a `@Deprecated` thin compatibility facade that
  delegates to `config.YamlConfigReader` (kept for one release so existing
  reflection-based lookups of the old class name keep working).
  `mobile.config.MobileConfigReader` already delegated to `config.YamlConfigReader`
  for its fallback path (no change needed there — it was already "a typed mobile
  view, not a second resolver"). Production call sites updated to import
  `config.YamlConfigReader` directly: `testbase.WebDriverFactory`,
  `testbase.TestBase`, `utility.GapReportWriter`, `utility.PageObjectGenerator`,
  `utility.mailinator.Mailinator`, `utility.mailinator.MailinatorEmailReader`.
  Test coverage: the full parsing/defaults test suite moved from
  `utility.YamlConfigReaderTest` to `config.YamlConfigReaderTest` (47 tests);
  `utility.YamlConfigReaderTest` now contains a small facade-delegation
  compatibility test instead. `README.md`/`SDK-USER-GUIDE.md` updated to
  reference `sdk.config.YamlConfigReader`. 442/442 tests passing, `BUILD SUCCESS`.
- `mobile.testbase.MobileTestBase` now `extends com.test.automation.sdk.testbase.TestBase`
  instead of duplicating a parallel lifecycle -- the deferred item from Phase 4/5 of
  `docs/proposals/unified-sdk-architect-review.md`. Both platforms now share one `driver`
  field (an `AppiumDriver` IS-A `WebDriver`), one `afterClass()` teardown (`driver.quit()`
  + Extent flush -- already driver-type-agnostic, needed no change), and the same Extent/
  Allure reporting hooks. `MobileTestBase` overrides only what's genuinely platform-specific:
  `setUp(String, String)` (the inherited web `@BeforeClass` -- overridden as a no-op so
  mobile test classes never attempt to launch a Selenium browser session) and
  `beforeMethod(Method)` (the inherited web `@BeforeMethod` retry hook -- overridden so a
  retry re-creates the `AppiumDriver` session via `MobileDriverFactory` instead of the web
  `initialization(...)` path, fixing a latent cross-test-class `testRetryCount` contamination
  risk identified during this merge, since that counter is `static`/process-wide). Removed
  `MobileTestBase`'s own duplicate `AppiumDriver driver` field, `tearDownDriver()` (now
  relies on the inherited generic `afterClass()`), and its own `waitForElementPresent(...)`
  overloads (now inherited unchanged from `TestBase`, which already waits generically on the
  `WebElement` itself and works identically for Appium-backed elements). Call sites needing
  Appium-specific APIs (`MobileActions.*`) now cast the shared field via a small
  `requireAppiumDriver()` helper. Validated by the SDK's own unit suite (451/451,
  `BUILD SUCCESS`, zero regressions); also exercised end-to-end against a real local
  Appium 3.0.1 server + booted Android emulator (`Medium_Phone_API_36.0`) via
  `mobile-functional-automation-consumer-template`'s `WikipediaSearchTest`.

### Removed
- **[Breaking]** `mobile.execution.MobileExecutionStrategy` /
  `MobileExecutionStrategyFactory` / `LocalExecutionStrategy` /
  `BrowserStackExecutionStrategy` / `MobileExecutionStrategySupport` /
  `ExecutionTarget` / `MobileSessionRequest` -- Phase 1 of
  `docs/proposals/sdk-structure-cleanup-assessment-updated-v2.md` (now the
  formal architecture document for this SDK). This mobile-only
  session-selection mechanism duplicated the already-existing, platform-neutral
  `execution.SessionFactoryRegistry`, which was wired up (all 6
  web/Android/iOS local/BrowserStack factories registered in
  `driver.DriverManager`) but not yet the sole resolution path. `ExecutionTarget`
  required no migration -- `execution.RunMode` already fully superseded it
  (including its `-Dmobile.execution.target`/`-DtestInBrowserstack` legacy-flag
  fallback resolution). `mobile.driver.MobileDriverFactory` now owns the real
  local/BrowserStack Appium driver-creation logic directly (mirroring how
  `testbase.WebDriverFactory` already owns the equivalent web logic), exposing
  new public `getLocalDriver`/`getBrowserStackDriver` methods that the 4
  `driver.mobile.*SessionFactory` classes call directly; `getDriver(mobileOS,
  deviceName)`'s public signature/behavior is unchanged. `MobileTestBase.
  isRunningInCloud()` switched from `ExecutionTarget.resolve()` to
  `RunMode.resolve()`. 441/441 tests passing, `BUILD SUCCESS`; additionally
  validated against a real local Appium 3.0.1 server + Android emulator
  (`emulator-5554`) -- `MobileDriverFactory.getLocalDriver("android", ...)`
  created and cleanly quit a live session.
- **[Breaking]** `testbase.SdkConfig` and `mobile.testbase.MobileSdkConfig` --
  the two backward-compat shims kept since Phase 3 (delegating to
  `config.SdkConfig`) have been deleted entirely; `config.SdkConfig` is now the
  **only** `SdkConfig` class in the SDK. All 6 internal call sites that still
  imported the legacy classes (`TestBase`, `WebDriverFactory`, `CSVReporter`,
  `CSVUtils`, `PropertiesReader`, `QueryExcelFile`, `MailinatorEmailReader`,
  `MobileConfigReader`) were updated to reference `config.SdkConfig` directly.
  Searched all known consumer projects
  (`mobile-functional-automation-consumer-template`, `311-Automation-SDK`) --
  neither referenced the legacy classes directly, so this is not expected to
  break any current consumer, but IS a breaking change for anyone who does
  (`com.test.automation.sdk.testbase.SdkConfig` /
  `com.test.automation.sdk.mobile.testbase.MobileSdkConfig` no longer exist).
  Test coverage consolidated: the resolution-priority-algorithm tests from
  the deleted `testbase.SdkConfigTest` were ported into `config.SdkConfigTest`
  (which is now the single test class for `SdkConfig`); the two
  legacy-delegation tests were removed since there is nothing left to delegate
  to. `README.md`/`SDK-USER-GUIDE.md` updated to reference `sdk.config.SdkConfig`.
  441/441 tests passing, `BUILD SUCCESS`.
- Illustrative/demo test-tree artifacts that had no place shipping inside a reusable
  SDK: `mobile.sample.SampleHomePage`/`SampleSmokeTest` (a disabled, illustrative-only
  page object + smoke test demonstrating `MobileTestBase` usage) and 5 manual/diagnostic
  harnesses under `mobile.crawler.manual` (`CrawlerEffectivenessCheck`,
  `DiagnoseNewServiceRequestList`, `HybridWebViewCrawlerValidation`,
  `LocalEmulatorCrawlerValidation`, `MobileAccessibilityValidation`) that required a
  real device/emulator to do anything and were never wired into the automated `mvn test`
  run. None of these had any external references; pure deletion, no code changes
  elsewhere needed. 451/451 tests still passing, zero regressions.
- `mobile.uiActions.*` (7 concrete 311-app page objects: `HomePage`,
  `NavigationUtility`, `NewServiceRequestPage`, `NotificationsPage`,
  `PermissionControllerPopUp`, `TermsOfUsePage`, `UserDataPolicyPage`) --
  Phase 5 of `docs/proposals/unified-sdk-architect-review.md`, section C.6.
  App-specific page objects have no architectural justification inside a
  reusable SDK (the mature web SDK correctly has zero page objects of its
  own). `mobile-functional-automation-consumer-template`'s
  `OnboardingAndHomeSmokeTest` directly imported `NavigationUtility` and
  `NewServiceRequestPage`, and `NavigationUtility` itself instantiates the
  other 5 classes internally -- all 7 were coordinated-migrated (not just
  deprecated) into that consumer template's own `com.yourcompany.automation.uiActions`
  package first, and its two imports updated, before deleting the SDK-side
  originals. Verified the consumer template still compiles
  (`mvn compile test-compile`) against the relocated classes. Also removed
  the SDK's own disabled, unreferenced sample test
  (`mobile.samples.onboarding.OnboardingAndHomeSmokeTest`) that depended on
  these same classes purely for demonstration purposes -- the consumer
  template's own enabled copy is the canonical version. `mobile.crawler.MobilePageObjectGenerator`'s
  default `crawler.pageObject.package` fallback changed from the now-deleted
  `com.test.automation.sdk.mobile.uiActions` to the generic
  `com.mycompany.automation.uiActions` placeholder, matching the web
  crawler's existing convention. No new unit tests (pure deletion +
  cross-repo migration); 451/451 tests still passing, zero regressions.

### Fixed
- `testbase.TestBase.driver` is now an **instance field** (was
  `public static WebDriver driver`) -- Phase 4 of
  `docs/proposals/unified-sdk-architect-review.md`, section C.5. Fixes a
  real thread-safety gap: previously every `TestBase`-family object in the
  JVM (test classes, `Listener`, `WebEventListener`) shared one process-wide
  driver slot. TestNG already gives each test class its own instance, so an
  instance field is correct without needing a `ThreadLocal`.
  Two call sites relied on the old static-sharing behavior and required a
  matching fix so failure-capture and accessibility scanning keep working
  unchanged:
  - `listener.Listener.onTestFailure(...)` no longer reads the removed
    `TestBase.driver` static reference; it now resolves the actual test's
    driver via `ITestResult.getInstance()` -- which is also more correct
    for parallel execution than a single shared static ever was.
  - `listener.WebEventListener` (constructed standalone by
    `testbase.WebDriverFactory`, then wrapped around the real session via
    `EventFiringDecorator`) now takes the driver as a constructor argument
    instead of implicitly inheriting it from the old shared static slot, so
    its element-level accessibility scan (`checkElementAccessibility`)
    keeps working.
  Six `TestBase` helper methods that reference the bare `driver` field lost
  their now-inapplicable `static` modifier: `waitForElementPresent(WebElement)`,
  `fluentWaitForElement(WebElement)`, `fluentWaitUntilElementToBeClickable(WebElement)`,
  `waitUntilElementToBeClickable(WebElement)`, `waitUntillPageLoad()`,
  `reloadPageUntilWebElementVisible(WebElement)`. No web or mobile consumer
  call site was found to call these statically (all use inheritance), so
  this is expected to be transparent to existing tests. 2 new unit tests.
  `mobile.testbase.MobileTestBase`'s own merge into `TestBase` (making it
  `extends TestBase` and sharing this same field) is deferred to a follow-up
  pass -- `MobileTestBase` currently declares its own `@BeforeClass`/`@AfterClass`
  configuration methods, and naively extending `TestBase` would make TestNG
  run **both** the web and mobile setup/teardown methods for every mobile
  test class, which cannot be safely verified without an actual
  Appium/device-farm run (not available in this environment).

### Added
- `config.SdkConfig` / `config.YamlConfigReader` -- Phase 3 of
  `docs/proposals/unified-sdk-architect-review.md`. Merges the previously
  duplicated `testbase.SdkConfig`/`mobile.testbase.MobileSdkConfig`
  directory-resolution algorithm into one class (now recognizes the legacy
  `-Dmobile.sdk.config.dir`/`MOBILE_SDK_CONFIG_DIR` overrides as a fallback
  too), and adds a platform-neutral facade in front of the existing,
  untouched `utility.YamlConfigReader` parser/singleton. `sdk-config.yaml`
  gains built-in defaults for `appium.localUrl`, `android.automationName`,
  and `ios.automationName`, so `android:`/`ios:` sections can now be
  configured directly in the single unified file. `testbase.SdkConfig` and
  `mobile.testbase.MobileSdkConfig` are unchanged from a consumer's
  perspective (same public static fields, same values) but now delegate to
  `config.SdkConfig` internally -- zero behavior change for existing web or
  mobile consumers. `mobile.config.MobileConfigReader` still honors a
  standalone `mobile-config.yaml` unchanged when present (one-release
  fallback), and now falls back to reading the same dotted keys from the
  unified `sdk-config.yaml` when that file is absent. 18 new unit tests.
- `driver.DriverManager` / `driver.web.{WebLocalSessionFactory,WebBrowserStackSessionFactory}` /
  `driver.mobile.{Android,Ios}{Local,BrowserStack}SessionFactory` / `driver.BrowserStackSupport`
  — Phase 2 of `docs/proposals/unified-sdk-architect-review.md`. One public
  entry point (`DriverManager.acquire(ExecutionContext)`) for acquiring a
  driver session on any platform/run-mode combination, registered against the
  Phase 1 `SessionFactoryRegistry`. All six factories are thin delegating
  wrappers: the web factories delegate to the existing, unmodified
  `WebDriverFactory.getWebDriver(...)`; the mobile factories delegate to the
  existing, unmodified `MobileExecutionStrategyFactory.forTarget(...)`. New
  capability: `WebBrowserStackSessionFactory` gives web tests first-class
  BrowserStack Automate support (previously mobile-only), guarded by the new
  `BrowserStackSupport.verifyActive()` fail-fast check (mirrors the existing
  mobile javaagent verification). No existing class is wired to
  `DriverManager` yet and no existing behavior changes -- purely additive.
  17 new unit tests.
- `execution.Platform` / `execution.RunMode` / `execution.ExecutionContext` /
  `execution.SessionFactory` / `execution.SessionFactoryRegistry` — new,
  platform-neutral execution model (Phase 1 of
  `docs/proposals/unified-sdk-architect-review.md`). Generalizes the mobile-only
  `ExecutionTarget`/`MobileExecutionStrategy`/`MobileExecutionStrategyFactory`/
  `MobileSessionRequest` pattern so it can eventually cover web (BrowserStack
  Automate, not just mobile App Automate) as well as mobile (Android/iOS, local
  and BrowserStack). Purely additive: no existing class is wired to these new
  types yet, and no existing behavior changes. 18 new unit tests
  (`RunModeTest`, `ExecutionContextTest`, `SessionFactoryRegistryTest`).
- `AccessibilityFinding` — normalized, engine-agnostic accessibility finding model
  (ruleId, WCAG criterion/level, severity, confidence, affected element
  selector/HTML, remediation guidance, page URL, test name, timestamp, screenshot
  path, engine/version). Maps both axe-core `Rule` results (Layer 1, including a
  new `manual_review` confidence bucket for axe's `incomplete` rules) and custom
  `AccessibilityChecker.InteractionIssue` results (Interaction/WCAG 2.2/Structural/
  Motion layers) into one shared shape. Purely additive -- existing
  `A11yReporter`/JSON/Excel output is unchanged. Accumulated findings are
  available via new `AccessibilityChecker.getFindings()` / `resetFindings()`.
  First step of the accessibility architecture roadmap in
  `docs/proposals/accessibility-strategy.md` (see REQUIRED item 1).
- `docs/proposals/accessibility-strategy.md` — vendor research (Deque/axe,
  Level Access, Siteimprove, Microsoft Accessibility Insights, WebAIM WAVE, IBM
  Equal Access, Pa11y, TPGi ARC, AudioEye) and proposed architecture for
  incrementally improving our accessibility implementation without adopting a
  vendor replacement.
- `AccessibilityEngine` — pluggable engine interface (`scan()`, `getEngineName()`,
  `getEngineVersion()`) so the public accessibility API is not coupled to axe-core.
  `AxeCoreEngine` (wraps `AccessibilityChecker`) and
  `com.test.automation.sdk.mobile.accessibility.NativeMobileEngine` (wraps
  `NativeAccessibilityChecker`) are the two shipped implementations — both
  additive facades, no change to either underlying checker's static API.
  Implements REQUIRED item 2 of the accessibility architecture roadmap.
- `AccessibilityExcelReporter`'s "Violations Detail" sheet now includes **WCAG SC**
  and **Confidence** columns, sourced from new `wcagCriterion`/`confidence` fields
  added to the `*_a11y.json` / `*_interaction_*.json` scan artifacts (additive JSON
  keys; older artifacts without them render blank in the new columns).
  Implements REQUIRED item 3 of the accessibility architecture roadmap.

> **STATUS as of 2026-09-08 (resume here tomorrow):**
> - `[1.0.0]` below is prepared and committed (pom.xml bumped, README/CHANGELOG promoted,
>   451/451 tests passing) but **NOT YET DEPLOYED** -- `mvn deploy` failed with 401
>   Unauthorized because `~/.m2/settings.xml` had no `<server>` entry matching this repo's
>   `distributionManagement` id (`cross-platform-functional-test-automation-sdk`), only
>   entries for the old `functional-test-automation-sdk`/`azure-artifacts-sdk` ids. The
>   missing `<server>` entry has been added locally to `~/.m2/settings.xml` (not a repo
>   file, so nothing to commit there) -- deploy has not been re-attempted yet, by request.
> - Also fixed a `scripts/release.ps1` bug while investigating: Step 5's git commit never
>   staged `pom.xml` or the bundled `src/main/resources/README.md` mirror, so a version
>   bump and the README mirror sync could silently stay uncommitted after a "successful"
>   release run. Fixed in this repo's working tree; verify committed before next release.
> - Unified Web+Mobile SDK architecture roadmap
>   (`docs/proposals/unified-sdk-architect-review.md`) — all 5 phases are now
>   done: Phase 1 (execution model), Phase 2 (DriverManager + 6
>   SessionFactory implementations), Phase 3 (config.SdkConfig +
>   config.YamlConfigReader unification), Phase 4 (TestBase.driver
>   static-to-instance thread-safety fix, with matching Listener/WebEventListener
>   fixes), and Phase 5 (removed `mobile.uiActions/*` app-specific page
>   objects from the SDK, coordinated-migrated into
>   `mobile-functional-automation-consumer-template`'s own `uiActions`
>   package). `MobileTestBase` now `extends TestBase` (structural merge complete,
>   validated against a real local Appium 3.0.1 server + booted Android emulator,
>   see the `### Changed` entry above under `[Unreleased]`) -- this was the
>   deferred item called out separately during Phase 4.
> - Accessibility architecture roadmap (`docs/proposals/accessibility-strategy.md`)
>   REQUIRED items 1-3 are now done (`AccessibilityFinding` model, `AccessibilityEngine`
>   interface + `AxeCoreEngine`/`NativeMobileEngine`, and Excel WCAG SC/Confidence
>   columns). RECOMMENDED items (file-based baseline/regression comparison, evidence
>   collector for screenshot correlation, richer confidence gradient) are still open.
> - **Next step tomorrow:** re-run `mvn deploy` (credentials now fixed) to actually publish
>   `cross-platform-functional-test-automation-sdk:1.0.0` to Azure Artifacts, then begin
>   converting the Poletop project to depend on this new unified SDK (per explicit user
>   request -- neither consumer template has been touched yet; both still point at the
>   old, separate `functional-test-automation-sdk`/`mobile-functional-test-automation-sdk`
>   artifacts).

---

## [1.0.0] — 2026-09-08
<!-- Add entries here during development; move to a version heading on release -->

### Fixed
- **`InstructionExtractor`:** fixed two documentation-delivery gaps discovered during a
  full audit of root-vs-bundled documentation consistency for this cross-platform SDK:
  1. `TESTBASE-API.md` had no bundled `src/main/resources/TESTBASE-API.md` resource at
     all -- `extractResource("TESTBASE-API.md", "docs/sdk/TESTBASE-API.md")` was silently
     failing (stderr `WARNING: resource not found`, no exception, no file written) for
     every consumer project that ran the extractor since the merge that created this
     repo. Added the missing bundled copy.
  2. `mobile-locator-strategy.instructions.md` existed as a bundled resource but was
     never in `INSTRUCTION_FILES`, so mobile consumer projects never received it via
     `mvn exec:java ... InstructionExtractor` -- added it to the extraction list.
  Also added `docs/sdk/MOBILE-USER-GUIDE.md` and `docs/sdk/MOBILE-TESTBASE-API.md`
  extraction (new bundled resources, mirroring the existing `SDK-USER-GUIDE.md`/
  `TESTBASE-API.md` treatment) so mobile consumers get the same locally-available
  reference docs web consumers already had. 4 new tests
  (`InstructionExtractorTest`); 387 total passing (was 383).
- **Root vs. bundled documentation drift (full audit + resync):** `.github/copilot-instructions.md`
  had been left as stale, Poletop-project-specific content since the web+mobile merge
  commit while the correct, generic, SDK-authored template already existed at
  `src/main/resources/sdk-instructions/copilot-instructions.md` -- root now matches the
  bundle. Also resynced (now byte-identical again): `CHANGELOG.md`, `README.md`
  (bundle was missing the "What the Crawler Can Do" section),
  `test-case-gap.instructions.md` (bundle had the pre-`ADO-`-prefix naming convention),
  and added the two bundled instruction/prompt files that existed on only one side of
  the repo since the merge: `mobile-locator-strategy.instructions.md` and
  `sdk-test-suite.instructions.md` into `src/main/resources/sdk-instructions/`, and
  `update-sdk-docs.prompt.md` into `src/main/resources/sdk-prompts/`, plus
  `start.prompt.md` into `.github/prompts/`.
- **`A11ySessionManager.shouldScan`:** added a mobile native-context guard. Native
  Android/iOS Appium screens (`NATIVE_APP` context) have no DOM, so axe-core's
  `JavascriptExecutor`-based injection previously threw once `accessibility.checking.enabled`
  was turned on for a mobile suite (confirmed via a manual validation harness against a live
  emulator). The scan is now skipped with a clear log reason for any driver in a native
  (non-`WEBVIEW_*`) Appium context; scanning against hybrid-app WebView contexts and plain
  desktop `WebDriver`s is unaffected. 4 new tests (`A11ySessionManagerMobileContextTest`);
  369 total passing (was 365).

### Added
- **`NativeAccessibilityChecker`/`NativeAccessibilityIssue`**
  (`com.test.automation.sdk.mobile.accessibility`) -- free, dependency-free accessibility
  audit for native (non-WebView) Android/iOS screens, derived entirely from Appium's
  `getPageSource()` XML (no axe-core, since native screens have no DOM to inject into).
  Checks: `missing-accessible-name`, `unlabeled-editable-field`,
  `duplicate-accessible-name`, `touch-target-too-small` (48x48dp Android / 44x44pt iOS,
  raw-pixel limitation documented). Works identically on Android and iOS from one
  implementation. Complements `AccessibilityChecker` (axe-core) for hybrid-app WebView
  content -- see `MOBILE-USER-GUIDE.md` Section 6, Accessibility Testing, for the full
  native-vs-WebView breakdown and why Google's Accessibility Test Framework (ATF) was
  evaluated and not chosen (Android-only, requires a live View/instrumentation tree,
  no iOS equivalent). 14 new tests (`NativeAccessibilityCheckerTest`); 383 total passing
  (was 369).
- `ElementCrawler.safeClick(WebDriver, WebElement)` -- scrolls into view, attempts a
  plain click, and retries once via a JS-executed click only on
  `ElementClickInterceptedException` (sticky footers, snackbars, CDK/Angular Material
  overlay backdrops). Now also runs a short, bounded, non-blocking
  `ExpectedConditions.elementToBeClickable` pre-check before clicking.
- `ElementCrawler.waitForNetworkIdle(WebDriver, Duration, long)` -- patches
  `fetch()`/`XMLHttpRequest` to detect when no network calls are in flight for a quiet
  period. Complements `waitForDomStable`; now called from `waitForPageReady()` for SPAs
  that update the DOM well after their own network calls settle. Bounded and
  non-throwing (mirrors Playwright's "networkidle" signal).
- `DataDrivenCrawler.setStateDeduplication(boolean)` -- opt-in state-fingerprint dedup
  (structural hash of interactive elements) that skips merging a full-scan snapshot when
  an identical, non-navigating state was already recorded. Inspired by the
  view-hierarchy-hashing technique used by mobile exploratory crawlers (Google Robo /
  Firebase Test Lab, Fastbot). Off by default -- no behavior change unless enabled.
- `ElementCrawler.crawlSubtree` now retries a tag's element scan (bounded, linear
  back-off, up to 3 attempts) if a transient `StaleElementReferenceException` is hit
  mid-scan (e.g. a live modal/overlay re-rendering), instead of silently dropping that
  tag's elements from the report.

---

## [2.0.0] — 2026-09-02

### Changed
- **BREAKING: Java target bumped from 1.8 to 20.**
  `maven.compiler.source`/`target` and the `maven-compiler-plugin` `<source>`/`<target>`
  raised from `1.8` to `20`. Done to align this SDK with the new
  `mobile-functional-test-automation-sdk` companion repo (which requires Java 11+ for
  the official `io.appium:java-client`), so both SDKs share a single target JDK across
  the ecosystem. Consumers still building/running on a Java 8 JVM will need to upgrade
  their JDK to consume this release. No language-level Java 8 syntax
  constraints (no `var`, no lambdas in `findElements`, etc. per this repo's own coding
  conventions) were relaxed as part of this change -- existing code style rules still
  apply; only the compiler target changed.
- All 342 existing unit tests re-run and passing under the Java 20 target before release.

---

## [1.9.1] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->

---

## [1.9.1] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->

### Fixed
- **`InstructionExtractor` no longer silently overwrites locally customized instruction files.** Previously, every extracted file (`.github/instructions/*.md`, `copilot-instructions.md`, prompts, config templates, `docs/sdk/*.md` mirrors) was unconditionally overwritten on every run -- clobbering any hand-edited or intentionally forked/git-tracked file (e.g. a consumer project's customized `failure-investigation.instructions.md`). Each extracted file now gets a hidden `<file>.sdkhash` sidecar recording the content hash at extraction time:
  - Unmodified files (hash still matches the sidecar) are safely refreshed to the newer SDK version, as before.
  - Locally customized files (hash no longer matches) are **never overwritten** -- the newer SDK version is instead written to `<file>.sdk-new` alongside it for manual review/merge.
  - `-Dsdk.forceExtract=true` discards local customizations and resets specific files back to the pristine SDK default.

---

## [1.9.0] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->

### Added
- **Map widget support** for the crawler/locator system: `ElementCrawler` now detects known map/canvas widget containers (Google Maps, Leaflet, Mapbox GL JS, MapLibre GL JS, OpenLayers, Bing Maps, plus a generic fallback for unrecognized canvas-based libraries) and tags them via new `ElementInfo.isMapWidget` / `ElementInfo.mapProvider` fields instead of generating brittle per-tile/per-pixel locators.
- New `com.test.automation.sdk.utility.MapWidgetHelper` class: provider detection (`detectProvider`), asynchronous map-ready waiting (`waitForMapReady`), address/place search (`searchAddress`, with Google Places `.pac-item` autocomplete support), accessible-label marker lookup (`findMarkerByLabel` via aria-label/alt/title), and a pixel-offset click fallback (`clickAtPixelOffset`) for canvas/WebGL-only markers with no DOM representation.
- `PageObjectGenerator` now emits a dedicated "Map / Canvas Widgets" section for detected map containers, with generated comments guiding consumers to `MapWidgetHelper` instead of unstable per-marker `@FindBy` locators.
- New `TestBase` convenience wrappers: `waitForMapReady(WebElement[, long])`, `searchMapAddress(WebElement, String)`, `findMapMarkerByLabel(WebElement, String)`.
- **Shadow DOM crawling** for Web Components / Lit / Stencil / Salesforce Lightning (LWC) and any framework using real DOM encapsulation: `ElementCrawler` now discovers interactive elements inside *open* shadow roots (recursing into nested shadow roots) and tags them via `ElementInfo.inShadowDom` / `shadowHostXpath` / `shadowRelativeCss`, since XPath cannot cross a shadow boundary. `PageObjectGenerator` emits a two-step accessor method (`host.getShadowRoot().findElement(By.cssSelector(...))`) instead of an unusable `@FindBy` for these elements. New `TestBase.findInShadowDom(By, String)` convenience wrapper.
- New generic `ElementCrawler.waitForDomStable(WebDriver, Duration, long)` -- a MutationObserver-based "adaptive" readiness wait that works across any JS framework (not just Angular), used as a supplemental signal in `waitForPageReady()` and to replace a blind settle-sleep in `MapWidgetHelper.waitForMapReady()`.

---

## [1.8.4] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->
### Changed
- Added an SDK version badge to `README.md`.
- Fixed stale `1.8.1` version references in the bundled `src/main/resources/README.md` mirror (was out of sync with the root README since a prior release).

---

## [1.8.3] — 2026-09-01

- **Docs**: Clarified `browser.chromeDriverPath` in `SDK-USER-GUIDE.md` §6.2 with a concrete step-by-step walkthrough for pinning a specific/offline ChromeDriver version, since by default `WebDriverFactory` uses WebDriverManager auto-download and no manual driver download is required. Motivated by consumer-template setup scripts previously presenting ChromeDriver download as a required step when it is actually optional.

---

## [1.8.2] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->

### Documentation
- Added a mandatory per-iteration validation gate to `sdk-development.instructions.md`
  (root + bundled mirror): every task touching the SDK and/or template repos must
  verify `mvn clean test` (SDK) and `mvn compile test-compile` (template) both pass,
  AND that `git status --short` is empty in both repos, before being marked complete
  -- not only when running the full release pipeline. Prompted by discovering that
  the 1.8.1 release left `pom.xml`, `TESTBASE-API.md`, and the bundled
  `src/main/resources/README.md` mirror uncommitted even though `mvn deploy` had
  already published the 1.8.1 artifact built from those working-tree changes.
- Committed the leftover 1.8.1 documentation changes (`TESTBASE-API.md` axe-core
  jar reference, `src/main/resources/README.md` version sync) that were
  accidentally left out of the 1.8.1 release commit.

---

## [1.8.1] — 2026-09-01
<!-- Add entries here during development; move to a version heading on release -->

---

## [1.8.1] — 2026-09-01

### Documentation
- Added SDK-USER-GUIDE.md §14.0 "Underlying engine" documenting the exact axe-core
  jar (`com.deque.html.axe-core:selenium:4.10.1`, Deque Systems, MPL-2.0) that
  powers accessibility Layer 1, with links to the official binding README, Deque's
  Selenium API reference, and the axe-core 4.10 rule catalogue.
- README.md AccessibilityChecker row and TESTBASE-API.md now name the exact
  underlying jar/version and cross-reference the new guide section.
- Fixed stale `1.5.0` version references in the bundled `src/main/resources/README.md`
  mirror (now matches the current release).

---

## [1.8.0] — 2026-08-31
<!-- Add entries here during development; move to a version heading on release -->

### Added
- **`.github/instructions/test-data-dependency.instructions.md`** (+ bundled copy) — new instruction file for grouping and sequencing data-dependent test classes: document the dependency chain, sequence classes in the suite XML (`parallel="none"`), call an idempotent `setupXToStateY(...)` precondition helper as the first action in every dependent test, and reset shared fixtures before the chain runs. Registered in `InstructionExtractor.INSTRUCTION_FILES` (10th resource). Cross-referenced from `test-creation.instructions.md`.
- New "10.1 Data-Dependent Test Chains" section in `SDK-USER-GUIDE.md` (+ mirror) summarizing the pattern.
- 2 `InstructionExtractorTest` value-source lists updated to cover the new 10th instruction resource.

---

## [1.7.1] — 2026-08-31
<!-- Add entries here during development; move to a version heading on release -->

### Fixed
- **`InstructionExtractor.extractResource`** — fixed a `NullPointerException` when extracting to a root-relative output path with no parent directory component (e.g. `azure-pipelines.yml.template`), which previously aborted extraction before `docs/sdk/CHANGELOG.md`, `SDK-USER-GUIDE.md`, and `TESTBASE-API.md` were written. Added a regression test (`extractResource_rootRelativeOutputPath_doesNotThrow`).

---

## [1.7.0] — 2026-08-31
<!-- Add entries here during development; move to a version heading on release -->

### Added
- **`.github/instructions/failure-investigation.instructions.md`** (+ bundled copy in `src/main/resources/sdk-instructions/`) — new instruction file mandating that every test failure be diagnosed from all THREE artifacts together (screenshot, DOM dump, AND log file), not just screenshot + DOM. Registered in `InstructionExtractor.INSTRUCTION_FILES` so it is automatically extracted into every consumer project alongside the other SDK-managed instructions.
- Cross-references to `failure-investigation.instructions.md` added in `test-fix.instructions.md` (Rule 1) and `copilot-instructions.md` (Section 6) so both files point to the same mandatory 3-artifact procedure.
- 2 `InstructionExtractorTest` value-source lists updated to cover the new 9th instruction resource.

---

## [1.6.0] — 2026-08-31
<!-- Add entries here during development; move to a version heading on release -->

### Added
- **`TestBase.selectFromCustomWidget(WebElement trigger, By panelLocator, By optionLocator, String value)`** — new method-level retry helper for selecting an option from custom (non-native) dropdown/listbox widgets (e.g. Angular Material select panels) where the native `Select` class does not apply. Implements a bounded select -> verify -> retry loop (3 attempts): opens the panel via `safeClick`, scopes the option search to the freshly-fetched panel element (never a bare `//` against the whole document), clicks the first matching option, and verifies the trigger's text via `verifyText` before returning. Retries the full open/search/click/verify sequence on `AssertionError` or `WebDriverException`.
- **`TestBase.findMatchingOption(List<WebElement> options, String value)`** (package-private) — extracted match logic used by `selectFromCustomWidget`, kept separate so it is unit-testable without a browser/driver.
- **`.github/instructions/page-object-creation.instructions.md`** — new "Select/Verify Retry Pattern for Custom Widgets" section documenting the required bounded-loop, scoped-locator, verify-before-success pattern for any custom widget interaction, with a reference template and a list of forbidden anti-patterns (recursive retry, instance-field counters, unscoped XPath, unexplained recovery clicks).
- 5 new unit tests in `TestBaseUnitTest` covering `findMatchingOption` (match found, no match, empty list, duplicate-text first-match) and presence of the `selectFromCustomWidget` signature.
- 5 new tests; 313 total passing.

---

## [1.5.1] — 2026-08-28

### Changed
- **`SDK-USER-GUIDE.md` Section 4.1b (`MavenAuthenticate@0`)** — expanded with:
  - A note clarifying `SYSTEM_ACCESSTOKEN` is not required for `MavenAuthenticate@0` itself (only for a custom `settings.xml` via `-s`).
  - A new "Authenticating external (non-ADO) Maven repositories" subsection covering the `mavenServiceConnections` input, including combining it with `artifactsFeeds`.
  - A consolidated "Cross-project feed access" subsection (removed a duplicate, shorter section) documenting the Reader role grant needed when the pipeline runs in a different ADO project than the one hosting the feed.
  - A new "`<id>` matching requirement" subsection clarifying that `artifactsFeeds` values must exactly match the `<repository><id>` in `pom.xml`.
- **`src/main/resources/sdk-defaults/azure-pipelines.yml.template`** — added inline comments pointing to the new `mavenServiceConnections` and cross-project access guidance.
- Synced `src/main/resources/SDK-USER-GUIDE.md` (bundled resource extracted by `InstructionExtractor`) with the above root `SDK-USER-GUIDE.md` changes.
- **Version bump** `1.5.0` -> `1.5.1`.

---


## [1.5.0] — 2026-08-27
<!-- Add entries here during development; move to a version heading on release -->

---

## [1.5.0] ? 2026-08-27

### Added
- **Built-in accessibility framework** integrating `AccessibilityChecker`, `A11ySessionManager`, `A11yTestNGListener`, `AllureA11yReporter`, and `A11yConfig` for a 5-layer WCAG engine covering axe-core, interaction, WCAG 2.2, structural, and motion checks.
- **`WebEventListener` per-element accessibility scanning** after click, sendKeys, clear, and submit actions, guarded by `AccessibilityChecker.isEnabled()` for zero overhead when disabled.
- **`TestBase` accessibility API**: `initAccessibility()`, `runAccessibilityScan(String pageName)`, `assertNoAccessibilityViolations(String pageName)`, and `getAccessibilityOutputDirectory()`.

### Changed
- **Unified `reporting:` configuration** in `sdk-config.yaml` now controls screenshots, DOM dumps, logs, crawler output, accessibility artifacts, and gap/blocker reports from one section.
- **Backward compatibility preserved** via aliases for `screenshots.outputDir`, `screenshots.domDumpDir`, `crawler.pageObject.reportDir`, and `reporting.gapOutputDir`.
- **Version bump** `1.4.6` -> `1.5.0`.

### Dependencies
- Added `com.deque.html.axe-core:selenium:4.10.1`.
- Added `org.slf4j:slf4j-api:2.0.13`.
- Added `org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0`.

---

## [1.4.6] — 2026-08-25

### Changed
- **`WebDriverFactory` — chromedriver resolution rewritten**
  - **Default: WebDriverManager resolves the latest chromedriver** version compatible
    with the installed Chrome browser — no manual driver management needed
  - **Optional config override:** set `browser.chromeDriverPath` in `sdk-config.yaml`
    to point at a local chromedriver binary; if the file exists it is used directly
  - If `chromeDriverPath` is set but the file is missing, a warning is logged and
    WebDriverManager is used automatically — no test failure from a misconfigured path
  - Removed hardcoded `webDrivers/chromedriver.exe` fallback path (Windows-only, fragile)
  - Removed aggressive `clearDriverCache()` / `clearResolutionCache()` calls — WDM
    cache is now preserved for faster startup on subsequent runs
  - Removed unused `java.nio.file.Paths` import
- **`sdk-config.yaml.template`** — added `browser.chromeDriverPath` (empty by default)
  with inline documentation

---

## [1.4.5] — 2026-08-25

### Changed
- **`selenium-java`** upgraded `4.40.0` → `4.47.0` (latest stable)
- **`jackson-databind`** upgraded `2.9.9.3` → `2.17.2` — critical fix: Selenium 4.40+
  requires jackson 2.15+; old version caused runtime classpath conflicts
- **`guava`** upgraded `31.0.1-jre` → `33.4.8-jre` to align with Selenium 4.47 internals
- **`commons-io`** upgraded `2.6` → `2.16.1` (security + compatibility)
- **`gson`** upgraded `2.8.2` → `2.11.0`
- **`org.json`** upgraded `20180813` → `20240303`
- `webdrivermanager` `6.3.4` — already latest, no change

---

## [1.4.4] — 2026-08-25

### Added
- **RCA-before-fix core principle** in `test-fix` skill — ordered evidence collection
  table (screenshot → DOM → surefire → ADO → git → source) with 3-question RCA gate
- **Rule 1a upgraded** — screenshot + DOM review is mandatory first action before any
  code change or crawler run
- **Rule 1b — Artifact-Driven Fast Path** — lookup table mapping screenshot/DOM findings
  directly to fix type and single direct action; eliminates unnecessary crawler runs
- **Step 0 in page-object-creation skill** — check failure artifacts before invoking
  crawler; decision table maps each finding to the correct action
- **SDK-USER-GUIDE §13.2 — Failure RCA Workflow** — full documentation of the RCA
  process, evidence sources, 3-question gate, and artifact-driven fast-path table

---

## [1.4.3] — 2026-08-24

### Added
- **`DataDrivenCrawler` — full-scan mode (default `true`)** — the crawler now performs a
  complete page scan at every test-case step, regardless of whether the MutationObserver
  detected a DOM mutation. Previously, only DOM-diff steps triggered a crawl, so elements
  already present on the page were missed if they appeared before a mutation. With full-scan
  mode, one `crawlTestCase()` run following the test case flow inventories every interactive
  element on every page state visited — no separate per-page runs needed.
  - `DataDrivenCrawler.setDiffOnlyMode(boolean)` — opt-in to the previous diff-only behavior
    for shallow forms where crawl speed is a concern.
  - Page-state tagging — each element is tagged with the URL route segment where it was first
    discovered (e.g. `dashboard`, `reservation/12345`, `construction`). Elements seen across
    multiple page states are tagged `Multiple pages: A, B`.
  - `ElementInfo.pageState` — new field populated by `DataDrivenCrawler`; shown in
    `toString()`, `PageObjectGenerator` comments, and the crawler report.
  - `DataDrivenCrawler.safeGetUrl()` — null-safe URL accessor used for page-state tracking.
  - `DataDrivenCrawler.pageStateLabel(String url)` — extracts human-readable route label from
    both Angular hash-routed (`#/route`) and standard URLs.
  `WebDriverWait` until `driver.getCurrentUrl()` contains the given substring. Use after
  `loginAsPoletopUser()` or any SAML/SSO flow to confirm the redirect has completed before
  proceeding with navigation. Throws `TimeoutException` if the timeout elapses.
- **`TestBase.scanReservationsByButtonTitle(String baseUrl, List<String> reservationIds, String buttonTitle, int renderWaitMs)`** —
  navigates to each reservation ID in sequence and returns those where the details page
  contains an action button with the given `title=` attribute. Uses `FluentWait` +
  `driver.findElements()` (no `Thread.sleep`, no fragile `getPageSource()` text search).
  Requires the driver to already be logged in.

### Changed
- **Blocker & gap report file naming** — report filenames now start with the ADO test
  case number so files sort naturally by ID in the file system.
  - `ADO-<ID>-blocker-report.md` → `<ID>-blocker-report.md` (e.g. `233504-blocker-report.md`)
  - `ADO-<ID>-gap-report.md` → `<ID>-gap-report.md` (e.g. `12345-gap-report.md`)
  - Class-based names (`<ClassName>-blocker-report.md`) are unchanged.
- **SDK-USER-GUIDE.md section 13.1** — updated naming table, Java code examples, and
  directory tree; added full blocker report format so users know what to expect.
- **Copilot instructions** (`test-case-gap.instructions.md`,
  `formal-testcase-to-script.instructions.md`) — updated to reflect new naming convention.
- **`PoletopLoginPage.loginAsPoletopUser()`** (consumer) — now blocks until the SAML
  handshake completes (browser exits `accounts*.nyc.gov`) before returning, eliminating
  the hang-on-login race condition in investigator methods.

---

## [1.4.0] — 2026-08-20

### Changed
- Released v1.4.0

## [1.4.0] - 2026-08-20
### Added
- **`TestBase.selectByValueAngular(WebElement, String)`** -- sets a native select value via
  JS and dispatches `input` + `change` events with `bubbles:true` so Angular ngModel/
  [(ngModel)] bindings detect the change. Use instead of `selectByValue()` when form Save
  is silently blocked after a plain Select.selectByValue() call.
- **`TestBase.clearAndTypeAngular(WebElement, String)`** -- types a value, dispatches
  Angular input/change events, then sends Keys.TAB to trigger blur validation. Use instead
  of `clearAndType()` when Angular required-field validators stay active after sendKeys().
- **`TestBase.waitForNavigationOrElement(String urlBefore, By landmark, int timeout)`** --
  composite FluentWait that resolves as soon as the URL changes OR a landmark element
  appears. Replaces fragile Thread.sleep() after clicks in Angular SPAs.
- **`TestBase.waitForModalOrUrlChange(String urlBefore, int timeout)`** -- polls for
  Angular/CDK modal anchors (mat-dialog-container, role=dialog, cdk-overlay-pane) or URL
  change after a click that opens a dialog overlay.
- **`ElementCrawler.crawlSubtree(WebElement root)`** -- crawls only the element subtree
  inside a modal/overlay root; used by generateFromCurrentPageWithModalCheck().
- **`PageObjectGenerator.generateFromCurrentPageWithModalCheck(String className)`** --
  detects open Angular modals before crawling; generates a separate className_Modal.java
  page object for the modal subtree alongside the background page object.

### Changed
- **`ElementCrawler.buildAllXPaths()`** -- `@value` strategy now explicitly covers icon-only
  buttons (e.g. Angular stage-action buttons with no visible text); comment clarified.

### Tests
- 300/300 passing (all existing tests pass; new methods covered by manual validation
  against Poletop ADO-233505 scenario).

---

## [1.3.10] — 2026-08-20

### Changed
- Released v1.3.10

## [1.3.10] - 2026-08-20
### Added
- **`TestBase.implicitWait(int sec)`** -- polls `document.readyState == "complete"` via
  FluentWait every 500ms for up to `sec` seconds; reliable alternative to
  `driver.manage().timeouts().implicitlyWait()` which behaves inconsistently across
  browser/driver versions. No Thread.sleep, no global driver state mutation.

### Fixed
- **`TestBaseUnitTest.saveDomDump_writesHtmlFile`** -- test now wipes the screenshots
  directory before each run; previously accumulated `_DOM.html` files from repeated
  `mvn test` invocations caused the file-count assertion to fail.

### Documentation
- **`test-fix.instructions.md`** -- added Rule 1a: mandatory screenshot and DOM dump
  review before any code change; lookup table mapping visual evidence to failure type.
- **`fix-failed-test.prompt.md`** -- added Step 1a: screenshot/DOM dump review with
  view tool instruction and same lookup table.
- **`formal-testcase-to-script.instructions.md`** -- added negative scenario derivation
  guide (optional, user-requested only; one scenario at a time with approval).
- **`sdk-development.instructions.md`** -- mandated maven-repository push to BOTH
  trunk AND master after every release; added verification commands.
- **`SDK-USER-GUIDE.md`** -- version header updated to 1.3.9 (was stale at 1.2.5).
- **`release.ps1`** -- auto-updates SDK-USER-GUIDE.md header on every release.

## [1.3.9] - 2026-08-19
### Fixed
- `saveDomDump()`: uses JavascriptExecutor outerHTML as primary capture (live rendered DOM),
  falls back to driver.getPageSource() if JS fails
- DOM dump co-located with screenshot in same folder -- filename: testCaseName_timestamp_DOM.html
- HTML header comments in dump: test name, URL at failure, timestamp
- Reporter.log clickable link to DOM file in TestNG HTML report

### Added
- **`TestBase` / `Listener`** -- data-driven tests can now publish row-specific TestNG XML names
  via `setCurrentTestCaseName()` and listener-based runtime renaming to `methodName.testCaseName`.
- **`TestBase`** -- `saveDomDump(driver, testName)` captures a full DOM snapshot on failure:
  uses `JavascriptExecutor.outerHTML` (live rendered DOM) with `getPageSource()` fallback;
  writes HTML to the same `test-output/screenshots/` folder as the screenshot, paired by
  filename; includes HTML header comments (test name, URL, timestamp) and a clickable
  `Reporter.log` link in the TestNG HTML report.

### Changed
- **`TestBase`** -- failure screenshot filenames now prefer the current data-driven test case name
  so rows no longer overwrite each other.
- **Tests:** added regression coverage for test case naming, file-name sanitizing, DOM dump
  capture, and resource/template updates; total passing count validated by `mvn test`.

---

## [1.3.7] -- 2026-08-18
### Fixed
- Replaced all triple-encoded mojibake characters in Java comments/Javadoc with
  plain ASCII equivalents (using ftfy for multi-layer encoding repair).
  All box-drawing chars become `-`, arrows become `->`, em-dashes become `--`.
  Java string literals are unchanged.

---

---

## [1.3.6] -- 2026-08-18
### Fixed
- Replaced all non-ASCII / Unicode characters (box-drawing, arrows, dashes, smart quotes,
  emoji) with plain ASCII equivalents in all Java comments, Javadoc, Markdown docs,
  and instruction files. String literals in Java source are unchanged.

---

## [1.3.5] -- 2026-08-18

### Fixed
- **`PageObjectGenerator`** -- elements with no unique locator are no longer generated
  as bare `public WebElement` fields (which caused PageFactory to silently use the
  wrong default strategy). They are now skipped and listed in a clearly marked
  `// UNRESOLVED ELEMENTS` comment block at the end of the class so the developer
  knows exactly which elements need a manual locator in DevTools.

---

## [1.3.4] -- 2026-08-18

### Added
- **`ElementInfo.buildRedundantXPath()`** -- generates a resilient union XPath combining
  all `UNIQUE [x]` non-fragile strategies (up to 4 branches) joined with `|`; if the
  primary locator breaks after a UI change, Selenium automatically falls through to the
  next stable branch (e.g. `@id` -> `@name` -> label-based XPath)
- **`ElementInfo.hasRedundantLocators()`** -- returns true when more than one stable
  unique strategy exists, used to decide whether to emit a union `@FindBy`
- **`PageObjectGenerator`** -- `@FindBy` now uses `buildRedundantXPath()` instead of the
  single primary XPath; fields with multiple stable strategies show the
  `// [*] RESILIENT UNION` marker and a multi-branch XPath annotation

---

## [1.3.3] -- 2026-08-18

### Added
- **`DataDrivenCrawler`** -- multi-pass DOM-diff crawler for dynamic forms (MS Dynamics,
  Salesforce, ServiceNow, Angular reactive forms); `crawlTestCase(url, name, steps)` follows
  a complete test case flow step-by-step; after each step a `MutationObserver` detects DOM
  changes, re-crawls the page, diffs the snapshot against the master element set, and tags
  newly discovered elements with the step that revealed them
- **`CrawlerStep`** -- single interaction step with dual locator modes:
  raw XPath (`select`, `type`, `click`) **or** semantic (`selectByLabel`, `typeByPlaceholder`,
  `typeByFormControlName`, `clickByText`, `clickByAriaLabel`, `selectByAriaLabel`,
  `selectByFormControlName`, `clickByLabel`); `.describe()` attaches a test case step label
  used in generated Page Object comments
- **`CrawlerScenario`** -- named step sequence with `fromTestCase(name, steps...)` static
  builder that auto-enables per-step DOM snapshots; supports `resetPageBeforeRun()` for
  independent scenario paths
- **`ElementSearchEngine`** -- live DOM semantic element resolver; turns human-readable
  hints (label text, placeholder, aria-label, visible text, `@formcontrolname`) into
  `WebElement` using the same priority ladder as `ElementCrawler`; used by `DataDrivenCrawler`
  to execute `CrawlerStep`s without requiring pre-known XPaths
- **`ElementInfo.scenarioTag`** + **`ElementInfo.stepTag`** -- new fields set by
  `DataDrivenCrawler`; `stepTag` = step that first revealed the element;
  `scenarioTag` = scenario name(s) or "All scenarios"
- **`PageObjectGenerator.generateFromElements()`** -- new method accepting a pre-crawled
  `List<ElementInfo>` from `DataDrivenCrawler`; emits step/scenario origin comments
  in `@FindBy` field blocks: `// [Visible after: Step 3: Select Category = Noise]`
- **Safe overwrite protection** -- `PageObjectGenerator` now detects when a file already
  exists and writes to `ClassName_Crawled.java` instead of overwriting hand-crafted files
- **`release.ps1`** -- doc automation added: README.md version references and CHANGELOG.md
  `[Unreleased]` promotion now happen automatically on every deploy

### Changed
- **`release.ps1`** -- expanded from 2-step (test -> deploy) to 4-step pipeline:
  test -> update README -> promote CHANGELOG -> git commit docs -> deploy

---

## [1.3.2] -- 2026-08-14

### Added
- **`start.prompt.md`** -- new interactive workflow launcher; presents a 6-option menu,
  collects all required parameters through guided questions, then routes to the correct
  workflow (`#create-test`, `#fix-failed-test`, `#fix-broken-locator`, `#ado-sync-test`,
  `#report-test-gap`); experienced users can skip questions by providing params upfront
- **`copilot-instructions.md`** -- `## Getting Started -- Type #start` section added at top;
  `#start` added as first entry in the prompts table
- **`InstructionExtractor`** -- `start.prompt.md` added to `PROMPT_FILES`; now extracts 7 prompts
- **Tests:** 1 new test; 296 total passing

## [1.3.1] -- 2026-08-14

### Changed
- **`InstructionExtractor`** -- removed `sdk-migration.instructions.md` from consumer extraction list;
  only test-script-development files are now delivered to consumer projects:
  `test-creation`, `page-object-creation`, `locator-strategy`, `formal-testcase-to-script`,
  `test-fix`, `test-case-gap` + all 6 test prompts
- **`sdk-instructions/copilot-instructions.md`** -- rewritten as a generic, project-agnostic
  bootstrap; removed all Poletop-specific package names and paths; consumers update the
  `## Project Identity` section for their own project
- **`sdk-development.instructions.md`** rule added: CHANGELOG.md and README.md must be
  updated on every SDK change before `mvn deploy`

### Fixed
- **Documentation encoding** -- `README.md`, `SDK-USER-GUIDE.md`, `TESTBASE-API.md`,
  `CHANGELOG.md`, `SDK-PUBLISHING.md` converted from Windows-1252 (cp1252) to clean UTF-8;
  all mojibake sequences (`???*`, `??"`, `????????`) replaced with correct Unicode characters

---

## [1.3.0] -- 2026-08-13

### Added -- `AbstractLocatorInvestigator` -- zero-duplication crawler base class

New class `com.test.automation.sdk.tools.locator.AbstractLocatorInvestigator` eliminates
all infrastructure duplication across consumer projects. Previously every project
copied the full crawler scaffolding (fail-fast login, role blacklist, nav helpers,
driver rebind, crawl summary). Now the SDK owns all of it.

**Consumer projects override exactly 3 methods:**

| Method | What to implement |
|---|---|
| `performLogin(email, password)` | Your app's login form interaction |
| `isSessionAlive()` | XPath check for a reliable post-login element |
| `defineCrawlSteps()` | List of pages to crawl in order |

**Optional overrides:**

| Method | Default |
|---|---|
| `registerRoles()` | Registers "default" role from `-Dinv.email`/`-Dinv.password` |
| `getPostLoginLandmark()` | Generic post-login XPath -- override with app-specific element |

**Infrastructure owned by the SDK (no longer in consumer projects):**
- `@BeforeClass setUp()` -- driver init, generator/crawler init, role registration
- `@AfterClass afterClass()` -- crawl summary log + `closeBrowser()`
- `@Test runFullCrawl()` -- driver rebind + `defineCrawlSteps()` call
- `loginAs(role)` -- fail-fast, blacklist, session reuse, single-attempt login, logout on role switch
- `crawlPage(pageKey, pageName)` -- `shouldSkip` + `generateFromCurrentPage` + crawled/skipped tracking
- `openNavDropdownAndClick(toggleId, label)` -- dropdown open + `WebDriverWait` for ul visibility + locator logging
- `clickStep(locator, description)` -- click + `crawler.waitForPageReady()`
- `logNavStructure(navContainerXpath)` -- logs nav item ids and text for discovery
- `shouldSkip(pageKeys...)` -- `-Dinv.page` filter logic
- `registerRole(role, email, password)` -- role credential registry

**No `Thread.sleep()` anywhere** -- all waits use `crawler.waitForPageReady()` or explicit `WebDriverWait`.

---

## [1.2.9] -- 2026-08-14

### Added -- Crawler: complete strategy coverage for all element patterns

**New XPath strategies in `buildAllXPaths()`** (in priority order):

| Strategy | Attribute | Use case |
|---|---|---|
| BY VALUE | `@value` | `<select>` options, pre-filled inputs (skips password/hidden) |
| BY SRC | `@src` | Image buttons -- `<input type=image>`, `<img>` inside `<button>` |
| BY ALT | `@alt` | Image elements used as buttons |
| BY DATA-VALUE | `@data-value` | Angular chips, autocomplete options, custom dropdowns |
| BY FORM-ROW LABEL | sibling `<label>/<th>/<legend>` | `//div[label[.='Email']]//input` -- covers label-without-for patterns |

**New collection strategies:**

- **`collectImageButtons()`** -- discovers `input[type=image]` and `button//img | a//img`;
  flags with `isImageButton=true`; builds locators from `@alt`, `@src`, `@title`

- **`collectTableColumns()`** -- discovers every `<th>` with visible text; emits:
  - Header locator: `//th[normalize-space(.)='Status']`
  - Column cells XPath: `//table//tr/td[count(//th[.='Status']/preceding-sibling::th)+1]`
  - Dynamic row+cell template: replace `{ROW_KEY}` with a row identifier value
  - Covers manual tester assertions like "verify Status column for row with Pole ID 'ABC'"

- **`buildFormRowLabelXpath()`** -- JS walks up to 5 DOM levels to find a sibling
  `<label>`, `<th>`, or `<legend>`; tries container patterns (`div`, `td`, `li`,
  `section`, `tr`) and returns first UNIQUE [x] match

**New `ElementInfo` fields**: `alt`, `src`, `dataValue`, `isImageButton`,
`isTableHeader`, `tableColumnText`, `tableColumnCellsXpath`, `tableColumnDynamicTemplate`

**Updated `toString()`**: includes `IMAGE-BUTTON`, `TABLE-HEADER=<text>`,
`label='<text>'` markers

**10 new unit tests** (295 total, all passing)

**Complete strategy pipeline** -- all 28 locator strategies now implemented:
standalone (17), contextual (4), collection (7). The crawler can resolve any
element pattern encountered in real-world Angular/HTML applications.

---

## [1.2.8] -- 2026-08-14

### Added -- Crawler: label-following resolution engine

Manual testers describe elements by their visible label text -- "enter value in the
**Borough** field", "click the **Email** input". This release makes the crawler follow
labels to their target controls so generated page object field names and locators
reflect the human-readable label.

Three resolution mechanisms implemented in `collectLabelAssociations()`:

**A) `<label for="X">` -- explicit HTML association**
- Resolves label text from the `<label>` element
- Finds the associated control by `[@id='X']` across `input`, `textarea`, `select`, `mat-select`
- Falls back to proximity XPath `//label[...]/following-sibling::input[1]` when no `for` attr
- Sets `linkedInputXpath` on the label ElementInfo

**B) `aria-labelledby="Y"` -- ARIA pointer to label element**
- Finds every element with `@aria-labelledby`
- Resolves the text of the referenced element (`//*[@id='Y']`)
- Sets `labelText` and `isLabelledBy=true` on the control ElementInfo

**C) Angular Material `<mat-form-field>` proximity**
- Finds every `<mat-form-field>` container
- Reads the `<mat-label>` sibling text inside the container
- Resolves the child `input`, `mat-select`, or `textarea`
- Builds a label-following XPath:
  `//mat-form-field[.//mat-label[normalize-space(.)='Borough']]//mat-select`
- Sets `labelText`, `isMatLabelled=true`, `labelFollowingXpath` on the control

**New `ElementInfo` fields:**
- `labelText` -- human-readable label text resolved from any mechanism
- `labelFollowingXpath` -- stable XPath targeting element via its visible label
- `isLabel` -- true when the element is a `<label>` element
- `isLabelledBy` -- true when resolved via `aria-labelledby`
- `isMatLabelled` -- true when resolved via Angular Material `mat-label`

**6 new unit tests** (285 total, all passing)

---

## [1.2.7] -- 2026-08-14

### Added -- ElementCrawler coverage improvements

- **iframe/frame crawling** -- `crawlCurrentPage()` now enumerates all `<iframe>` and
  `<frame>` elements, switches into each same-origin frame, crawls its contents, and
  switches back to the default document. Cross-origin frames are silently skipped.
  Elements found inside frames are tagged with `inFrame=true` and `frameIndex=N` so
  `PageObjectGenerator` can emit `driver.switchTo().frame(N)` in action methods.

- **Extended Angular Material interactive tags** -- `INTERACTIVE_TAGS` now includes:
  `mat-slide-toggle`, `mat-datepicker`, `mat-autocomplete`, `mat-chip-list`,
  `mat-chip-input`, `mat-expansion-panel`, `mat-slider`, `mat-button-toggle`

- **Extended `data-*` attribute strategy** -- XPath strategy ladder now probes all
  common testing-specific data attributes in priority order: `data-testid`, `data-cy`,
  `data-qa`, `data-automation`, `data-id`, `data-test`

- **`title` attribute strategy** -- added to strategy ladder for icon buttons and
  elements that use title as their only stable attribute

- **`autocomplete` attribute strategy** -- added for semantic form fields
  (`autocomplete="email"`, `given-name`, etc.); `"on"` and `"off"` are excluded as
  they are not stable selectors

- **`<select>` option collection** -- `collectSelectOptions()` now discovers all
  `<option>` children under every `<select>`, builds scoped XPath
  `//select[@id='X']/option[normalize-space(.)='Value']`, and flags each with
  `isSelectOption=true`

- **File input detection** -- `input[type=file]` elements are flagged with
  `isFileInput=true` so generated page objects emit upload handlers instead of
  `clearAndType()`

- **`contenteditable` div collection** -- `collectContentEditable()` finds all
  `[contenteditable='true']` elements and flags them with `isContentEditable=true`
  for rich-text-aware action generation

- **Hidden element flagging** -- `input[type=hidden]` elements are included but
  flagged with `isHidden=true` to reduce noise in primary locator selection

- **New `ElementInfo` fields**: `title`, `autocomplete`, `inFrame`, `frameIndex`,
  `isHidden`, `isFileInput`, `isContentEditable`, `isSelectOption`

- **Configurable ancestor walk depth** -- `ANCESTOR_WALK_DEPTH` constant (12 levels,
  up from hardcoded 8) passed as JS argument to `resolveStableAncestor()`

- **15 new unit tests** in `ElementCrawlerTest` covering all new fields and constants
  (total: 279 tests, all passing)

---

## [1.2.6] -- 2026-08-14

### Fixed
- **`Mailinator.java` -- removed duplicate legacy class definition**: File contained two
  `public class Mailinator` blocks -- the original v1 implementation (using `?token=` query
  param and `https://mailinator.com/api/v2`) was left as dead code below the new v2 class.
  Removed the legacy block entirely. Only the v2 implementation (Authorization header,
  `https://api.mailinator.com/api/v2`) is now present.
- **`deleteAllEmails` URL bug**: `MAILINATOR_DELETE_ALL_URL` was targeting
  `DELETE /api/v2/domains/{domain}/inboxes` (domain-level delete -- wipes ALL messages).
  Corrected to `DELETE /api/v2/domains/{domain}/inboxes/{inbox}` per the official
  Mailinator OpenAPI spec (inbox-level delete only).
- **Removed dead `boolean privateDomain` overloads** from `Mailinator.java`: Four
  overloaded methods accepted the `privateDomain` boolean parameter but ignored it
  entirely. Per the v2 API spec, private vs. public routing is determined by the domain
  name in the URL path, not a separate flag. The overloads are removed; callers use
  the canonical `(apikey, domain, inbox, ...)` signatures.
- **Removed dead `isPrivateDomain()` from `MailinatorEmailReader`**: Read
  `api.mailinator.privateDomain` from yaml but the return value was never used anywhere.
  Removed to eliminate misleading dead code.

### Notes
- Official Mailinator API v2 spec confirms both `Authorization` header and `?token=`
  query param are supported for authentication; the SDK correctly uses the header form.
- To target a private domain, set `api.mailinator.domain` in `sdk-config.yaml` to your
  private domain name (e.g., `yourcompany.com`). No boolean flag is needed.

---

## [1.2.4] -- 2026-08-13

### Added
- **`README.md` as live status page** -- mandatory update rule added to all processing
  flows (create-test, modify-test, fix-failed-test, fix-broken-locator, SDK publish):
  - Consumer project `README.md` must have a `## Test Coverage` table with one row
    per test class showing status (`? Automated`, `[!]? Partial`, `? Blocked`, `?? Fixing`, `? Retired`)
    and last-updated date -- updated on every create/modify/fix/block action
  - SDK `README.md` must be updated on every version bump (component table,
    config snippet, prompts table, version badge)
  - Every completion report now shows a diff-style summary of README.md changes
- **`copilot-instructions.md`** -- new Rule 7: README.md Is the Live Project Status Page;
  defines `## Test Coverage` table format, status values, and update rules
- **`SDK-PUBLISHING.md`** -- Section 5 Version Bump now includes README.md update
  as mandatory step 3 before deploy

---

## [1.2.3] -- 2026-08-12

### Added
- **`CHANGELOG.md`** -- standalone changelog file in SDK root (this file);
  extracted to consumer projects as `docs/sdk/CHANGELOG.md` by `InstructionExtractor`
- **`InstructionExtractor`** -- now also copies `CHANGELOG.md` to `docs/sdk/CHANGELOG.md`
  in consumer projects; updated completion message to mention `docs/sdk/`
- **Prompts** -- `create-test`, `modify-test`, `fix-failed-test`, `fix-broken-locator`:
  mandatory `CHANGELOG.md` update step added to every consumer project flow
  (completion report box, step in workflow, output checklist)
- **`SDK-PUBLISHING.md`** -- Section 5 (Version Bump) now mandates changelog entry
  before deploy; Section 6 replaced inline version list with reference to `CHANGELOG.md`

### Changed
- Changelog content moved from `SDK-PUBLISHING.md` Section 6 into `CHANGELOG.md`

---

## [1.2.2] -- 2026-08-12

### Added
- **`GapReportWriter`** -- new utility class that writes `gap-report.md` and
  `blocker-report.md` files to a configurable output directory.
  Resolution order: `-Dsdk.gapOutputDir` system property -> `sdk-config.yaml
  reporting.gapOutputDir` -> default `docs/test-case-gaps/`
- **`YamlConfigReader`** -- new default key `reporting.gapOutputDir`
  (value: `docs/test-case-gaps/`)
- **`sdk-config.yaml.template`** -- new `reporting:` section with `gapOutputDir` key
  and inline comments
- **`test-case-gap.instructions.md`** -- directory resolution priority table;
  Gap vs Blocker decision guide; blocker naming convention (`<ClassName>-blocker-report.md`)
- **`SDK-USER-GUIDE.md`** -- `GapReportWriter` in component table; `reporting:` yaml
  block in Section 6.2; new Section 13.1 "Gap & Blocker Reports -- Configurable Output"
- **`sdk-migration.instructions.md`** -- `reporting:` section in Step 6 yaml snippet;
  two new checklist items
- **Prompts** -- `create-test`, `modify-test`, `fix-failed-test`, `fix-broken-locator`:
  mandatory `CHANGELOG.md` update step added to every flow
- **`SDK-PUBLISHING.md`** -- changelog update made a mandatory pre-deploy step

### Tests
- 14 new tests in `GapReportWriterTest` covering all three resolution tiers,
  trailing separator normalisation, file naming, content write, auto-directory creation
- **60 total unit tests passing**

---

## [1.2.1] -- 2026-08-11

### Added
- **`create-test.prompt.md`** -- autonomous mode consent block; Step 3 1-to-1
  step/assertion coverage rule (`assertTrue(true)` forbidden); Step 6 Blocker Output
  (writes to `docs/test-case-gaps/`); Step 7 mandatory Completion Report
- **`fix-failed-test.prompt.md`** -- Step 0 ADO Pre-Fix Change Check: fetches ADO test
  case, compares `lastModifiedDate` vs last git commit, routes to `#ado-sync-test` when
  ADO changed, retires test when ADO is `Closed`/`Inactive`; autonomous mode block;
  completion report includes ADO pre-check section
- **`modify-test.prompt.md`** -- autonomous mode consent; coverage rules; completion report
- **`test-creation.instructions.md`** -- Autonomous Mode Consent section; full step
  coverage rules; blocker file requirement; Step 8 Completion Report
- **`formal-testcase-to-script.instructions.md`** -- autonomous mode; 1-to-1 mapping rule;
  blocker output section
- **`test-fix.instructions.md`** -- Rule 0 ADO Pre-Fix Check with full decision table
- **`PageObjectGenerator`** -- project-agnostic output: `uiActionsPackage`, `outputSrc`,
  `outputReport` resolved at runtime via `-Dpog.*` system properties ->
  `sdk-config.yaml crawler.pageObject.*` -> built-in Poletop defaults (backward compat)
- **`sdk-config.yaml.template`** -- new `crawler:` section
- **`YamlConfigReader`** -- three new default keys for `crawler.pageObject.*`
- **`SDK-USER-GUIDE.md`** -- Sections 6.2 and 7 updated for project-agnostic crawler config

### Tests
- `YamlConfigReaderTest`: +7 tests for crawler defaults and YAML overrides
- `PageObjectGeneratorConfigTest` (new): 15 tests covering all three resolution tiers
- **46 total unit tests passing**

---

## [1.1.0] -- 2026-07-XX

### Added
- **Mailinator -- TestBase wrappers**: `getEmailUrl`, `getEmailText`,
  `getConfirmationEmailUrl`, `getDeactivationEmailUrl`, `deleteEmailById` --
  email flows accessible from TestBase without importing any Mailinator class
- **Mailinator -- configurable templates**: `mailinator-email-templates.yaml` with
  `subjectContains`, `urlPathPatterns`, `excludePatterns`, `textPattern`, `masks`
- **Mailinator -- value masks**: `EmailValueMask` -- `trim`, `uppercase`, `lowercase`,
  `substring`, `dateFormat`, `replaceAll`
- **Mailinator -- smart cleanup**: auto-delete removes only the single processed message
  (not the entire inbox) -- safe for shared domains across projects
- **Mailinator -- retry polling**: configurable initial wait, poll interval, and timeout
  via `sdk-config.yaml`
- **`scrollIntoView(WebElement)`**: new public method -- viewport-aware scroll using
  Selenium Actions wheel input with JS fallback; returns element for fluent chaining

### Changed
- **TestBase visibility**: 15 internal methods changed from `public` to `protected`
  to reduce API surface clutter

### Documentation
- `TESTBASE-API.md` section 23 updated
- `SDK-USER-GUIDE.md` sections 9.1 and 6.2 updated

---

## [1.0.0] -- Initial Release

- Initial release: TestBase, WebDriverFactory, SdkConfig, ElementCrawler,
  PageObjectGenerator, Excel_Reader, Listener, RetryListener, WebEventListener,
  YamlConfigReader

