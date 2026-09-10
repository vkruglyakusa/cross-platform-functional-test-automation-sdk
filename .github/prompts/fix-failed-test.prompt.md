---
mode: agent
description: Diagnose and fix a failing automated test in a test-automation-sdk project
---

# Fix a Failed Test

You are diagnosing and fixing a test failure in a **test-automation-sdk** project.
Work systematically through the triage steps below. Do not guess -- read the evidence first.

---

## [*] Autonomous Mode -- Read First

Before starting, declare your operating mode:

> **"I will proceed autonomously. Please investigate and fix the failure without
> asking follow-up questions unless the fix requires a decision that cannot be
> reversed (e.g. deleting a page object, disabling a test permanently). Apply
> standard fix patterns from test-fix.instructions.md without confirmation."**

When the user grants autonomous mode, apply fixes without step-by-step approval.
Stop and ask only when:
- The fix requires permanently disabling a test (`runMode=N`) -- confirm intent first
- The ADO test case is in `Closed`/`Inactive` state -- confirm whether to retire the test
- The product UI change is so significant the test case itself may need ADO updates

---

## Required Information

Provide one of the following:
- **Failure output**: paste the Maven surefire failure message or stack trace
- **Test class name**: the failing test -- agent will read the surefire report
- **Run command** used (so agent can re-run after fix)
- **ADO Test Case ID** (if the test has one -- used for the mandatory pre-fix ADO check)

---

## Triage Workflow

### Step 0 -- ADO Pre-Fix Change Check (MANDATORY -- always run first)

> [!]? **Before touching any code**, check whether the ADO test case was recently
> updated. A test may "fail" because the product correctly changed and the ADO
> expected results were updated -- meaning the test code needs to reflect the new
> expected behavior, not the old one.

**If the test has an ADO test case ID** (check class JavaDoc or `testCaseName` in Excel):

1. Use Azure DevOps MCP to fetch the test case:
   ```
   Get test case: ${ADO Test Case ID}
   Fields: title, steps, expected results, state, lastModifiedDate, lastModifiedBy
   ```

2. Compare `lastModifiedDate` of the ADO test case against the last git commit
   date of the test class file:
   ```bash
   git log -1 --format="%ci" -- src/test/java/**/${TestClassName}.java
   ```

3. Classify the result:

   | Condition | Conclusion | Action |
   |-----------|-----------|--------|
   | ADO modified **after** last test commit | ADO changed -- test is probably outdated | Treat as **ADO Drift** -> skip to Step 3F |
   | ADO modified **before** last test commit | ADO unchanged -- test code has a real bug | Continue to Step 1 (normal triage) |
   | ADO state is `Closed` or `Inactive` | Test case retired | Set `runMode=N`, add comment, stop |
   | No ADO ID found in class | Cannot check -- note this in output | Continue to Step 1 |

4. If ADO was modified after the last code commit:
   - Do **not** fix the test logic yet
   - Run `#ado-sync-test` to align the test with the updated ADO steps first
   - Only after sync is complete, re-run the test and check if it now passes

> **Rule**: If ADO changed since the last code change, ADO sync ALWAYS comes
> before any other fix activity. Fixing code to pass against stale expected
> results hides real product behavior.

---

### Step 1 -- Re-Run the Failing Test (MANDATORY -- always the very first action)

> [!]? **Do not open any artifact, do not read code, do not propose any fix
> until the test has been re-run and fresh artifacts have been produced.**

```bash
mvn test -Dtest=${TestClass}#${failingMethod} -Denvironment=stg -DbrowserName=chrome
```

**Why re-run first?**
Artifacts from a previous run reflect a previous state -- different app version, different
timing, possibly a transient condition. Only artifacts produced by THIS re-run are valid
evidence for RCA.

**Interpret the re-run result:**

| Re-run result | Action |
|---|---|
| **Passes** | Run it a second time. If both pass: transient failure -- document and close without code change. If second run fails: treat as real failure, proceed to Step 2. |
| **Fails** | Fresh artifacts are now ready. Proceed immediately to Step 2. |
| **Compile error** | Fix compile error first (`mvn compile test-compile -q`), then re-run. |

