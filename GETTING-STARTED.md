# Getting Started -- Cross-Platform Functional Test Automation SDK

**Artifact:** `com.test.automation:cross-platform-functional-test-automation-sdk:1.5.0`

This is the single first-day setup guide for a brand-new consumer project.
Follow the shared steps first, then continue with the track(s) you need:
Web (Selenium), Mobile (Local Appium), Mobile (BrowserStack), or API
(RestAssured, no browser/device session).

```
Step 1: Prerequisites  --------------------------------------------------+
Step 2: Get the SDK jar                                                  |
Step 3: Add the Maven dependency + repository + exec plugin             |  Required
Step 4: Extract Copilot instructions                                     |  for every
Step 5: Create your project's configuration file(s)  -------------------+  consumer
        |
        +-- Track A: Web (Selenium)         -> Step 6A -> Step 7A
        +-- Track B: Mobile (Local Appium)  -> Step 6B -> Step 7B
        +-- Track C: Mobile (BrowserStack)  -> Step 6C -> Step 7C
        +-- Track D: API (RestAssured)      -> Step 6D -> Step 7D
                |
Step 8: Verify your setup (compile + smoke test)
```

---

## Step 1 -- Prerequisites

| Tool | Required For | Version | Notes |
|---|---|---|---|
| Java JDK | Everyone | 20 or higher | Must be on `PATH`. Run `java -version` and `mvn -version` to confirm Maven is also using JDK 20+. |
| Maven | Everyone | 3.6+ | Must be on `PATH`. |
| Git | Everyone | Any recent | To clone the SDK or a consumer template. |
| Chrome browser | Web track | Latest stable | WebDriverManager auto-downloads the matching driver unless you pin `browser.chromeDriverPath`. |
| Android Studio + Android SDK | Mobile (Local) | Latest | Provides `adb`, emulators, and platform tools. |
| Node.js + Appium server | Mobile (Local) | Node 18+, Appium 2.x | Install with `npm install -g appium`; add the platform drivers you need. |
| BrowserStack account | Mobile (BrowserStack) | N/A | Required only for BrowserStack App Automate runs. |
| Allure CLI | Optional | Any current release | Required only if you want automatic `allure generate` / `allure open` HTML reporting. |

---

## Step 2 -- Get the SDK Jar

Use one of these sources:

1. **Released version from Azure Artifacts** - normal path for shared consumer projects.
2. **Local install from this SDK repo** - useful when validating unpublished SDK changes.

Local install flow:

```bash
git clone <this-repository-url>
cd cross-platform-functional-test-automation-sdk
mvn clean install -DskipTests
```

This places the jar in:
`~/.m2/repository/com/test/automation/cross-platform-functional-test-automation-sdk/1.5.0/`

---

## Step 3 -- Add the Maven Dependency

### 3A. Add the dependency

```xml
<dependency>
    <groupId>com.test.automation</groupId>
    <artifactId>cross-platform-functional-test-automation-sdk</artifactId>
    <version>1.5.0</version>
</dependency>
```

### 3B. Add the Azure Artifacts repository

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

### 3C. Add Maven authentication (`~/.m2/settings.xml`) when using Azure Artifacts

```xml
<settings>
  <servers>
    <server>
      <id>cross-platform-functional-test-automation-sdk</id>
      <username>YOUR_AZURE_ARTIFACTS_USERNAME</username>
      <password>YOUR_PAT_HERE</password>
    </server>
  </servers>
</settings>
```

### 3D. Add `exec-maven-plugin` so `InstructionExtractor` is deterministic

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.1.0</version>
    </plugin>
  </plugins>
