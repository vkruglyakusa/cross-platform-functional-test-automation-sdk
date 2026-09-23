# Framework Automation SDK

![SDK](https://img.shields.io/badge/SDK-cross--platform--functional--test--automation--sdk:1.5.3-blue)

> **Reusable Selenium + Appium + RestAssured framework layer for OTI QA Automation**  
> Java 20+ * Maven * `com.test.automation:cross-platform-functional-test-automation-sdk:1.5.3`

This SDK is a single JAR that consumer automation projects depend on.
It provides one shared framework layer for:
- Web UI automation (Selenium + TestNG)
- Native and hybrid mobile automation (Appium for Android/iOS)
- Pure REST API automation (RestAssured, no browser or device session)

Consumer projects keep only their own page objects, test classes, API payloads,
and test data; the framework plumbing, reporting, crawler tooling, and Copilot
prompts stay in the SDK.

## Validated dependency baseline

| Component | Supported baseline |
|---|---|
| Java | 20 or newer |
| TestNG | 7.10.2 |
| Selenium | 4.44.0 |
| Appium Java Client | 10.1.1 |
| RestAssured | 5.5.0 |
| Allure TestNG / Commons / Attachments | 2.23.0 |
| Log4j2 | 2.20.0 |
| WebDriverManager | 6.3.4 |
| Apache POI | 3.17 |
| axe-core Selenium binding | 4.10.1 |
| AspectJ Weaver | 1.9.25 |
| BrowserStack Java SDK | Optional for consumers; declare it explicitly only when using BrowserStack |

LOCAL consumers should depend on the SDK alone and should not inherit
`browserstack-java-sdk` transitively. BrowserStack-enabled consumers must opt in
explicitly with their own `com.browserstack:browserstack-java-sdk` dependency,
`browserstack.yml`, credentials, and any Surefire `-javaagent` wiring they use.

---

## What's Inside

| Component | Class | Purpose |
|---|---|---|
| **TestBase** | `sdk.testbase.TestBase` | Base class with 60+ Selenium helpers (waits, clicks, scrolls, assertions) |
| **WebDriverFactory** | `sdk.testbase.WebDriverFactory` | Browser initialization -- Chrome, Firefox, Edge, headless, Grid |
| **SdkConfig** | `sdk.config.SdkConfig` | Config path resolution via system property or env variable |
| **ElementCrawler** | `sdk.tools.crawler.web.ElementCrawler` | Scans a live page and generates a `@FindBy`-annotated Page Object |
| **PageObjectGenerator** | `sdk.tools.pageobject.PageObjectGenerator` | Standalone runner for ElementCrawler; `generateFromElements()` for data-driven results |
| **DataDrivenCrawler** | `sdk.tools.crawler.web.DataDrivenCrawler` | Multi-pass DOM-diff crawler for dynamic forms; follows test case flow step-by-step |
| **CrawlerStep** | `sdk.tools.crawler.web.CrawlerStep` | Single interaction step with raw XPath or semantic locators (byLabel, byText, etc.) |
| **CrawlerScenario** | `sdk.tools.crawler.web.CrawlerScenario` | Named step sequence; `fromTestCase()` builder for ADO-driven crawling |
| **ElementSearchEngine** | `sdk.tools.crawler.web.ElementSearchEngine` | Live DOM semantic element resolver -- finds elements by label, placeholder, aria-label, text |
| **GapReportWriter** | `sdk.utility.GapReportWriter` | Writes gap/blocker `.md` reports when automation is not possible |
| **YamlConfigReader** | `sdk.config.YamlConfigReader` | Reads `sdk-config.yaml` -- browser, proxy, crawler, reporting settings |
| **ConfigurationManager** | `sdk.config.ConfigurationManager` | Central precedence-aware resolver (`-D` > env var > `sdk-config.yaml` > default) plus typed views: `CommonConfig`, `WebConfig`, `AllureReportConfig`, `AnalyticsConfig`, `RcaBundleConfig`, `VisualRegressionConfig`, `FlakyQuarantineConfig`, `TestImpactConfig`, and `ApiConfig` |
| **Excel_Reader** | `sdk.utility.Excel_Reader` | Reads `.xlsx` test data into `Object[][]` for `@DataProvider` |
| **Mailinator** | `sdk.utility.mailinator` | Reads emails from Mailinator API for email-flow testing |
| **Listener** | `sdk.listener.Listener` | Automatic TestNG lifecycle bridge: emits centralized test start/pass/fail/skip events, captures failure evidence once, and renames data-driven rows |
| **ExecutionReporting** | `sdk.reporting.ExecutionReporting` | Technology-neutral reporting facade: one execution event stream fans out to logs, Allure, Extent, reusable evidence references, and authoritative TestNG suite/test/class/method metadata |
| **AllureReportGenerator** | `sdk.reporting.AllureReportGenerator` | Optional post-run `allure generate` / `allure open` automation that never changes test pass/fail outcomes |
| **AnalyticsExecutionReporter / AnalyticsTrendReport** | `sdk.reporting.*` | Cross-run JSONL event store plus aggregators for pass/fail trends, flaky-test detection, and healed-locator frequency |
| **RcaBundleWriter** | `sdk.reporting.RcaBundleWriter` | Writes one JSON RCA bundle per failure, pre-linking screenshot, DOM dump, log tail, exception chain, and (v1.5.1+) optional browser console log / network trace references |
| **BrowserConsoleCapture** *(v1.5.1)* | `sdk.evidence.BrowserConsoleCapture` | Fail-safe capture of browser console/JS errors on Web failure (Chrome/Edge; Firefox unsupported), attached to Allure/Extent/RCA |
| **NetworkTraceRecorder** *(v1.5.1)* | `sdk.evidence.NetworkTraceRecorder` | Opt-in CDP-based browser network trace evidence for Chrome/Edge failures (not a canonical HAR document), disabled by default, with header/param redaction |
| **HealingElementLocator** | `sdk.healing.HealingElementLocator` | Opt-in runtime self-healing wrapper for XPath `@FindBy` locators; only accepts uniquely resolved relaxed candidates |
| **VisualRegressionChecker** | `sdk.visual.VisualRegressionChecker` | Baseline-vs-actual screenshot comparison powered by `ImageDiffEngine`, with no external visual-testing service |
| **RetryListener** | `sdk.listener.RetryListener` | Automatic test retry on failure |
| **FlakyTestQuarantineListener** | `sdk.flaky.FlakyTestQuarantineListener` | Opt-in post-retry reclassification of historically flaky failures to SKIP, backed by analytics history |
| **TestImpactCli** | `sdk.impact.TestImpactCli` | Generates an impacted-tests TestNG suite from `git diff` so CI can run a smaller targeted subset |
| **InstructionExtractor** | `sdk.utility.InstructionExtractor` | Extracts Copilot prompts and instructions into consumer projects |
| **AccessibilityChecker** | `sdk.accessibility.AccessibilityChecker` | Built-in 5-layer accessibility scanner: axe-core (`com.deque.html.axe-core:selenium:4.10.1`, bundled transitively), interaction, WCAG 2.2, structural, and motion analysis -- see [SDK-USER-GUIDE.md §14](SDK-USER-GUIDE.md#14-accessibility-testing) |
| **A11ySessionManager** | `sdk.accessibility.A11ySessionManager` | Scan de-duplication, severity thresholding, allowlists, and DOM fingerprint protection |
| **A11yTestNGListener** | `sdk.accessibility.A11yTestNGListener` | Automatic post-test accessibility scanning when enabled |
| **AllureA11yReporter** | `sdk.accessibility.AllureA11yReporter` | Publishes accessibility findings to Allure with attachments |
| **ExtentA11yReporter** | `sdk.accessibility.report.ExtentA11yReporter` | Publishes accessibility findings to the active ExtentReports test |
| **A11yReporterFactory** | `sdk.accessibility.report.A11yReporterFactory` | Composes the default Allure/Extent/Excel reporter set from `accessibility.reporting.*` config |
| **ApiTestBase** | `sdk.api.ApiTestBase` | Standalone base class for pure REST API tests (RestAssured-backed) -- no `WebDriver` required; `get/post/put/patch/delete`, status/JSON-path/response-time/JSON-schema assertions -- see [SDK-USER-GUIDE.md §18](SDK-USER-GUIDE.md#18-api-testing-apitestbase) and [`API-TESTBASE-API.md`](API-TESTBASE-API.md) |
| **MobileTestBase** | `sdk.mobile.testbase.MobileTestBase` | Appium (Android/iOS) peer of `TestBase` -- 60+ mobile gesture/wait/assertion helpers, no `WebDriver`/browser dependency |
| **MobileDriverFactory** | `sdk.mobile.driver.MobileDriverFactory` | Appium session initialization -- local Android/iOS and BrowserStack App Automate |
| **MobileElementCrawler** | `sdk.tools.crawler.mobile.MobileElementCrawler` | Scans a live app screen and generates an `@AndroidFindBy`/`@iOSXCUITFindBy`-annotated Page Object |
| **MobilePageObjectGenerator** | `sdk.mobile.crawler.MobilePageObjectGenerator` | Standalone runner for `MobileElementCrawler` |
| **AbstractMobileLocatorInvestigator** | `sdk.tools.locator.AbstractMobileLocatorInvestigator` | Mobile analogue of `AbstractLocatorInvestigator` -- declarative role/login/crawl-step shape for Appium crawl scripts -- see [SDK-USER-GUIDE.md §7.4](SDK-USER-GUIDE.md#74-mobile-appium-crawler--abstractmobilelocatorinvestigator) |

### v1.5.1 Web failure-evidence support matrix

| Evidence | Status |
|---|---|
| Screenshot / DOM / execution log / RCA bundle | **Supported** |
| Non-terminal `WebEventListener` handling (`NoSuchElementException`) | **Supported** |
| Allure attachment of failure evidence | **Supported** (fixed in v1.5.1 -- see CHANGELOG) |
| Extent attachment of failure evidence | **Supported** |
| Browser console log -- Chrome/Edge | **Supported** |
| Browser console log -- Firefox | **Unsupported** (geckodriver has no `LogType.BROWSER`) |
| Network trace -- Chrome/Edge, compatible CDP version | **Supported with limitation** (opt-in, disabled by default, pinned `cdp-v146` adapter) |
| Network trace -- Firefox | **Unsupported** (no CDP) |
| Network trace end-to-end generation on Chrome/Edge 153 | **Not fully validated** in this release's consumer-level testing -- CDP v146 vs. 153 mismatch was detected and failed safely, but no real `*_network-trace.json` was produced; see SDK-USER-GUIDE.md §13.4.3 |

---

## Quick Start -- Using the SDK in a Consumer Project

### Option A -- Start from the consumer template for your track (recommended)

Each track has its own canonical consumer template repository:

| Track | Canonical repository |
|---|---|
| Web (Selenium) | `functional-automation-consumer-template` |
| API (RestAssured) | `api-functional-automation-consumer-template` |
| Mobile (Appium) | `mobile-functional-automation-consumer-template` |

```bash
# Web
git clone https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/functional-automation-consumer-template

# API
git clone https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/api-functional-automation-consumer-template

# Mobile
git clone https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/mobile-functional-automation-consumer-template
```

Each template already has the SDK dependency, suite XMLs, config files, and
folder structure for its track. Use the matching template for the track you
are starting, or follow Option B below to add the SDK to an existing project.

### Option B -- Add to an existing Maven project

**1. Add the repository to `pom.xml`:**
```xml
<repositories>
  <repository>
    <id>cross-platform-functional-test-automation-sdk</id>
    <url>https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.pkgs.visualstudio.com/_packaging/cross-platform-functional-test-automation-sdk/maven/v1</url>
  </repository>
</repositories>
```

**2. Add the dependency:**
```xml
<dependency>
  <groupId>com.test.automation</groupId>
  <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
<version>1.5.3</version>
</dependency>
```

**3. Add Maven authentication** in `~/.m2/settings.xml` for local workstation use.
For CI, prefer secret-backed `settings.xml` injection or `MavenAuthenticate@0`
rather than storing or echoing credentials inline.
```xml
<server>
  <id>cross-platform-functional-test-automation-sdk</id>
  <username>YOUR_AZURE_ARTIFACTS_USERNAME</username>
  <password>YOUR_PAT_HERE</password>  <!-- PAT scope: Packaging -> Read -->
</server>
```

**4. Extract Copilot instructions:**
```bash
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.utility.InstructionExtractor"
```

---

## Configuration

Create `configuration/sdk-config.yaml` in your consumer project.
Copy from `sdk-config.yaml.template` (extracted by `InstructionExtractor`).

Key sections:

```yaml
browser:
  default: chrome
  headless: false

crawler:
  pageObject:
    package:   "com.yourcompany.automation.uiActions"
    outputDir: "src/test/java/com/yourcompany/automation/uiActions/"

reporting:
  screenshotsDir: "test-output/screenshots"
  crawlerDir: "test-output/crawler"
  accessibilityDir: "test-output/accessibility"
  gapOutputDir: "docs/test-case-gaps"
```

Full reference: [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md). For pure API projects,
also read [`API-TESTBASE-API.md`](API-TESTBASE-API.md). For mobile/Appium
projects, read [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md) and
[`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md).

Use inherited `TestBase.step("...", () -> { ... })` for business-readable test steps.
The SDK now fans those steps out consistently to file/console logs, Allure,
Extent, and reusable failure evidence while keeping low-level Selenium chatter at
DEBUG level.

Report hierarchy comes from TestNG runtime metadata, not Maven/Surefire display
names. The SDK resolves the business-facing suite once from `ISuite.getName()`
(the `<suite name="...">` value in `testng.xml`), keeps the TestNG `<test
name="...">` distinct, and then republishes that metadata consistently to SDK
logs, Allure (`parentSuite` / `suite` / `subSuite`), and Extent.

## Accessibility Testing

The SDK includes a built-in accessibility framework with a 5-layer WCAG engine,
automatic TestNG listener scans, WebEventListener per-element checks, and
`TestBase` helpers for manual scans and assertions. All accessibility features are
opt-in and write JSON, Excel, and HTML artifacts under `reporting.accessibilityDir`.

Findings support configurable enforcement (`accessibility.mode=report-only|fail-test`
with `accessibility.failOnSeverity`) and multi-format reporting (Allure/Extent/Excel,
all default `true`) — see [SDK-USER-GUIDE.md §14.1a](SDK-USER-GUIDE.md#141a-enforcement-modes-and-reporting-outputs).

The HTML and Excel reports both offer two independent, **report-only filters**:
an Engine Filter (`axe-core` / `Interaction`) and a Finding Type Filter
(`Violation` / `Needs Review`), plus a native Excel AutoFilter and independent
`Engine`/`Finding Type` columns — for reducing noise without altering
suppression or enforcement results — see
[SDK-USER-GUIDE.md §14.6a](SDK-USER-GUIDE.md#146a-accessibility-report-filters).

---

## What the Crawler Can Do

| Capability | Summary |
|---|---|
| Full-page element scan + uniqueness testing | Every candidate locator is tested live and labeled `UNIQUE [x]` / `NOT UNIQUE` / `DYNAMIC` / `STALE` / `STRUCTURAL` |
| Page Object generation | Ready-to-edit `.java` file with `@FindBy` fields using only stable locators |
| iframe / frame support | Descends into same-origin frames automatically |
| Label-following resolution | Resolves `<label for>`, `aria-labelledby`, Angular Material `mat-form-field` proximity |
| Modal detection | Separate `_Modal.java` file for open Angular CDK modals |
| Map / canvas widgets | Detects Google Maps, Leaflet, Mapbox GL, MapLibre GL, OpenLayers, Bing Maps and routes to `MapWidgetHelper` |
| Shadow DOM traversal | Recurses into open shadow roots; emits a lookup method instead of an impossible `@FindBy` |
| Dynamic / multi-step forms | `DataDrivenCrawler` re-crawls after each simulated step -- see below |
| Self-healing during a crawl | Stale-element retry with backoff, actionability pre-check + JS-click fallback (`safeClick`), network-idle detection, opt-in page-state dedup -- see [`SDK-USER-GUIDE.md` Section 7.3](SDK-USER-GUIDE.md#73-crawler-reliability--self-healing-features) |
| Safe overwrite protection | Never clobbers a hand-written page object -- writes to `ClassName_Crawled.java` |

Full details, code samples, and the complete capability reference: [`SDK-USER-GUIDE.md` Section 7](SDK-USER-GUIDE.md#7-the-element-crawler--generating-page-objects).

---

## Data-Driven Crawler -- Dynamic Forms Support

Standard `ElementCrawler` does a single-pass snapshot. For enterprise apps
(MS Dynamics 365, Salesforce, ServiceNow, Angular reactive forms) where selecting
a value dynamically shows/hides fields, use `DataDrivenCrawler` instead.

### How it works

```
Navigate to page -> baseline snapshot
  ?
Execute Step 1 (e.g. select dropdown value)
  ?  MutationObserver detects DOM change
Re-crawl -> diff -> tag new elements "Step 1: ..."
  ?
Execute Step 2 ...
  ?
Merged Page Object with all elements from all steps
```

### Test case mode (recommended for ADO workflows)

```java
List<CrawlerStep> steps = Arrays.asList(
    CrawlerStep.selectByLabel("Complaint Type", "Noise")
               .describe("Step 1: Select Complaint Type"),
    CrawlerStep.selectByLabel("Noise Category", "Music")
               .describe("Step 2: Select Noise Category"),
    CrawlerStep.typeByPlaceholder("Describe the issue", "Loud music")
               .describe("Step 3: Enter description"),
    CrawlerStep.clickByText("Next")
               .describe("Step 4: Click Next")
);

DataDrivenCrawler ddCrawler = new DataDrivenCrawler(driver);
List<ElementInfo> elements = ddCrawler.crawlTestCase(url, "TC-311: Noise Complaint", steps);

PageObjectGenerator gen = new PageObjectGenerator(driver);
gen.generateFromElements("NoisePage", url, elements);
```

### Generated Page Object output

```java
// [Visible after: Step 2: Select Noise Category]
@FindBy(xpath = "//mat-select[@formcontrolname='noiseCategory']")
public WebElement noiseCategoryDropdown;

// [All scenarios]
@FindBy(xpath = "//button[normalize-space(.)='Next']")
public WebElement nextButton;
```

### Semantic locator factories

| Factory | Resolves by |
|---|---|
| `CrawlerStep.selectByLabel("Label", "Value")` | Visible label text / mat-label |
| `CrawlerStep.typeByPlaceholder("hint", "value")` | `@placeholder` attribute |
| `CrawlerStep.typeByFormControlName("name", "value")` | `@formcontrolname` |
| `CrawlerStep.clickByText("Submit")` | Visible element text |
| `CrawlerStep.clickByAriaLabel("Close")` | `@aria-label` attribute |
| `CrawlerStep.select("//xpath", "Option")` | Raw XPath (explicit) |

### Safe file protection

If a hand-crafted Page Object already exists, `PageObjectGenerator` automatically
writes to `ClassName_Crawled.java` instead of overwriting it.
Review the `_Crawled` file and manually merge any improved locators.

---

## Generating Page Objects

Never write `@FindBy` locators by hand. Run the crawler against a live page.

Prefer environment-backed secrets, CI secret variables, or your existing SDK
credential-resolution flow for any required login. Treat explicit inline
`-Dinv.email` / `-Dinv.password` style overrides as compatibility fallbacks for
one-off local troubleshooting only -- never as the default or a committed script.

```bash
# Via crawler suite (when LocatorInvestigator exists in consumer project)
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=your@email.com -Dinv.******

# Standalone
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
  -Dexec.args="LoginPage https://your-app.example.com/#/login user@example.com pass"
```

**Only use locators marked `UNIQUE [x]`** in the generated report.


## Copilot Prompts

After running `InstructionExtractor` in a consumer project, these prompts are available:

| Prompt | Invoke | Purpose |
|---|---|---|
| `start.prompt.md` | `#start` | Interactive launcher -- menu + guided parameter collection |
| `create-test.prompt.md` | `#create-test` | New test class from ADO or local test case |
| `modify-test.prompt.md` | `#modify-test` | Add scenario or update existing test |
| `fix-failed-test.prompt.md` | `#fix-failed-test` | Diagnose and fix -- ADO pre-check included |
| `fix-broken-locator.prompt.md` | `#fix-broken-locator` | Heal page object after UI change |
| `ado-sync-test.prompt.md` | `#ado-sync-test` | Align test with updated ADO test case |
| `report-test-gap.prompt.md` | `#report-test-gap` | Document unautomatable test case |

All prompts support **autonomous mode** -- grant it once to skip step-by-step confirmations.

---

## Documentation

| Document | Description |
|---|---|
| [`GETTING-STARTED.md`](GETTING-STARTED.md) | First-day installation and setup for Web, Mobile, and API consumer projects |
| [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md) | Complete setup, config, crawler, reporting, accessibility, and API examples |
| [`TESTBASE-API.md`](TESTBASE-API.md) | Every TestBase method with usage guidance |
| [`API-TESTBASE-API.md`](API-TESTBASE-API.md) | Every `ApiTestBase` method with usage guidance (RestAssured-based API testing) |
| [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md) | Mobile (Appium) consumer-facing setup and usage guide |
| [`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md) | Every `MobileTestBase` method with usage guidance |
| [`CHANGELOG.md`](CHANGELOG.md) | SDK version history -- all changes since 1.0.0 |
| [`SDK-PUBLISHING.md`](SDK-PUBLISHING.md) | Build, deploy, and version bump process for SDK maintainers |

---

## Building and Publishing (SDK Maintainers)

```bash
# Run all unit tests
mvn test

# Build and install locally
mvn clean install -DskipTests

# Deploy to Azure Artifacts + local mvn-repo
mvn clean deploy -DskipTests
```

> [!]? **Always add a `CHANGELOG.md` entry under `[Unreleased]` before deploying.**
> See [`SDK-PUBLISHING.md`](SDK-PUBLISHING.md) for the full version bump checklist.

**Versioning: `MAJOR.MINOR.PATCH`**

| Increment | When |
|---|---|
| `PATCH` | Bug fix, no API change |
| `MINOR` | New feature, backward compatible |
| `MAJOR` | Breaking change -- removed/renamed API |

---

## Repository Layout

```
cross-platform-functional-test-automation-sdk/
+-- pom.xml                          <- version, dependencies, deploy config
+-- CHANGELOG.md                     <- all version changes
+-- GETTING-STARTED.md               <- first-day install/setup guide (web/mobile/api)
+-- SDK-USER-GUIDE.md                <- consumer-facing user guide (web + shared features)
+-- TESTBASE-API.md                  <- TestBase API reference (web)
+-- API-TESTBASE-API.md              <- ApiTestBase API reference (REST API)
+-- MOBILE-USER-GUIDE.md             <- consumer-facing user guide (mobile/Appium)
+-- MOBILE-TESTBASE-API.md           <- MobileTestBase API reference (mobile)
+-- SDK-PUBLISHING.md                <- maintainer deploy guide
+-- mvn-repo/                        <- git submodule -> local Maven repository
+-- src/
    +-- main/
    |   +-- java/com/test/automation/sdk/
    |   |   +-- testbase/            <- TestBase, WebDriverFactory, SdkConfig
    |   |   +-- api/                 <- ApiTestBase, ApiConfig (REST/RestAssured testing)
    |   |   +-- mobile/              <- MobileTestBase, MobileDriverFactory,
    |   |   |                           MobileConfigReader, mobile crawler facades
    |   |   +-- tools/               <- crawler/, pageobject/, locator/ -- desktop +
    |   |   |                           mobile element crawlers and page-object generators
    |   |   +-- utility/             <- GapReportWriter, YamlConfigReader,
    |   |   |                           Excel_Reader, InstructionExtractor, Mailinator
    |   |   +-- listener/            <- Listener, RetryListener, WebEventListener
    |   +-- resources/
    |       +-- sdk-instructions/    <- Copilot instruction files (bundled in JAR)
    |       +-- sdk-prompts/         <- Copilot prompt files (bundled in JAR)
    |       +-- ai/                  <- AI agent prompts/skills/schemas (bundled in JAR;
    |       |                           NOT the same as sdk-prompts/ above -- see ai/README.md)
    |       +-- sdk-defaults/        <- sdk-config.yaml.template, log4j templates
    |       +-- sdk-templates/       <- gap report template
    |       +-- GETTING-STARTED.md
    |       +-- SDK-USER-GUIDE.md / TESTBASE-API.md / API-TESTBASE-API.md
    |       +-- MOBILE-USER-GUIDE.md / MOBILE-TESTBASE-API.md
    |       +-- CHANGELOG.md
    +-- test/
        +-- java/com/test/automation/sdk/
            +-- utility/             <- unit tests (664+ total)
```

---

*Maintained by OTI QA Automation Team*  
*SDK: `com.test.automation:cross-platform-functional-test-automation-sdk:1.5.3`*
