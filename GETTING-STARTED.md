# Getting Started -- Cross-Platform Functional Test Automation SDK

**Artifact:** `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.4`

This is the **single, definitive, step-by-step setup guide** for a new consumer
project. Follow the steps **in order**. Steps 1-5 are required for every
consumer. After that, follow **only the track(s) you need**: Web, Mobile
(Local Appium/Emulator), or Mobile (BrowserStack).

```
Step 1: Prerequisites  ──────────────────────────────────────────────┐
Step 2: Get the SDK jar                                              │  Required
Step 3: Add the Maven dependency                                     │  for every
Step 4: Extract Copilot instructions                                 │  consumer
Step 5: Create your project's configuration file(s)  ────────────────┘
        │
        ├── Track A: Web (Selenium)        -> Step 6A -> Step 7A
        ├── Track B: Mobile (Local Appium) -> Step 6B -> Step 7B
        └── Track C: Mobile (BrowserStack) -> Step 6C -> Step 7C
                │
Step 8: Verify your setup (compile + smoke test)  <── all tracks converge here
```

---

## Step 1 -- Prerequisites

| Tool | Required For | Version | Notes |
|---|---|---|---|
| Java JDK | Everyone | **20** or higher | Must be on `PATH`. Run `java -version` to confirm. The SDK is compiled at Java 20 -- your consumer project's runtime JVM must be 20+, even if your own source/target level is lower (e.g. Poletop compiles at Java 8 but must *run* on a Java 20+ JVM to load this SDK's classes). |
| Maven | Everyone | 3.6+ | Must be on `PATH`. Run `mvn -version` to confirm. |
| Git | Everyone | Any recent | To clone this SDK / a consumer template. |
| Chrome browser | Web track | Latest stable | WebDriverManager auto-downloads the matching ChromeDriver -- you do not install ChromeDriver manually. |
| Android Studio + Android SDK | Mobile (Local) track | Latest | Provides `adb`, the AVD Manager, and platform tools. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) and add `platform-tools` to `PATH`. |
| Node.js + Appium server | Mobile (Local) track | Node 18+, Appium 2.x | Install with `npm install -g appium`, then `appium driver install uiautomator2` (Android) / `appium driver install xcuitest` (iOS, macOS only). |
| A BrowserStack account | Mobile (BrowserStack) track | -- | Username + Access Key from your BrowserStack dashboard. |
| IntelliJ IDEA | Recommended | Any recent | Not strictly required, but all examples assume it. |

---

## Step 2 -- Get the SDK Jar

Install the released SDK from Azure Artifacts or from a local
`maven-repository` clone if you are validating unpublished changes:

```bash
git clone <this-repository-url>
cd cross-platform-functional-test-automation-sdk
mvn clean install -DskipTests
```

This places the jar in `~/.m2/repository/com/test/automation/cross-platform-functional-test-automation-sdk/1.1.4/`,
where any local consumer project's Maven build can find it.

> Released builds can be consumed directly from Azure Artifacts; a local install
> remains useful for validating an unpublished SDK working tree before release.

---

## Step 3 -- Add the Maven Dependency

**3A. Add the dependency** to your consumer project's `pom.xml`:

```xml
<dependency>
    <groupId>com.test.automation</groupId>
    <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
    <version>1.1.4</version>
</dependency>
```

LOCAL-only consumers should stop here. Do **not** declare
`com.browserstack:browserstack-java-sdk` unless you explicitly run against
BrowserStack. BrowserStack-enabled consumers must add that dependency
themselves, plus `browserstack.yml`, credentials, and any Surefire
`-javaagent` wiring they use.

**3B. Add the Azure Artifacts repository** (for when a real release is
published; harmless to add now, Maven will fall back to your local `.m2` copy
from Step 2 until then):

```xml
<repositories>
    <repository>
        <id>cross-platform-functional-test-automation-sdk</id>
        <url>https://clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d.pkgs.visualstudio.com/_packaging/functional-test-automation-sdk/maven/v1</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

**3C. Add Maven authentication** in `~/.m2/settings.xml` (only needed once a
real release exists on Azure Artifacts; skip while using the local `.m2`
install from Step 2):

```xml
<settings>
  <servers>
    <server>
      <id>cross-platform-functional-test-automation-sdk</id>
      <username>clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d</username>
      <password>YOUR_PAT_HERE</password> <!-- PAT scope: Packaging -> Read -->
    </server>
  </servers>
</settings>
```

---

## Step 4 -- Extract Copilot Instructions

Run this once per project (and again after every SDK version upgrade):

```bash
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.utility.InstructionExtractor"
```

This writes `.github/instructions/`, `.github/prompts/`, and
`configuration/sdk-config.yaml.template` into your consumer project.
Customized instruction files are never silently overwritten.

---

## Step 5 -- Create Your Project's Configuration File(s)

Copy the extracted templates and fill in real values:

| File | Copy from | Used by |
|---|---|---|
| `configuration/sdk-config.yaml` | `configuration/sdk-config.yaml.template` | All tracks -- browser defaults, crawler output paths, reporting dirs |
| `configuration/log4j2.xml` | SDK's `sdk-defaults/log4j2.xml.template` | All tracks -- logging |
| `configuration/mobile-config.yaml` | SDK's `configuration/mobile-config.yaml.example` | Mobile (Local) track only -- Appium server URL, app path, device name |
| `browserstack.yml` (project root, **not** `configuration/`) | SDK's `browserstack.yml.example.mobile` | Mobile (BrowserStack) track only |

Keep `sdk-config.yaml`, `mobile-config.yaml`, and `browserstack.yml` **out of
git** (add to `.gitignore`) since they may contain environment-specific paths
or credentials; commit a `.example`/`.template` copy instead.

Now proceed to **one or more** of the tracks below.

---

## Track A -- Web (Selenium)

### Step 6A -- Configure `sdk-config.yaml`

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
  gapOutputDir: "docs/test-case-gaps"
```

