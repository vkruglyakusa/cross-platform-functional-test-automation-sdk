---
applyTo: "src/main/**"
---

# Skill: SDK Core Development Rules

## Trigger
Apply this skill whenever modifying or adding any file inside `src/main/` of the SDK
(Java classes, resources, instructions, prompts, templates, config files).

---

## [!]? MANDATORY CHANGE FLOW -- Every SDK Task Must Follow This

**Any time you change ANY SDK file, you must complete ALL of these steps in the same task -- not separately, not later:**

```
1. MAKE the code/resource change
       |
       ?
2. ADD or UPDATE unit tests in src/test/java/
   * Every new class or method -> at least 1 new test
   * Every bug fix -> 1 regression test proving the fix
   * Every behavior change -> update existing test to match new behavior
   * Aim to increase total test count with every PR -- never decrease it
       |
       ?
3. RUN tests -- mvn clean test (ALL tests must pass, zero failures)
   Report total test count before and after: "Tests: X -> Y"
       |
       ?
4. UPDATE CHANGELOG.md -- add entry under [Unreleased]:
   - What changed and why
   - New test count and total passing (e.g. "3 new tests; 298 total passing")
       |
       ?
5. UPDATE README.md -- reflect new components, config keys, or behavior changes
       |
       ?
6. BUMP version in pom.xml -- PATCH for fixes, MINOR for new features, MAJOR for breaking changes
       |
       ?
7. DEPLOY -- run the release script (tests are mandatory, never skip):
   ```bash
   # Windows
   scripts\release.ps1

   # Mac / Linux
   scripts/release.sh
   ```
   The release script runs `mvn clean test` first. If ANY test fails, deployment
   is ABORTED automatically. Only a full green test run allows deploy to proceed.

   **NEVER run `mvn deploy -DskipTests` directly** -- this bypasses the quality gate.

   After deploy, **verify ALL of these are pushed to ADO remote:**
   ```bash
   git log origin/master..HEAD --oneline   # must return EMPTY (nothing unpushed)
   git status --short                      # must return EMPTY (no uncommitted files)
   ```
   If either returns output -- commit and push before declaring the task complete.

   **Also push maven-repository to BOTH branches** (trunk is authoritative; master is what
   the ADO UI browses by default -- if master is not updated, artifacts appear missing):
   ```bash
   cd C:\Users\vkruglyak\IdeaProjects\maven-repository
   git push origin trunk
   git push origin trunk:master --force
   ```
       |
       ?
8. UPDATE consumer template -- bump SDK version in template pom.xml, update template CHANGELOG.md,
   then run `mvn compile test-compile` in the template BEFORE committing/pushing (release.ps1
   does this automatically and aborts the template commit/push if it fails)
```

> **A task is NOT complete if any of steps 2-8 were skipped.**
> Do not wait for the user to ask "did you add tests?" or "did you run tests?" or "did you update the version?" -- do it as part of every change.
> **Tests MUST pass before deployment -- no exceptions.**

---

## [!]? MANDATORY PER-ITERATION VALIDATION GATE -- Not Just at Release Time

**Every single iteration/turn that touches a file in the SDK repo, the consumer
template repo, or both -- even a "just docs" change -- must end with ALL of the
following verified before the task is considered complete, not only when
running the full release pipeline:**

```
1. COMPILE the SDK
   cd functional-test-automation-sdk
   mvn clean test          -- must be BUILD SUCCESS, zero test failures

2. COMPILE the consumer template against the SDK version currently in play
   cd functional-automation-consumer-template
   mvn compile test-compile -- must be BUILD SUCCESS

3. VERIFY every edited file in BOTH repos is actually committed -- not just
   that a release script "succeeded". A release script's own doc-commit step
   may only stage a subset of files (e.g. README.md + CHANGELOG.md) and
   silently leave others (pom.xml version bumps, TESTBASE-API.md, bundled
   resource mirrors) uncommitted even though `mvn deploy` already published
   an artifact built from those uncommitted working-tree changes:
   cd functional-test-automation-sdk
   git status --short       -- must be EMPTY
   cd functional-automation-consumer-template
   git status --short       -- must be EMPTY

4. VERIFY documentation is updated AND synced in BOTH repos for the change
   just made -- not just that "a doc exists":
   - SDK: CHANGELOG.md, README.md, SDK-USER-GUIDE.md (root + bundled mirror),
     TESTBASE-API.md, and any `.instructions.md` file whose content changed
     (root `.github/instructions/` + bundled `src/main/resources/sdk-instructions/`
     copy -- both must be byte-identical after every edit)
   - Template: pom.xml SDK version, README.md, SDK-USER-GUIDE.md,
     GETTING-STARTED.md, CHANGELOG.md -- all reflecting the SDK version
     actually depended on
```

