# Changelog -- Framework Automation SDK

All notable changes to `com.test.automation:functional-test-automation-sdk` are documented here.

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
>   449/449 tests passing) but **NOT YET DEPLOYED** -- `mvn deploy` failed with 401
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
>   (`docs/proposals/unified-sdk-architect-review.md`) — Phase 1 (execution
>   model), Phase 2 (DriverManager + 6 SessionFactory implementations), and
>   Phase 3 (config.SdkConfig + config.YamlConfigReader unification) are
>   done. Phases 4-5 (TestBase lifecycle unification, uiActions migration +
>   compatibility cleanup) are still open.
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

New class `com.test.automation.sdk.tools.AbstractLocatorInvestigator` eliminates
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