</build>
```

---

## Step 4 -- Extract Copilot Instructions

Run this once per consumer project, and again after every SDK upgrade:

```bash
mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.utility.InstructionExtractor"
```

This extracts:
- `.github/instructions/`
- `.github/prompts/`
- `configuration/sdk-config.yaml.template`
- `configuration/log4j2.xml.template`
- the mirrored SDK docs bundled inside the JAR

---

## Step 5 -- Create Your Project's Configuration File(s)

| File | Copy from | Used by |
|---|---|---|
| `configuration/sdk-config.yaml` | `configuration/sdk-config.yaml.template` | All tracks - browser, mobile, API, reporting, analytics, visual, RCA, accessibility |
| `configuration/log4j2.xml` | `configuration/log4j2.xml.template` | All tracks - SDK logging |
| `configuration/config.properties` | your project template or create manually | Web and API tracks. Web uses URLs/data-set paths; API-only projects still need at least `extReportDir=test-output/reports` for the legacy Extent listener path. |
| `browserstack.yml` (project root) | your BrowserStack template/example | Mobile BrowserStack track only |
| `configuration/mobile-config.yaml` | optional compatibility path only | Deprecated mobile-only fallback. New projects should prefer the `appium:`, `android:`, and `ios:` sections in `sdk-config.yaml`. |

Keep environment-specific secrets out of git:
- `configuration/sdk-config.yaml`
- `configuration/log4j2.xml` (if customized with environment paths)
- `browserstack.yml`
- any file containing live credentials or tokens

---

## Track A -- Web (Selenium)

### Step 6A -- Configure `sdk-config.yaml`

```yaml
browser:
  default: chrome
  headless: false

crawler:
  pageObject:
    package: "com.yourcompany.automation.uiActions"
    outputDir: "src/main/java/com/yourcompany/automation/uiActions/"

reporting:
  screenshotsDir: "test-output/screenshots"
  crawlerDir: "test-output/crawler"
  gapOutputDir: "docs/test-case-gaps"
```

### Step 7A -- Write a smoke test

Extend `com.test.automation.sdk.testbase.TestBase` and use `step("...", () -> { ... })`.

```java
public class Test_WebSmoke extends TestBase {
    @Test
    public void openHomePage() throws Exception {
        step("Open the application", () -> initialization("chrome", "https://example.com"));
        step("Verify the page title is present", () -> Assert.assertFalse(driver.getTitle().isEmpty()));
    }
}
```

---

## Track B -- Mobile (Local Appium)

### Step 6B -- Start the device/emulator and Appium server

```bash
adb devices
appium
curl http://127.0.0.1:4723/status
```

### Step 6B (cont.) -- Configure unified mobile keys in `sdk-config.yaml`

```yaml
appium:
  localUrl: "http://127.0.0.1:4723/"

android:
  appPath: "apps/app-debug.apk"
  automationName: "UiAutomator2"

# or iOS
ios:
  appPath: "apps/app.ipa"
  automationName: "XCUITest"
```

### Step 7B -- Write a smoke test

```java
public class Test_MobileSmoke extends MobileTestBase {
    @Test
    public void appLaunches() {
        Assert.assertNotNull(driver);
    }
}
```

Run locally:

```bash
mvn test -Dtest=Test_MobileSmoke -DmobileOS=android
```

---

## Track C -- Mobile (BrowserStack)

### Step 6C -- Configure `browserstack.yml`

Place the file at the project root:

```yaml
userName: ${BROWSERSTACK_USERNAME}
accessKey: ${BROWSERSTACK_ACCESS_KEY}
framework: testng
appiumVersion: 2.15.0
app: bs://<uploaded-app-id>
platforms:
  - platformName: android
    platformVersion: "14.0"
    deviceName: Google Pixel 8
```

### Step 7C -- Run the same `MobileTestBase` smoke test on BrowserStack

```bash
mvn test -Dtest=Test_MobileSmoke -DmobileOS=android -Drun.mode=BROWSERSTACK
```

---

## Track D -- API (RestAssured)

### Step 6D -- Configure API keys in `sdk-config.yaml`

```yaml
api:
  baseUrl.stg: "https://reqres.in"
  authHeaderName: "Authorization"
  authTokenEnvVar: "API_TOKEN"
  connectionTimeoutMillis: 10000
  readTimeoutMillis: 30000
  logRequestsAndResponses: true
  outputDirectory: "test-output/api"
  relaxedHttpsValidation: false
