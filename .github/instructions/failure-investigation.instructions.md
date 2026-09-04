---
applyTo: "**"
---

# Skill: Failure Investigation — Screenshot, DOM & Log Together

## Core Principle — RCA Before Fix

**No fix code is written until Root Cause Analysis is complete.**
This skill is the evidence-gathering mechanism that makes RCA possible.

Answer all 3 before writing any fix:
1. **What failed?** — exact element, method, or assertion
2. **Why did it fail?** — specific root cause from evidence
3. **What is the minimal fix?** — one targeted change

---

## The Non-Negotiable Rule

**When a test fails, the FIRST action is ALWAYS to review all THREE artifacts
together: the failure screenshot, the DOM dump, AND the log file.** No exceptions.
No code reading first. No guessing. No stopping after just screenshot + DOM.

This is the single most efficient way to diagnose any test failure. The screenshot
shows exactly what the browser saw at the moment of failure. The DOM dump shows
exactly what elements existed on the page. The log file shows the execution
timeline — every action taken before the failure, the exception chain, and any
retry-exhaustion warnings. **Screenshot and DOM show the end state; the log shows
how execution got there.** Reviewing only two of the three is insufficient and
routinely leads to fixing the wrong thing (e.g. a locator "fix" when the real
problem was a session timeout visible only in the log).

> ❌ **WRONG** — reading stack trace → reading page object → guessing → re-running
> ❌ **WRONG** — screenshot → DOM → fix (skipping the log)
> ✅ **RIGHT** — screenshot → DOM → log → understand root cause → targeted 1-line fix

---

## Where the Artifacts Are

Every test failure automatically produces all three artifacts via the SDK
`Listener` and Log4j2:

```
test-output/screenshots/<testCaseName>_<timestamp>.png        ← screenshot
test-output/screenshots/<testCaseName>_<timestamp>_DOM.html   ← full rendered DOM
test-output/logs/<testCaseName>_<timestamp>.log               ← execution log
                                                                  (or target/surefire-reports/<Class>-output.txt
                                                                  if a per-test-case log file isn't found)
```

The `testCaseName` prefix matches `setCurrentTestCaseName()` in the test.
The TestNG HTML report (`test-output/reports/`) has a clickable link to the DOM
dump directly in the failing test row.

---

## Step-by-Step: What To Do Every Time

### 1. Find the artifacts

```powershell
# Find most recent screenshots/DOM dumps for a test
Get-ChildItem "test-output\screenshots" -Filter "*<testName>*" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 4

# Find the matching log file
Get-ChildItem "test-output\logs" -Filter "*<testName>*" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
```

### 2. Open the screenshot with the `view` tool

Use the `view` tool on the `.png` file. Read it carefully.

### 3. Diagnose from screenshot

| What you see | Root cause | Action |
|---|---|---|
| Login / SSO page | Session expired or logout failed | Fix pre-condition / logout logic |
| Correct page, button/field missing | Locator stale or role-restricted | Run crawler on that URL; check user role |
| Correct page, wrong stage/state | Pre-condition reset failed | Fix setup method; check state machine |
| Validation error on screen | Wrong data, required field empty | Fix test data or form fill sequence |
| Spinner / blank page | Page not fully loaded | Add `waitUntillPageLoad()` / increase timeout |
| Unexpected modal/overlay | Element blocked by dialog | Add dismissal step |
| Completely different page | Wrong navigation | Fix navigation in page object |

### 4. Open the DOM dump

Search the DOM for the expected element's `@id`, `@formcontrolname`, `@value`, or label text.

```powershell
# Search DOM dump for an element attribute
Select-String -Path "test-output\screenshots\*DOM*.html" -Pattern "id='expectedValue'" |
  Select-Object -First 5
```

| What you find in DOM | Meaning | Action |
|---|---|---|
| Attribute present, correct value | Element exists but locator wrong | Fix XPath (use correct attribute) |
| Attribute present, DIFFERENT value | ID/value changed | Update `@FindBy` to new value |
| Attribute completely absent | Element not rendered yet | Check page state / add wait |
| Page redirected (wrong URL in DOM) | Navigation failed | Fix pre-condition or navigation |

### 5. Open the log file — MANDATORY, do not skip this step

The log is what turns "the screenshot looks wrong" into "I know exactly why."
Without it, the DOM/screenshot only show the *symptom*, not the *sequence of
actions* that produced it.

```powershell
# Search the log for the last actions before failure
Select-String -Path "test-output\logs\*<testName>*.log" -Pattern "Clicking|Entering|WARN|ERROR" |
  Select-Object -Last 20
```

| What you find in the log | Meaning | Action |
|---|---|---|
| Last action = click/type on the missing element | Confirms which interaction actually failed | Cross-reference with screenshot/DOM for that exact element |
| `safeClick`/`clearAndType` retry-exhaustion warning | Persistent interaction problem, not one-off timing | Investigate why retries all failed (overlay? wrong locator?) — do not just add `Thread.sleep()` |
| Unexpected URL change / redirect logged | Session timeout or navigation issue the screenshot alone wouldn't explain | Fix login/session handling, not the locator |
| Action logged as completed successfully, then assertion failed | This is an assertion/data mismatch (Type B), not a locator problem | Compare expected vs actual value — do not touch `@FindBy` |
| No log entry for the expected action at all | The test never reached that step | Look earlier in the log/code for an earlier failure or skipped branch |

> [!]? **If your root-cause conclusion from the screenshot/DOM doesn't line up with
> what the log shows happened right before the failure, the log wins — re-check your
> conclusion before writing a fix.**

---

## Rule Enforcement

This investigation rule applies to:
- **Any** `NoSuchElementException` or `TimeoutException`
- **Any** `AssertionError` (wrong state, wrong text, wrong element visible)
- **Any** pre-condition/setup helper failure
- **Any** locator fix request

**Do not read code first. Do not guess. Do not modify `@FindBy` without DOM evidence.
Do not conclude root cause from screenshot/DOM alone — the log must be reviewed too.**

Screenshot → DOM → Log → fix. In that order, all three, every time.

---

## Integration With Other Workflows

- This file and `test-fix.instructions.md` Rule 1 describe the **same mandatory
  3-artifact review** (screenshot + DOM + log) — they must never be followed as
  if only 2 of the 3 are required.
- After identifying root cause from screenshot/DOM/log, jump to the appropriate rule in
  `test-fix.instructions.md` (Type A, B, C, D, E, or F)
- If locator is broken, proceed to `fix-broken-locator.prompt.md`
- If state/pre-condition is wrong, read the state machine and fix the setup helper
- If the fix requires a new locator, ALWAYS confirm it against crawler output before committing
