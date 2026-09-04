---
applyTo: "src/test/java/**/*.java"
---

# Skill: Creating Test Classes

## Trigger
Apply this skill whenever creating a new test class in `testCases/`.
If test logic comes from formal test cases, also apply:
`formal-testcase-to-script.instructions.md`.
If this test depends on state produced by another test class (or a shared
record also used by other tests), also apply:
`test-data-dependency.instructions.md`.

## Autonomous Mode Consent
At the start of any test implementation task, request the following confirmation
from the user and proceed autonomously once granted:

> **"I will proceed autonomously without interrupting you for routine decisions.
> I will ask only when: (1) a required element has no stable locator,
> (2) a test step is technically impossible to automate,
> (3) a precondition requires information I cannot infer.
> All standard decisions (naming, ordering, wait patterns, assertion structure,
> Excel column naming) will follow the conventions in `.github/instructions/`."**

Once the user confirms, do **not** ask approval for:
- Which wait method to use
- How to name Excel columns
- Which assertion method to use for a given check
- Whether to add log.info() calls
- Page object method naming

**Do** pause and ask when:
- The crawler finds no `UNIQUE [x]` locator for a required element
- A test step is ambiguous enough that two different automations would produce different outcomes
- The test requires environment credentials not available in config

## Step-by-Step Process

### Step 1 -- Verify the Page Object Exists
Before writing a test class, confirm the Page Object for the target page exists in `uiActions/`.
If it does not exist -> **run the crawler first** (see `page-object-creation.instructions.md`).
> [!]? Crawler class: `com.test.automation.tools.LocatorInvestigator` -- package `tools`, NOT `testCases`.
> Always invoke via `crawler_suite.xml`, never via `regression_suite.xml`.

### Step 1.1 -- Normalize Formal Test Case Input
If the source is a formal test case (file or ADO), first extract:
- case ID/title
- preconditions
- test data values
- step-by-step actions
- expected result per step

Then implement each expected result as an assertion in the automated test.

### Step 2 -- Class Structure (mandatory template)
```java
package com.test.automation.testCases;

import static io.qameta.allure.Allure.step;
import java.io.IOException;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.automation.poletop.testBase.TestBase;
import com.automation.poletop.uiActions.MyPage;  // page object

/***********************************************************************
 * Class Name  : Test_<FeatureName>
 * Objective   : <what this test suite validates>
 * URL         : <target page URL>
 * Created By  : <author>
 ***********************************************************************/
public class Test_<FeatureName> extends TestBase {

    private MyPage myPage;  // declare page objects here

    @DataProvider(name = "<dataProviderName>")
    public Object[][] <dataProviderName>() throws IOException {
        return getData("<ExcelSheetName>");
    }

    @Test(dataProvider = "<dataProviderName>", priority = 1,
          description = "<what this test verifies>")
    public void test<ScenarioName>(
            String testCaseName, /* ... data columns ... */, String runMode)
            throws Exception {

        if ("N".equalsIgnoreCase(runMode))
            throw new SkipException("Skipping: " + testCaseName);
        setCurrentTestCaseName(testCaseName);  // required for distinct report entries and paired failure artifacts per DataProvider row

        log.info("=== START: " + testCaseName + " ===");

        step("Navigate to page");
        driver.get(baseURL);
        waitUntillPageLoad();

        step("Initialize page objects");
        myPage = new MyPage(driver);

        step("Perform action");
        // call page object methods

        step("Assert expected result");
        Assert.assertTrue(/* condition */, "Failure message");

        step("Clean up");
        // logout or navigate away

        log.info("=== END: " + testCaseName + " ===");
    }
}
```

### Step 3 -- Test Scenario Rules
| Scenario Type | Required Assertions |
|---|---|
| Positive (happy path) | Assert success state is visible; assert NO error visible |
| Negative (invalid input) | Assert error message IS visible; assert success NOT visible |
| Boundary (empty fields) | Assert validation message visible; assert user cannot proceed |
| End-to-End | Assert each page transition + final confirmation |

#### Full Step Coverage -- Mandatory
Every formal test case step with an expected result **must** have:
- A `step("...")` call matching the step description
- An `Assert.*` call covering the expected result

**Steps that say "no error" or "page loads" require an explicit presence assertion --
`assertTrue(true)` is forbidden.**