> [!]? **If the test passes on re-run: do NOT make any code changes yet.
> Run it a second time to confirm stability before closing.**

---

### Step 2 -- Collect All Three Fresh Artifacts (MANDATORY -- do not skip any)

> [!]? **Only open artifacts produced by the re-run in Step 1.
> Delete or ignore any artifact files with an older timestamp.**

| Artifact | Location | Open and examine |
|---|---|---|
| **Screenshot** | `test-output/screenshots/<testCaseName>_<timestamp>.png` | What did the browser show at the moment of failure? |
| **DOM dump** | `test-output/screenshots/<testCaseName>_<timestamp>_DOM.html` | Is the expected element in the DOM? With correct attributes? |
| **Log file** | `test-output/logs/<testCaseName>_<timestamp>.log` OR `target/surefire-reports/<TestClassName>-output.txt` OR console output | What was the last action logged before failure? Any retry exhaustion? Any redirect? |

**Required output after artifact review (state findings before proposing any fix):**
```
Re-run result      : FAILED (confirmed real failure)
Screenshot finding : <e.g. "Login page displayed", "Toast error visible", "Element missing">
DOM dump finding   : <e.g. "formcontrolname='email' present", "@id changed to mat-input-4", "element absent">
Log file finding   : <e.g. "Clicking Submit button", "safeClick retry 3/3 exhausted", "URL redirected to /login">
```

Then locate the surefire report for the exception details:
```
target/surefire-reports/<TestClassName>.txt
```
Identify: failing test method name, failure type, exact error message and line number.

---

### Step 3 -- Classify the Failure

| Failure Type | Symptoms | Go to |
|---|---|---|
| **Locator broken** | `NoSuchElementException`, `StaleElementReferenceException`, `TimeoutException` on a specific element | Step 4A |
| **Assertion failed** | `AssertionError: expected X but was Y` | Step 4B |
| **Test data mismatch** | `NullPointerException` in Excel reader, wrong column count, `SkipException` unexpectedly | Step 4C |
| **Timeout / page load** | `TimeoutException`, `WebDriverException: timeout`, browser never loaded | Step 4D |
| **Compile error** | `BUILD FAILURE` before tests run | Step 4E |
| **ADO drift** | Test logic no longer matches ADO test case steps (detected in Step 0 or by inspection) | Step 4F |

---

### Step 4A -- Fix: Broken Locator
The UI has changed. Run `#fix-broken-locator` for the affected page object.

Before crawling, confirm whether the UI change is reflected in the ADO test case
(was Step 0 completed? Did ADO change?). If ADO was NOT updated but the UI changed,
note this as a potential ADO gap after fixing.

```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=${email} -Dinv.password=${password}
```

Prefer secret-backed credential injection/environment variables if the crawler
needs authentication. Inline `-Dinv.email` / `-Dinv.password` style overrides are
compatibility fallbacks only.

Workflow:
1. Read the `@FindBy` XPath in the page object.
2. Re-run the crawler on the affected page.
3. Replace with the new `UNIQUE [x]` locator from the crawler report.
4. If element is completely gone from the UI: stop and report -- may need product team input.

---

### Step 4B -- Fix: Assertion Failed
1. Read the failing assertion and both values (expected vs actual).
2. Determine if:
   - **Expected value is wrong in the test** -> update the assertion or Excel data
   - **Actual value changed in the product** -> check ADO test case (Step 0 covered this; if not done yet, run `#ado-sync-test`)
   - **Timing issue** -> element text returned before page fully loaded -> add `waitForElementPresent()` before the read
3. Never change an assertion to make a test pass without understanding why the values differ.

---

### Step 4C -- Fix: Test Data Mismatch
1. Open the Excel file for this test class.
2. Verify column order matches `@DataProvider` parameter order exactly.
3. Verify the failing row has `runMode=Y` and all required fields populated.
4. If `getData()` throws: check `ExcelName` field is set in test class and sheet name matches exactly.

---

### Step 4D -- Fix: Timeout / Page Load
1. Check if the environment is reachable:
   ```bash
   mvn test -Dtest=${TestClass} -Denvironment=stg -DbrowserName=chrome
   ```