> **This gate applies per-iteration, not just at release time.** A change that
> "will be committed later" or "the release script will pick it up" is not
> acceptable -- verify with `git status --short` in BOTH repos before telling
> the user the task is done. Discovering leftover uncommitted files (a version
> bump, a doc edit, a bundled-mirror sync) after the fact is a process failure.
>
> If a release script only commits a subset of the files you changed
> (e.g. it commits README.md/CHANGELOG.md but not pom.xml or TESTBASE-API.md),
> you are still responsible for committing the remainder yourself in the same
> task -- do not assume the script covered everything it touched.

---



### Canonical branch map -- ALWAYS use these, never guess:

| Repository | Default Branch | Push Target |
|---|---|---|
| `functional-test-automation-sdk` | `master` | `origin master` |
| `functional-automation-consumer-template` | `master` | `origin master` |
| `Poletop_Automation` | `trunk` | `origin trunk` AND `origin master` |
| `maven-repository` | `trunk` | `origin trunk` |

> **Poletop_Automation is special:** it has BOTH `trunk` and `master` branches on the remote.
> Every push to `trunk` must also be pushed to `master` to keep them in sync.
> Always run:
> ```bash
> git push origin trunk        # primary branch
> git push origin trunk:master # keep master in sync
> ```

### Before every push -- verify the correct branch:
```bash
git rev-parse --abbrev-ref HEAD          # confirm you are on the right branch
git fetch origin
git log origin/<branch>..HEAD --oneline  # must be EMPTY after push
git status --short                       # must be EMPTY after push
```

### When master and trunk diverge:
- If histories are unrelated (`fatal: refusing to merge unrelated histories`):
  -> **Do NOT attempt to merge.** Force-push the authoritative branch:
  ```bash
  git push origin trunk:master --force
  ```
- `trunk` is always the authoritative branch for `Poletop_Automation`.
- After force-push, verify both branches point to the same commit:
  ```bash
  git log --oneline origin/master -3
  git log --oneline origin/trunk  -3
  # Both must show identical commits
  ```

### Affected projects checklist -- for every SDK change:
After any SDK release, ALL of these must be updated and pushed:
- [ ] `functional-test-automation-sdk` `master` -- source + tests + CHANGELOG + README + pom.xml
- [ ] `maven-repository` `trunk` AND `master` -- new version JARs committed and pushed to BOTH branches
- [ ] `functional-automation-consumer-template` `master` -- pom.xml version bump, InstructionExtractor rerun
- [ ] `Poletop_Automation` `trunk` AND `master` -- pom.xml version bump, InstructionExtractor rerun
- [ ] Any other consumer projects registered with the team

> **`maven-repository` requires pushing to BOTH `trunk` and `master`** every release.
> The Azure DevOps UI defaults to browsing `master` -- if only `trunk` is pushed,
> artifacts will appear missing in the UI even though they are committed locally.
>
> After every SDK deploy, verify both branches are in sync:
> ```bash
> cd C:\Users\vkruglyak\IdeaProjects\maven-repository
> git push origin trunk
> git push origin trunk:master --force   # master must always mirror trunk
> git log --oneline origin/master -3
> git log --oneline origin/trunk  -3
> # Both must show identical top commits
> ```

---

**Test coverage rules:**
- Prefer unit tests (no browser, no network) -- fast, reliable, always runnable in CI
- Test the logic directly: config resolution, file parsing, string transformations, field flags
- Use `@Test` methods that assert specific outcomes -- no empty tests, no `assertTrue(true)`
- Test both the happy path AND the failure/edge case for every new feature

**CHANGELOG.md entry format (add under `[Unreleased]` during development, move to version heading at deploy):**
```markdown
### Changed / Added / Fixed
- **`ClassName` or area:** What changed and why.
  New keys, removed methods, behavioral changes, files added/removed.
```


## Project Identity
- **Artifact**: com.test.automation:cross-platform-functional-test-automation-sdk
- **Language**: Java 20 or newer (`Java >=20`). Generate code compatible with the SDK's minimum supported Java version; do not downlevel code to Java 8 for compatibility.
- **Core base class**: com.test.automation.sdk.testbase.TestBase
- **Listeners**: com.test.automation.sdk.listener.{Listener, RetryListener, Retry}
- **Utilities**: com.test.automation.sdk.utility.{Excel_Reader, PageContext, SdkConfig}
- **Driver factory**: com.test.automation.sdk.testbase.WebDriverFactory

---

## SDK Design Principles

