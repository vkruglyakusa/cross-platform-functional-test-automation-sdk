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
- **Platform**: `web` (Selenium/browser) | `android` | `ios` (Appium/native or hybrid app). If not stated, infer from the test case source (page URL vs. app screen name) and confirm with the user.
- **Target URL** (web) or **Target screen/app** (mobile): the page or screen to automate
- **Test case source**: local file path OR Azure DevOps test case ID(s)
- **Test class name**: e.g. `Test_LoginPage`
- **Excel sheet name**: e.g. `LoginData`
- **Environment**: `stg` | `tst` | `dev`

If any of the above is missing, ask the user before proceeding.

> **Web vs Mobile branching**: every step below has a web path (default) and a
> mobile path. Mobile steps use `mobile-locator-strategy.instructions.md` instead
> of `locator-strategy.instructions.md`, `MobileElementCrawler`/`MobilePageObjectGenerator`
> instead of `ElementCrawler`/`PageObjectGenerator`, and `MobileTestBase` instead
> of `TestBase`. Use the mobile path whenever **Platform** is `android` or `ios`.

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

### Step 0.5 -- Optional Implementation Plan Review

This is a **human-in-the-loop planning gate**, independent from Autonomous Mode
above. Autonomous Mode controls whether you're asked about *routine* implementation
choices; this gate controls whether the Test Engineer wants to see the overall
approach *once*, up front, before any code is generated. After understanding the
requested test (and passing the Step 0 gate check), ask:

> **Would you like to review the proposed test implementation steps before I create the test script?**

Treat the response conceptually as **Yes** or **No**. Do not force the engineer to
review the plan -- this question must always be asked, but a "No" answer is a
first-class, equally valid outcome.

**If No:** continue directly to Step 1. Still perform the same internal analysis
(objective, required pages/screens/endpoints, reusable components, test data,
assertions, cleanup, reporting/evidence) -- you simply don't present it for a
separate approval interaction. Do not later force the engineer through the
plan-approval workflow below unless a material ambiguity or genuine blocker
requires clarification (see Step 6).

**If Yes:** do NOT begin implementation (no crawler run, no file creation/edits)
yet. Inspect the repository first -- do not invent Page Objects, components,
`ApiClient`s, or test data builders before checking what already exists (see
"Reuse before creation" below). Then present a **Proposed Test Implementation
Plan**:

```markdown
## Proposed Test Implementation Plan

### Test Objective
<what behavior is being validated>

### Preconditions
<environment, test data, authentication, existing records, browser/device/API state>

### Test Flow
1. <numbered, implementation-level automation steps -- not vague business language>
2. ...

### Page Objects / Components
- Existing: <PageObject/ApiClient/component names actually found in the repo>
- New (required): <name + one-line reason, only if reuse is genuinely insufficient>

### Test Data
<required data, source, dynamic/generated data, cleanup requirements -- never
include actual passwords/secrets, describe how they are resolved instead
(e.g. "via existing SDK credential/secret-resolution flow")>

### Assertions
<every expected/intermediate result that will be asserted -- every plan must
include meaningful validation, not just actions>

### Reporting / Evidence
<only the applicable SDK evidence: Allure, Extent, Screenshot, DOM, Console,
Network, RCA, Accessibility>

### Cleanup
<postconditions if the test creates/modifies data; "None" if not applicable>

### Expected Files
<existing files expected to change, new files expected to be created -- based on
repository inspection, not assumption>
```

Adapt the plan's content to the platform:
- **Web**: Page Objects, locators (only from a fresh crawler run's `UNIQUE [x]` results, referenced not yet executed), browser interaction, wait strategy, navigation, screenshot/DOM/console evidence.
- **API**: endpoint, HTTP method, request payload, authentication, response status/schema/business assertions, cleanup of created resources. Do not introduce browser steps.
- **Mobile**: Appium screens/components, device/app state, gestures, platform (Android/iOS) differences, mobile test data.
- **Accessibility**: when accessibility validation is part of the request, include applicable scanning/report expectations (see §14 in `SDK-USER-GUIDE.md`).

After presenting the plan, ask for an explicit decision:

> **Do you approve these implementation steps?**

Offer **Approve**, **Request Changes**, or **Cancel** as the conceptual choices,
and do not implement anything while waiting for this decision.

- **Approve** -- the approved plan becomes the implementation contract. Proceed
  with Step 1 onward following the approved scope, test flow, assertions, reuse
  strategy, and files/components. Only accept clear approval intent (e.g.
  "Approve", "Approved", "Yes, implement", "Looks good, proceed", "Go ahead").
  Do **not** treat ambiguous responses ("maybe", "interesting", "looks close",
  "continue explaining") as approval -- ask again if intent is unclear.
- **Request Changes** -- do not start implementation. Incorporate the requested
  changes, present the revised plan, and ask for approval again. Repeat until
  **Approved** or **Cancelled**. A request for changes is never itself approval.
- **Cancel** -- do not create or modify the test. Report that test creation was
  cancelled by the engineer, and stop.

**Material deviations after approval:** if implementation later discovers a
meaningful difference from the approved plan (e.g. a required API/Page Object
doesn't actually exist, a different authentication architecture is required,
test data can't be created as planned, or a framework constraint changes the
test flow/assertions/architecture), stop implementation, explain the deviation,
and ask the engineer to approve the revised approach before continuing. Minor
implementation details that don't change the agreed test behavior (e.g. exact
wait timeout, internal helper method name) do not require another approval.