2. If environment is down: not a code issue -- report and skip.
3. If environment is up: check `waitUntillPageLoad()` is called after every navigation.
4. Increase `pageLoadTimeoutSeconds` in `configuration/sdk-config.yaml` if needed.
5. Verify no `Thread.sleep()` was replaced -- only `waitUntillPageLoad()` is acceptable.

---

### Step 4E -- Fix: Compile Error
1. Run: `mvn compile test-compile 2>&1 | grep "error:"`
2. Fix each import, missing method, or type mismatch.
3. Do not proceed to run tests until compile is clean.

---

### Step 4F -- Fix: ADO Drift
Test logic no longer matches the current ADO test case.
Run `#ado-sync-test` to compare and align the test script.
This is triggered automatically when Step 0 detects ADO was modified after last code commit.

---

### Step 5 -- Apply Fix and Re-Validate (MANDATORY)

**Before applying any fix, confirm the 3-artifact RCA checklist is complete:**
- [ ] Screenshot reviewed -- finding documented above
- [ ] DOM dump reviewed -- finding documented above
- [ ] Log file reviewed -- finding documented above

If any item is unchecked, complete Rule 1a before continuing.

```bash
# Compile
mvn compile test-compile -q

# Run 1 of 2 -- the test must pass
mvn test -Dtest=${TestClass}#${failingMethod} -Denvironment=stg -DbrowserName=chrome

# Run 2 of 2 -- stability check -- must also pass
mvn test -Dtest=${TestClass}#${failingMethod} -Denvironment=stg -DbrowserName=chrome
```

Task is **NOT complete** if:
- Compile still fails
- The same test still fails for the same reason
- Fix was applied without reviewing all three artifacts
- Run 1 passes but Run 2 fails -- the test is flaky; investigate instability before committing


### Step 6 -- Update CHANGELOG.md and README.md (MANDATORY)

### CHANGELOG.md
After the test passes, append one entry to `CHANGELOG.md` in the project root:

```markdown
## [Unreleased]
### Fixed
- `${TestClassName}.${methodName}` -- ${root cause type} fix: ${one-line description} (${date})
```

If `CHANGELOG.md` already has an `[Unreleased]` section, append under it.
If the file follows a different format already in the project, match that format.

### README.md
Update the `## Test Coverage` table in `README.md`.
Find the row for `${TestClassName}` and update its **Status** and **Last Updated** columns:

```
README.md changes:
  ~ | `${TestClassName}` | ${ADO-ID} | ${scenario} | ? Automated | ${date} |
```

If the row previously showed `?? Fixing` or `? Blocked`, update it to `? Automated`.
If the test was retired (`runMode=N` permanently), update status to `? Retired`.
Always show the before->after diff in the completion report.

---

## Output Format
After fixing, report:

```
+==================================================================+
|  TEST FIX COMPLETE                                               |
+==================================================================+
|  Test Class   : ${TestClassName}                                 |
|  Test Method  : ${methodName}                                    |
+==================================================================+
|  ADO PRE-FIX CHECK                                               |
|  ADO Test Case      : ${ADO-ID or "Not linked"}                  |
|  ADO Last Modified  : ${date or "N/A"}                           |
|  Last Code Commit   : ${date}                                    |
|  ADO Changed After  : ${YES -> sync ran first / NO / N/A}        |
+==================================================================+
|  3-ARTIFACT RCA                                                  |
|  Screenshot finding : ${what was visible at failure}             |
|  DOM dump finding   : ${element state in DOM}                    |
|  Log file finding   : ${last action + exception chain}           |
+==================================================================+
|  FIX DETAILS                                                     |
|  Root cause         : ${failure type -- A/B/C/D/E/F}             |
|  Fix applied        : ${description of change}                   |
|  Files changed      : ${list}                                    |
+==================================================================+
|  VALIDATION                                                      |
|  Compile            : ? PASS / ? FAIL                          |
|  Run 1 result       : ? PASS / ? SKIP (intentional) / ? FAIL  |
|  Run 2 result       : ? PASS / ? SKIP (intentional) / ? FAIL  |
|  Stability          : ${STABLE (both passed) / FLAKY (fix not complete)} |
|  CHANGELOG.md       : ? Updated                                 |
|  README.md          : ? Updated (diff shown above)              |
+==================================================================+
```