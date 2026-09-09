# Unified SDK Structure Cleanup — Architecture Assessment

Status: **Assessment only — no implementation yet.** Per the Senior Architect
review request, this document must be reviewed and a phase approved before
any code changes begin.

Repo: `cross-platform-functional-test-automation-sdk` @ `a108c36` (post
`SdkConfig`/`MobileSdkConfig` shim removal, 441/441 tests passing).

---

## A. Current package structure

```
com.test.automation.sdk
├── accessibility/            (web: engine, checker, axe-core, session mgr, TestNG listener)
│   ├── config/                A11yConfig
│   └── report/                A11yReporter, AllureA11yReporter, CompositeReporter, Slf4jReporter, NoOpReporter
├── config/                    SdkConfig (paths), YamlConfigReader (facade)
├── driver/                    DriverManager, BrowserStackSupport
│   ├── mobile/                 Android/iOS Local + BrowserStack SessionFactory
│   └── web/                    Web Local + BrowserStack SessionFactory
├── execution/                  ExecutionContext, Platform, RunMode, SessionFactory, SessionFactoryRegistry
├── listener/                   Listener, WebEventListener, Retry, RetryListener
├── mobile/
│   ├── accessibility/          NativeAccessibilityChecker, NativeAccessibilityIssue, NativeMobileEngine
│   ├── actions/                MobileActions
│   ├── config/                 MobileConfigReader
│   ├── crawler/                MobileElementCrawler, MobileDataDrivenCrawler, MobilePageObjectGenerator, + models
│   ├── driver/                 MobileDriverFactory
│   ├── execution/               MobileExecutionStrategy(+Factory), ExecutionTarget, Local/BrowserStack strategies
│   └── testbase/               MobileTestBase (extends TestBase)
├── testbase/                   TestBase, WebDriverFactory
├── tools/                       AbstractLocatorInvestigator
└── utility/
    ├── mailinator/              Email, EmailTemplate, Mailinator, readers, masks
    ├── reports/                 ExtentManager, ExtentTestManager
    └── (root)                   ElementCrawler, DataDrivenCrawler, PageObjectGenerator, CrawlerStep/Scenario,
                                  ElementSearchEngine, CSVReporter/Utils, Excel_Reader, GapReportWriter,
                                  InstructionExtractor, MapWidgetHelper, PageContext, PropertiesReader,
                                  QueryExcelFile, UpdateExcellFile, YamlConfigReader (real impl)
```

Single-module Maven project (`com.test.automation:cross-platform-functional-test-automation-sdk:1.0.0`, jar). No submodules.

## B. Duplication report

| # | Area | Finding | Severity |
|---|---|---|---|
| 1 | **YAML config readers** | `config.YamlConfigReader` is a thin facade delegating every method to `utility.YamlConfigReader` (the real, tested singleton). Intentional per its own Javadoc (Phase 3 note), but leaves the "real" implementation living in the generic `utility` package instead of `config`, which is confusing and backwards vs. every other config class. | Medium — cosmetic/structural, not functional duplication |
| 2 | **Execution/session selection built twice** | `execution.SessionFactory` + `SessionFactoryRegistry` (shared, platform-agnostic factory registry pattern used by web+mobile driver factories) coexists with a **second, parallel** selection abstraction: `mobile.execution.MobileExecutionStrategy` + `MobileExecutionStrategyFactory` + `ExecutionTarget` (local vs. BrowserStack). Both solve "pick the right session-creation strategy for this run," for the mobile platform, via two different mechanisms. | **High** — real architectural duplication, most impactful finding |
| 3 | **Crawler/PageObjectGenerator pairs** | `utility.{ElementCrawler, DataDrivenCrawler, CrawlerStep, PageObjectGenerator}` vs `mobile.crawler.{MobileElementCrawler, MobileDataDrivenCrawler, MobileCrawlerStep, MobilePageObjectGenerator}` — legitimately separate implementations (DOM vs. native Appium XML), but share no common interface/base class today, so reporting, gap-writing, and step-model conventions have silently diverged (`CrawlerStep.Action` vs `MobileCrawlerStep.Action` are independent enums with no shared contract). | Medium — not wasted duplicate logic, but a missed shared-abstraction opportunity |
| 4 | **Accessibility engines** | `accessibility.AccessibilityEngine` interface is reused correctly by `mobile.accessibility.NativeMobileEngine` (good example of proper abstraction). `AccessibilityFinding` vs `NativeAccessibilityIssue` are separate result models that both represent "one accessibility problem" but aren't unified under one type, forcing `NativeMobileEngine` to do field-by-field translation. | Low |
| 5 | **`TestBase` / `MobileTestBase`** | Correct inheritance (`MobileTestBase extends TestBase`), no duplicate lifecycle logic. Already a good example from the Phase 4/5 merge. | None — no action needed |
| 6 | **Mailinator, reports, listener, Excel/CSV utilities** | No mobile mirrors exist; genuinely shared. | None |

## C. Configuration duplication report

