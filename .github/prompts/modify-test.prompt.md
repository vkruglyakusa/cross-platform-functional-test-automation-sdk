---
mode: agent
description: Modify an existing test class or page object in a test-automation-sdk project
---

# Modify Existing Test or Page Object

You are modifying existing automation code in a **test-automation-sdk** project.
Follow every rule in `.github/instructions/` before making any change.

---

## [*] Autonomous Mode -- Read First

Before starting, declare your operating mode:

> **"I will proceed autonomously. Apply all changes without asking for confirmation
> on routine implementation decisions (naming, wait strategy, assertion patterns,
> locator selection from UNIQUE [x] crawler results). Interrupt me only when:
> a locator has no stable replacement, a step cannot be implemented, or you need
> to permanently disable a test."**

When autonomous mode is granted, do not ask approval for each change.
Stop and ask only when permanently disabling a test or when a locator cannot be resolved.

---

## Required Information

Tell me what you need to change:
- **Modification type** (choose one):
  - Add a new `@Test` method to an existing class
  - Add a new Excel data row / test case to an existing sheet
  - Update an existing assertion or expected result
  - Update a locator in a page object
  - Add a new field/method to a page object
  - Refactor or rename something
- **Target class**: e.g. `Test_LoginPage` or `PoletopLoginPage`
- **Source** (optional): ADO test case ID or description of change

If the source is an ADO test case, run `#ado-sync-test` first to detect drift before making changes.

---

## Optional Implementation Plan Review

For a **significant** modification (new `@Test` method, new scenario, meaningful
assertion/flow changes, locator strategy changes affecting multiple elements),
ask, independently of Autonomous Mode above:

> **Would you like to review the proposed implementation steps before I modify the test script?**

- **No** -- proceed with the normal workflow below (Steps 1-7). Still internally
  analyze what will change (objective, affected pages/components, test data,
  assertions, cleanup, evidence) without a separate approval interaction.
- **Yes** -- do not modify anything yet. Present a **Proposed Test Implementation
  Plan** in the same format used by `#create-test` (Objective, Preconditions,
  Test Flow, Page Objects/Components -- existing vs. new, Test Data, Assertions,
  Reporting/Evidence, Cleanup, Expected Files), scoped to the requested change.
  Then ask **"Do you approve these implementation steps?"** with **Approve**,
  **Request Changes**, or **Cancel**. Do not implement while awaiting this
  decision. Only clear approval intent (e.g. "Approve", "Yes, implement", "Go
  ahead") counts -- ambiguous responses are not approval. **Request Changes**
  triggers a revised plan and another approval request; **Cancel** stops the
  modification entirely and must be reported as such. If a material deviation
  from an approved plan is discovered mid-implementation, stop and get renewed
  approval before continuing; minor implementation details do not require it.

**Do not create unnecessary approval friction for trivial, mechanical fixes**
(formatting, import corrections, a single locator swap already backed by a fresh
crawler `UNIQUE [x]` result, renaming) -- follow the existing lightweight Steps
1-7 workflow directly for those.

---

## Execution Steps

### Step 1 -- Read the Existing Code
Before changing anything:
1. Read the target class fully.
2. Understand existing `@DataProvider`, `@Test` methods, and Excel sheet columns.
3. If modifying a page object, identify all existing `@FindBy` locators.

### Step 2 -- If Locators Need Updating
**Do not guess new locators.** Re-run the crawler first. Prefer secret-backed
credential injection/environment variables for any required login. Inline
`-Dinv.email` / `-Dinv.password` style overrides are compatibility fallbacks only:
```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=${email} -Dinv.password=${password}
```
Use only `UNIQUE [x]` locators from the new report.
If the element is no longer present, stop and ask -- do not use structural fallbacks.

### Step 3 -- Apply Changes
Follow the appropriate instruction file:
- Page object change -> `page-object-creation.instructions.md`
- New test method -> `test-creation.instructions.md`
- ADO-sourced change -> `formal-testcase-to-script.instructions.md`

**Rules that must not be violated:**
- Never add raw `Thread.sleep()` -- use `waitUntillPageLoad()` or `waitForElementPresent()`
- Never use raw `element.click()` -- use `safeClick(element)`
- Never use raw `element.getText()` -- use `safeGetText(element)`
- Never add a `@Test` method without a `@DataProvider`
- Never remove the `runMode` skip check
- **Every new step in the test case must have an SDK `step("...", () -> { ... })` call**
- **Every expected result must have a matching `Assert.*` call**

### Step 4 -- Update Excel Data (if needed)
If adding a new scenario, add the corresponding row to the Excel sheet with `runMode=Y`.
Document any new columns added.

### Step 5 -- Validate (MANDATORY)
```bash
# Compile check
mvn compile test-compile -q

# Execute modified class
mvn test -Dtest=${TargetClass} -Denvironment=stg -DbrowserName=chrome
```

Task is **NOT complete** until compile passes and tests run (pass or intentionally skip).

### Step 6 -- Update CHANGELOG.md and README.md (MANDATORY)

### CHANGELOG.md
After tests pass, append one entry to `CHANGELOG.md` in the project root:

```markdown
## [Unreleased]
### Changed
- `${TargetClass}` -- ${short description of modification} (${date})
```

If `CHANGELOG.md` already has an `[Unreleased]` section, append under it.
Match the existing format if the file already exists.

### README.md
Update the **"Test Coverage"** section (or equivalent) to reflect the change.
If the modification adds a new scenario, add a new row.
If it updates an existing one, update the existing row status/description.

**Show a diff-style summary** of what changed in README.md:
```
README.md changes:
  ~ | `Test_Login` | ADO-12345 | Valid login + invalid login scenarios | ? Automated |
```
(`~` = modified row, `+` = new row)

### Step 7 -- Completion Report
After `mvn test` passes, output:
```
+==================================================================+
|  MODIFICATION COMPLETE                                           |
+==================================================================+
|  Target       : ${TargetClass}                                   |
|  Change type  : ${modification type}                             |
+==================================================================+
|  Plan reviewed by engineer : Yes / No                            |
|  Plan approved / N/A        : Yes / No / N/A                     |
|  Implemented per approved plan : Yes / No + reason / N/A          |
+==================================================================+
|  VALIDATION                                                      |
|  Compile      : ? PASS                                          |
|  Re-run result: ? PASS / ? SKIP (intentional) / ? FAIL        |
|  Files changed: ${list}                                          |
|  CHANGELOG.md : ? Updated                                       |
|  README.md    : ? Updated (diff shown above)                    |
+==================================================================+
```

---

## Output Checklist
- [ ] Autonomous mode declared by user
- [ ] Implementation plan review offered for significant changes; if accepted, plan approved before any modification began
- [ ] Existing code read before any change was made
- [ ] Crawler re-run if any locator was modified
- [ ] Change applied following correct instruction file
- [ ] **Every new step has an SDK `step("...", () -> { ... })` call**
- [ ] **Every expected result has an `Assert.*` call**
- [ ] Excel data updated if new scenario added
- [ ] `mvn compile test-compile` passes
- [ ] Modified tests executed and verified
- [ ] Completion report produced
- [ ] `CHANGELOG.md` updated