If a step cannot be automated, do NOT silently skip it. Instead:
1. Create ``\${reporting.gapOutputDir}` (default: docs/test-case-gaps/)${ClassName}-blocker-report.md`
2. Document: step number, step description, expected result, reason blocked, suggested resolution
3. Mark the corresponding `@Test` method with `@Test(enabled=false)` and a `SkipException`
4. Set `runMode=BLOCKED` in Excel for that row
5. Implement all other automatable steps normally

#### Assertion Quality Rules -- Mandatory
Every `Assert.*` call must satisfy ALL of the following:

| Rule | Forbidden | Required |
|---|---|---|
| **Specific condition** | `Assert.assertTrue(true)` | `Assert.assertTrue(page.isErrorVisible(), ...)` |
| **Meaningful message** | `Assert.assertTrue(x, "")` or no message at all | `Assert.assertTrue(x, "Expected X after clicking Y but element was not visible")` |
| **No silent swallow** | `try { assert... } catch (AssertionError e) {}` | Let assertions propagate |
| **Exact value check** | `Assert.assertTrue(text.contains(""))` | `Assert.assertEquals(actual, "Expected Text", "...")` |
| **Negative validated** | No assertion after an invalid-input action | Assert the specific error message text the app shows |

**Assertion message format** (mandatory for all `Assert.*` calls):
```java
// Assert.assertTrue
Assert.assertTrue(
    myPage.isSuccessBannerVisible(),
    "Success banner should be visible after saving, but was not found on page"
);

// Assert.assertEquals
Assert.assertEquals(
    myPage.getStatusText(),
    "Submitted",
    "Status label should read 'Submitted' after form submission"
);

// Assert.assertFalse (for negative scenarios)
Assert.assertFalse(
    myPage.isErrorBannerVisible(),
    "No error banner should appear on valid login, but error was displayed"
);
```

**Minimum assertion count per scenario:**
- Positive scenario: at least **2** assertions (success visible + no error visible)
- Negative scenario: at least **2** assertions (error visible + success NOT visible)
- End-to-end flow: at least **1** assertion per page transition + 1 on final state

### Step 4 -- Excel Sheet Setup
Every test class needs a corresponding Excel sheet. Minimum required columns:

| Column | Type | Description |
|---|---|---|
| `testCaseName` | String | Descriptive row label |
| *(domain columns)* | String | Test-specific data |
| `runMode` | String | `Y` = execute, `N` = skip |

### Step 5 -- Naming Conventions
- Class name: `Test_<FeatureInPascalCase>` -> e.g. `Test_PoletopLogin`
- Test method: `test<ScenarioInCamelCase>` -> e.g. `testValidLogin`
- Data provider name: matches sheet name in camelCase -> e.g. `loginData`
- Priority: P1 = positive flows, P2 = negative flows, P3 = boundary/edge

### Step 6 -- Quality Checklist
Before finalizing a test class:
- [ ] Class extends `TestBase`
- [ ] All `@Test` methods have a `dataProvider`
- [ ] `runMode` checked before any test logic
- [ ] `setCurrentTestCaseName(testCaseName)` called after `runMode` check in every data-driven `@Test`
- [ ] **Every formal step has a `step("...")` call**
- [ ] **Every expected result has a matching `Assert.*` call**
- [ ] **Every assertion has a descriptive failure message (not empty string, not omitted)**
- [ ] **No `assertTrue(true)` used as a placeholder**
- [ ] **Positive scenarios have >= 2 assertions (success visible + no error)**
- [ ] **Negative scenarios have >= 2 assertions (error visible + success not visible)**
- [ ] Email flows use `getEmailUrl` / `getEmailText` / `getConfirmationEmailUrl` -- **never** import `MailinatorEmailReader` directly
- [ ] Cleanup (logout/navigate) at the end
- [ ] Compiles: `mvn test-compile -q`
- [ ] Blocker file created for any steps that could not be automated

### Step 7 -- Mandatory Validation Before Closing Any Task (Required)
After **any** creation or modification of a test class or page object:

**7a. Compile check (always first):**
```bash
mvn compile test-compile -q
```
Fix all errors before proceeding. Do not skip.

**7b. Execute the test class -- twice (stability requirement):**
```bash
# Run 1 of 2
mvn test -Dtest=<YourTestClass> `\
         -Denvironment=stg -DbrowserName=chrome

# Run 2 of 2 -- must also pass to confirm stability
mvn test -Dtest=<YourTestClass> `\
         -Denvironment=stg -DbrowserName=chrome
```