### Step 7A -- Write a smoke test

Extend `com.test.automation.sdk.testbase.TestBase` and write one TestNG test
that navigates to a URL and asserts on the title. See `SDK-USER-GUIDE.md`
Section 17 for a complete end-to-end example.

Full reference: **`SDK-USER-GUIDE.md`**.

---

## Track B -- Mobile (Local Appium / Emulator)

### Step 6B -- Start the emulator and Appium server

```bash
# 1. Start (or attach to) an Android emulator/device.
emulator -avd <your-avd-name>
adb devices   # confirm it shows up as "device", not "offline"

# 2. Start the Appium server (separate terminal, leave running).
appium
# Confirm it's ready:
curl http://127.0.0.1:4723/status
```

### Step 6B (cont.) -- Configure `mobile-config.yaml`

```yaml
appium:
  localUrl: "http://127.0.0.1:4723/"

android:
  appPath: "apps/app-debug.apk"   # relative to project root
  deviceName: ""                  # leave blank to use whatever `adb devices` reports
  automationName: "UiAutomator2"
```

### Step 7B -- Write a smoke test

Extend `com.test.automation.sdk.mobile.testbase.MobileTestBase`. Pass
no BrowserStack flag for a local run. The SDK defaults to LOCAL unless you
explicitly opt into BrowserStack:

```bash
mvn test -Dtest=YourMobileSmokeTest
```

Full reference: **`MOBILE-USER-GUIDE.md`** (mobile driver factory, execution
strategies, `MobileElementCrawler` usage, locator rules).

---

## Track C -- Mobile (BrowserStack)

### Step 6C -- Configure `browserstack.yml`

Place at the **project root** (not `configuration/`):

```yaml
userName: <your-browserstack-username>
accessKey: <your-browserstack-access-key>
framework: testng
appiumVersion: 2.15.0
app: bs://<app-id>   # upload your .apk/.ipa via BrowserStack App Automate first
platforms:
  - platformName: android
    platformVersion: "14.0"
    deviceName: Google Pixel 8
```

Prefer environment variables over inline credentials:
`BROWSERSTACK_USERNAME` / `BROWSERSTACK_ACCESS_KEY`.

### Step 7C -- Write a smoke test

Same `MobileTestBase` subclass as Track B works unmodified -- the
BrowserStack-vs-local switch is handled internally by `RunMode.resolve()`. Run
with an explicit BrowserStack opt-in:

```bash
mvn test -Dtest=YourMobileSmokeTest -Drun.mode=BROWSERSTACK
```

---

## Step 8 -- Verify Your Setup

Regardless of which track(s) you followed:

```bash
# 1. Confirm the project compiles against the SDK.
mvn compile test-compile

# 2. Run your smoke test and confirm it passes.
mvn test -Dtest=YourSmokeTestClassName
```

If both succeed, your setup is complete. If either fails, see
**Troubleshooting** below before asking for help.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Could not resolve dependencies... cross-platform-functional-test-automation-sdk` | SDK not installed locally and not yet published | Re-run Step 2 (`mvn clean install -DskipTests` inside the SDK repo) |
| `UnsupportedClassVersionError` at runtime | Your JVM is older than Java 20 | Point `JAVA_HOME`/your run configuration at a Java 20+ JDK |
| Appium: `A new session could not be created` | Emulator not running, or Appium server not started, or wrong `appium.localUrl` | Run `adb devices` and `curl http://127.0.0.1:4723/status`; fix `mobile-config.yaml` |
| BrowserStack: session fails immediately with no device error | `browserstack.yml` missing/misplaced, or credentials wrong | Confirm the file is at the **project root**, not `configuration/`; confirm `userName`/`accessKey` |
| `mvn exec:java InstructionExtractor` does nothing | `exec-maven-plugin` not declared in your consumer `pom.xml` | Add the plugin block (see `SDK-USER-GUIDE.md` Section 3) |
| Crawler produces 0 `UNIQUE` locators | App not fully loaded, or crawling behind a version-gate/login wall | Add a wait before crawling; confirm you're authenticated/past any forced-update screens |

---

## Where to Go Next

| Document | For |
|---|---|
| [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md) | Full web/Selenium reference (crawler, TestBase, accessibility, reporting) |
| [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md) | Full mobile/Appium reference |
| [`TESTBASE-API.md`](TESTBASE-API.md) | Every `TestBase` method |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history |
| [`SDK-PUBLISHING.md`](SDK-PUBLISHING.md) | Maintainer build/deploy process |
