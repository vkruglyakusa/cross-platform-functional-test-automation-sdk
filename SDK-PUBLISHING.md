# Framework Automation SDK -- Publishing & Maintenance Guide

## Overview

`cross-platform-functional-test-automation-sdk` is the reusable framework layer providing:
- `testbase/` -- TestBase, WebDriverFactory, SdkConfig
- `utility/` -- ElementCrawler, PageObjectGenerator, Excel/CSV/Mailinator utils
- `listener/` -- Listener, RetryListener, WebEventListener, Retry

Consumer projects declare a single Maven dependency and write only their own `uiActions/` and `testCases/`.

---

## 1. Build Locally

```bash
cd cross-platform-functional-test-automation-sdk
mvn clean install -DskipTests
```

Produces in `~/.m2`:
- `cross-platform-functional-test-automation-sdk-1.2.1.jar`
- `cross-platform-functional-test-automation-sdk-1.2.1-sources.jar`
- `cross-platform-functional-test-automation-sdk-1.2.1-javadoc.jar`

---

## 2. GitHub Packages Feed

Feed URL (GitHub):
```
https://maven.pkg.github.com/vkruglyakusa/cross-platform-functional-test-automation-sdk
```

The `<distributionManagement>` block in `pom.xml` already points to this feed.

> [!NOTE]
> Azure Artifacts is not the default publishing destination. It remains available as an optional integration for CI/CD pipelines that require it, but GitHub Packages is now canonical.

---

## 3. Configure Maven Authentication

Add to `%USERPROFILE%\.m2\settings.xml`. This is a **one-time setup per machine**.

**Minimal (no corporate proxy):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0
          http://maven.apache.org/xsd/settings-1.1.0.xsd">

  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_USERNAME}</username>
      <password>${env.GITHUB_TOKEN}</password>  <!-- Token scope: packages write -->
    </server>
  </servers>

</settings>
```

**With corporate proxy:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0
          http://maven.apache.org/xsd/settings-1.1.0.xsd">

  <proxies>
    <proxy>
      <id>http-proxy-settings</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>bcpxy.nycnet</host>
      <port>8080</port>
      <nonProxyHosts>10.*|192.168.*|172.16.*|*.nycnet|localhost|github.com|*.github.com|maven.pkg.github.com</nonProxyHosts>
    </proxy>
  </proxies>

  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_USERNAME}</username>
      <password>${env.GITHUB_TOKEN}</password>  <!-- Token scope: packages write -->
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

> **Rules:**
> - `<id>` must match `<distributionManagement>` -> `<id>` in `pom.xml` -- currently `github`
> - For publishing, the GitHub token requires **packages write** scope; for consumption, it needs **packages read** only
> - `maven.pkg.github.com` must be in `nonProxyHosts` if behind a corporate proxy
> - Never commit `settings.xml` -- it lives outside the project in `~/.m2/`

---

## 4. Publish

```bash
mvn clean deploy -DskipTests
```

---

## 5. Version Bump Process

1. Update `<version>` in `pom.xml`
2. **Update `CHANGELOG.md`** -- add a `### X.Y.Z` section documenting every change
3. **Update `README.md`** -- reflect any new components, config keys, or prompts:
   - Component table: add/update rows for new/changed classes
   - Configuration snippet: update if new yaml keys were added
   - Copilot prompts table: add/update if prompts changed
   - SDK Version badge at the bottom
   - Show a diff-style summary of README.md changes in the commit message
4. Update version references in `SDK-USER-GUIDE.md` and `TESTBASE-API.md`
5. Run `mvn clean deploy -DskipTests`
6. In `functional-automation-consumer-template/pom.xml` -- update the SDK dependency version
7. Run `mvn compile test-compile` in the consumer to verify

> [!]? **Rule: no SDK version may be deployed without both a `CHANGELOG.md` entry
> and a `README.md` update. Write both before running `mvn deploy`.**

**Changelog entry format:**

```markdown
### X.Y.Z
- **`ClassName` or area:** What changed and why.
  Include: new keys, removed methods, behavioral changes, new tests count.
- **`SDK-USER-GUIDE.md`:** sections updated (list them).
- **Tests:** N new tests; M total passing.
```

**Versioning: `MAJOR.MINOR.PATCH`**

| Increment | When |
|-----------|------|
| `PATCH` | Bug fixes, no API change (e.g. `1.1.0` -> `1.1.1`) |
| `MINOR` | New helpers, backward-compatible additions (e.g. `1.0.0` -> `1.1.0`) |
| `MAJOR` | Breaking changes -- package rename, method removal, signature change |

---

## 6. Changelog

The full changelog is maintained in **[`CHANGELOG.md`](CHANGELOG.md)** in the SDK project root.

> [!]? **Mandatory:** add a changelog entry under `[Unreleased]` **before** running `mvn deploy`.
> Move `[Unreleased]` entries to the new version heading as part of the version bump step.

## 7. SDK vs. Consumer Split

| SDK | Consumer |
|-----|----------|
| TestBase, WebDriverFactory | `uiActions/` page objects |
| ElementCrawler, Generator | `testCases/` test classes |
| Listeners | `tools/LocatorInvestigator` |
| Excel/CSV/Mailinator utilities | `testData/` Excel files |
| SdkConfig path resolution | `configuration/config.properties` |
| Default config templates | `regression_suite.xml` |

Rule: anything with `@FindBy`, app URLs, or business logic -> consumer project.

---

## 8. SdkConfig -- Override Config Path

Priority (first wins):
1. `-Dsdk.config.dir=<path>`
2. `SDK_CONFIG_DIR` env variable
3. `./configuration/` (default)

```bash
mvn test -Dsdk.config.dir=/opt/automation/config -Denvironment=stg
```