1. **Framework only** - SDK contains NO test logic, NO page objects, NO application-specific locators.
2. **Zero hardcoded config** - all configuration read from consumer-supplied config.properties via SdkConfig.
3. **Backward compatible** - adding methods is fine; changing method signatures breaks consumers.
4. **Thread safe** - all shared state must use ThreadLocal (driver, currentPage, etc.).
5. **Java >=20 compatible** - keep examples and generated code aligned with the SDK's minimum supported Java version of 20 or newer.

---

## TestBase Extension Rules

When adding new helpers to TestBase:

### Helper method template:
```java
/**
 * <one-line description>
 * Retries up to 3 times on <ExceptionType>.
 */
public <ReturnType> <methodName>(<params>) {
    int attempts = 0;
    while (attempts < 3) {
        try {
            // interaction logic
            return result;
        } catch (WebDriverException e) {
            attempts++;
            log.warn("<methodName> attempt " + attempts + " failed: " + e.getMessage());
            if (attempts >= 3) throw new RuntimeException("<methodName> failed after 3 attempts", e);
            waitUntillPageLoad();
        }
    }
    throw new RuntimeException("Unreachable");
}
```

### Existing retry helpers (DO NOT duplicate):
| Method | Purpose | Retry |
|---|---|---|
| safeClick(WebElement) | Click with retry | 3 attempts, WebDriverException |
| clearAndType(WebElement, String) | Clear + type with retry | 3 attempts |
| safeGetText(WebElement) | Get text with retry | 3 attempts, StaleElementReferenceException |

---

## MANDATORY: Interaction Safety Rules (SDK enforces these)

The SDK helpers enforce the following sequence that ALL page objects must follow:
1. waitForElementPresent(driver, element) before any interaction
2. fluentWaitUntilElementToBeClickable(element) before clicks
3. safeClick / clearAndType / safeGetText (never raw Selenium)
4. waitUntillPageLoad() after any click that causes navigation

These rules must be preserved in all SDK helper implementations.

---

## WebDriverFactory Rules
- If webDrivers/chromedriver.exe exists -> use local binary
- Otherwise -> delegate to WebDriverManager (auto-downloads matching version)
- NEVER hardcode driver paths
- NEVER hardcode Chrome/browser versions

---

## SdkConfig Rules
- All properties read from config.properties supplied by consumer at runtime
- Path separator regex must be `[/\\\\]+$` (Java regex in a Java string literal)
- Fail fast with IllegalStateException if required properties are missing

---

## SDK Test Suite (src/test/java)
SDK has its own 39-test JUnit 5 suite validating framework logic (NOT end-to-end tests):

| Test class | What it tests |
|---|---|
| SdkConfigTest | Property loading, path normalization, missing-key behavior |
| TestBaseUnitTest | Pure-logic methods (no browser) |
| PageContextTest | ThreadLocal isolation |
| RetryTest | Retry logic and status name mapping |
| Excel_ReaderTest | POI workbook read operations |
| SdkSmokeTest | Class loading verification |

### Validation after any SDK change:
```bash
cd C:\Users\vkruglyak\IdeaProjects\test-automation-sdk
mvn clean test
```
All 39 tests must pass before publishing (mvn install) a new version.

---

## SDK Version Lifecycle

> [!]? **Steps 2, 3, and 4 are MANDATORY before every `mvn deploy`. No exceptions.**

1. Make code changes
2. **Update `CHANGELOG.md`** (SDK root) -- add entry under `[Unreleased]` describing every change:
   - What class/area changed and why
   - New keys, removed methods, behavioral changes
   - New test count and total passing
   - Move `[Unreleased]` entries to the new version heading when bumping the version
3. **Update `README.md`** (SDK root) -- reflect the change:
   - Component table: add/update rows for new/changed classes
   - Configuration snippet: update if new yaml keys were added
   - Copilot prompts table: add/update if prompts changed
   - SDK Version badge at the bottom
4. **Update documentation** -- `SDK-USER-GUIDE.md`, `src/main/resources/SDK-USER-GUIDE.md`, `TESTBASE-API.md`
5. Run `mvn clean test` -- all tests must pass
6. Bump version in `pom.xml` (e.g. `1.0.0 -> 1.0.1`)
7. Run `mvn clean deploy -DskipTests` to publish
8. **Update consumer template:**
   - Bump SDK version in `functional-automation-consumer-template/pom.xml`
   - Update `CHANGELOG.md` in the template repo -- add an entry under `[Unreleased]` noting the SDK version bump and what changed
   - **MANDATORY GATE:** Run `mvn compile test-compile` in the template BEFORE committing or pushing. If it fails, the template commit/push must NOT happen -- fix the breaking change (or the template) first. `release.ps1` enforces this automatically.

---

## MANDATORY: Documentation Maintenance

Every time a `TestBase` method is **added**, **changed**, or **removed**, both documentation
files must be updated in the **same commit** as the code change. A code change without
a matching doc update is considered incomplete.