**Reuse before creation:** whether or not the plan-review gate is used, prefer
reusing existing Page Objects, components, utilities, `ApiClient`s, test data
builders, SDK services, helper methods, fixtures, and suite configuration over
proposing new ones. The plan (when presented) should make it clear whether any
new framework code is actually required.

### Step 1 -- Run the Crawler
Before writing any `@FindBy` locator, run the crawler on the target page.

**Credential handling:** prefer pipeline secrets, environment-backed values, or the
project's existing SDK credential/secret-resolution flow. Treat explicit inline
`-Dinv.email` / `-Dinv.password` style overrides as a temporary compatibility
fallback for one-off local troubleshooting only -- never as the default or a
committed script.

**Web:**
```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=${environment} -DbrowserName=chrome \
         -Dinv.email=${email} -Dinv.password=${password}
```
Review `test-output/crawler/` report. Use **only `UNIQUE [x]` locators**. Follow
`locator-strategy.instructions.md`.

**Mobile (android/ios):**
```bash
mvn test -Dsurefire.suiteXmlFiles=mobile_crawler_suite.xml \
         -Denvironment=${environment} -DmobileOS=${platform} \
         -Dinv.email=${email} -Dinv.password=${password}
```
Review the mobile crawler report. Use only `UNIQUE` locators and reject anything
flagged `DYNAMIC`/`STRUCTURAL`. Follow `mobile-locator-strategy.instructions.md`
(priority ladder, recycled-list-id rejection, React Native fallback rules).

If no stable locators found for a required element -- write a blocker entry (see Step 6).

### Step 2 -- Create or Verify Page Object
- If page object for this page/screen exists in `uiActions/`, verify all locators are current.
- **Web**: create following `page-object-creation.instructions.md` (class extends `TestBase`, `@FindBy` XPath only).
- **Mobile**: create a class extending `MobileTestBase` with `@AndroidFindBy`/`@iOSXCUITFindBy` annotations, following the same locator-uniqueness discipline via `mobile-locator-strategy.instructions.md`.
- Every locator must be `UNIQUE [x]` (web) / `UNIQUE` (mobile) from the crawler report.
- **All test case steps must be represented by page object methods** -- if a step
  requires an action not yet in the page object, add the method now.

### Step 3 -- Create Test Class -- Full Step Coverage Required
Follow `test-creation.instructions.md` exactly:
- **Web**: class extends `TestBase`.
- **Mobile**: class extends `MobileTestBase` and supplies `mobileOS`/`deviceName` TestNG parameters instead of `browserName`.
- Generate code compatible with Java 20 or newer (`Java >=20`). Do not downlevel generated code to Java 8.
- `@DataProvider` backed by Excel sheet `${Excel sheet name}`
- **Strict 1:1 Test Case Generation Contract**: one Azure Test Case ID MUST produce exactly one `@Test` method. Never split one TC ID's steps, preconditions, validations, cleanup, alternate UI paths, or many assertions across multiple `@Test` methods. This rule has higher priority than granularity, readability, decomposition, or attempts to create smaller independent tests. Helper methods (`login()`, `createRequest()`, `validateRequest()`, etc.) are encouraged for readability, but helper methods must NOT carry `@Test` unless they represent a genuinely separate Azure TC ID. Distinct Azure TC IDs in the input must equal generated `@Test` method count (for example: 3 distinct TC IDs -> exactly 3 `@Test` methods). This contract concerns `@Test` METHOD count, not TestNG runtime invocation count -- one `@Test(dataProvider = "data")` method with 10 rows is still one `@Test` method. Before returning code, self-validate: (1) extract distinct TC IDs, (2) count them, (3) count generated `@Test` annotations, (4) verify counts match, (5) verify each `@Test` maps to exactly one TC ID, (6) verify no TC ID maps to multiple `@Test` methods, and (7) verify helper methods contain no `@Test`. If any check fails, the result is INVALID and must be corrected before returning it. See `formal-testcase-to-script.instructions.md` for full contract and examples.
- `runMode` check first in every test method
- **SDK `step("...", () -> { ... })` for EVERY formal test case step** -- steps must be 1-to-1 with the source test case; no steps may be skipped or merged without a documented reason
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
|  IMPLEMENTATION PLAN                                              |
|  Plan reviewed by engineer : Yes / No                            |
|  Plan approved              : Yes / No / N/A (not reviewed)       |
|  Implemented per approved plan : Yes / No + reason if No          |
|  Deviations from approved plan : ${none or description}          |
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

Omit the `IMPLEMENTATION PLAN` block's individual lines only when the engineer
never used the plan-review gate at all (Step 0.5 answered "No") -- in that case a
single `Plan reviewed by engineer : No` line is sufficient.

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
- [ ] Implementation plan review offered (Step 0.5); if accepted, plan presented and explicit approval obtained before any implementation began
- [ ] Crawler ran and report reviewed
- [ ] Page object created/updated with `UNIQUE [x]` locators only
- [ ] Test class created following template
- [ ] Test Case Generation Contract validated: `@Test` count == distinct TC ID count (1:1, no splitting/merging)
- [ ] **Every formal step has an SDK `step("...", () -> { ... })` call**
- [ ] **Every expected result has an `Assert.*` call**
- [ ] Excel column mapping documented
- [ ] `mvn compile test-compile` passes
- [ ] Tests executed and passed (or skipped intentionally via `runMode=N`)
- [ ] Completion report produced (including plan-review/approval outcome)
- [ ] Blocker report created if any steps could not be automated
- [ ] `CHANGELOG.md` updated with a one-line entry for this test class
- [ ] `README.md` updated -- new row in Test Coverage table with diff summary shown