| Config concern | Class(es) | Notes |
|---|---|---|
| Config directory / file path resolution | `config.SdkConfig` | Single source of truth (post shim removal this session). ✅ |
| YAML value reads | `utility.YamlConfigReader` (real) + `config.YamlConfigReader` (facade) | Facade adds no value beyond package placement; could be collapsed by moving the real implementation into `config` and deleting the facade, OR by keeping the facade but documenting it as permanent (current Javadoc suggests it's transitional). |
| Mobile-specific YAML (`android.*`, `ios.*`) | `mobile.config.MobileConfigReader` | Already correctly falls back to unified `sdk-config.yaml` via `SdkConfig`/`YamlConfigReader` — this is the right pattern, not duplication. |
| Accessibility config/defaults | `accessibility.config.A11yConfig` | Self-contained, no overlap. |
| `.properties` reads | `utility.PropertiesReader` | Distinct concern (Java properties, not YAML) — no overlap. |

**Net assessment:** configuration is in much better shape than session/execution handling (item B.2) — the only open question is whether to physically relocate the real `YamlConfigReader` implementation into `config/` to match `SdkConfig`'s location, or leave the facade in place permanently.

## D. Target package structure (proposed)

No change to top-level `web` vs `mobile` split — it is working well (Phase 4/5 already validated this on a real emulator). Two targeted moves only:

```
config/
  SdkConfig.java
  YamlConfigReader.java          ← real implementation moved here from utility/
                                     (utility.YamlConfigReader becomes a
                                     deprecated re-export, OR is deleted if
                                     no external consumer depends on it —
                                     needs the same consumer-grep check done
                                     for SdkConfig)

execution/
  SessionFactory.java
  SessionFactoryRegistry.java
  ExecutionContext.java / Platform.java / RunMode.java
  ExecutionTarget.java            ← moved from mobile.execution (platform-neutral concept: local vs BrowserStack applies to web too, currently web has no equivalent enum)
  # MobileExecutionStrategy* retired — logic folded into
  # AndroidLocalSessionFactory / AndroidBrowserStackSessionFactory /
  # IosLocalSessionFactory / IosBrowserStackSessionFactory choice already
  # made via SessionFactoryRegistry; MobileExecutionStrategyFactory's
  # local/BrowserStack branching becomes redundant once ExecutionTarget
  # is read directly by SessionFactoryRegistry's existing selection logic.
```

Everything else (`accessibility`, `mobile.accessibility`, `mobile.crawler`, `utility` crawler classes, `testbase`, `mobile.testbase`) stays where it is — the assessment does not support moving these; they're legitimately parallel, platform-specific implementations, and forcing a shared interface today (item B.3/B.4) is a "nice to have," not a duplication bug.

## E. Class migration map

| Class | Current package | Action | Target package |
|---|---|---|---|
| `utility.YamlConfigReader` | `utility` | Move (pending consumer grep) | `config` |
| `config.YamlConfigReader` (facade) | `config` | Delete after move (methods become the real impl in place) | — |
| `mobile.execution.ExecutionTarget` | `mobile.execution` | Move | `execution` |
| `mobile.execution.MobileExecutionStrategy` + `MobileExecutionStrategyFactory` + `Local/BrowserStackExecutionStrategy` + `MobileExecutionStrategySupport` | `mobile.execution` | Retire — fold branching into existing `SessionFactoryRegistry` + the 4 mobile `*SessionFactory` classes | — |
| `mobile.execution.MobileSessionRequest` | `mobile.execution` | Keep (still a useful mobile-specific value object) | unchanged |
| All others | — | No move | unchanged |

This is a deliberately **small** migration map — the inventory shows the SDK is already well-organized after Phases 1–5; the only high-severity finding is the duplicate execution-strategy-selection mechanism (B.2).

## F. Target configuration model

No changes needed beyond E's `YamlConfigReader` relocation — `SdkConfig` (paths) + `YamlConfigReader` (values) + `MobileConfigReader` (mobile-specific overlay) is already a clean 3-layer model with one unified `sdk-config.yaml` as the single file consumers edit.

## G. Target lifecycle (unchanged, confirmed correct)

```
TestBase (web lifecycle, WebDriverFactory)
  └── MobileTestBase (extends TestBase, overrides setup for Appium)
```
`SessionFactoryRegistry` picks the right `SessionFactory` (web-local, web-BrowserStack, android-local, android-BrowserStack, ios-local, ios-BrowserStack) — once `MobileExecutionStrategy*` is retired (E), this becomes the **single** place session/target selection happens, for both platforms.

## H. Backward-compatibility impact

- Moving `YamlConfigReader`: same consumer-check process used for `SdkConfig` (grep known consumer repos for `utility.YamlConfigReader` / `config.YamlConfigReader` direct imports) before deleting either class.
- Retiring `mobile.execution.MobileExecutionStrategy*`: these are internal-only (used by `MobileTestBase`/`MobileDriverFactory`), not expected to be imported by consumer test projects — needs the same grep confirmation as above before removal.
- No impact expected to `311-Automation-SDK` (not yet using mobile execution strategies) or `mobile-functional-automation-consumer-template`.

## I. Migration plan (scoped down from the original 8-phase proposal)

Given the actual duplication found is much smaller than assumed, recommend **2 phases**, not 8:

1. **Phase A — Execution/session consolidation** (the one High-severity item): retire `mobile.execution.MobileExecutionStrategy*`, move `ExecutionTarget` to `execution`, fold local/BrowserStack branching into `SessionFactoryRegistry`. Validate via `mvn test` + real Appium/emulator smoke run (same validation used for the Phase 4/5 merge).
2. **Phase B — YamlConfigReader relocation** (cosmetic/structural cleanup): move real implementation to `config`, delete facade, consumer-grep first. Lower priority — safe to defer or skip if not worth the churn.

No changes recommended for accessibility, crawler, or TestBase packages — they are correctly separated by platform already.

---
**Awaiting sign-off on Phase A / Phase B before any implementation begins.**