Both runs are required. A test that passes on Run 1 and fails on Run 2 is flaky
and must not be committed. Fix the instability before marking the task done.

**7c. If a run fails -- re-run once more first to produce fresh artifacts, then do RCA:**

> [!]? **Do not analyze any artifact file from a previous session.
> Always re-run the test to produce fresh, synchronized artifacts, then review all three.**

| Artifact | Location (use the timestamp from the failing re-run) |
|---|---|
| **Screenshot** | `test-output/screenshots/<testCaseName>_<timestamp>.png` |
| **DOM dump** | `test-output/screenshots/<testCaseName>_<timestamp>_DOM.html` |
| **Log file** | `test-output/logs/<testCaseName>_<timestamp>.log` OR console output |

Review findings from all three before writing any fix. After applying the fix, re-run both times again.

**7d. Interpret results:**
| Result | Action |
|---|---|
| Both runs: `BUILD SUCCESS`, all tests passed | Task complete |
| Both runs: tests skipped (`runMode=N` in Excel) | Expected -- not a failure |
| Run 1 pass, Run 2 fail | Flaky -- re-run for fresh artifacts, investigate, fix, re-run twice |
| Either run: `BUILD FAILURE` -- compile error | Fix and re-run 7a |
| Either run: `BUILD FAILURE` -- test failure | Re-run for fresh artifacts, review all 3, fix root cause, re-run twice |
| Test stuck / browser never closes | Login or locator issue -- debug before marking done |

**7e. If page object locators were changed:**
```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml `\
         -Denvironment=stg -DbrowserName=chrome `\
         -Dinv.email=<email> -Dinv.password=<password>
```

> ?? **A task is NOT complete if:**
> - `mvn compile test-compile` fails
> - Either of the two required test runs fails
> - Tests were never executed after code changes
> - The test is flaky (inconsistent results across the two runs)

---

## Step 8 -- Completion Report (MANDATORY after successful mvn test run)

After `mvn test` exits with code 0, output this report -- do not skip it:

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
|  Steps automated      : ${N} / ${N}  (or X/N if blockers)       |
|  Assertions added     : ${count}                                 |
|  Steps blocked        : ${0 or count -- see blocker report}       |
+==================================================================+
|  VALIDATION                                                      |
|  Compile              : ? PASS                                  |
|  Run 1 -- Tests run   : ${count} / passed: ${count} / failed: 0  |
|  Run 2 -- Tests run   : ${count} / passed: ${count} / failed: 0  |
|  Stability            : STABLE (both runs passed)                |
|  Tests skipped (N)    : ${count}                                 |
+==================================================================+
|  FILES CREATED / MODIFIED                                        |
|  ${list uiActions files}                                         |
|  ${list testCases files}                                         |
|  Blocker report       : ${path or "None"}                        |
+==================================================================+
```

> A task is marked complete **only after this report is produced with
> Tests failed = 0** (or 0 unintentional failures).

---

## Step 9 -- Interaction Safety Rules in Test Classes

> [!]? These rules apply in test classes just as in page objects.

### Do NOT call Selenium directly in test classes:
```java
// WRONG -- raw Selenium in a test class
driver.findElement(By.xpath("//button")).click();
Thread.sleep(2000);

// CORRECT -- delegate all interaction to page object methods
myPage.clickSubmit();   // page object handles waits and retries internally
```

### Do NOT add waits in test classes:
- All `waitUntillPageLoad()`, `waitForElementPresent()`, and `fluentWait*` calls belong in **page object methods**.
- Test classes call **page object methods only** -- they should read like a business script.

### Test-Level Retry (automatic):
Every `@Test` method automatically gets **3 retry attempts** via `RetryListener` configured in `regression_suite.xml`:
```xml
<listener class-name="com.automation.poletop.listener.RetryListener"/>
```
- **Do NOT** add `retryAnalyzer = Retry.class` manually to individual `@Test` annotations -- it is applied globally by `RetryListener`.
- Retry fires on transient failures (network, timing) up to 3 times before marking the test failed.
- Method-level retry (inside page object helpers `safeClick`, `clearAndType`, `safeGetText`) provides a separate inner 3-attempt guard at the element interaction level.

### Two-tier retry model:
```
Test level  -> RetryListener retries the entire @Test method up to 3 times
Method level -> safeClick / clearAndType / safeGetText retry the single interaction up to 3 times
```
Both layers are always active. No manual configuration needed in test classes.