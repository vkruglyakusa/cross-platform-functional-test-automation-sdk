---
applyTo: "src/test/java/**/*.java"
---

# Skill: Test Failure Diagnosis and Fix Rules

## Purpose
These rules apply automatically whenever a test class is being edited in the context
of a failure. They define how to diagnose a failure and which fix pattern to apply.

---

## Core Principle -- RCA Before Fix (Non-Negotiable)

> [!]? **Never start fixing until Root Cause Analysis (RCA) is complete.**
> **Never write a single line of fix code before reviewing ALL THREE failure artifacts.**

A fix applied without confirmed root cause is a guess. Guesses produce tests that
pass for the wrong reason, hide real product bugs, or break again on the next run.

### RCA means: collect ALL THREE primary artifacts first, then act once

The three primary failure artifacts are **mandatory** -- all three must be reviewed
before touching any code, running the crawler, or making any decision:

| Priority | Artifact | Location | What it tells you |
|---|---|---|---|
| **1 -- MANDATORY** | **Screenshot** | `test-output/screenshots/<testCaseName>_<timestamp>.png` | Exact visual state of the browser at the moment of failure |
| **2 -- MANDATORY** | **DOM dump** | `test-output/screenshots/<testCaseName>_<timestamp>_DOM.html` | Full live-rendered HTML -- confirms element presence, attributes, locator state |
| **3 -- MANDATORY** | **Log file** | `test-output/logs/<testCaseName>_<timestamp>.log` or console output | Execution trace -- every action taken before the failure, exception chain, timing |

> [!]? **All three artifacts are captured automatically on every test failure by `Listener`
> and the Log4j2 configuration. If any artifact is missing, investigate why before
> proceeding -- a missing log or screenshot is itself a symptom.**

**Additional evidence sources (after the 3 primary artifacts):**

| # | Evidence Source | What it tells you |
|---|---|---|
| 4 | **Surefire report** (`target/surefire-reports/<Class>.txt`) | Exception type, stack trace, line number -- use to pinpoint the exact failure line |
| 5 | **ADO test case** (via MCP if ID exists) | Whether the expected behavior itself changed |
| 6 | **Git log** (`git log -1 -- <file>`) | When the test code was last changed vs when ADO was last modified |
| 7 | **Test method + page object** (source code) | The logic path that led to failure |

**RCA is complete when you can answer all three questions:**
1. *What exactly failed?* (exception + line from surefire report)
2. *Why did it fail?* (root cause confirmed by all 3 artifact artifacts -- screenshot shows browser state, DOM confirms element state, log shows execution path)
3. *What is the minimal change that fixes the root cause?*

Only after answering all three should you write a single line of fix code.

> [!]? **The three artifacts together form a complete picture that a single artifact cannot.
> The screenshot shows WHAT the browser displayed. The DOM shows WHAT HTML was present.
> The log shows WHAT sequence of actions led there. All three are required -- reviewing
> only one or two is insufficient and leads to incomplete or wrong fixes.**

---

## Rule 0 -- ADO Pre-Fix Check (Mandatory First Step)

**Before reading surefire reports or touching any code**, check whether the test's
source ADO test case was modified after the last code commit.

### Why This Rule Exists
A test failure may be correct behavior: the product changed, ADO was updated to
reflect that, but the test code was not yet synced. Fixing the test code against
stale expected values would produce a test that passes but validates the wrong thing.

### How to Execute

1. **Find the ADO test case ID** -- look in the test class JavaDoc or `testCaseName` column in Excel.

2. **If an ADO ID exists**, fetch the test case via Azure DevOps MCP:
   ```
   Get test case: ${ADO Test Case ID}
   Fields: lastModifiedDate, lastModifiedBy, state, steps, expected results
   ```

3. **Find the last code commit date** for the test class:
   ```bash
   git log -1 --format="%ci" -- src/test/java/**/${TestClassName}.java
   ```

