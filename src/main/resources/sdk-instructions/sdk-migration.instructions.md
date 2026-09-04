---
applyTo: "**"
---

# Skill: Migrating an Existing Project to use test-automation-sdk

## Purpose
This instruction guides an AI agent through converting an existing Selenium + TestNG
automation project to depend on `test-automation-sdk` instead of bundling its own
framework utilities.

---

## Prerequisites
- `test-automation-sdk` JAR is available in a local Maven repository (e.g., `../maven-repository`)
- The consumer project uses Maven and Java 8+
- The existing project has a `TestBase` class or equivalent framework base

---

## Step 1 ? Add Maven Repository and Dependency

In the consumer project's `pom.xml`, add inside `<project>`:

```xml
<repositories>
  <repository>
    <id>test-automation-sdk-repo</id>
    <url>file://${project.basedir}/../maven-repository</url>
  </repository>
</repositories>
```

Add inside `<dependencies>`:
```xml
<dependency>
  <groupId>com.test.automation</groupId>
  <artifactId>test-automation-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

Add the `exec-maven-plugin` inside `<build><plugins>` so instructions can be extracted:
```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.1.0</version>
</plugin>
```

---

## Step 2 ? Extract SDK Instructions

From the consumer project root, run:
```bash
mvn exec:java -Dexec.mainClass="com.test.automation.sdk.utility.InstructionExtractor"
```

This creates `.github/instructions/` with all SDK instruction files.

Add to consumer project `.gitignore`:
```
# SDK-managed instructions ? do not commit
.github/instructions/
.github/copilot-instructions.md
```

---

## Step 3 ? Update TestBase Import

In every Java file that imports the old TestBase:

| Replace | With |
|---|---|
| `import com.automation.poletop.testBase.TestBase;` | `import com.test.automation.sdk.testbase.TestBase;` |
| `import com.poletop.automation.testBase.TestBase;` | `import com.test.automation.sdk.testbase.TestBase;` |

Search command:
```bash
grep -r "import.*testBase.TestBase" src/
```

---

## Step 4 ? Remove Duplicate Utilities from Consumer Project

After adding the SDK dependency, these utility classes must be **deleted** from the consumer project (they are now provided by the SDK JAR):

- `**/utility/Excel_Reader.java`
- `**/utility/PropertiesReader.java`
- `**/utility/PageContext.java`
- `**/utility/reports/ExtentManager.java`
- `**/utility/reports/ExtentTestManager.java`
- `**/listener/WebEventListener.java`
- `**/listener/RetryListener.java`
- `**/listener/Retry.java`
- `**/listener/Listener.java`
- `**/utility/mailinator/Mailinator.java`
- `**/utility/mailinator/MailinatorEmailReader.java`
- `**/utility/mailinator/InboxMessage.java`
- `**/utility/mailinator/Email.java`
- `**/utility/ElementCrawler.java`
- `**/utility/PageObjectGenerator.java`

> ?? Only delete if the consumer project has its OWN copy ? verify the class exists locally before deleting.

---

## Step 5 ? Update Package References in Remaining Files

After removing duplicates, fix any remaining import references in page objects and test classes.

Run compile to find all broken imports:
```bash
mvn compile test-compile 2>&1 | grep "error:"
```

Fix each error by updating the import to `com.test.automation.sdk.*`.

---

## Step 6 ? Add sdk-config.yaml

Copy the SDK YAML template to the consumer project:
```bash
# Copy from SDK resources (after extracting instructions):
cp .github/sdk-config.yaml.template configuration/sdk-config.yaml
```

Or manually create `configuration/sdk-config.yaml` with content matching the template in `sdk-defaults/sdk-config.yaml.template`.

Fill in project-specific values (mailinator API key, proxy settings, etc.).

**Critical -- set these two sections** in `configuration/sdk-config.yaml`:

```yaml
# 1. Crawler: so PageObjectGenerator writes page objects into YOUR package
crawler:
  pageObject:
    package:   "com.mycompany.automation.uiActions"
    outputDir: "src/main/java/com/mycompany/automation/uiActions/"
    reportDir: "test-output/crawler/"

# 2. Reporting: where gap-report.md and blocker-report.md files are written
#    when a test case cannot be fully automated (default shown below)
reporting:
  gapOutputDir: "docs/test-case-gaps/"
```

Without the `crawler` section, generated files will go to the Poletop-specific package path.
Without the `reporting` section, gap/blocker reports land in the default `docs/test-case-gaps/` directory -- which is fine for most projects.

You can also override per-run without editing the YAML:

```bash
-Dpog.package=com.mycompany.automation.uiActions    # crawler package override
-Dpog.outputDir=src/main/java/...                   # crawler output dir override
-Dsdk.gapOutputDir=docs/blocked-tests/              # gap output dir override
```

---

## Step 7 ? Update log4j2 Configuration

Copy the log4j2 template to the consumer project's configuration directory:
```
configuration/log4j2.xml
```

Use the content from `sdk-defaults/log4j2.xml.template` in the SDK.

---

## Step 8 ? Add configuration/ to .gitignore Selectively

`sdk-config.yaml` may contain sensitive API keys ? add to `.gitignore`:
```
configuration/sdk-config.yaml
```

Commit a `sdk-config.yaml.example` (with placeholder values) to document required keys.

---

## Step 9 ? Validate

```bash
# Must succeed with zero errors
mvn compile test-compile

# Run a smoke test to confirm the SDK integrates correctly
mvn test -Dtest=<AnySimpleTest> -Denvironment=stg -DbrowserName=chrome
```

---

## Completion Checklist
- [ ] `pom.xml` has repository + dependency
- [ ] `mvn exec:java InstructionExtractor` ran successfully
- [ ] `.gitignore` updated to exclude `.github/instructions/` and `sdk-config.yaml`
- [ ] All old TestBase imports updated to `com.test.automation.sdk.testbase.TestBase`
- [ ] Duplicate utility classes removed
- [ ] `configuration/sdk-config.yaml` created and populated
  - [ ] `crawler.pageObject.package` / `outputDir` / `reportDir` set to project values
  - [ ] `reporting.gapOutputDir` set (or left as default `docs/test-case-gaps/`)
- [ ] `configuration/log4j2.xml` created
- [ ] `docs/test-case-gaps/` directory exists (or custom path from `reporting.gapOutputDir`)
- [ ] `mvn compile test-compile` passes with zero errors
- [ ] At least one test executed and passed (or skipped intentionally)
