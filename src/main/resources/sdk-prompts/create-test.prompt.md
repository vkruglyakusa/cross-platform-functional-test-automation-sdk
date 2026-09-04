---
mode: agent
description: Create a new automated test class using the test-automation-sdk framework
---

# Create New Automated Test

You are creating a new Selenium + TestNG automated test using the **test-automation-sdk** framework.
Follow every rule in `.github/instructions/` before writing any code.

---

## [*] Autonomous Mode -- Read First

Before starting, declare your operating mode:

> **"I will proceed autonomously. Please implement everything without asking follow-up
> questions. I accept all standard implementation decisions (locator selection,
> wait strategies, assertion patterns, Excel column naming, page object structure).
> Interrupt me only if a test step is technically impossible to automate or a
> required element has no stable locator."**

When the user grants autonomous mode:
- Do **not** ask for confirmation on routine implementation choices
- Do **not** stop for approval before each step
- Do **not** ask about naming, ordering, or structure -- use the conventions in `.github/instructions/`
- **DO** stop and ask only when:
  - A required locator has no `UNIQUE [x]` strategy and the crawler finds nothing stable
  - A test step is ambiguous and cannot be interpreted without clarification
  - A precondition requires credentials or access the agent cannot obtain
  - Technical implementation is blocked (see Step 6 -- Blocker Output)

If autonomous mode is **not** granted, ask one question at a time and wait for confirmation.

---

## Required Information

Before starting, confirm you have:
- **Target URL**: the page to automate
- **Test case source**: local file path OR Azure DevOps test case ID(s)
- **Test class name**: e.g. `Test_LoginPage`
- **Excel sheet name**: e.g. `LoginData`
- **Environment**: `stg` | `tst` | `dev`

If any of the above is missing, ask the user before proceeding.

---

## Execution Steps

### Step 0 -- Gate Check: Test Case Completeness
Before doing anything, review the test case source for gaps:

| Check | If NO -> |
|---|---|
| Every step has a clear UI action? | Run `#report-test-gap` -- stop here |
| Every step has a defined expected result? | Run `#report-test-gap` -- stop here |
| All required test data is specified? | Run `#report-test-gap` -- stop here |
| Preconditions are clear and achievable? | Run `#report-test-gap` -- stop here |

If **any check fails**, do NOT proceed to implementation.
Create the gap report in ``\${reporting.gapOutputDir}` (default: docs/test-case-gaps/)` and stop.
See `#report-test-gap` prompt and `test-case-gap.instructions.md`.

### Step 1 -- Run the Crawler
Before writing any `@FindBy` locator, run the crawler on the target page:

```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=${environment} -DbrowserName=chrome \
         -Dinv.email=${email} -Dinv.password=${password}
```

Review `test-output/crawler/` report. Use **only `UNIQUE [x]` locators**.
If no stable locators found for a required element -- write a blocker entry (see Step 6).

### Step 2 -- Create or Verify Page Object
- If page object for this page exists in `uiActions/`, verify all locators are current.
- If it does not exist, create it following `page-object-creation.instructions.md`.
- Every `@FindBy` must be `UNIQUE [x]` from the crawler report.
- **All test case steps must be represented by page object methods** -- if a step
  requires an action not yet in the page object, add the method now.

### Step 3 -- Create Test Class -- Full Step Coverage Required
Follow `test-creation.instructions.md` exactly:
- Class extends `TestBase`
- `@DataProvider` backed by Excel sheet `${Excel sheet name}`
- One `@Test` method per scenario
- `runMode` check first in every test method
- **`step("...")` for EVERY formal test case step** -- steps must be 1-to-1 with the source test case; no steps may be skipped or merged without a documented reason
- **`Assert.*` for EVERY expected result** -- every formal expected result from the test case must have a matching assertion; a test step with no assertion does not count as covered
- Cleanup at end of each test
- ADO traceability comment in class JavaDoc (if ADO source)

> [!]? **Coverage rule**: If a formal test case has N steps with expected results,
> the automated test must have at least N `Assert.*` calls covering those results.
> Steps where the expected result is "no error / page loads" must use an explicit
> element-presence or title assertion -- `assertTrue(true)` is never acceptable.

### Step 4 -- Map Excel Data
Document required Excel columns:
```
| Column name | Type | Description |
|---|---|---|
| testCaseName | String | Descriptive row label (include ADO ID if applicable) |
| ... | ... | ... |
| runMode | String | Y = run, N = skip |
```

### Step 5 -- Validate (MANDATORY -- do not skip)
```bash
# Compile check
mvn compile test-compile -q

# Execute -- ALL test rows with runMode=Y must pass
mvn test -Dtest=${Test class name} -Denvironment=${environment} -DbrowserName=chrome
```

