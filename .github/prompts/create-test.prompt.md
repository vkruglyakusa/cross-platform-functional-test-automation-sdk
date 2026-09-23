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
above, and it applies to **every kind of test creation** this prompt covers --
Web, API, Mobile, Accessibility, end-to-end, integration, regression, and smoke
tests alike. This is not an accessibility-specific or platform-specific feature.
Autonomous Mode controls whether you're asked about *routine* implementation
choices; this gate controls whether the Test Engineer / Operator wants to see the
**exact code-level implementation** *once*, up front, before any file is created
or modified. After understanding the requested test (and passing the Step 0 gate
check), ask:

> **Would you like to review the detailed implementation plan before I create or modify the test script?**

Treat the response conceptually as **Yes** or **No**. Do not force the operator to
review the plan -- this question must always be asked, but a "No" answer is a
first-class, equally valid outcome.

**If No:** continue directly to Step 1. Still perform the same internal analysis
(existing reusable code, test flow, test data, assertions, files to modify,
validation approach) -- you simply don't present it for a separate approval
interaction. Do not later force the operator through the plan-approval workflow
below unless a material ambiguity or genuine blocker requires clarification (see
Step 6).

**If Yes:** STOP before implementation. No crawler run, no file creation, no file
edits, no suite/config changes yet. First inspect the actual repository --
existing tests, Page Objects/screen objects, API clients, services, utilities,
data builders, fixtures, configuration, TestNG suites, base classes, listeners,
SDK APIs, and other common components. The plan must be **repository-aware**: do
not invent class names, methods, directories, endpoints, or locators if the
repository already contains the real implementation. Anything that does not
already exist must be explicitly labeled `NEW`. Then present a **Detailed Test
Implementation Plan** describing the actual code changes intended, not merely a
restatement of business/test-case steps:

```markdown
## Detailed Test Implementation Plan

### Implementation Summary
Files modified: <count>
New files: <count>
New test methods: <count>
New Page Objects / Components: <count>
Existing Page Objects / Components reused: <count>
New test data: <description or "None">
New configuration: <description or "None">
New dependencies: <description or "None">
Suite XML changes: <Yes/No>
Assertions: <count>

### Test Objective
<what behavior is being validated>

### Preconditions
<environment, test data, authentication, existing records, browser/device/API state>

### Files to Create / Modify
MODIFY
<actual repository path>
Reason: <why this file changes>

NEW
<actual repository path -- only if reuse is genuinely insufficient>
Reason: <why no existing file/class covers this>

### Class / Method Changes
<ClassName>
ADD / MODIFY:
<methodSignature()>
Purpose: <what it does>
Uses: <existing methods/components it calls>
Change (if MODIFY): <exactly what is being changed and why>

### Existing Components to Reuse
<ComponentName>
    <one-line description of the existing behavior being reused>
... (list every reused Page Object / ApiClient / utility / base class / service)

### New Components (only if required)
NEW COMPONENT
<ComponentName>
Reason: <why no existing component covers this>
Methods proposed:
<method1()>
<method2()>

### Test Flow
1. <numbered, implementation-level automation steps -- not vague business language>
2. ...

### Assertions
ASSERTION 1
<expected condition being validated>
Reusable helper: <existing assertion helper, or "New -- none exists">
ASSERTION 2
...
(every meaningful expected result must appear here -- a plan of actions with no
assertions is not acceptable)

### Test Data
Reuse: <existing test-data file/sheet, or "None">
Add: <new scenario/record description, or "None">
Credentials: <"No credentials hardcoded -- resolved via existing SDK
credential/secret-resolution flow" or equivalent -- never include actual secrets>
Cleanup requirements: <description or "None">

### Locators / UI Interaction Plan  (Web/Mobile only -- omit for API-only tests)
Reuse: <existing @FindBy fields/locators actually found in the page object>
New locator (if required):
Preferred strategy: <e.g. id/data-testid/formcontrolname per locator-strategy.instructions.md>
Fallback: <only a stable alternative -- never introduce a brittle/positional locator>

### API Implementation Plan  (API tests only -- omit for pure Web/Mobile UI tests)
HTTP method: <GET/POST/...>
Endpoint: <actual endpoint>
Request payload: <request model/class>
Authentication: <existing auth mechanism used>
Headers: <if relevant>
Expected status: <e.g. 201>
Response model: <class/schema>
Schema validation: <applicable or "None">
Business assertions: <field-level assertions>
Cleanup: <resource deletion/expiry approach>

### Mobile Implementation Plan  (Mobile tests only)
Appium screens/components: <existing MobileScreen classes reused, new ones needed>
Device/application state: <preconditions>
Platform: <Android/iOS differences, if any>
Gestures: <swipe/tap/etc., if any>
Navigation: <screen transitions>
Permissions: <if applicable>
Test data: <mobile-specific>
Cleanup: <if applicable>

### Test Suite Impact
TestNG Suite Change: <Yes/No>
File: <actual suite XML path, if Yes>
Change: <exact class/method being added>

### Configuration Impact
SDK configuration change: <description or "None">
Environment configuration change: <description or "None">
New property: <description or "None">

### Dependency Impact
New Maven dependencies required: <Yes -- name + reason / No>
pom.xml / SDK version / plugin changes: <description or "None">

### Reporting and Evidence
<only the applicable SDK evidence already handled automatically -- Allure, Extent,
Screenshot, DOM, Console, Network, RCA, Accessibility -- do not propose redundant
custom reporting code the SDK already provides>

### Expected Result
<the intended business validation in one or two sentences>

### Cleanup / Postconditions
<what happens to any created/modified state after the test runs, or "None
required" with the reason>

### Assumptions / Risks  (include only if genuine uncertainty exists)
ASSUMPTION: <...>
RISK: <...>
PROPOSED APPROACH: <how the plan resolves it, pending repository evidence>
```

