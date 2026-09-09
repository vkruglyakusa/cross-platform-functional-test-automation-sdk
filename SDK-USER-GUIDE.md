# Framework Automation SDK -- User Guide

**Version:** 1.1.1  
**Artifact:** `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.1`
**Repository:** `OTI QA Automation / cross-platform-functional-test-automation-sdk`

This guide is the **single document** a QA engineer needs to start a new Selenium
automation project on top of this SDK. No Selenium or TestNG expertise required
beyond what is described here.

---

## Table of Contents

1. [What the SDK Provides](#1-what-the-sdk-provides)
2. [Prerequisites](#2-prerequisites)
3. [Set Up a New Project](#3-set-up-a-new-project)
4. [Maven Dependency](#4-maven-dependency)
5. [Project Structure](#5-project-structure)
6. [Configuration Reference](#6-configuration-reference)
7. [The Element Crawler -- Generating Page Objects](#7-the-element-crawler--generating-page-objects)
    - [7.1 Map Widgets -- Google Maps, Leaflet, Mapbox GL, OpenLayers, Bing Maps](#71-map-widgets--google-maps-leaflet-mapbox-gl-openlayers-bing-maps)
    - [7.2 Shadow DOM -- Web Components, Lit/Stencil, Salesforce Lightning (LWC)](#72-shadow-dom--web-components-litstencil-salesforce-lightning-lwc)
    - [7.3 Crawler Reliability & Self-Healing Features](#73-crawler-reliability--self-healing-features)
8. [Writing Page Objects (uiActions)](#8-writing-page-objects-uiactions)
9. [TestBase API -- What You Can Use](#9-testbase-api--what-you-can-use)
10. [Writing Test Classes](#10-writing-test-classes)
11. [Test Data (Excel)](#11-test-data-excel)
12. [Running Tests](#12-running-tests)
13. [Reports & Screenshots](#13-reports--screenshots)
    - [13.1 Gap & Blocker Reports -- Configurable Output](#131-gap--blocker-reports--configurable-output)
    - [13.2 Failure RCA Workflow](#132-failure-rca-workflow)
14. [Accessibility Testing](#14-accessibility-testing)
15. [Retry & Listeners](#15-retry--listeners)
16. [Locator Rules -- Non-Negotiable](#16-locator-rules--non-negotiable)
17. [Complete End-to-End Example](#17-complete-end-to-end-example)
18. [Troubleshooting](#18-troubleshooting)

---

## 1. What the SDK Provides

The SDK is a single JAR that replaces every manual framework dependency. Your
automation project only needs **one** Maven dependency.

| Component | Class | What it gives you |
|-----------|-------|-------------------|
| **TestBase** | `sdk.testbase.TestBase` | Base class with 60+ ready-to-use Selenium helpers |
| **WebDriverFactory** | `sdk.testbase.WebDriverFactory` | Browser initialization (Chrome, Firefox, Edge, headless, Grid) |
| **SdkConfig** | `sdk.config.SdkConfig` | Auto-resolves config file paths via system property or env variable |
| **ElementCrawler** | `sdk.tools.crawler.web.ElementCrawler` | Scans a live page and generates a `@FindBy`-annotated Page Object. `waitForPageReady()` is public (1.2.4+) for use in consumer crawler tools. Supports iframe crawling, label associations, image buttons, table columns, and 28 XPath strategies. |
| **PageObjectGenerator** | `sdk.tools.pageobject.PageObjectGenerator` | Standalone runner for ElementCrawler. `generateFromElements()` accepts a pre-crawled list. `generateFromCurrentPageWithModalCheck()` detects open Angular modals and generates a separate `_Modal` page object. |
| **DataDrivenCrawler** | `sdk.tools.crawler.web.DataDrivenCrawler` | Multi-pass DOM-diff crawler for dynamic forms (Angular, Dynamics, Salesforce). `crawlTestCase(url, name, steps)` follows a test case flow step-by-step; full-scan mode (default) inventories every element on every page state visited. |
| **CrawlerStep** | `sdk.tools.crawler.web.CrawlerStep` | Single interaction step with raw XPath or semantic locators (`selectByLabel`, `typeByPlaceholder`, `clickByText`, etc.). `.describe()` attaches a step label used in generated Page Object comments. |
| **CrawlerScenario** | `sdk.tools.crawler.web.CrawlerScenario` | Named step sequence. `fromTestCase(name, steps...)` builder auto-enables per-step DOM snapshots. |
| **ElementSearchEngine** | `sdk.tools.crawler.web.ElementSearchEngine` | Live DOM semantic element resolver -- finds elements by label text, placeholder, aria-label, visible text, or `@formcontrolname`. Used internally by `DataDrivenCrawler`. |
| **AbstractLocatorInvestigator** | `sdk.tools.locator.AbstractLocatorInvestigator` | Base class for consumer `LocatorInvestigator` tools. Override 3 methods (`performLogin`, `isSessionAlive`, `defineCrawlSteps`); all crawl infrastructure (login, role switching, nav helpers, summary) is SDK-owned. |
| **Excel_Reader** | `sdk.utility.Excel_Reader` | Reads `.xlsx` test data into `Object[][]` for `@DataProvider` |
| **Listener** | `sdk.listener.Listener` | Auto-screenshot + DOM dump on failure, data-driven XML test name renaming |
| **RetryListener** | `sdk.listener.RetryListener` | Automatic test retry on failure |
| **WebEventListener** | `sdk.listener.WebEventListener` | Logs every browser action for debugging |
| **Mailinator** | `sdk.utility.mailinator` | Reads emails from Mailinator API for email-flow testing |
| **YamlConfigReader** | `sdk.config.YamlConfigReader` | Reads `sdk-config.yaml` for advanced SDK settings |
| **GapReportWriter** | `sdk.utility.GapReportWriter` | Writes gap-report.md / blocker-report.md to the configured output directory |
| **AccessibilityChecker** | `sdk.accessibility.AccessibilityChecker` | Built-in 5-layer WCAG scan engine: axe-core, interaction, WCAG 2.2, structural, and motion checks. |
| **A11ySessionManager** | `sdk.accessibility.A11ySessionManager` | De-duplicates scans by URL, cooldown, and DOM fingerprint; applies severity threshold and allowlists. |
| **A11yTestNGListener** | `sdk.accessibility.A11yTestNGListener` | Automatically scans pages after each test when accessibility is enabled. Registered by the SDK via `META-INF/services`. |
| **AllureA11yReporter** | `sdk.accessibility.AllureA11yReporter` | Sends accessibility violations, summaries, and artifact attachments to Allure steps. |

All Selenium, TestNG, Allure, Extent, and Apache POI transitive dependencies
are declared in the SDK's `pom.xml` -- **you do not add them yourself**.

---

## 2. Prerequisites

| Tool | Required Version | Notes |
|------|-----------------|-------|
| Java JDK | 20 or higher | Must be on `PATH`. SDK is compiled at Java 20 source level (bumped from 8 in v2.0.0 -- see CHANGELOG). |
| Maven | 3.6 or higher | Must be on `PATH`. |
| Chrome + ChromeDriver | Latest stable | WebDriverManager auto-downloads the matching driver. Optionally set `browser.chromeDriverPath` in `sdk-config.yaml` to pin a local binary. |
| IntelliJ IDEA | Any recent | Recommended IDE. |

---

## 3. Set Up a New Project

**Option A -- Consumer Template (recommended)**

Clone the `framework_automation_consumer_template` repository. It is a
ready-to-run project with all folders, config files, and example tests already
in place.

```
git clone https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/framework_automation_consumer_template
```

Then rename the project:
1. Rename the folder to your app name.
2. Update `groupId` / `artifactId` / `name` in `pom.xml`.
3. Update `configuration/config.properties` with your app URLs.
4. Rename the Java packages under `src/test/java/` to match your company/app.

**Option B -- Manual Project**

Create a Maven project and follow Sections 4-6 to configure it from scratch.

---

## 4. Maven Dependency

Add exactly one dependency to your `pom.xml`. No other framework deps are needed.

```xml
<dependency>
    <groupId>com.test.automation</groupId>
    <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

Also add the Azure Artifacts repository so Maven knows where to download it from:

```xml
<repositories>
    <repository>
        <id>functional-test-automation-sdk</id>
        <url>https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.pkgs.visualstudio.com/_packaging/functional-test-automation-sdk/maven/v1</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
</repositories>
```

---

## 4a. Keeping Your Project on the Latest SDK Version

**The recommended way to always have the latest SDK** is to use the consumer
template -- its `pom.xml` is automatically updated on every SDK release by the
SDK release pipeline. If you cloned the template recently, you already have the
latest version.

### Why not use `LATEST` or `RELEASE`?

Maven supports `LATEST` and `RELEASE` meta-versions in dependency declarations,
but they are **officially deprecated in Maven 3** and do not work reliably with
Azure Artifacts feeds. Using them produces build warnings and may fail silently
with future Maven versions. Always use an explicit version number.

### How to upgrade manually

When a new SDK version is released:

**1. Update the version in `pom.xml`:**
```xml
<dependency>
    <groupId>com.test.automation</groupId>
    <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
    <version>X.Y.Z</version>   <!-- replace with new version -->
</dependency>
```

**2. Verify the build still compiles:**
```bash
mvn compile test-compile -q
```

**3. Re-run InstructionExtractor** to get updated Copilot instructions matching
the new SDK version:
```bash
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.utility.InstructionExtractor"
```

> [!]? **Customized instruction files are never silently overwritten.** Each extracted
> file gets a hidden `<file>.sdkhash` sidecar recording its content hash. If you've
> hand-edited or intentionally forked a file (e.g. `copilot-instructions.md` or a
> `.github/instructions/*.md` file), the extractor detects the change and writes the
> newer SDK version to `<file>.sdk-new` instead of overwriting your customization --
> review and merge it manually. Use `-Dsdk.forceExtract=true` to discard local edits
> and reset a specific file back to the pristine SDK default.

**4. Check the CHANGELOG** for breaking changes or new required config keys:
- [SDK CHANGELOG](https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_git/cross-platform-functional-test-automation-sdk?path=/CHANGELOG.md)

### How to check what version is currently latest

- **Azure Artifacts feed**: [functional-test-automation-sdk packages](https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.visualstudio.com/OTI%20QA%20Automation/_artifacts/feed/functional-test-automation-sdk)
- **maven-repository** (git fallback): browse `../maven-repository/com/test/automation/cross-platform-functional-test-automation-sdk/`
- **SDK README**: the version badge at the top always shows the current release

### How the template stays current automatically

The SDK `release.ps1` script updates the consumer template's `pom.xml`,
`README.md`, `GETTING-STARTED.md`, and `CHANGELOG.md` on every release.
Cloning the template always gives you the latest stable version -- no manual
version tracking needed.

---

## 4.1 Maven Authentication — Choose Your Option

The SDK feed on Azure Artifacts requires authentication. There are **two supported
approaches** depending on where the build runs:

| | Option A — PAT in `settings.xml` | Option B — `MavenAuthenticate@0` pipeline task |
|---|---|---|
| **Use when** | Local developer machine | Azure DevOps CI/CD pipeline |
| **Credential type** | Personal Access Token stored in `~/.m2/settings.xml` | Pipeline-managed OAuth token (`$(System.AccessToken)`) |
| **Secrets committed to repo?** | No — `settings.xml` is outside the project | No — token injected at runtime by Azure DevOps |
| **Setup per machine?** | Yes — one-time per dev workstation | No — zero config per agent |
| **Works in headless CI?** | Only if PAT is injected as a secret variable | Yes — native ADO support |

---

## 4.1a Option A — PAT Authentication (Local Developer Machine)

This is a **one-time setup per workstation**. No pipeline changes needed.

### Step 1 — Generate a PAT in Azure DevOps

1. Go to Azure DevOps → click your avatar (top right) → **Personal Access Tokens**
2. Click **+ New Token**
3. Fill in:
   - **Name:** `maven-sdk-read`
   - **Organization:** `clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d`
   - **Expiration:** 1 year
   - **Scopes:** ✅ **Packaging → Read** (read-only — for downloading the SDK)
4. Click **Create** and **copy the token immediately** — it won't be shown again

### Step 2 — Create or update `%USERPROFILE%\.m2\settings.xml`

If the file does not exist, create it. If it already exists, add the `<server>` block inside `<servers>`.

**Minimal `settings.xml` (no corporate proxy):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.4"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.4
          http://maven.apache.org/xsd/settings-1.2.4.xsd">

  <servers>
    <server>
      <id>functional-test-automation-sdk</id>
      <username>clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d</username>
      <password>YOUR_PAT_HERE</password>
    </server>
  </servers>

</settings>
```

**With corporate proxy (e.g. behind `bcpxy.nycnet`):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.4"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.4
          http://maven.apache.org/xsd/settings-1.2.4.xsd">

  <proxies>
    <proxy>
      <id>http-proxy-settings</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>bcpxy.nycnet</host>
      <port>8080</port>
      <nonProxyHosts>10.*|192.168.*|172.16.*|*.nycnet|localhost|*.visualstudio.com|*.pkgs.visualstudio.com|*.dev.azure.com</nonProxyHosts>
    </proxy>
  </proxies>

  <servers>
    <server>
      <id>functional-test-automation-sdk</id>
      <username>clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d</username>
      <password>YOUR_PAT_HERE</password>
      <configuration>
        <httpConfiguration>
          <all>
            <usePreemptive>true</usePreemptive>
          </all>
        </httpConfiguration>
      </configuration>
    </server>
  </servers>

</settings>
```

> **Important rules:**
> - The `<id>` in `settings.xml` must exactly match the `<id>` in the `<repositories>` block in `pom.xml` — both are `functional-test-automation-sdk`
> - The `<username>` must be the org GUID: `clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d`
> - `*.pkgs.visualstudio.com` must be in `nonProxyHosts` if you use a corporate proxy
> - **Never commit your PAT** — `settings.xml` lives outside the project in `~/.m2/`

### Step 3 — Verify

```bash
mvn dependency:resolve -Dartifact=com.test.automation:cross-platform-functional-test-automation-sdk:1.1.1
```

Expected output: `BUILD SUCCESS` with `cross-platform-functional-test-automation-sdk-1.1.0.jar` downloaded.

---

## 4.1b Option B — `MavenAuthenticate@0` Pipeline Task (Azure DevOps CI/CD)

This is the **recommended approach for all CI/CD pipelines**. No PATs, no
`settings.xml` maintenance — Azure DevOps injects credentials automatically using
the pipeline's built-in OAuth token.

> 📖 Official reference: [MavenAuthenticate@0 task](https://learn.microsoft.com/en-us/azure/devops/pipelines/tasks/reference/maven-authenticate-v0?view=azure-pipelines)

### How it works

The `MavenAuthenticate@0` task writes temporary `<server>` credentials into the
agent's `~/.m2/settings.xml` before Maven runs. It uses `$(System.AccessToken)` —
the pipeline's own OAuth token — so no PAT is stored anywhere.

> ℹ️ **Note:** `MavenAuthenticate@0` obtains and injects the OAuth token
> internally — you do **not** need to declare the `SYSTEM_ACCESSTOKEN` variable
> for the task itself to work. Declaring it (Step 1 below) is only required if
> you *also* run Maven with a custom `settings.xml` via the `-s` switch and
> reference `${env.SYSTEM_ACCESSTOKEN}` manually (see "Custom `settings.xml`"
> below). It's included here for consistency and because other tooling in this
> pipeline (e.g. custom scripts) may need it.

### Step 1 — Enable `System.AccessToken` in the pipeline

In your Azure DevOps pipeline YAML, allow the job to use the built-in token:

```yaml
jobs:
  - job: RunTests
    pool:
      vmImage: 'ubuntu-latest'      # or windows-latest
    variables:
      SYSTEM_ACCESSTOKEN: $(System.AccessToken)
    steps:
      - checkout: self
```

Or at the pipeline level:
```yaml
trigger:
  - trunk

pool:
  vmImage: 'ubuntu-latest'

variables:
  SYSTEM_ACCESSTOKEN: $(System.AccessToken)
```

### Step 2 — Add `MavenAuthenticate@0` before any `mvn` command

```yaml
steps:
  - task: MavenAuthenticate@0
    displayName: 'Authenticate Azure Artifacts feed'
    inputs:
      artifactsFeeds: functional-test-automation-sdk
```

The `artifactsFeeds` value must match the `<id>` in your `pom.xml` `<repositories>` block.

### Step 3 — Run your tests with Maven

```yaml
  - task: Maven@4
    displayName: 'Run regression tests'
    inputs:
      mavenPomFile: 'pom.xml'
      goals: 'test'
      options: >
        -Denvironment=stg
        -DbrowserName=chrome
        -Dsurefire.suiteXmlFiles=regression_suite.xml
      publishJUnitResults: true
      testResultsFiles: '**/surefire-reports/TEST-*.xml'
```

### Authenticating external (non-ADO) Maven repositories

If your `pom.xml` also references a Maven repository **outside** your Azure
DevOps organization (e.g. Maven Central mirrors, JitPack, a partner org's
feed), authenticate it via a [Maven service connection](https://learn.microsoft.com/en-us/azure/devops/pipelines/library/service-endpoints)
instead of `artifactsFeeds`:

```yaml
- task: MavenAuthenticate@0
  displayName: 'Authenticate external Maven repositories'
  inputs:
    mavenServiceConnections: central,MavenOrg   # names of Maven service connections
```

Both inputs can be combined on the same task call if you need internal feeds
**and** external repositories authenticated in the same job:

```yaml
- task: MavenAuthenticate@0
  inputs:
    artifactsFeeds: functional-test-automation-sdk
    mavenServiceConnections: central
```

### Cross-project feed access

If the pipeline runs in a **different ADO project** than the one hosting the
`functional-test-automation-sdk` feed, `MavenAuthenticate@0` will still write
credentials, but Maven will get a 401/403 unless the feed's hosting project
grants the consuming pipeline's build service identity **Reader** access. Go to
**Project Settings → Artifacts → Feed Settings → Permissions** on the feed's
project and add the other project's Build Service account
(`<OtherProject> Build Service (<Org>)`) as **Reader**. See
[Package permissions in Azure Pipelines](https://learn.microsoft.com/en-us/azure/devops/artifacts/feeds/feed-permissions#pipelines-permissions)
for details.

### `<id>` matching requirement

The value(s) passed to `artifactsFeeds` must exactly match the `<id>` of the
corresponding `<repository>` block in `pom.xml` — this is how Maven knows which
injected `<server>` credentials apply to which repository:

```xml
<repository>
  <id>functional-test-automation-sdk</id>
  <url>https://pkgs.dev.azure.com/<Org>/<Project>/_packaging/functional-test-automation-sdk/maven/v1</url>
</repository>
```

### Complete pipeline example

```yaml
# azure-pipelines.yml
trigger:
  - trunk

pool:
  vmImage: 'ubuntu-latest'

variables:
  SYSTEM_ACCESSTOKEN: $(System.AccessToken)

steps:
  # 1. Authenticate the Azure Artifacts feed — injects credentials into settings.xml
  - task: MavenAuthenticate@0
    displayName: 'Authenticate Azure Artifacts — functional-test-automation-sdk'
    inputs:
      artifactsFeeds: functional-test-automation-sdk

  # 2. Install Java 20 (source level as of SDK v2.0.0)
  - task: JavaToolInstaller@0
    inputs:
      versionSpec: '20'
      jdkArchitectureOption: 'x64'
      jdkSourceOption: 'PreInstalled'

  # 3. Run tests
  - task: Maven@4
    displayName: 'Run regression suite'
    inputs:
      mavenPomFile: 'pom.xml'
      goals: 'test'
      options: >
        -Denvironment=stg
        -DbrowserName=chrome
        -Dheadless=true
        -Dsurefire.suiteXmlFiles=regression_suite.xml
      publishJUnitResults: false      # disabled — we publish TestNG XML directly below
      javaHomeOption: 'JDKVersion'
      jdkVersionOption: '1.11'
    continueOnError: true             # allow publish steps to run even if tests fail

  # 4. Filter TestNG results — remove runMode=N skips before publishing to ADO.
  #    Without this step ADO counts SKIPs as "Others" and reports ~79% pass rate
  #    even when every executed test passed (0 failures). This step removes SKIP
  #    nodes and corrects the total/skipped attributes on <test> and <testng-results>
  #    so ADO shows 100% pass rate when all executed tests pass.
  #    NOTE: Allure reads its own JSON artifacts — it still shows SKIPs correctly.
  - task: PowerShell@2
    displayName: 'Filter TestNG results (remove runMode=N skips from ADO report)'
    condition: always()
    continueOnError: true
    inputs:
      targetType: inline
      script: |
        $files = Get-ChildItem -Path "$(System.DefaultWorkingDirectory)" `
                               -Recurse -Filter "testng-results.xml" `
                               -ErrorAction SilentlyContinue
        if (-not $files) {
          Write-Host "No testng-results.xml found -- skipping filter"
          exit 0
        }
        foreach ($f in $files) {
          try {
            [xml]$doc = Get-Content $f.FullName -Raw

            # Snapshot BEFORE removal -- live XmlNodeList shifts index on every RemoveChild,
            # causing every other SKIP node to be silently missed without the snapshot.
            $skipped = @($doc.SelectNodes("//test-method[@status='SKIP']"))
            if ($skipped.Count -eq 0) {
              Write-Host "No SKIPs in $($f.Name) -- nothing to filter"
              continue
            }

            foreach ($n in $skipped) {
              $null = $n.ParentNode.RemoveChild($n)
            }

            # Fix totals on every <test> node (ADO reads these attributes directly)
            foreach ($testNode in $doc.SelectNodes("//test")) {
              $cur = [int]$testNode.GetAttribute("skipped")
              $testNode.SetAttribute("skipped", "0")
              $total = [int]$testNode.GetAttribute("total")
              $testNode.SetAttribute("total", [string]($total - $cur))
            }

            # Fix totals on root <testng-results> element
            $root = $doc.DocumentElement
            $rootSkipped = [int]$root.GetAttribute("skipped")
            $root.SetAttribute("skipped", "0")
            $rootTotal = [int]$root.GetAttribute("total")
            $root.SetAttribute("total", [string]($rootTotal - $rootSkipped))

            $doc.Save($f.FullName)
            Write-Host "Removed $($skipped.Count) SKIP entries from $($f.Name) -- new total: $($rootTotal - $rootSkipped)"

          } catch {
            Write-Warning "Failed to process $($f.FullName): $_"
          }
        }

  # 5. Publish TestNG results to ADO Tests tab
  - task: PublishTestResults@2
    displayName: 'Publish TestNG results to ADO'
    condition: always()
    inputs:
      testResultsFormat: 'TestNG'
      testResultsFiles: '**/testng-results.xml'
      mergeTestResults: true
      failTaskOnFailedTests: true
      testRunTitle: 'Regression Suite — $(Build.BuildNumber)'

  # 6. Publish all test artifacts (screenshots, DOM dumps, logs, Allure, accessibility)
  - task: PublishBuildArtifacts@1
    displayName: 'Publish test artifacts'
    condition: always()
    inputs:
      PathtoPublish: 'test-output'
      ArtifactName: 'test-output'
```

### Comparison: when to use which option

| Scenario | Use |
|---|---|
| Developer running tests locally from IDE or terminal | Option A (PAT) |
| Azure DevOps pipeline — same project as SDK feed | Option B (`MavenAuthenticate@0`) |
| Azure DevOps pipeline — different project than SDK feed | Option B + grant build service Reader role |
| GitHub Actions or Jenkins (non-ADO) | Option A with PAT injected as a pipeline secret variable |

---

## 5. Project Structure

```
your-app-automation/
+-- pom.xml
+-- regression_suite.xml          <- TestNG suite -- add your test classes here
+-- crawler_suite.xml             <- Runs the page object generator
+-- configuration/
|   +-- config.properties         <- App URLs, Excel file names, paths
|   +-- sdk-config.yaml           <- Browser, proxy, screenshot settings
|   +-- log4j.properties          <- Log4j1 config (legacy)
|   +-- log4j2.properties         <- Log4j2 config (active)
+-- webDrivers/                   <- Optional: local chromedriver binary
|   +-- chromedriver.exe          <- Set browser.chromeDriverPath in sdk-config.yaml to use
|                                    Leave empty to let WebDriverManager download automatically
+-- src/
    +-- test/
        +-- java/
        |   +-- com/<yourco>/automation/
        |       +-- uiActions/    <- Page Objects -- ALWAYS extend TestBase
        |       +-- testCases/    <- Test Classes -- ALWAYS extend TestBase
        |       +-- tools/
        |           +-- LocatorInvestigator.java
        +-- resources/
            +-- testData/
                +-- YourApp_STG_TestData.xlsx
```

---

## 6. Configuration Reference

### 6.1 `configuration/config.properties` -- Required

This file controls which URLs and Excel files the framework uses per environment.
The environment is selected at run time with `-Denvironment=stg`.

```properties
# Base URLs -- one per environment
dev_base_url     = https://your-app-dev.example.com/
tst_base_url     = https://your-app-tst.example.com/
stg_base_url     = https://your-app-stg.example.com/
nonprod_base_url = https://your-app-nonprod.example.com/
prod_base_url    = https://your-app-prod.example.com/

# Browser (overridden at run time with -DbrowserName=chrome)
browser = chrome

# Paths -- relative to project root
testDataDir    = /src/test/resources/testData/
screenshotsDir = /test-output/screenshots/
extReportDir   = /test-output/reports/

# Excel test data files -- one per environment
dev_data_set     = YourApp_DEV_TestData.xlsx
tst_data_set     = YourApp_TST_TestData.xlsx
stg_data_set     = YourApp_STG_TestData.xlsx
nonprod_data_set = YourApp_NONPROD_TestData.xlsx
prod_data_set    = YourApp_PROD_TestData.xlsx
```

### 6.2 `configuration/sdk-config.yaml` -- Optional Advanced Settings

Controls browser behavior, proxy, screenshots, logs, crawler artifacts, gap
reports, and the built-in accessibility engine. All artifact output directories
are now centralized under one `reporting:` section.

```yaml
browser:
  default: chrome            # chrome | firefox | edge
  headless: false            # true for CI/CD
  windowSize: "1920x1080"
  pageLoadTimeoutSeconds: 30
  implicitWaitSeconds: 0     # keep 0 -- use TestBase explicit waits
  chromeDriverPath: ""       # optional: absolute path to a local chromedriver binary
                             # leave empty (default) -- WebDriverManager downloads
                             # the latest version matching your installed Chrome
                             # example: "C:/tools/chromedriver/chromedriver.exe"
```

**Pinning a specific ChromeDriver version (optional):**

By default, `WebDriverFactory` never requires a manually downloaded
ChromeDriver -- WebDriverManager resolves and downloads the correct version
automatically at test-run time. Only set `chromeDriverPath` if you need to
pin an exact/offline binary (e.g. no internet access on a CI runner, or a
version WebDriverManager doesn't yet know about):

1. Download the matching binary for your OS from
   [chromedriver.chromium.org](https://chromedriver.chromium.org) (or the
   [Chrome for Testing endpoints](https://googlechromelabs.github.io/chrome-for-testing/)
   for Chrome 115+).
2. Place it anywhere in your project (e.g. `webDrivers/chromedriver.exe`).
3. Set `browser.chromeDriverPath` to that path (relative or absolute):
   ```yaml
   browser:
     chromeDriverPath: "webDrivers/chromedriver.exe"
   ```
4. If the configured path is missing or the binary fails to initialize
   (version mismatch, corrupt file), `WebDriverFactory` logs a warning and
   **automatically falls back to WebDriverManager** -- it never fails the
   test run because of a bad `chromeDriverPath`.

```yaml
proxy:
  enabled: false
  host: "bcpxy.nycnet"       # your corporate proxy
  port: 8080

screenshots:
  captureOnFailure: true
  captureOnStep: false

api:
  mailinator:
    apiKey: "YOUR_MAILINATOR_API_KEY"
    domain: "mailinator.com"
    privateDomain: true
    inboxInitialWaitSeconds: 5
    inboxPollIntervalSeconds: 3
    inboxPollTimeoutSeconds: 60

logging:
  level: INFO
  sdkLogFile: "test-output/logs/sdk.log"
  seleniumLogFile: "test-output/logs/selenium.log"
  browserLogFile: "test-output/logs/browser.log"
  enableSeleniumLogs: true
  enableBrowserConsoleLogs: true
  enableDriverLogs: false

crawler:
  pageObject:
    package:   "com.yourcompany.automation.uiActions"
    outputDir: "src/main/java/com/yourcompany/automation/uiActions/"

reporting:
  screenshotsDir:   "test-output/screenshots"    # -Dreporting.screenshotsDir
  domDumpsDir:      "test-output/dom-dumps"      # -Dreporting.domDumpsDir
  logsDir:          "test-output/logs"           # -Dreporting.logsDir
  crawlerDir:       "test-output/crawler"        # -Dreporting.crawlerDir
  accessibilityDir: "test-output/accessibility"  # -Dreporting.accessibilityDir
  gapOutputDir:     "docs/test-case-gaps"        # -Dreporting.gapOutputDir

accessibility:
  checking.enabled: false
  fail.on.violation: false
  wcag.tags: "wcag2a,wcag2aa"
  debug: false

  session.noise.threshold: MINOR
  session.max.scans.per.url: 1
  session.dedup.cooldown.ms: 0
  session.dedup.dom.fingerprint: true
  session.allowed.rules: ""
  session.allowed.urls: ""

  session.spa.poll.interval.ms: 0
  scan.wait.enabled: true
  scan.wait.timeout.ms: 5000
  scan.iframe.max.depth: 3

  engine.interaction.enabled: true
  engine.wcag22.enabled: true
  engine.structural.enabled: true
  engine.motion.enabled: true

  scan.on.dialog: false
  scan.dialog.poll.interval.ms: 1000
```

Backward-compatible aliases still work for older projects: `screenshots.outputDir`,
`screenshots.domDumpDir`, `crawler.pageObject.reportDir`, and `reporting.gapOutputDir`.
New projects should use the unified `reporting:` keys only.

### 6.3 Config Path Override

By default, the SDK reads from `./configuration/`. You can override this:

```bash
# Override via system property
mvn test -Dsdk.config.dir=/opt/ci/config -Denvironment=stg

# Override via environment variable
set SDK_CONFIG_DIR=C:\ci\config
mvn test -Denvironment=stg
```

---

## 7. The Element Crawler -- Generating Page Objects

**Never write `@FindBy` locators by hand.** Always run the crawler first.

The crawler opens a live page, scans every element, tests each candidate locator
for uniqueness, and generates a ready-to-use Page Object Java file with only the
stable locators.

### What the Crawler Can Do -- Capability Overview

| Capability | What it means for you |
|---|---|
| Full-page element scan | Finds every candidate locator (`@id`, `@data-testid`, `@formcontrolname`, `@name`, `@aria-label`, `@placeholder`, text, `@routerlink`, `@href`) for every element on the page |
| Uniqueness testing | Every candidate XPath is tested live against the page (`driver.findElements(...).size()`) and labeled `UNIQUE [x]` / `NOT UNIQUE` / `DYNAMIC` / `STALE` / `STRUCTURAL` -- you never have to guess |
| Page Object generation | Emits a ready-to-edit `.java` file with `@FindBy` fields, using only the stable strategy for each element |
| iframe / frame support | Automatically descends into same-origin `<iframe>`/`<frame>` children and tags elements with the frame index needed to reach them |
| Label-following resolution | Resolves visible label text to its control via `<label for>`, `aria-labelledby`, and Angular Material `<mat-form-field>` proximity, and names the generated field after the label |
| Modal detection | Detects an open Angular CDK modal and generates a separate `_Modal.java` file for its subtree instead of missing it or mixing it with the background page |
| Map / canvas widget detection | Recognizes Google Maps, Leaflet, Mapbox GL, MapLibre GL, OpenLayers, and Bing Maps containers and routes you to `MapWidgetHelper` instead of generating brittle per-tile locators (see [7.1](#71-map-widgets--google-maps-leaflet-mapbox-gl-openlayers-bing-maps)) |
| Shadow DOM traversal | Recurses into open shadow roots (including nested ones) and generates a lookup method instead of an impossible cross-boundary `@FindBy` (see [7.2](#72-shadow-dom--web-components-litstencil-salesforce-lightning-lwc)) |
| Dynamic / multi-step forms | `DataDrivenCrawler` re-crawls after each simulated user action (dropdown select, click, type) to catch fields that only appear after an earlier step -- see [Data-Driven Crawler](#data-driven-crawler----dynamic-forms) below |
| Self-healing during a crawl | Automatically retries a stale DOM scan, waits for pending network requests to settle, and can skip re-processing a page state it has already seen -- see [7.3 Crawler Reliability & Self-Healing Features](#73-crawler-reliability--self-healing-features) |
| Safe overwrite protection | Never clobbers a hand-written page object -- writes to `ClassName_Crawled.java` instead so you can merge manually |

### Configure the output location (required for new projects)

Before running the crawler for the first time, set the `crawler` section in
`configuration/sdk-config.yaml` so generated files land in **your** project's
package -- not the SDK default:

```yaml
crawler:
  pageObject:
    package:   "com.yourcompany.automation.uiActions"
    outputDir: "src/main/java/com/yourcompany/automation/uiActions/"
```

You can also override per-run without editing the YAML:

```bash
mvn exec:java \
  -Dexec.mainClass="com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
  -Dexec.args="LoginPage https://your-app.example.com/#/login user@example.com pass" \
  -Dpog.package=com.yourcompany.automation.uiActions \
  -Dpog.outputDir=src/main/java/com/yourcompany/automation/uiActions/
```

**Resolution order** (first non-empty value wins):

| Priority | Source | Property / Key |
|----------|--------|---------------|
| 1 | `-D` system property | `-Dpog.package`, `-Dpog.outputDir`, `-Dpog.reportDir` |
| 2 | `sdk-config.yaml` | `crawler.pageObject.package` / `outputDir` / `reportDir` |
| 3 | Built-in default | `com.poletop.automation.uiActions` (backward compat) |

### Run the crawler

```bash
# Option A -- via TestNG crawler suite (when LocatorInvestigator exists in the project)
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg \
         -DbrowserName=chrome \
         -Dinv.email=your@email.com \
         -Dinv.password=yourpassword

# Option B -- standalone generator (no suite XML required)
mvn exec:java \
  -Dexec.mainClass="com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
  -Dexec.args="LoginPage https://your-app.example.com/#/login your@email.com yourpassword"
```

### Outputs

| File | Location | Purpose |
|------|----------|---------|
| Page Object `.java` | `crawler.pageObject.outputDir` in `sdk-config.yaml` | Ready-to-edit page object in your package |
| Locator report `.txt` | `crawler.pageObject.reportDir` (default: `test-output/crawler/`) | Full locator list with uniqueness markers |

### Uniqueness markers in the report

| Marker | Meaning | Action |
|--------|---------|--------|
| `UNIQUE [x]` | Exactly 1 element -- safe to use | ? Keep |
| `NOT UNIQUE (N found)` | Multiple matches | ? Delete |
| `DYNAMIC` | Auto-generated ID (e.g. `mat-input-3`) | ? Delete |
| `STALE` | 0 elements found | ? Delete |
| `STRUCTURAL` | Position-based fallback | ? Delete |

> After generating, open the `.java` file and **remove all locators that are not `UNIQUE [x]`**.

### Advanced Crawler Features

#### iframe / frame support
`ElementCrawler` automatically crawls same-origin `<iframe>` and `<frame>` children.
Elements inside frames are tagged `inFrame=true` / `frameIndex=N` and the generated
page object emits `driver.switchTo().frame(N)` in action method comments.

#### Label-following resolution
The crawler resolves visible label text to its associated control via three mechanisms:
`<label for="X">` explicit association, `aria-labelledby` ARIA pointer, and Angular
Material `<mat-form-field>` proximity. Generated field names use the human-readable
label (e.g. `boroughField`) and the locator uses a label-following XPath:
`//mat-form-field[.//mat-label[normalize-space(.)='Borough']]//mat-select`.

#### Modal detection
`PageObjectGenerator.generateFromCurrentPageWithModalCheck(className)` detects open
Angular CDK modals before crawling. If a modal is present it generates two files:
`ClassName.java` (background page) and `ClassName_Modal.java` (modal subtree), with
`crawlSubtree(WebElement root)` used internally for the modal pass.

#### Safe overwrite protection
If a hand-crafted page object already exists, `PageObjectGenerator` writes to
`ClassName_Crawled.java` instead of overwriting it. Review the `_Crawled` file and
manually merge any improved locators.

### Data-Driven Crawler -- Dynamic Forms

Standard `ElementCrawler` does a single-pass snapshot. For enterprise apps where
selecting a value dynamically shows/hides fields, use `DataDrivenCrawler` instead.

**How it works:**
```
Navigate to page -> baseline snapshot
  -> Execute Step 1 (e.g. select dropdown value)
  -> MutationObserver detects DOM change + full page re-crawl
  -> Tag new elements "Step 1: ..."
  -> Execute Step 2 ...
  -> Merged page object with all elements from all page states
```

**Full-scan mode (default `true`):** crawls the complete page at every step, not just
diff elements. Ensures elements present before any mutation are never missed.
Use `DataDrivenCrawler.setDiffOnlyMode(true)` to revert to diff-only for shallow forms.

**Page-state tagging:** each element is tagged with the URL route segment where it
was first discovered (e.g. `dashboard`, `reservation/12345`). Elements seen on multiple
states are tagged `Multiple pages: A, B`.

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

Generated output includes step-origin comments:
```java
// [Visible after: Step 2: Select Noise Category]
@FindBy(xpath = "//mat-select[@formcontrolname='noiseCategory']")
public WebElement noiseCategoryDropdown;
```

**`CrawlerStep` semantic factories:**

| Factory | Resolves by |
|---|---|
| `CrawlerStep.selectByLabel("Label", "Value")` | Visible label text / mat-label |
| `CrawlerStep.typeByPlaceholder("hint", "value")` | `@placeholder` attribute |
| `CrawlerStep.typeByFormControlName("name", "value")` | `@formcontrolname` |
| `CrawlerStep.clickByText("Submit")` | Visible element text |
| `CrawlerStep.clickByAriaLabel("Close")` | `@aria-label` attribute |
| `CrawlerStep.select("//xpath", "Option")` | Raw XPath (explicit) |

### `AbstractLocatorInvestigator` -- Consumer Investigator Base

Extend this SDK class in your consumer project's `LocatorInvestigator` tool.
Override exactly 3 methods; all infrastructure is provided by the SDK:

| Method | What to implement |
|---|---|
| `performLogin(email, password)` | Your app's login form interaction |
| `isSessionAlive()` | XPath check for a reliable post-login element |
| `defineCrawlSteps()` | List of pages to crawl in order |

The SDK owns: driver init, role registration, `@Test runFullCrawl()`, login fail-fast,
session reuse, `crawlPage()`, nav helpers, and the crawl summary log.

### 7.1 Map Widgets -- Google Maps, Leaflet, Mapbox GL, OpenLayers, Bing Maps

Map libraries do not follow the normal "unique, stable DOM node" model everything
else in this SDK is built around: tiles and many markers are painted on a
`<canvas>`/WebGL surface with **zero DOM representation**, and any marker DOM that
does exist is often regenerated with unstable, internal, auto-generated CSS class
names. `ElementCrawler` cannot -- and should not -- try to generate `@FindBy`
locators for that content.

Instead, `ElementCrawler` now **detects** known map containers by CSS-class
fingerprint and tags them on `ElementInfo` rather than treating them as ordinary
elements:

| Provider | Detected via container class | `ElementInfo.mapProvider` value |
|---|---|---|
| Google Maps | `.gm-style` | `"Google Maps"` |
| Leaflet | `.leaflet-container` | `"Leaflet"` |
| Mapbox GL JS | `.mapboxgl-map` | `"Mapbox GL JS"` |
| MapLibre GL JS | `.maplibregl-map` | `"MapLibre GL JS"` |
| OpenLayers | `.ol-viewport` | `"OpenLayers"` |
| Bing Maps | `.MicrosoftMap` | `"Bing Maps"` |
| Unrecognized `<canvas>` | -- | `"Generic canvas (unrecognized map/chart library)"` |

`PageObjectGenerator` emits a dedicated **"Map / Canvas Widgets"** section for each
detected container -- a `WebElement` field plus guidance comments pointing at
`MapWidgetHelper` instead of brittle per-tile/per-marker locators. Real DOM elements
*inside* the map (search boxes, aria-labeled markers, zoom controls) are still
discovered normally alongside everything else on the page.

**Interacting with a detected map** -- use `com.test.automation.sdk.utility.MapWidgetHelper`
directly, or the `TestBase` convenience wrappers:

```java
// Wait for the map to render (heuristic: sized canvas or tile images + settle buffer --
// there is no universal "map fully loaded" DOM event across libraries)
waitForMapReady(mapPage.mapWidget);

// Type an address into the map's search/autocomplete box and submit it
// (clicks the first Google Places .pac-item suggestion when present, else ENTER)
searchMapAddress(mapPage.mapWidget, "350 5th Ave, New York, NY");

// Find a marker by its accessible label (tries aria-label, then alt, then title)
WebElement pin = findMapMarkerByLabel(mapPage.mapWidget, "My Store");
if (pin != null) {
    pin.click();
} else {
    // No DOM presence at all (canvas/WebGL-drawn marker, e.g. legacy google.maps.Marker) --
    // last-resort pixel fallback. Assert on the resulting side effect (info window,
    // URL change), never on the marker itself.
    com.test.automation.sdk.utility.MapWidgetHelper.clickAtPixelOffset(driver, mapPage.mapWidget, 0, 0);
}
```

**Tip for Google Maps specifically:** setting a `google.maps.Marker`'s `title`
property causes Google to render a real `aria-label` in the DOM -- this is by far
the most reliable locator for legacy markers. The newer `AdvancedMarkerElement` API
lets you set fully custom HTML/`aria-label` directly. Never rely on Google's
internal/auto-generated CSS class names; they change across API versions.

---

### 7.2 Shadow DOM -- Web Components, Lit/Stencil, Salesforce Lightning (LWC)

XPath **cannot cross a shadow boundary** -- a W3C DOM spec limitation, not a
tooling gap. Any framework that renders through a real `element.shadowRoot`
(Web Components, Lit, Stencil, Salesforce Lightning/LWC) produces content that
`driver.findElement(By.xpath(...))` simply cannot see. Angular's default view
encapsulation does **not** use real shadow roots, so it needs no special handling.

`ElementCrawler` now recurses into every **open** shadow root on the page
(including nested shadow roots) and tags discovered elements on `ElementInfo`:

| Field | Meaning |
|---|---|
| `inShadowDom` | `true` if the element lives inside an open shadow root |
| `shadowHostXpath` | Light-DOM XPath to the shadow-root HOST element |
| `shadowRelativeCss` | CSS selector for the element, resolved via `host.getShadowRoot()` (shadow roots only support CSS lookups, never XPath) |

Because a single XPath can't express this two-step lookup, `PageObjectGenerator`
emits a **method** instead of a `@FindBy` field for these elements:

```java
public WebElement submitButton() {
    WebElement host = driver.findElement(By.xpath("//my-form-component"));
    return host.getShadowRoot().findElement(By.cssSelector("button#submit"));
}
```

Or use it directly via the `TestBase` convenience wrapper:

```java
WebElement submit = findInShadowDom(By.xpath("//my-form-component"), "button#submit");
submit.click();
```

**Limitation (not solvable):** *closed* shadow roots (`element.shadowRoot ===
null` from any external script) are undiscoverable by design -- an intentional
browser security boundary, not a crawler gap. If a component uses
`attachShadow({mode: 'closed'})`, flag it to the development team; automation
cannot reach inside it.

### 7.3 Crawler Reliability & Self-Healing Features

Modern SPAs re-render mid-scan, fire background XHR/fetch calls, and throw
`StaleElementReferenceException` at the worst possible moment. These four
features (ported from proven patterns in Scrapy/Crawlee/Playwright for web,
and Google's Robo/Fastbot for mobile exploratory crawling) make a crawl
resilient to that churn **without changing any existing behavior** -- every
one of them is either automatic-and-transparent or opt-in.

| Feature | Where | Default | What it does |
|---|---|---|---|
| Stale-element retry with backoff | `ElementCrawler.crawlSubtree()` | Always on | If the DOM mutates mid-scan and a `StaleElementReferenceException` is thrown, the whole tag scan (not just the one element) is retried up to 3 times with a 150ms x attempt linear backoff, instead of failing or silently skipping elements |
| Actionability pre-check | `ElementCrawler.safeClick(driver, element)` | Always on (when you call `safeClick`) | Before clicking, waits up to 300ms for the element to become clickable (Playwright-style proactive check). If it's still not interactable in time, falls through to the click attempt anyway -- this is a best-effort speed-up, never a hard gate |
| JS-click fallback | `ElementCrawler.safeClick(driver, element)` | Always on (when you call `safeClick`) | Scrolls the element into view and performs a normal click; only on `ElementClickInterceptedException` (e.g. a sticky header or overlay is blocking it) does it retry with a JavaScript-dispatched click |
| Network-idle detection | `ElementCrawler.waitForNetworkIdle()`, wired into `waitForPageReady()` | Always on | Patches `fetch()`/`XMLHttpRequest` via injected JS to track in-flight requests, and waits for a quiet period before the crawler treats the page as "ready" -- catches async widgets that finish loading just after `document.readyState === 'complete'` |
| Page-state deduplication | `DataDrivenCrawler.setStateDeduplication(true)` | **Off by default** | Computes a structural fingerprint (tag + id + name + type of interactive elements, ignoring text/values) after each step; if a non-navigating step produces a page state already seen, its snapshot is skipped instead of re-merged -- prevents infinite-loop-style duplicate work on forms that toggle back and forth between the same two states |

```java
// Enable state dedup for a form that can loop between the same states
// (e.g. Back/Next toggling between two already-seen steps):
DataDrivenCrawler ddCrawler = new DataDrivenCrawler(driver);
ddCrawler.setStateDeduplication(true);
List<ElementInfo> elements = ddCrawler.crawlTestCase(url, "TC-311: Noise Complaint", steps);
```

```java
// safeClick is available directly if you're driving a crawl step by hand:
ElementCrawler.safeClick(driver, someElement);
```

> **None of this changes what locators end up in your generated Page Object.**
> These features only make the *scan itself* more resilient to a flaky or
> slow-loading page -- they do not alter the uniqueness rules or which
> locators are marked `UNIQUE [x]`.

---



Every Page Object must follow this exact template.

```java
package com.yourcompany.automation.uiActions;

import com.test.automation.sdk.testbase.TestBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class LoginPage extends TestBase {

    public static final Logger log = LogManager.getLogger(LoginPage.class.getName());

    // -- Locators -----------------------------------------------------------
    // Only UNIQUE [x] locators from the crawler report. XPath only -- no CSS.

    @FindBy(xpath = "//input[@id='email']")
    public WebElement emailField;

    @FindBy(xpath = "//input[@id='password']")
    public WebElement passwordField;

    @FindBy(xpath = "//button[normalize-space(.)='Log In']")
    public WebElement loginButton;

    @FindBy(xpath = "//h1[normalize-space(.)='Dashboard']")
    public WebElement dashboardHeader;

    // -- Constructor --------------------------------------------------------

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(driver, this);
    }

    // -- Actions ------------------------------------------------------------

    public void enterEmail(String email) {
        waitForElementPresent(driver, emailField);
        clearAndType(emailField, email);
    }

    public void enterPassword(String password) {
        waitForElementPresent(driver, passwordField);
        clearAndType(passwordField, password);
    }

    public void clickLogin() {
        fluentWaitUntilElementToBeClickable(loginButton);
        safeClick(loginButton);
        waitUntillPageLoad();
    }

    public void login(String email, String password) {
        enterEmail(email);
        enterPassword(password);
        clickLogin();
    }

    public boolean isDashboardLoaded() {
        try {
            waitForElementPresent(driver, dashboardHeader);
            return dashboardHeader.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
}
```

### Rules
- Always `extends TestBase`.
- Always `PageFactory.initElements(driver, this)` in the constructor.
- Only XPath in `@FindBy` -- no CSS selectors.
- No raw `sendKeys()` -- always use `clearAndType()`.
- No `Thread.sleep()` -- always use a TestBase wait method.
- No `new WebDriverWait(...)` -- always use a TestBase wait method.

---

## 9. TestBase API -- What You Can Use

All methods below are available in any class that extends `TestBase`.

### Waiting -- before interacting with elements

| Method | When to use |
|--------|-------------|
| `waitForElementPresent(driver, element)` | Before reading text or attributes |
| `fluentWaitUntilElementToBeClickable(element)` | Before every click |
| `waitUntillPageLoad()` | After any navigation or page transition |
| `waitForElementToDisappear(By locator)` | After form submit -- wait for spinner to disappear |
| `waitForAttributeToContain(element, "class", "active", 30)` | Wait for CSS state changes |
| `waitForTextPresent(element, "text")` | Wait for dynamic text to appear |
| `implicitWait(int sec)` | When `driver.manage().timeouts().implicitlyWait()` is unreliable -- polls `document.readyState` via FluentWait instead |

### Text input

| Method | When to use |
|--------|-------------|
| `clearAndType(element, value)` | **Any** text field input |
| `clearAndTypeAngular(element, value)` | Angular reactive forms -- dispatches `input`+`change` events + TAB to trigger blur validators |

### Clicks

| Method | When to use |
|--------|-------------|
| `safeClick(element)` | Standard clicks -- retries on timing issues |
| `clickOnElementbyJavaScript(element)` | When a normal click is blocked by an overlay |

### Reading element text

| Method | When to use |
|--------|-------------|
| `safeGetText(element)` | Any `getText()` call -- handles stale element retries |

### Verification (in test classes)

| Method | When to use |
|--------|-------------|
| `verifyText(expected, actual)` | Assert element text equals expected value |

### Dropdowns

| Method | When to use |
|--------|-------------|
| `selectOptionInDropDownBox(element, text)` | Native HTML `<select>` |
| `visibleInDropDownList(element, text)` | Native HTML `<select>` by visible text |
| `selectByValueAngular(element, value)` | Angular two-way binding `<select>` -- dispatches Angular events so form Save is not silently blocked |
| `getSelectedOption(element)` | Read current selection |
| `getAllDropdownOptions(element)` | Get all options as a list |
| `selectFromCustomWidget(trigger, panelLocator, optionLocator, value)` | Custom listbox/panel widget (e.g. Angular Material) needing select -> verify -> retry safety; `optionLocator` must be relative (`.//...`) to the panel |

> For Angular `<mat-select>`: click the trigger -> click the `mat-option` element directly, or use `selectFromCustomWidget` for a retry-safe version of the same flow.

### Waiting -- Page & Navigation

| Method | When to use |
|--------|-------------|
| `waitUntillPageLoad()` | After any navigation or page transition |
| `waitForUrlContains(partial, timeoutSeconds)` | After SSO/SAML login -- confirm redirect completed before proceeding. Throws `TimeoutException` on timeout. |
| `waitForNavigationOrElement(urlBefore, landmark, timeout)` | After a click in an Angular SPA -- resolves when URL changes OR landmark element appears |
| `waitForModalOrUrlChange(urlBefore, timeout)` | After a click that opens an Angular CDK modal -- polls for `mat-dialog-container`, `role=dialog`, or URL change |
| `implicitWait(int sec)` | Alternative to driver-level implicit wait -- polls `document.readyState` via FluentWait every 500 ms |

### Scrolling

| Method | When to use |
|--------|-------------|
| `scrollIntoView(element)` | **Primary.** Viewport-aware scroll -- calculates exact offset, falls back to JS. Returns the element for fluent chaining. |
| `scrollToElement(element)` | JavaScript scroll with UI-settle pause |
| `scrollToTop()` / `scrollToBottom()` | Full-page scrolling |

### Checkboxes

| Method | When to use |
|--------|-------------|
| `selectCheckbox(element)` | Check a box (idempotent -- won't double-click) |
| `deselectCheckbox(element)` | Uncheck a box (idempotent) |

### Windows & Tabs

| Method | When to use |
|--------|-------------|
| `switchToNewWindow()` | After an action opens a new tab |
| `closeCurrentTabAndSwitchToParent()` | Close the new tab and return |
| `openNewTab()` | Programmatically open a new tab |

### Frames

| Method | When to use |
|--------|-------------|
| `switchtoFrame(frameName)` | Enter a named iframe |
| `switchtoDefaultFrame()` | Return to main page content |

### Alerts

| Method | When to use |
|--------|-------------|
| `acceptAlert()` | Confirm a browser alert dialog |
| `dismissAlert()` | Cancel a browser alert dialog |
| `getAlertText()` | Read alert message text |

### Mouse & Keyboard (Advanced)

| Method | When to use |
|--------|-------------|
| `hoverOverElement(element)` | Reveal hover menus |
| `doubleClick(element)` | Double-click interaction |
| `pressKey(element, Keys.TAB)` | Send keyboard keys |
| `dragAndDrop(source, target)` | Drag-and-drop interactions |

### Screenshots

| Method | When to use |
|--------|-------------|
| `getScreenShot(name)` | Manual screenshot at any test step |
| `saveDomDump(driver, testName)` | Save the current page source as an HTML DOM dump |
| `captureScreen(fileName)` | Returns the file path after capturing |

> Failure screenshots and DOM dumps are taken **automatically** by `Listener` -- no manual code needed.

### Data-driven test naming

When a `@Test` uses `@DataProvider`, call `setCurrentTestCaseName(testCaseName)`
immediately after the `runMode` skip check. This lets Listener rename the TestNG
entry to `methodName.testCaseName`, and it uses the same `testCaseName` for
failure screenshot and DOM dump filenames so each row stays distinct in CI and
HTML reports.

### Data Generators

| Method | Returns |
|--------|---------|
| `randomEmailAddress()` | `test123456@doitt.nyc.gov` |
| `randomEmailAddress("domain.com")` | `test12345678@domain.com` |
| `randomPassword()` | `test123456` |
| `newUniqueUsername()` | `user2606111503001` (timestamp-based) |
| `randomString()` | 5-digit numeric string |

### File Upload

| Method | When to use |
|--------|-------------|
| `uploadFile(fileInputElement, "C:\\path\\to\\file.pdf")` | Upload via `<input type="file">`. Works with Selenium Grid. |

### Email Verification (Mailinator)

Use these methods in test classes to verify email-based flows. Never import
`MailinatorEmailReader` directly -- always go through TestBase.

| Method | When to use |
|--------|-------------|
| `getConfirmationEmailUrl(baseURL, email)` | Get a registration/confirmation link from the inbox |
| `getDeactivationEmailUrl(baseURL, email)` | Get a deactivation/unsubscribe link |
| `getEmailUrl(templateName, baseURL, email)` | Get any link -- template name matches entry in `mailinator-email-templates.yaml` |
| `getEmailText(templateName, email)` | Extract a text value (OTP, account ID, date) from the email body |
| `deleteEmailById(emailId)` | Delete a specific message by ID (auto-delete already happens after get* calls) |

> All methods poll with retry. Processed messages are auto-deleted after extraction
> so they cannot interfere with parallel tests on the same address.
> See [Section 9.1 -- Mailinator Setup](#91-mailinator-setup) for configuration.

---

## 9.1 Mailinator Setup

Required when your tests verify email flows (registration, password reset, OTP, etc.).

### Step 1 -- Add API key to `sdk-config.yaml`

```yaml
api:
  mailinator:
    apiKey: "YOUR_MAILINATOR_API_KEY"
    domain: "mailinator.com"          # or your private Mailinator domain
    privateDomain: true
    inboxInitialWaitSeconds: 5        # wait before first inbox poll
    inboxPollIntervalSeconds: 3       # time between retries
    inboxPollTimeoutSeconds: 60       # total timeout
```

### Step 2 -- Create `configuration/mailinator-email-templates.yaml`

Copy `mailinator-email-templates.yaml.template` from the SDK (extracted by
`InstructionExtractor` on first run) and define one template per email type:

```yaml
templates:

  confirmation:
    subjectContains: "Confirm Your Email"
    urlPathPatterns: "validateToken,validateReset"
    excludePatterns: "deactivate,amp;"
    textPattern: ""
    masks: ""

  passwordReset:
    subjectContains: "Reset Your Password"
    urlPathPatterns: "validateReset"
    excludePatterns: "amp;"
    textPattern: ""
    masks: ""

  otpCode:
    subjectContains: "Your Verification Code"
    urlPathPatterns: ""
    excludePatterns: ""
    textPattern: "Your code is: (\\d{6})"
    masks: "trim"
```

**Template fields:**

| Field | Purpose |
|-------|---------|
| `subjectContains` | Subject must contain this text (case-insensitive). Blank = match any. |
| `urlPathPatterns` | Comma-separated path segments -- extracted URL must contain one. Used by `getEmailUrl`. |
| `excludePatterns` | Patterns that disqualify a URL match. |
| `textPattern` | Java regex with **one** capture group. `group(1)` is returned. Used by `getEmailText`. |
| `masks` | Ordered transformations: `trim`, `uppercase`, `lowercase`, `substring:start:end`, `dateFormat:in:out`, `replaceAll:regex:replacement` |

### Step 3 -- Use in test classes

```java
// -- Registration confirmation link ----------------------------------------
step("Get confirmation URL from email");
String confirmUrl = getConfirmationEmailUrl(baseURL, "testuser@mailinator.com");
driver.get(confirmUrl);
waitUntillPageLoad();

// -- Password reset link ---------------------------------------------------
String resetUrl = getEmailUrl("passwordReset", baseURL, "testuser@mailinator.com");
driver.get(resetUrl);

// -- OTP / verification code -----------------------------------------------
String otp = getEmailText("otpCode", "testuser@mailinator.com");
Assert.assertNotNull(otp, "OTP code was not received");
myPage.enterOtp(otp);
```

> **Inbox isolation:** each `getEmail*` call auto-deletes only the single processed
> message. Never wipe an entire inbox -- other projects may share the same domain.

---

## 10. Writing Test Classes

Every test class must follow this exact template.

```java
package com.yourcompany.automation.testCases;

import static io.qameta.allure.Allure.step;

import com.test.automation.sdk.testbase.TestBase;
import com.yourcompany.automation.uiActions.LoginPage;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

public class Test_Login extends TestBase {

    // -- DataProvider -- maps to one sheet in the Excel test data file -------

    @DataProvider(name = "loginData")
    public Object[][] loginData() throws IOException {
        return getData("LoginSheet");
    }

    // -- Test method --------------------------------------------------------

    @Test(dataProvider = "loginData", priority = 1)
    public void testValidLogin(String testCaseName,
                               String email,
                               String password,
                               String runMode) throws Exception {

        // 1. Skip check -- ALWAYS first
        if ("N".equalsIgnoreCase(runMode))
            throw new SkipException("Skipping: " + testCaseName);

        // 2. Test steps with Allure reporting
        step("Navigate to application");
        driver.get(baseURL);
        waitUntillPageLoad();

        step("Enter credentials and log in");
        LoginPage loginPage = new LoginPage(driver);
        loginPage.login(email, password);

        step("Assert dashboard is loaded");
        Assert.assertTrue(loginPage.isDashboardLoaded(),
            "Dashboard header was not displayed after login");
    }
}
```

### Rules
- Always `extends TestBase`.
- Always `@DataProvider` backed by an Excel sheet.
- First line of every `@Test` method: check `runMode` -- skip if `"N"`.
- Use `step("...")` for every logical step (visible in Allure report).
- Use `Assert.assertTrue/assertFalse/assertEquals` -- never an `if` without assertion.
- Navigate back to the base URL or log out at the end of each test.

### 10.1 Data-Dependent Test Chains

If a test's preconditions describe a state that only **another** test class
produces (e.g. "the record must already be in an approved state"), the two
tests are data-dependent. This must be handled deliberately, not left to
accidental suite execution order:

1. **Document the chain** -- a comment diagram in the suite XML and a
   Javadoc note on each dependent class stating its precondition and which
   class/helper produces it.
2. **Sequence at the suite level** -- list dependent classes as separate
   `<test>` blocks in the required order, with `parallel="none"` for that
   block. Do not rely on alphabetical/declaration order across classes.
3. **Call an idempotent `setupXToStateY(...)` helper as the first action**
   in every dependent test -- even though the suite already sequences the
   classes. This makes the test pass when run alone
   (`mvn test -Dtest=<ClassName>`), on retry, and after suite reordering.
4. **Reset shared fixtures** -- if multiple classes reuse the same record,
   the setup helper should be corrective (drive the record to the required
   state regardless of its current state), not just a one-time creator.

Full rules and forbidden patterns: `.github/instructions/test-data-dependency.instructions.md`

---

## 11. Test Data (Excel)

Each test class reads from a sheet in the environment Excel file.

### Excel file naming
```
YourApp_STG_TestData.xlsx      (declared as stg_data_set in config.properties)
```

### Required sheet columns

Every sheet must start with these two columns and end with `runMode`:

| Column | Example | Description |
|--------|---------|-------------|
| `testCaseName` | `TC_001 -- Valid Login` | Identifies the row in reports |
| (your data cols) | `user@email.com` | Any columns your test needs |
| `runMode` | `Y` or `N` | `Y` = execute, `N` = skip |

### DataProvider method

```java
@DataProvider(name = "loginData")
public Object[][] loginData() throws IOException {
    return getData("LoginSheet");         // reads ExcelName set by @BeforeClass
}
```

Each row in the sheet maps to one test execution. The columns map positionally
to the `@Test` method parameters.

---

## 12. Running Tests

### Full regression suite

```bash
mvn test -Denvironment=stg -DbrowserName=chrome
```

### Single test class

```bash
mvn test -Dtest=Test_Login -Denvironment=stg -DbrowserName=chrome
```

### Run via TestNG suite XML

```bash
mvn test -Dsurefire.suiteXmlFiles=regression_suite.xml \
         -Denvironment=stg -DbrowserName=chrome
```

### Run the page object crawler

```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=your@email.com -Dinv.password=yourPassword
```

### Environment options

| `-Denvironment` | URL used |
|-----------------|---------|
| `dev` | `dev_base_url` |
| `tst` | `tst_base_url` |
| `stg` | `stg_base_url` (default) |
| `nonprod` | `nonprod_base_url` |
| `prod` | `prod_base_url` |

### Browser options

| `-DbrowserName` | Browser |
|----------------|---------|
| `chrome` | Google Chrome (default) |
| `firefox` | Mozilla Firefox |
| `edge` | Microsoft Edge |

---

## 13. Reports & Screenshots

The SDK generates screenshots, DOM dumps, logs, crawler reports, accessibility
artifacts, and gap/blocker reports automatically. The root directory for each
artifact type is configured from the unified `reporting:` section in
`configuration/sdk-config.yaml`.

### Unified `reporting:` configuration

| Key | Default | Runtime override | Used by |
|---|---|---|---|
| `reporting.screenshotsDir` | `test-output/screenshots` | `-Dreporting.screenshotsDir` | Failure screenshots and manual `getScreenShot()` captures |
| `reporting.domDumpsDir` | `test-output/dom-dumps` | `-Dreporting.domDumpsDir` | DOM dump HTML artifacts from `saveDomDump()` and listeners |
| `reporting.logsDir` | `test-output/logs` | `-Dreporting.logsDir` | SDK, Selenium, browser, and driver log files |
| `reporting.crawlerDir` | `test-output/crawler` | `-Dreporting.crawlerDir` | Locator crawler and PageObjectGenerator reports |
| `reporting.accessibilityDir` | `test-output/accessibility` | `-Dreporting.accessibilityDir` | Accessibility JSON, Excel, and HTML reports |
| `reporting.gapOutputDir` | `docs/test-case-gaps` | `-Dreporting.gapOutputDir` | Gap and blocker markdown reports |

Backward-compatible aliases remain supported for older projects: `screenshots.outputDir`, `screenshots.domDumpDir`, `crawler.pageObject.reportDir`, and `reporting.gapOutputDir`.

The SDK generates two report types automatically -- no configuration required.

### Allure Report

```bash
# Generate and open Allure report after test run
mvn allure:serve
```

Steps recorded via `step("...")` are visible in the Allure timeline.

### Extent Report

HTML report generated at `test-output/reports/` after every run.

### Screenshots and DOM dumps

Failure screenshots are captured automatically by `Listener` and attached to both
reports. On failure the SDK also saves the current page source for investigation
of dynamic UI state and validation messages. Manual captures:

```java
getScreenShot("my-screenshot");           // saves to reporting.screenshotsDir
String path = captureScreen("my-file");   // saves and returns the path
saveDomDump(driver, "my-dom-dump");       // saves to reporting.domDumpsDir
```

---

## 13.1 Gap & Blocker Reports -- Configurable Output

When Copilot cannot fully automate a test case (missing spec, technical blocker,
inaccessible environment), it writes a `.md` report explaining the reason instead
of creating a broken or incomplete test.

### Two report types

| File | When created | Naming |
|------|-------------|--------|
| `<ID>-gap-report.md` | ADO test case has missing/ambiguous steps or expected results | Gap in the *specification* -- spec must be fixed first |
| `<ClassName>-gap-report.md` | Local file test case is incomplete | Same: spec-level issue |
| `<ID>-blocker-report.md` | Test case is complete but a step is technically unautomatable | Technical blocker (no locator, external system, defect, etc.) |
| `<ClassName>-blocker-report.md` | Same but identified by class name | Technical blocker |

> **File names start with the ADO test case number** so that reports sort naturally
> by ID in the file system.  Example: `233504-blocker-report.md`.

### Configure the output directory

All `.md` report files land in the **configured gap output directory** from the
unified `reporting:` section:

```yaml
reporting:
  gapOutputDir: "docs/test-case-gaps"   # default -- change to any relative path
```

**Resolution order** (first non-empty value wins):

| Priority | Source | How to set |
|----------|--------|-----------|
| 1 | `-D` system property | `-Dreporting.gapOutputDir=my/custom/path` |
| 2 | `sdk-config.yaml` | `reporting.gapOutputDir: "docs/test-case-gaps"` |
| 3 | backward-compat alias | `-Dsdk.gapOutputDir=my/custom/path` |
| 4 | SDK default | `docs/test-case-gaps` |

Override at runtime without editing YAML:

```bash
mvn test -Dtest=Test_MyFeature -Dreporting.gapOutputDir=docs/blocked-tests
```

### Using GapReportWriter in Java

```java
import com.test.automation.sdk.utility.GapReportWriter;

// Write a gap report for an ADO test case (numeric ID -> 12345-gap-report.md)
GapReportWriter.writeGapReport("12345", markdownContent);

// Write a gap report for a local test class (Test_Login-gap-report.md)
GapReportWriter.writeGapReport("Test_Login", markdownContent);

// Write a blocker report (12345-blocker-report.md)
GapReportWriter.writeBlockerReport("12345", markdownContent);

// Resolve the configured directory programmatically
String dir = GapReportWriter.resolveGapOutputDir();
```

The directory is created automatically if it does not exist.

### These files should be committed

Gap and blocker reports are **project artifacts** -- commit them to source control.
They track which test cases are pending, why they are blocked, and what needs
to be resolved before automation can proceed.

```
docs/
+-- test-case-gaps/
    +-- 12345-gap-report.md              <- spec is incomplete; ADO-12345
    +-- 233504-blocker-report.md         <- technically cannot be automated yet; ADO-233504
    +-- Test_ExportPdf-blocker-report.md <- technically cannot be automated yet (class-based)
    +-- test-case-gap-report.md.template <- reference template (from InstructionExtractor)
```

### What a blocker report looks like

When Copilot hits a product defect or unresolvable technical barrier, it produces
a structured report like this so the issue can be triaged by manual QA, the
product owner, or the development team:

```markdown
# Test Automation Blocker Report — ADO-<ID>

## What is this file?
Documents a product defect or technical blocker discovered during automation
of ADO-<ID>. Escalated for manual verification and product team review.
Share with: Manual Testers, Product Owner, Development Team

## Identification

| Field | Value |
|---|---|
| Report File | `<ID>-blocker-report.md` |
| Test Case ID | ADO-<ID> |
| Test Case Title | <title> |
| Target Test Class | <ClassName>.java |
| Date Reported | <date> |
| Reported By | Copilot Automation / <user> |
| Status | 🔴 BLOCKED — Product Defect |

## Defect Summary
<One-paragraph description of what is broken and why it blocks the test.>

## Steps to Reproduce (Manual)
1. Log in as <role> ...
2. Navigate to ...
3. Expected: ...  /  Actual: ...

## Root Cause Analysis
<Description of the inconsistency or technical barrier.>

## Evidence
<Page source excerpts, screenshots, observed hrefs, etc.>

## Impact on Test Automation

| ADO Step | Automated? | Notes |
|---|---|---|
| 1 — ... | ✅ Yes | |
| 7 — ... | ❌ BLOCKED | Button not rendered — see defect above |

## Recommended Resolution
- [ ] Dev Team: ...
- [ ] Product Owner: ...
- [ ] QA (Manual): ...

## Resolution Log
| Date | Action | By |
|---|---|---|
| <date> | Defect discovered. RunMode=N set. | Copilot Automation |

## Next Steps After Resolution
1. Fix test data / confirm defect resolved
2. Set RunMode=Y in Excel sheet
3. Re-run: `mvn test -Dtest=<ClassName> -Denvironment=stg -DbrowserName=chrome`
4. Verify all steps pass and delete this blocker report
```

> The report is also written when the automation agent discovers a **product bug**
> mid-execution -- not just when the test case spec is incomplete.
> The agent will set `RunMode=N` in the Excel sheet and continue with other test cases.

---

## 13.2 Failure RCA Workflow

When a test fails the SDK automatically captures a **screenshot**, a **DOM dump**,
and a **log file** of/for the exact moment of failure. These three artifacts together
are the primary input for Root Cause Analysis (RCA) and must ALL be reviewed **before**
touching any code, running the crawler, or making any other change. Reviewing only the
screenshot and DOM dump is not sufficient -- the log is the only artifact that shows the
*sequence of actions* that led to the failure (screenshot/DOM only show the end state).

### Core Principle -- RCA Before Fix

> **Never start fixing until Root Cause Analysis is complete.**

A fix applied without a confirmed root cause is a guess. Guesses produce tests that
pass for the wrong reason, hide real product bugs, or break again on the next run.

### Evidence Sources -- Collect in This Order

| # | Source | Location | What it tells you |
|---|---|---|---|
| 1 | **Screenshot** | `reporting.screenshotsDir/<testCaseName>_<timestamp>.png` | What the browser showed at the exact moment of failure |
| 2 | **DOM dump** | `reporting.domDumpsDir/<testCaseName>_<timestamp>_DOM.html` | Live rendered HTML -- whether the element exists and with what attributes |
| 3 | **Log file** | `test-output/logs/<testCaseName>_<timestamp>.log` (or `target/surefire-reports/<Class>-output.txt`) | Execution timeline -- every action taken before the failure, exception chain, retry-exhaustion warnings |
| 4 | **Surefire report** | `target/surefire-reports/<ClassName>.txt` | Exact exception type, stack trace, line number |
| 5 | **ADO test case** | via MCP (if ADO ID exists) | Whether the expected behavior itself changed |
| 6 | **Git log** | `git log -1 -- <file>` | When test code was last changed vs when ADO was modified |
| 7 | **Source code** | test method + page object | The logic path that led to failure |

The `testCaseName` prefix in artifact file names matches the value passed to
`setCurrentTestCaseName()`. For non-data-driven tests it is the method name.
The TestNG HTML report (`test-output/reports/`) also contains a clickable link
to the DOM dump directly in the test result row.

Full step-by-step procedure: `.github/instructions/failure-investigation.instructions.md`

### RCA is Complete When You Can Answer Three Questions

1. **What exactly failed?** — exception type + line number from surefire report
2. **Why did it fail?** — root cause confirmed by screenshot, DOM dump, AND log evidence
3. **What is the minimal change that fixes the root cause?** — one targeted action

Only after answering all three -- with all three artifacts reviewed -- should you write
a single line of fix code. If your conclusion from the screenshot/DOM contradicts what
the log shows happened right before the failure, the log wins -- re-check your conclusion.

### Artifact-Driven Fast Path

Once screenshot, DOM dump, and log are reviewed, the fix action is already known.
Go directly to the indicated action -- no further investigation needed.

| Screenshot shows | DOM dump confirms | Log confirms | Fix action |
|---|---|---|---|
| Element not found / timeout | Attribute **missing** from DOM | Last logged action targeted this element | Run crawler → update `@FindBy` with new `UNIQUE [x]` locator |
| Element not found / timeout | Attribute present, **value changed** | Last logged action targeted this element | Update `@FindBy` value to match DOM → confirm `UNIQUE [x]` |
| Element not found / timeout | Attribute present, value unchanged | Retry-exhaustion warning logged | Check for overlay/modal in screenshot → add dismissal step |
| Validation error / toast visible | N/A | Action logged as completed before validation appeared | Fix test data in Excel or fix pre-condition setup |
| Wrong field value / assertion mismatch | N/A | Action logged as completed successfully | Compare screenshot actual vs assertion expected → update assertion or Excel |
| Login / session expired page | N/A | Unexpected redirect/URL change logged | Verify environment is up; check login step |
| Blank / partially loaded page | N/A | No follow-up action logged after page load | Add `waitUntillPageLoad()` after the navigation click preceding failure |
| Unexpected modal or overlay | Element behind overlay | Click logged but element remained | Add modal dismissal step in page object before the failing interaction |

> **If the DOM dump shows the exact attribute that changed, you do not need to run the
> crawler for the whole page.** Update only that one `@FindBy`, confirm it is `UNIQUE [x]`,
> and re-run the test. Running the crawler is only needed when the element attribute is
> missing entirely from the DOM.

### Where Artifacts Are Captured

The `Listener` registered in `regression_suite.xml` captures the screenshot and DOM
dump automatically on every `@Test` failure -- no code changes required. Log4j2 writes
the log file continuously throughout execution.

```java
// Equivalent manual capture (available in page objects and test classes):
getScreenShot("label");           // saves PNG to reporting.screenshotsDir
saveDomDump(driver, "label");     // saves HTML to reporting.domDumpsDir
```

---

## 14. Accessibility Testing

The SDK now includes a built-in accessibility framework with a full 5-layer WCAG
engine. No extra Maven dependency or custom listener registration is required.
The feature is **opt-in only** and has zero runtime overhead when disabled.

### 14.0 Underlying engine — what jar powers Layer 1

Layer 1 (static WCAG analysis) is powered by [**axe-core**](https://github.com/dequelabs/axe-core),
the industry-standard open-source accessibility rules engine from **Deque Systems**,
via its official Selenium Java binding:

| | |
|---|---|
| Maven coordinates | `com.deque.html.axe-core:selenium:4.10.1` |
| Vendor | Deque Systems, Inc. |
| License | MPL-2.0 (Mozilla Public License 2.0) |
| Entry point used internally | `com.deque.html.axecore.selenium.AxeBuilder` |
| Versioning scheme | The binding's major.minor tracks the axe-core rules version it embeds — `4.10.1` embeds axe-core `4.10.x` rules |
| Source / API docs | [axe-core-maven-html-selenium README](https://github.com/dequelabs/axe-core-maven-html/blob/develop/selenium/README.md) |
| Deque API reference | [Selenium Java API reference](https://docs.deque.com/devtools-for-web/4/en/java-api-selenium/) |
| Full rule catalogue | [List of axe 4.10 rules](https://dequeuniversity.com/rules/axe/4.10) — rule IDs shown here are what you pass to `accessibility.session.allowed.rules` |

**You do not add this dependency yourself** — it ships transitively with the SDK jar
(see `pom.xml`'s `<!-- Accessibility (axe-core) -->` block), so a consumer project's
`pom.xml` still only needs the single `cross-platform-functional-test-automation-sdk` dependency
described in [§4](#4-maven-dependency).

`AccessibilityChecker` wraps `AxeBuilder` internally and drives it with
`.withTags(...)` using the tags from `accessibility.wcag.tags` (§14.1) — it does not
currently expose `AxeBuilder`'s `include()`/`exclude()`/`withRules()`/`disableRules()`
chain directly. If you need to scope a scan to a CSS selector or limit it to specific
rule IDs, use `accessibility.session.allowed.rules` (§14.5) to suppress noisy rule IDs
project-wide, or open an SDK feature request if per-call scoping is needed.

### 14.1 Enable or disable accessibility scanning

```yaml
accessibility:
  checking.enabled: true
  fail.on.violation: false
  wcag.tags: "wcag2a,wcag2aa,wcag21aa"
```

| Key | Meaning |
|---|---|
| `accessibility.checking.enabled` | Master on/off switch. Default `false`. When `false`, the listener and WebEventListener checks short-circuit immediately. |
| `accessibility.fail.on.violation` | Throws on detected violations when enabled. |
| `accessibility.wcag.tags` | Comma-separated axe-core tag set for Layer 1 scans. |

### 14.2 Five engine layers

| Layer | Engine area | What it checks |
|---|---|---|
| 1 | axe-core | Static WCAG 2.x analysis using `axe-core:selenium` (see §14.0 for the exact jar/version). |
| 2 | Interaction | Keyboard navigation, focus handling, touch target size, text spacing, zoom reflow. |
| 3 | WCAG 2.2 | Focus appearance, dragging movements, target size minimum. |
| 4 | Structural | Headings, landmarks, page title, language, link text, duplicate IDs. |
| 5 | Motion | Auto-play media and `prefers-reduced-motion` behavior. |

### 14.3 Automatic listener scans vs manual API calls

| Mode | How it works | When to use |
|---|---|---|
| `A11yTestNGListener` auto-scan | Runs after each test when accessibility is enabled. The SDK registers it through `META-INF/services`. | Baseline suite-wide monitoring with no test changes. |
| `runAccessibilityScan(pageName)` | Runs the full 5-layer engine on demand for the current page. | Explicit checkpoints in critical workflows or hooks. |
| `assertNoAccessibilityViolations(pageName)` | Runs an axe-core scan and fails the test if violations exist. | Gating a page or workflow on zero static WCAG violations. |

Initialize the composite reporter once, typically from `@BeforeSuite`:

```java
@BeforeSuite
public void beforeSuite() {
    initAccessibility();
}
```

Manual usage from a test:

```java
step("Run full accessibility scan on dashboard");
runAccessibilityScan("Dashboard");

step("Assert no axe-core accessibility violations on dashboard");
assertNoAccessibilityViolations("Dashboard");
```

### 14.4 WebEventListener per-element scanning

When accessibility is enabled, `WebEventListener` performs a lightweight targeted
scan after `afterClick`, `afterSendKeys`, `afterClear`, and `afterSubmit`:

1. Guard with `AccessibilityChecker.isEnabled()` so disabled runs have zero overhead
2. Resolve the interacted element's XPath via JavaScript
3. Run `AccessibilityChecker.checkWithTags(driver, pageName, "wcag2a", "wcag2aa", "wcag21aa")`
   within that page context

This catches newly introduced accessibility issues close to the interaction that
triggered them while avoiding constant full-page rescans.

### 14.5 Configuration reference

| Key | Default | Description |
|---|---|---|
| `accessibility.checking.enabled` | `false` | Master opt-in toggle. |
| `accessibility.fail.on.violation` | `false` | Fail the test on any detected violation. |
| `accessibility.wcag.tags` | `wcag2a,wcag2aa` | Axe tags for Layer 1 scanning. |
| `accessibility.debug` | `false` | Writes debug lines for troubleshooting. |
| `accessibility.session.noise.threshold` | `MINOR` | Minimum severity recorded: `MINOR`, `MODERATE`, `SERIOUS`, `CRITICAL`. |
| `accessibility.session.max.scans.per.url` | `1` | Maximum scans per URL per run. `0` means unlimited. |
| `accessibility.session.dedup.cooldown.ms` | `0` | Cooldown between scans of the same URL. |
| `accessibility.session.dedup.dom.fingerprint` | `true` | Skip duplicate scans when DOM fingerprint is unchanged. |
| `accessibility.session.allowed.rules` | `""` | Comma-separated axe rule IDs to suppress. |
| `accessibility.session.allowed.urls` | `""` | Comma-separated URL fragments to skip. |
| `accessibility.session.spa.poll.interval.ms` | `0` | Poll interval for SPA URL changes. `0` disables polling. |
| `accessibility.scan.wait.enabled` | `true` | Wait for DOM settle before scanning. |
| `accessibility.scan.wait.timeout.ms` | `5000` | Maximum wait before scanning anyway. |
| `accessibility.scan.iframe.max.depth` | `3` | Nested iframe recursion depth. |
| `accessibility.engine.interaction.enabled` | `true` | Enable Layer 2 checks. |
| `accessibility.engine.wcag22.enabled` | `true` | Enable Layer 3 checks. |
| `accessibility.engine.structural.enabled` | `true` | Enable Layer 4 checks. |
| `accessibility.engine.motion.enabled` | `true` | Enable Layer 5 checks. |
| `accessibility.scan.on.dialog` | `false` | Auto-scan newly opened modal/dialog content. |
| `accessibility.scan.dialog.poll.interval.ms` | `1000` | Poll interval for dialog detection. |

Accessibility artifacts are written under `reporting.accessibilityDir`.

### 14.6 Output artifacts

All generated files are written under the directory resolved from
`reporting.accessibilityDir`:

| Artifact | Description |
|---|---|
| `*.json` | Raw accessibility scan output per page/test. |
| `*_interaction_*.json` | Interaction-layer detail output. |
| `accessibility-summary.jsonl` | One-line-per-scan rollup (JSON Lines), useful for CI dashboards/log aggregation. |
| `accessibility_report_<timestamp>.xlsx` | Consolidated Excel workbook of all violations. |
| `accessibility_summary_<timestamp>.html` | Human-readable HTML summary report. |

You can also resolve the directory programmatically:

```java
File outputDir = getAccessibilityOutputDirectory();
```

#### Example: raw scan JSON (`<timestamp>_<pageName>_a11y.json`)

Written by `runAccessibilityScan(pageName)` / `assertNoAccessibilityViolations(pageName)`
-- one file per page/test, containing the full axe-core (Layer 1) violation list:

```json
{
  "timestamp": "2026-09-02_16-42-10",
  "pageName": "LoginPage",
  "outcome": "FAIL",
  "enabled": true,
  "failOnViolation": false,
  "violationCount": 2,
  "threadId": 1,
  "scanScope": {
    "mainDocumentScanned": true,
    "totalIFramesDetected": 1,
    "iframesScannedIndividually": 1,
    "iframesSkippedCrossOrigin": 0,
    "shadowDomEnabled": false,
    "lwcComponentsDetected": false,
    "salesforceLightningDetected": false,
    "iframeSelectors": ["iframe#recaptcha"]
  },
  "tags": ["wcag2a", "wcag2aa"],
  "violations": [
    {
      "id": "color-contrast",
      "description": "Elements must meet minimum color contrast ratio thresholds",
      "impact": "SERIOUS",
      "help": "Elements must have sufficient color contrast",
      "helpUrl": "https://dequeuniversity.com/rules/axe/4.10/color-contrast",
      "affectedElements": [
        "<button class=\"btn-login\">Log In</button>"
      ]
    },
    {
      "id": "label",
      "description": "Form elements must have labels",
      "impact": "CRITICAL",
      "help": "Form elements must have labels",
      "helpUrl": "https://dequeuniversity.com/rules/axe/4.10/label",
      "affectedElements": [
        "<input type=\"text\" id=\"username\">"
      ]
    }
  ]
}
```

#### Example: interaction-layer JSON (`<timestamp>_<pageName>_interaction_<checkId>.json`)

Written by `WebEventListener`'s per-element scans (§14.4) and Layer 2 checks:

```json
{
  "timestamp": "2026-09-02_16-42-11",
  "pageName": "LoginPage",
  "checkId": "afterClick",
  "outcome": "FAIL",
  "issueCount": 1,
  "needsReviewCount": 0,
  "issues": [
    {
      "ruleId": "focus-visible",
      "impact": "MODERATE",
      "wcagRef": "2.4.7",
      "description": "Interactive element does not show a visible focus indicator",
      "element": "<button class=\"btn-login\">Log In</button>",
      "helpUrl": "https://www.w3.org/WAI/WCAG21/Understanding/focus-visible.html",
      "needsReview": false
    }
  ]
}
```

#### Example: rollup line (`accessibility-summary.jsonl`)

One compact JSON object appended per scan (JSON Lines format -- one valid JSON
document per line, easy to `tail -f` or feed into a log aggregator):

```json
{"timestamp":"2026-09-02_16-42-10","pageName":"LoginPage","outcome":"FAIL","violationCount":2,"iframesScanned":1,"shadowDomScanned":false}
```

#### Excel workbook (`accessibility_report_<timestamp>.xlsx`) -- sheet layout

| Sheet | Columns |
|---|---|
| **Run Overview** | Run-level summary + Impact Distribution (Impact, Count) + Top 10 Rule IDs (Rule ID, Occurrences) |
| **Scan History** | Timestamp, Page Name, Outcome, Violations, WCAG Tags, Error |
| **Violations Detail** | Timestamp, Page Name, Outcome, Rule ID, Impact, Description, Help, Help URL, Affected Elements, First Element (HTML) |

The HTML summary (`accessibility_summary_<timestamp>.html`) presents the same
Run Overview / Scan History / Violations Detail data as a standalone, styled page
you can open directly in a browser or attach to a CI build artifact -- no Excel
required to review results at a glance.

### 14.7 Important caveats

- `checkFocusVisibility()` and `checkFocusAppearance()` call `el.focus()` via JavaScript.
  Run them only **after** your functional assertions, because they intentionally change
  `document.activeElement`.
- `checkZoomReflow()` and `checkTextSpacing()` use short `Thread.sleep()` waits
  (200-600 ms) after viewport changes so the UI can settle. These are deliberate,
  framework-owned exceptions to the normal no-`Thread.sleep()` rule.
- All accessibility features are disabled by default. Enable them only for suites or
  tests where you want accessibility coverage.

---

## 15. Retry & Listeners

These are pre-configured in the SDK -- declare them in your TestNG suite XML.

```xml
<!-- regression_suite.xml -->
<suite name="Regression" verbose="1">
    <listeners>
        <listener class-name="com.test.automation.sdk.listener.Listener"/>
        <listener class-name="com.test.automation.sdk.listener.RetryListener"/>
    </listeners>
    <test name="All Tests">
        <classes>
            <class name="com.yourco.automation.testCases.Test_Login"/>
        </classes>
    </test>
</suite>
```

| Listener | What it does |
|---------|--------------|
| `Listener` | Takes a screenshot, saves a paired DOM dump, adds a DOM file link to TestNG HTML, and renames data-driven test entries |
| `RetryListener` | Automatically retries a failed test once |
| `WebEventListener` | Logs every driver action (enabled per WebDriverFactory config) |

---

## 16. Locator Rules -- Non-Negotiable

These rules exist to prevent flaky tests.

### ? Allowed XPath strategies (priority order)

```
1.  @id                 -- stable non-generated ID
2.  @data-testid        -- intentional test attribute
3.  @formcontrolname    -- Angular reactive forms
4.  @name + @type       -- standard form fields
5.  @name               -- when type is not needed
6.  @aria-label         -- accessibility attribute
7.  @placeholder        -- input fields
8.  normalize-space(.)  -- buttons/links with unique text
9.  @routerlink         -- Angular navigation
10. @href               -- static links
```

### ? Never use

```
@id='mat-input-3'          dynamic Angular Material ID
@id='cdk-overlay-1'        dynamic CDK overlay ID
(//input)[2]               positional index
//div[3]/form/input[1]     structural path
@class only                breaks on any styling change
```

### Examples

```java
// ? Correct
@FindBy(xpath = "//input[@id='email']")
@FindBy(xpath = "//input[@formcontrolname='password']")
@FindBy(xpath = "//button[normalize-space(.)='Log In']")
@FindBy(xpath = "//mat-select[@formcontrolname='borough']")

// ? Wrong
@FindBy(xpath = "//input[@id='mat-input-0']")     // dynamic
@FindBy(xpath = "(//input)[2]")                    // positional
@FindBy(css   = "input.form-control")              // CSS not allowed
```

---

## 17. Complete End-to-End Example

**Scenario:** Automate a login test.

### Step 1 -- Run the crawler on the login page

```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=admin@app.com -Dinv.password=secret
```

### Step 2 -- Review the generated Page Object

Open `src/test/java/.../uiActions/LoginPage.java` and the `.txt` report.
Remove all locators not marked `UNIQUE [x]`.

### Step 3 -- Add business-logic methods to LoginPage

```java
public void login(String email, String password) {
    waitForElementPresent(driver, emailField);
    clearAndType(emailField, email);
    clearAndType(passwordField, password);
    fluentWaitUntilElementToBeClickable(loginButton);
    safeClick(loginButton);
    waitUntillPageLoad();
}

public String getDashboardTitle() {
    waitForElementPresent(driver, dashboardHeader);
    return safeGetText(dashboardHeader);
}
```

### Step 4 -- Add test data to Excel

Add a sheet named `LoginSheet` to `YourApp_STG_TestData.xlsx`:

| testCaseName | email | password | runMode |
|---|---|---|---|
| TC_001 -- Valid Login | user@app.com | Pass123! | Y |
| TC_002 -- Skip Me | skip@app.com | xxx | N |

### Step 5 -- Create the test class

```java
@DataProvider(name = "loginData")
public Object[][] loginData() throws IOException {
    return getData("LoginSheet");
}

@Test(dataProvider = "loginData", priority = 1)
public void testLogin(String testCaseName, String email,
                      String password, String runMode) throws Exception {
    if ("N".equalsIgnoreCase(runMode))
        throw new SkipException("Skipping: " + testCaseName);

    step("Open login page");
    driver.get(baseURL);

    step("Login with " + email);
    LoginPage page = new LoginPage(driver);
    page.login(email, password);

    step("Verify dashboard title");
    verifyText("Dashboard", page.getDashboardTitle());
}
```

### Step 6 -- Compile and run

```bash
mvn compile test-compile
mvn test -Dtest=Test_Login -Denvironment=stg -DbrowserName=chrome
```

Expected output:
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

---

## 18. Troubleshooting

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| `NoSuchElementException` | Locator is dynamic or not `UNIQUE [x]` | Re-run crawler; use a stable XPath |
| `StaleElementReferenceException` | DOM re-rendered after element was found | Use `safeClick()` / `safeGetText()` / `clearAndType()` |
| `TimeoutException` on wait | Page is slow or element is conditional | Increase timeout in `waitForElementPresent(element, 120)` |
| `SessionNotCreatedException` | ChromeDriver version mismatch | Set `browser.chromeDriverPath` to a compatible binary or leave empty to let WebDriverManager auto-resolve |
| `Cannot read config.properties` | Wrong working directory | Run `mvn test` from the project root |
| Corporate proxy blocking ChromeDriver download | Proxy not configured | Set `proxy.enabled=true` in `sdk-config.yaml`; or set `browser.chromeDriverPath` to a pre-downloaded binary |
| Excel `NullPointerException` | Wrong sheet name in `getData()` | Check sheet name matches exactly (case-sensitive) |
| `BUILD FAILURE` on compile | Import not found | Verify SDK dependency version in `pom.xml` |

---

*Framework Automation SDK -- `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.1`*  
*Maintained by OTI QA Automation Team*