Task is **NOT complete** until:
- `mvn compile test-compile` exits with code 0
- `mvn test` exits with code 0 (all `runMode=Y` rows pass)
- The completion report (Step 7) has been produced

### Step 6 -- Blocker Output (when a step CANNOT be automated)
If any test case step or expected result cannot be automated, do NOT silently skip it.
Instead:

1. Create file: ``\${reporting.gapOutputDir}` (default: docs/test-case-gaps/)${ClassName}-blocker-report.md`
2. Contents:
```markdown
# Implementation Blocker Report
**Test Class**: ${TestClassName}
**Generated**: ${timestamp}
**Source**: ${ADO-ID or file path}

## Blocked Steps

| Step # | Step Description | Expected Result | Reason Cannot Automate | Suggested Resolution |
|--------|-----------------|-----------------|------------------------|----------------------|
| 3 | Click the "Export" button | PDF downloads automatically | No stable locator -- mat-button-0 (dynamic) | Add data-testid to Export button |
| 7 | Verify email received | Confirmation email arrives | Email system not accessible in stg env | Configure Mailinator in sdk-config.yaml |

## Implementable Steps
Steps NOT listed above were successfully automated.

## Next Steps
- [ ] Resolve blockers listed above
- [ ] Re-run `#create-test` after blockers are resolved
```
3. Implement all automatable steps and mark blocked steps with `@Test(enabled=false)` and a `SkipException` explaining the blocker.
4. Set `runMode=BLOCKED` in Excel for those rows.

### Step 7 -- Completion Report (MANDATORY after successful run)
After `mvn test` passes, output the following report:

```
+==================================================================+
|  TEST IMPLEMENTATION COMPLETE                                    |
+==================================================================+
|  Test Class   : ${TestClassName}                                 |
|  Source       : ${ADO-ID or file}                                |
|  Environment  : ${environment}                                   |
+==================================================================+
|  COVERAGE                                                        |
|  Test case steps      : ${N}                                     |
|  Steps automated      : ${N} / ${N}    (or X/N if blockers)     |
|  Assertions added     : ${count}                                 |
|  Steps blocked        : ${0 or count} (see blocker report)       |
+==================================================================+
|  VALIDATION                                                      |
|  Compile              : ? PASS                                  |
|  Tests run            : ${count}                                 |
|  Tests passed         : ${count}                                 |
|  Tests skipped (N)    : ${count}                                 |
|  Tests failed         : 0                                        |
+==================================================================+
|  FILES CREATED / MODIFIED                                        |
|  ${list uiActions files}                                         |
|  ${list testCases files}                                         |
|  Blocker report       : ${path or "None"}                        |
|  CHANGELOG.md         : ? Updated                               |
|  README.md            : ? Updated                               |
+==================================================================+
```

---

## Step 8 -- Update CHANGELOG.md and README.md (MANDATORY)

### CHANGELOG.md
After the test passes, append one entry to `CHANGELOG.md` in the project root
(create the file if it does not exist):

```markdown
## [Unreleased]
### Added
- `${TestClassName}` -- automated ADO-${ID} / ${short test case title} (${date})
```

If `CHANGELOG.md` already has an `[Unreleased]` section, append under it.
If the file follows a different format already in the project, match that format.

### README.md
Update `README.md` to reflect the new test coverage. Find or create a
**"Test Coverage"** or **"What's Automated"** section and add a row:

```markdown
## Test Coverage

| Test Class | ADO ID | Scenario | Status |
|---|---|---|---|
| `${TestClassName}` | ADO-${ID} | ${short test case title} | ? Automated |
```

If the section already exists, append the new row.
If the README has a different structure, add the entry in the most relevant section.

**Show a diff-style summary** of what changed in README.md:
```
README.md changes:
  + | `Test_Login` | ADO-12345 | Valid login scenario | ? Automated |
```

---

## Traceability (if ADO source)
Include in class JavaDoc:
```java
/***********************************************************************
 * Test Case ID : ADO-XXXXX
 * Source       : Azure DevOps
 * Objective    : <formal objective>
 ***********************************************************************/
```

## Output Checklist
- [ ] Autonomous mode declared by user
- [ ] Crawler ran and report reviewed
- [ ] Page object created/updated with `UNIQUE [x]` locators only
- [ ] Test class created following template
- [ ] **Every formal step has a `step("...")` call**
- [ ] **Every expected result has an `Assert.*` call**
- [ ] Excel column mapping documented
- [ ] `mvn compile test-compile` passes
- [ ] Tests executed and passed (or skipped intentionally via `runMode=N`)
- [ ] Completion report produced
- [ ] Blocker report created if any steps could not be automated
- [ ] `CHANGELOG.md` updated with a one-line entry for this test class
- [ ] `README.md` updated -- new row in Test Coverage table with diff summary shown