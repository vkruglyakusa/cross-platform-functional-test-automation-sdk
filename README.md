# Framework Automation SDK

![SDK](https://img.shields.io/badge/SDK-functional--test--automation--sdk:1.1.3-blue)

> **Reusable Selenium + TestNG framework layer for OTI QA Automation**  
> Java 20+ * Maven * `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.3`

This SDK is a single JAR that consumer automation projects depend on.
It provides TestBase, WebDriverFactory, ElementCrawler, listeners, utilities,
and AI-agent prompts -- so every consumer project contains **only** its own
page objects, test classes, and test data.

## Validated dependency baseline

| Component | Supported baseline |
|---|---|
| Java | 20 or newer |
| Selenium | 4.44.0 |
| Appium Java Client | 10.1.1 |
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
| **Excel_Reader** | `sdk.utility.Excel_Reader` | Reads `.xlsx` test data into `Object[][]` for `@DataProvider` |
| **Mailinator** | `sdk.utility.mailinator` | Reads emails from Mailinator API for email-flow testing |
| **Listener** | `sdk.listener.Listener` | Auto-screenshot, DOM dump on failure, Extent report integration, XML test-name renaming for data-driven rows |
| **RetryListener** | `sdk.listener.RetryListener` | Automatic test retry on failure |
| **InstructionExtractor** | `sdk.utility.InstructionExtractor` | Extracts Copilot prompts and instructions into consumer projects |
| **AccessibilityChecker** | `sdk.accessibility.AccessibilityChecker` | Built-in 5-layer accessibility scanner: axe-core (`com.deque.html.axe-core:selenium:4.10.1`, bundled transitively), interaction, WCAG 2.2, structural, and motion analysis -- see [SDK-USER-GUIDE.md §14](SDK-USER-GUIDE.md#14-accessibility-testing) |
| **A11ySessionManager** | `sdk.accessibility.A11ySessionManager` | Scan de-duplication, severity thresholding, allowlists, and DOM fingerprint protection |
| **A11yTestNGListener** | `sdk.accessibility.A11yTestNGListener` | Automatic post-test accessibility scanning when enabled |
| **AllureA11yReporter** | `sdk.accessibility.AllureA11yReporter` | Publishes accessibility findings to Allure with attachments |

---

## Quick Start -- Using the SDK in a Consumer Project

### Option A -- Start from the consumer template (recommended)

```bash
git clone https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/framework_automation_consumer_template
```

The template already has the SDK dependency, suite XMLs, config files, and folder structure.

### Option B -- Add to an existing Maven project

**1. Add the repository to `pom.xml`:**
```xml
<repositories>
  <repository>
    <id>functional-test-automation-sdk</id>
    <url>https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.pkgs.visualstudio.com/_packaging/functional-test-automation-sdk/maven/v1</url>
  </repository>
</repositories>
```

**2. Add the dependency:**
```xml
<dependency>
  <groupId>com.test.automation</groupId>
  <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
<version>1.1.3</version>
</dependency>
```

**3. Add Maven authentication** in `~/.m2/settings.xml`:
```xml
<server>
  <id>functional-test-automation-sdk</id>
  <username>clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d</username>
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

Full reference: [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md)

## Accessibility Testing

Version 1.5.0 adds a built-in accessibility framework with a 5-layer WCAG engine,
automatic TestNG listener scans, WebEventListener per-element checks, and
`TestBase` helpers for manual scans and assertions. All accessibility features are
opt-in and write JSON, Excel, and HTML artifacts under `reporting.accessibilityDir`.

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

Never write `@FindBy` locators by hand. Run the crawler against a live page:

```bash
# Via crawler suite (when LocatorInvestigator exists in consumer project)
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=your@email.com -Dinv.password=yourpassword

# Standalone
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
  -Dexec.args="LoginPage https://your-app.example.com/#/login user@example.com pass"
```

**Only use locators marked `UNIQUE [x]`** in the generated report.

---

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
| [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md) | Complete setup, config, crawler, API, and examples |
| [`TESTBASE-API.md`](TESTBASE-API.md) | Every TestBase method with usage guidance |
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
+-- SDK-USER-GUIDE.md                <- consumer-facing user guide
+-- TESTBASE-API.md                  <- TestBase API reference
+-- SDK-PUBLISHING.md                <- maintainer deploy guide
+-- mvn-repo/                        <- git submodule -> local Maven repository
+-- src/
    +-- main/
    |   +-- java/com/test/automation/sdk/
    |   |   +-- testbase/            <- TestBase, WebDriverFactory, SdkConfig
    |   |   +-- utility/             <- ElementCrawler, PageObjectGenerator,
    |   |   |                           GapReportWriter, YamlConfigReader,
    |   |   |                           Excel_Reader, InstructionExtractor, Mailinator
    |   |   +-- listener/            <- Listener, RetryListener, WebEventListener
    |   +-- resources/
    |       +-- sdk-instructions/    <- Copilot instruction files (bundled in JAR)
    |       +-- sdk-prompts/         <- Copilot prompt files (bundled in JAR)
    |       +-- ai/                  <- AI agent prompts/skills/schemas (bundled in JAR;
    |       |                           NOT the same as sdk-prompts/ above -- see ai/README.md)
    |       +-- sdk-defaults/        <- sdk-config.yaml.template, log4j templates
    |       +-- sdk-templates/       <- gap report template
    |       +-- SDK-USER-GUIDE.md
    |       +-- TESTBASE-API.md
    |       +-- CHANGELOG.md
    +-- test/
        +-- java/com/test/automation/sdk/
            +-- utility/             <- unit tests (296 total)
```

---

*Maintained by OTI QA Automation Team*  
*SDK: `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.3`*