4. **Decision table**:

   | ADO State | ADO Modified vs Code | Required Action |
   |-----------|----------------------|-----------------|
   | `Closed` / `Inactive` | Any | Set `runMode=N`, add comment, stop -- do not fix |
   | `Active` | ADO modified **after** code | Run `#ado-sync-test` first; do NOT fix code until synced |
   | `Active` | ADO modified **before** code | Proceed to Rule 1 -- normal fix triage |
   | No ADO ID found | N/A | Note in output, proceed to Rule 1 |

5. **Only when ADO pre-check clears** (ADO not modified after last code, or no ADO ID),
   proceed with the fix workflow from Rule 1.

> [!]? **If ADO changed after last code commit, run `#ado-sync-test` instead of fixing
> the test. The sync may resolve the failure without any additional fix work.**

---

## Rule 1 -- Re-Run First, Then Read the Artifacts
**The first action after receiving a failure report is always to re-run the test —
not to read old artifacts, not to read code, not to make changes.**

> See `failure-investigation.instructions.md` for the full step-by-step procedure
> for reviewing the screenshot, DOM dump, and log file together.

Re-running produces fresh, synchronized artifacts (screenshot + DOM dump + log) that
reflect the current state of the application and the current code. Stale artifacts
from a previous run may reflect a different app state, a different code version, or
a transient condition that no longer exists.

```bash
# Step 1: Re-run the failing test to produce fresh artifacts
mvn test -Dtest=<ClassName>#<failingMethod> -Denvironment=stg -DbrowserName=chrome
```

> [!]? **Do not open any artifact file until this re-run has completed.
> The artifacts you analyze must come from this re-run, not from any prior run.**

If the test **passes** on re-run:
- The original failure was transient (network blip, timing, environment instability)
- Run it a **second time** to confirm it is consistently stable
- If both re-runs pass: document the transient failure and close without a code change
- If the second re-run fails: treat as a real failure and proceed to Rule 1a

If the test **fails** on re-run:
- Fresh artifacts are now available -- proceed immediately to Rule 1a
- Never modify code before completing Rule 1a

**Never modify code to fix a failure without first reading ALL THREE primary artifacts
produced by the re-run:**
1. **Screenshot** -- mandatory (see Rule 1a)
2. **DOM dump** -- mandatory (see Rule 1a)
3. **Log file** -- mandatory (see Rule 1a)
4. The full surefire failure report: `target/surefire-reports/<TestClass>.txt`
5. The failing test method and the page object method called at the point of failure

**Skipping any of the three primary artifacts is a process violation.** If an artifact
is unavailable after re-run, document why before proceeding.

---

## Rule 1a -- Review All Three Failure Artifacts (Mandatory First Action)

> [!]? **This is the FIRST thing you must do after any test failure -- before reading
> code, before running the crawler, before making any changes.**

`Listener` automatically captures the screenshot and DOM dump on every test failure.
The log file is written by Log4j2 throughout execution. **All three must be reviewed
before any action is taken** -- together they provide a complete and unambiguous picture
of what happened.

### Mandatory 3-artifact review sequence:

```
Step 1: SCREENSHOT
   Locate:  test-output/screenshots/<testCaseName>_<timestamp>.png
   Action:  Open and examine -- identify the exact browser state at failure

Step 2: DOM DUMP
   Locate:  test-output/screenshots/<testCaseName>_<timestamp>_DOM.html
   Action:  Search for the missing/expected element by @id, @formcontrolname,
            @placeholder, or visible text -- confirm whether it exists in DOM

Step 3: LOG FILE
   Locate:  test-output/logs/<testCaseName>_<timestamp>.log
            OR: target/surefire-reports/<TestClassName>-output.txt (console capture)
            OR: console output from the mvn test run
   Action:  Trace the execution sequence -- find the last successful action before
            failure, identify the exception chain and the triggering method call
```

> [!]? **All three steps are mandatory. Do not skip to the log without seeing the
> screenshot. Do not fix after the screenshot alone without checking the DOM.
> Each artifact answers a different question -- all three are needed.**

### What to look for in the screenshot