Adapt the plan's content to the platform, per the sections above:
- **Web**: Page Objects, locators (only from a fresh crawler run's `UNIQUE [x]` results, referenced not yet executed), browser interaction, wait strategy, navigation, screenshot/DOM/console evidence.
- **API**: endpoint, HTTP method, request payload, authentication, response status/schema/business assertions, cleanup of created resources. Do not introduce browser steps.
- **Mobile**: Appium screens/components, device/app state, gestures, platform (Android/iOS) differences, mobile test data.
- **Accessibility**: when accessibility validation is part of the request, include applicable scanning/report expectations (see §14 in `SDK-USER-GUIDE.md`).

After presenting the plan, ask for an explicit decision:

> **Do you approve this implementation plan?**

Offer **Approve**, **Request Changes**, or **Cancel** as the conceptual choices,
and do not implement anything while waiting for this decision.

- **Approve** -- the approved plan becomes the implementation contract. Proceed
  with Step 1 onward following the approved scope, test flow, assertions, reuse
  strategy, and files/components. Only accept clear approval intent (e.g.
  "Approve", "Approved", "Yes, implement", "Looks good, proceed", "Go ahead").
  Do **not** treat ambiguous responses ("maybe", "interesting", "looks close",
  "continue explaining") as approval -- ask again if intent is unclear.
- **Request Changes** -- do not start implementation. Incorporate the requested
  changes, clearly highlight what changed, present the revised plan, and ask for
  approval again. Repeat until **Approved** or **Cancelled**. A request for
  changes is never itself approval.
- **Cancel** -- do not create or modify the test. Report that test creation was
  cancelled by the operator, and stop.

**Material deviations after approval:** if implementation later discovers a
meaningful difference from the approved plan (e.g. a planned method/API/Page
Object doesn't actually exist, the API behaves differently, the Page Object
architecture differs, test data cannot be created as planned, an additional
shared component is required, a planned assertion cannot be implemented
correctly, or a framework constraint changes the test flow/files/architecture/
assertions/data/scope/configuration/dependencies), STOP implementation and
explain using this structure:

```markdown
Approved Plan:
<relevant excerpt of what was approved>

Discovered During Implementation:
<what repository reality actually is>

Required Change:
<the revised approach>
```

Ask the operator to approve the revised approach before continuing. Minor
implementation details that don't change the agreed test behavior (local
variable names, import ordering, minor private helper extraction, formatting,
exact wait timeout) do not require another approval.

**Reuse before creation:** whether or not the plan-review gate is used, prefer
reusing existing Page Objects, components, utilities, `ApiClient`s, test data
builders, SDK services, helper methods, fixtures, and suite configuration over
proposing new ones. The plan (when presented) should make it clear whether any
new framework code is actually required.

**Final comparison after implementation (only when the plan was reviewed and
approved):** once implementation and validation (Step 5) are complete, report an
**Approved Plan vs. Implemented Result** comparison before or as part of the Step
7 completion report:

```markdown
## Approved Plan vs Implemented Result
Approved files to change: <count>       Actual files changed: <count>
Approved new components: <count>        Actual new components: <count>
Approved assertions: <count>            Implemented assertions: <count>
Approved suite changes: <Yes/No>        Implemented suite changes: <Yes/No>
Material deviations: <None, or list with the approval obtained for each>
```

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
|  Plan reviewed by operator  : Yes / No                            |
|  Plan explicitly approved   : Yes / Not required (not reviewed)   |
|  Implemented per approved plan : Yes / No + reason if No          |
|  Deviations from approved plan : ${none or description}          |
+==================================================================+
|  APPROVED PLAN VS IMPLEMENTED RESULT (only if plan was reviewed) |
|  Approved / Actual files changed      : ${N} / ${N}              |
|  Approved / Actual new components     : ${N} / ${N}              |
|  Approved / Implemented assertions    : ${N} / ${N}              |
|  Approved / Implemented suite changes : Yes|No / Yes|No           |
|  Material deviations                  : ${none or list}          |
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

Omit the `IMPLEMENTATION PLAN` and `APPROVED PLAN VS IMPLEMENTED RESULT` blocks'
individual lines only when the operator never used the plan-review gate at all
(Step 0.5 answered "No") -- in that case a single `Plan reviewed by operator : No`
line is sufficient. Never claim success if only compilation was performed --
`Tests run` / `Tests passed` must reflect an actual `mvn test` execution.

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
- [ ] Detailed implementation plan review offered (Step 0.5) for every test creation request (Web/API/Mobile/Accessibility/other); if accepted, the plan was repository-aware (existing components inspected, `NEW` items explicitly labeled), covered files/classes/methods/reuse/new components/test flow/assertions/test data/locators-or-API-or-mobile detail/suite impact/configuration impact/dependency impact/reporting/expected result/cleanup, and explicit approval was obtained before any implementation began
- [ ] Material deviations from an approved plan (if any) were paused on and re-approved before continuing
- [ ] Approved Plan vs Implemented Result comparison produced when the plan was reviewed
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