### Documents to maintain

| File | What to update |
|------|---------------|
| `SDK-USER-GUIDE.md` (root + `src/main/resources/`) | Section 9 "TestBase API -- What You Can Use" -- add/update the method row in the relevant table; update code examples if the method signature changed |
| `TESTBASE-API.md` (root) | The section matching the method category -- add/update the full method row including signature, timeout, and description |

### Rules

1. **New public method added to TestBase**
   - Add a row to the correct table in `SDK-USER-GUIDE.md` Section 9
   - Add a full entry (signature + timeout + description) to the correct section in `TESTBASE-API.md`
   - If it fits a new category, create the category section in both documents

2. **Existing method signature changed**
   - Update the signature in both documents
   - Update all code examples in both documents that reference this method
   - If it is a breaking change, add a migration note to `SDK-PUBLISHING.md`

3. **Method removed**
   - Remove the row from both documents
   - Add a deprecation/removal note to `SDK-PUBLISHING.md` under the version heading

4. **Both copies of `SDK-USER-GUIDE.md` must always be identical**
   - Root: `SDK-USER-GUIDE.md`
   - Bundled: `src/main/resources/SDK-USER-GUIDE.md`
   - After any edit to one, copy to the other before committing

### Documentation update checklist (add to every PR / commit touching TestBase)

RULE: Docs must be updated IN THE SAME COMMIT as the code change.
The release script will abort if CHANGELOG.md [Unreleased] is empty.

```
- [ ] CHANGELOG.md [Unreleased] -- entry added describing the change
- [ ] SDK-USER-GUIDE.md root -- Section 9 table updated
- [ ] src/main/resources/SDK-USER-GUIDE.md -- identical copy updated
- [ ] TESTBASE-API.md -- relevant section updated (no resources mirror needed)
- [ ] .github/instructions/test-creation.instructions.md -- if usage pattern changed
- [ ] src/main/resources/sdk-instructions/test-creation.instructions.md -- identical copy
- [ ] .github/copilot-instructions.md -- if test class template changed
- [ ] src/main/resources/sdk-instructions/copilot-instructions.md -- identical copy
- [ ] Code examples in all docs are syntactically correct for Java 20 or newer
- [ ] All doc files are plain ASCII (no em-dashes, arrows, smart quotes)
- [ ] Both docs still render correctly as Markdown (no broken tables)
```

### Quick reference -- document structure map

```
New wait method         -> SDK-USER-GUIDE Section 9 (Waiting table) + TESTBASE-API Section 4 or 5 or 6 or 7 or 8
New click/input method  -> SDK-USER-GUIDE Section 9 + TESTBASE-API Section 9 or 10
New dropdown method     -> SDK-USER-GUIDE Section 9 (Dropdowns table) + TESTBASE-API Section 11
New window/tab method   -> SDK-USER-GUIDE Section 9 (Windows table) + TESTBASE-API Section 15
New alert method        -> SDK-USER-GUIDE Section 9 (Alerts table) + TESTBASE-API Section 16
New scroll/JS method    -> SDK-USER-GUIDE Section 9 (Scrolling table) + TESTBASE-API Section 17
New generator method    -> SDK-USER-GUIDE Section 9 (Data Generators table) + TESTBASE-API Section 20
New lifecycle change    -> SDK-USER-GUIDE Section 6 (Config) + TESTBASE-API Section 1
```

---

## Quality Checklist for SDK Changes
- [ ] Java >=20 compatible; do not instruct consumers or agents to downlevel to Java 8
- [ ] No hardcoded application URLs, locators, or credentials
- [ ] All shared state uses ThreadLocal
- [ ] New helpers have 3-attempt retry for transient WebDriverExceptions
- [ ] **New or updated unit tests added** -- every change must increase or maintain test count
- [ ] **Test count reported** in CHANGELOG entry: "N new tests; M total passing"
- [ ] All SDK unit tests pass after change (`mvn clean test`)
- [ ] No breaking changes to existing public method signatures
- [ ] Compiled with mvn compile and tested with mvn test before install
- [ ] **`CHANGELOG.md` updated** -- entry added under `[Unreleased]`
- [ ] **`README.md` updated** -- version, components, or config reflected
- [ ] **`SDK-USER-GUIDE.md` (root) updated** -- Section 9 table reflects the change
- [ ] **`src/main/resources/SDK-USER-GUIDE.md` updated** -- identical copy of root
- [ ] **`TESTBASE-API.md` updated** -- relevant section reflects the change
- [ ] **`pom.xml` version bumped** -- PATCH / MINOR / MAJOR as appropriate
- [ ] **Consumer template `pom.xml` updated** -- SDK version bumped
- [ ] **Consumer template `CHANGELOG.md` updated** -- SDK upgrade noted