```

Set the token in your shell or CI secret store if your API needs one. If not,
leave `authHeaderName` and `authTokenEnvVar` blank.

### Step 7D -- Write a smoke test

```java
public class Test_ApiSmoke extends ApiTestBase {
    @Test
    public void getUsersReturns200() {
        Response response = get("List users", "/api/users?page=2");
        assertStatusCode(response, 200);
        assertJsonPath(response, "page", 2);
        assertResponseTimeUnder(response, 5000);
    }
}
```

API-only projects still need a minimal `configuration/config.properties` so the
legacy Extent listener can resolve `extReportDir`:

```properties
extReportDir=test-output/reports
```

---

## Step 8 -- Verify Your Setup

```bash
mvn compile test-compile
```

Then run the smoke test for your chosen track:

```bash
# Web
mvn test -Dtest=Test_WebSmoke -Denvironment=stg -DbrowserName=chrome

# Mobile local
mvn test -Dtest=Test_MobileSmoke -Denvironment=stg -DmobileOS=android

# Mobile BrowserStack
mvn test -Dtest=Test_MobileSmoke -Denvironment=stg -DmobileOS=android -Drun.mode=BROWSERSTACK

# API
mvn test -Dtest=Test_ApiSmoke -Denvironment=stg
```

If compile and the smoke test both succeed, your setup is complete.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Could not resolve dependencies... cross-platform-functional-test-automation-sdk` | SDK not installed locally and feed auth is missing or wrong | Re-run Step 2 for a local install, or fix Step 3C feed credentials. |
| `UnsupportedClassVersionError` at runtime | Maven is running on a JVM older than Java 20 | Point `JAVA_HOME` and your IDE/test runner to JDK 20+. |
| `mvn exec:java` cannot find `InstructionExtractor` | `exec-maven-plugin` missing from the consumer `pom.xml` | Add the plugin block from Step 3D. |
| WebDriver fails to start | Browser/driver mismatch or corporate proxy interference | Leave `browser.chromeDriverPath` empty for WebDriverManager, or configure `proxy.*` / pin a known-good driver path. |
| Appium local session fails immediately | Emulator/device not running, Appium not started, or wrong `appium.localUrl` | Re-check `adb devices`, `appium`, and the `appium.localUrl` value. |
| BrowserStack session fails before app launch | `browserstack.yml` missing/misplaced or credentials invalid | Keep `browserstack.yml` at project root and verify `BROWSERSTACK_USERNAME` / `BROWSERSTACK_ACCESS_KEY`. |
| `IllegalStateException: No API base URL configured` | Missing `api.baseUrl.<environment>` and no fallback `api.baseUrl` | Add the API base URL key in `sdk-config.yaml`. |
| API requests all return `401` / `403` | `api.authTokenEnvVar` name is wrong or the named env var is unset | Fix the environment variable name or export the variable before running tests. |
| API payload evidence is missing | `api.logRequestsAndResponses=false` or wrong `api.outputDirectory` | Re-enable evidence capture or check the configured directory. |
| `NullPointerException` inside `ExtentManager` on an API-only project | `configuration/config.properties` is missing entirely | Add a minimal file with `extReportDir=test-output/reports`. |

---

## Where to Go Next

| Document | For |
|---|---|
| [`README.md`](README.md) | Top-level SDK navigation and feature map |
| [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md) | Shared configuration, crawler, reporting, accessibility, and API overview |
| [`TESTBASE-API.md`](TESTBASE-API.md) | Full `TestBase` method reference |
| [`API-TESTBASE-API.md`](API-TESTBASE-API.md) | Full `ApiTestBase` method reference |
| [`MOBILE-USER-GUIDE.md`](MOBILE-USER-GUIDE.md) | Mobile/Appium concepts and crawler guide |
| [`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md) | Full `MobileTestBase` method reference |
| [`CHANGELOG.md`](CHANGELOG.md) | Upgrade history and release notes |