| What you see | Likely failure type | Action |
|---|---|---|
| Login / session expired page | Type D -- session/env issue | Verify env is up; check login step |
| Page loaded but element missing | Type A -- locator broken | Re-run crawler |
| Validation error / toast message | Type B -- wrong data or state | Check test data and pre-conditions |
| Blank / partially loaded page | Type D -- timing / page load | Add `waitUntillPageLoad()` |
| Correct page, wrong field value | Type B -- assertion mismatch | Compare screenshot value vs assertion |
| Unexpected modal / overlay | Type A -- element obscured | Add dismissal step before the action |
| Wrong page entirely | Wrong navigation step | Check page object navigation method |

### What to look for in the DOM dump
- The DOM dump captures the **live rendered DOM** (Angular/React rendered, not raw HTML)
- Use it when the screenshot is not enough (e.g. element is present but not visible, hidden by CSS)
- Search for the element's expected `@id`, `@formcontrolname`, or `@placeholder` in the HTML
- If the attribute is missing entirely -- the locator is broken (Type A)
- If the attribute exists but has a different value -- update the `@FindBy`
- If the element is present but inside an unhandled frame or shadow DOM -- update the page object to switch context

### What to look for in the log file
- Trace the **last action logged before the exception** -- this is the failing step
- Look for `log.info("Clicking ...")` or `log.info("Entering ...")` entries -- the last one before the failure identifies the exact interaction that broke
- Look for **warning or error lines** emitted by `WebEventListener` -- browser-level errors (JS console errors, navigation errors) appear here
- Look for **retry exhaustion messages** (`safeClick` / `clearAndType` retry count reached) -- signals a persistent element interaction problem, not a transient timing issue
- Look for **unexpected URL changes** logged during navigation -- may indicate a redirect or session timeout that the screenshot confirms
- If the log shows the action completed successfully but the assertion failed -- this is a Type B (assertion) failure, not a locator failure

> [!]? **The log file is the execution timeline. Combined with the screenshot (end state)
> and the DOM dump (element state), it tells the complete story: what was done, what the
> page looked like, and what was in the DOM when the test failed. All three together
> eliminate guesswork entirely.**

---

## Rule 1b -- Artifact-Driven Fast Path (Apply After Rule 1a)

Once all three artifacts have been reviewed, the fix path is already known.
Go directly to the indicated action -- do not perform additional code investigation
or run the crawler unless the table explicitly says to.

| Screenshot shows | DOM dump confirms | Log file shows | Fix type | Direct action |
|---|---|---|---|---|
| Element not found / timeout | Attribute **missing** from DOM | Last log entry = action on that element | **Type A** | Run crawler → update `@FindBy` with new `UNIQUE [x]` locator |
| Element not found / timeout | Attribute present, **value changed** | Last log entry = action on that element | **Type A** | Update `@FindBy` value to match DOM → confirm `UNIQUE [x]` |
| Element not found / timeout | Attribute present, **value unchanged** | Log shows click completed, then timeout | **Type A (obscured)** | Check for overlay/modal in screenshot → add dismissal step |
| Validation error / toast visible | N/A | Log shows form submitted with specific data | **Type B** | Fix test data in Excel or fix pre-condition setup |
| Wrong value in field or page | N/A | Log shows element read succeeded | **Type B** | Compare assertion expected value vs screenshot actual → update assertion or Excel |
| Wrong page / login page | N/A | Log shows redirect / session timeout | **Type D** | Verify environment is up; check login step in test |
| Blank / partial page | N/A | Log shows navigation clicked, no page-ready logged | **Type D** | Add `waitUntillPageLoad()` after the navigation click |
| Unexpected modal or overlay | Element present behind overlay | Log shows action reached correct element | **Type A (obscured)** | Add modal dismissal step in page object before the failing interaction |
| Correct page, correct element visible | N/A | Log shows retry exhaustion on interaction | **Type B or D** | Assert value mismatch or persistent timing -- compare screenshot value vs assertion |

> [!]? **The log column is not optional in this table. If the log was not reviewed,
> go back to Rule 1a before using this fast path.**

---

## Rule 2 -- Failure Classification

Every test failure belongs to one of these types. Identify it before writing any fix:

### Type A -- Locator Failure
**Symptoms:** `NoSuchElementException`, `StaleElementReferenceException`,
`TimeoutException` on `waitForElementPresent()` or `fluentWait*`

