---
mode: agent
description: Retrieve an Azure DevOps test case, compare it to the existing automation script, detect drift, and apply updates
---

# Sync Test Script with Azure DevOps Test Case

You are checking whether an existing automated test script still accurately reflects
its source Azure DevOps (ADO) test case, and applying any necessary updates.

## Required Information

- **ADO Test Case ID**: e.g. `12345` or full work item URL
- **Automated test class**: e.g. `Test_LoginPage.java`
- **Page object(s)** used by the test (agent will discover from test class if not provided)

---

## Execution Steps

### Step 1 -- Retrieve the ADO Test Case
Use the Azure DevOps MCP tool to fetch the test case:
```
Get test case: ${ADO Test Case ID}
Fields needed: title, steps, expected results, preconditions, state, last modified date
```

Extract and list:
- Test case **title** and **ID**
- Each **step** (action + expected result)
- **Preconditions**
- Last modified date of the ADO test case

### Step 2 -- Read the Current Automation Script
Read the full content of `${Test class name}.java`.

Map each `step("...")` and `Assert.*` call to the corresponding ADO step + expected result.

Build a comparison table:
```
| ADO Step | ADO Expected Result | Automated step("...") | Assertion | Match? |
|---|---|---|---|---|
| 1. Enter credentials | Login successful | step("Enter credentials") | assertTrue(dashboard.isVisible()) | ? |
| 2. Click logout | Session ended | ? NOT FOUND | ? NOT FOUND | ? DRIFT |
```

### Step 3 -- Detect Drift

Drift conditions that require a test update:

| Drift Type | Condition | Required Action |
|---|---|---|
| **Missing step** | ADO step has no matching `step("...")` in test | Add the missing step + assertion |
| **Missing assertion** | ADO expected result has no matching `Assert.*` | Add assertion for that expected result |
| **Changed expected result** | ADO expected result text differs from assertion logic | Update assertion to match ADO intent |
| **Extra step in test** | Test has `step()` with no ADO counterpart | Review -- may be setup/teardown, may be stale |
| **Precondition not implemented** | ADO precondition exists but test has no corresponding setup | Add precondition steps |
| **Test case obsolete** | ADO state is `Closed` or `Inactive` | Set `runMode=N` in Excel, add comment |
| **Gap detected** | ADO step is ambiguous, has no expected result, or references missing data | Run `#report-test-gap` -- do NOT partially implement |

> [!]? **Gap Rule:** If a missing or changed step **cannot be implemented** because the ADO
> test case itself is incomplete (ambiguous step, no expected result, missing test data),
> do NOT write a placeholder assertion. Instead run `#report-test-gap` to create
> `docs/test-case-gaps/ADO-${ID}-gap-report.md` and mark the affected method with
> `enabled = false` + `SkipException`.

### Step 4 -- Check Page Object Currency
For each page object used by the test:
1. List all `@FindBy` locators.
2. If ADO steps reference UI elements that are not in the page object -> use `#fix-broken-locator` to add them after running the crawler.
3. If ADO steps reference UI elements removed from the product -> mark them with a comment and set `runMode=N`.

### Step 5 -- Apply Updates
Apply only the changes needed to align the test with the ADO test case.
Follow `formal-testcase-to-script.instructions.md` for every change.

For every updated/added assertion, add traceability comment:
```java
// ADO Step 3: Expected -- "Error message displayed"
Assert.assertTrue(loginPage.isErrorMessageVisible(), "Error message should be visible after invalid login");
```

Update `testCaseName` in Excel to include the ADO test case ID:
```
ADO-12345 -- Valid Login with correct credentials
```

### Step 6 -- Validate (MANDATORY)
```bash
mvn compile test-compile -q
mvn test -Dtest=${TestClass} -Denvironment=stg -DbrowserName=chrome
```

---

## No-Drift Outcome
If no drift is detected:
```
ADO Test Case : ADO-${ID} -- ${Title}
Last Modified : ${date}
Drift Detected: None ?
No code changes required.
```

## Drift Found Outcome
```
ADO Test Case : ADO-${ID} -- ${Title}
Last Modified : ${date}
Drift Detected: YES
Changes Applied:
  - Added step 3 assertion (error message check)
  - Updated step 5 expected text (button label changed)
  - Added runMode=N for obsolete scenario row
Compile      : ?
Re-run result: ?
```

## Gap Found Outcome
```
ADO Test Case : ADO-${ID} -- ${Title}
Last Modified : ${date}
Gap Detected  : YES -- test case is incomplete
Gap Report    : docs/test-case-gaps/ADO-${ID}-gap-report.md  <- CREATED
Blocked Steps : Step ${N} (no expected result), Step ${M} (ambiguous action)
Action Needed : Product Owner / BA to update ADO test case
Next Step     : Re-run #ado-sync-test after gap is resolved
```