**Required fix:** Re-run the crawler. Replace `@FindBy` with new `UNIQUE [x]` locator.
Do **not** hardcode a structural XPath fallback.
Use `#fix-broken-locator` prompt for guided workflow.

### Type B -- Assertion Failure
**Symptoms:** `AssertionError: expected:<X> but was:<Y>`

**Required fix:**
- If `Y` is the correct current product behavior -> update assertion and/or Excel expected value
- If `Y` is wrong -> debug the page object method returning the value
- If linked to ADO test case -> run `#ado-sync-test` to check if ADO expected result changed
- **Never change an assertion to pass without understanding why values differ**

### Type C -- Test Data Failure
**Symptoms:** `NullPointerException` in `getData()`, `ArrayIndexOutOfBoundsException`,
column count mismatch, wrong data type

**Required fix:**
1. Verify Excel column order matches `@DataProvider` method parameter order exactly
2. Verify sheet name matches the string passed to `getData()`
3. Verify `ExcelName` is set in the test class (usually in `@BeforeClass`)
4. Verify all required columns exist in the Excel sheet

### Type D -- Timeout / Infrastructure Failure
**Symptoms:** `WebDriverException: timeout`, page never loaded, browser never opened

**Required fix:**
1. Verify target environment URL is reachable
2. Check `pageLoadTimeoutSeconds` in `sdk-config.yaml` (increase if slow environment)
3. Verify `waitUntillPageLoad()` is called after every navigation click
4. Do not add `Thread.sleep()` -- it is never the correct fix

### Type E -- Compile Failure
**Symptoms:** `BUILD FAILURE` before any test runs; `error:` in compile output

**Required fix:**
1. Run `mvn compile test-compile 2>&1 | grep "error:"` to list all errors
2. Fix import errors, missing methods, type mismatches
3. All compile errors must be resolved before running tests

### Type F -- ADO Drift
**Symptoms:** Test passes but does not reflect current ADO test case steps;
assertions do not match current expected results in ADO

**Required fix:** Run `#ado-sync-test` prompt.

---

## Rule 3 -- Fix Verification is Mandatory
After every fix attempt, verify in this order:

**3a. Confirm all three RCA artifacts were reviewed** (non-negotiable pre-condition):
- [ ] Screenshot reviewed and finding documented
- [ ] DOM dump reviewed and finding documented
- [ ] Log file reviewed and finding documented
- If any artifact was skipped, the fix may be addressing the wrong root cause -- go back to Rule 1a.

**3b. Compile and re-run:**
```bash
mvn compile test-compile -q                                                   # must succeed
mvn test -Dtest=<ClassName>#<method> -Denvironment=stg -DbrowserName=chrome   # Run 1 of 2
mvn test -Dtest=<ClassName>#<method> -Denvironment=stg -DbrowserName=chrome   # Run 2 of 2 -- stability check
```

A fix is **not complete** until:
- Compile passes with zero errors
- The previously failing test now passes on **both** consecutive runs
- If Run 1 passes and Run 2 fails -- the test is flaky; do NOT commit; investigate the instability

> [!]? **A fix that passes on one run but fails on another is not a fix.
> The double-run requirement exists precisely to catch fixes that mask flakiness.**

---

## Rule 4 -- Forbidden Fix Patterns

| Pattern | Why Forbidden |
|---|---|
| Adding `Thread.sleep(N)` to fix timing | Masks the real issue; breaks in slow envs |
| Changing assertion to always `assertTrue(true)` | Hides product bug |
| Wrapping failure in `try/catch` to swallow it | Test will always pass, never catch regressions |
| Using structural XPath `//div[3]/input[1]` | Breaks on any UI change |
| Removing the failing assertion | Eliminates test coverage |
| Setting `runMode=N` without explanation | Silently disables test; must add comment |

---

## Rule 5 -- ADO Traceability After Fix
If the fix changes an assertion or step:
- Add or update the ADO test case reference comment on that line
- If the product behavior changed (not test code error), update the ADO test case steps too
