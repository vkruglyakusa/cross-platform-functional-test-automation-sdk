# SDK Fix Prompt: Allure `updateTestCase` Lifecycle Race — "Could not update test case: test case with uuid X not found"

> **Status: RESOLVED in SDK v1.5.2.** See `CHANGELOG.md` `[1.5.2]` and
> `SDK-USER-GUIDE.md` §13.4.1 for the shipped fix description. This document
> is retained as the original root-cause investigation/fix-request record;
> its body below (including "SDK 1.5.1") describes the state *at the time
> the bug was found and diagnosed*, prior to the fix.

## Where
- **Repo**: `cross-platform-functional-test-automation-sdk`
- **File**: `src/main/java/com/test/automation/sdk/reporting/AllureExecutionReporter.java`
- **Method**: `applyTestMetadata(ExecutionEvent event)` — called **unconditionally, for every single `ExecutionEvent`** at the top of `report(ExecutionEvent event)`, before the type-specific `switch`.
- **Related, already-fixed sibling issue** (do not re-break): `src/main/java/com/test/automation/sdk/listener/Listener.java`, `afterInvocation(...)` / `ensureFailureEvidenceCaptured(...)` — has an `"OBS-Allure-fix"` comment documenting the exact same class of problem (Allure listener-ordering is not guaranteed) for the **evidence-attachment** path, fixed by moving evidence capture into `IInvokedMethodListener#afterInvocation`, which TestNG guarantees runs before any `ITestListener#onTestFailure/onTestSuccess` callback fires for *any* listener. `applyTestMetadata()` was never given the equivalent protection.

## Current Behavior

```java
@Override
public void report(ExecutionEvent event) {
    if (event == null) {
        return;
    }
    applyTestMetadata(event);        // <-- runs for EVERY event type
    switch (event.getType()) { ... }
}

private void applyTestMetadata(ExecutionEvent event) {
    if (event.getSuiteName().isEmpty() && event.getTestNgTestName().isEmpty() && event.getClassName().isEmpty()) {
        return;
    }
    if (!Allure.getLifecycle().getCurrentTestCase().isPresent()) {
        return;
    }
    Allure.getLifecycle().updateTestCase(testResult -> { ... });  // fails here
}
```

## Problem — Root Cause (Confirmed with Evidence, Not Speculation)

Observed while validating **Poletop_Automation** (consumer project) against
SDK **1.5.1**. A full regression run (34 tests) produced **exactly 68**
occurrences of:

```
ERROR io.qameta.allure.AllureLifecycle - Could not update test case: test case with uuid <uuid> not found
```

**Empirical pattern** (verified directly from the timestamped log, not
inferred):
- **34 unique uuids** appear across the 68 lines — i.e. **exactly one per
  test**, and **each uuid appears exactly twice**.
- The two occurrences for a given test happen **at the very start of the
  test**, within ~2 seconds of each other, immediately around WebDriver
  session initialization — e.g. one hit right after
  `WebDriverFactory - [WebDriverFactory] ChromeDriver version: ...` logs, the
  next hit ~2s later right after the first `Page loading time is: ...` log
  (i.e., around the first navigation, well before any real test logic runs).
- This is **100% reproducible** — every one of the 34 tests hits it, always
  exactly twice, never zero times and never more than twice.

**Mechanism**: `report()` calls `applyTestMetadata(event)` unconditionally
for *every* `ExecutionEvent` — including the very first events emitted for a
test (`TEST_STARTED`, and the first `STEP_STARTED`/navigation-related event
right after it). At the moment those earliest events fire, TestNG has only
just invoked `Listener.onTestStart(result)` →
`ExecutionReporting.onTestStarted(result)`. Whether Allure's own
`AllureTestNg` listener (auto-registered via `META-INF/services` ServiceLoader,
**not** declared in `regression_suite.xml`) has *already* called its own
`onTestStart` → `getLifecycle().scheduleTestCase(...)`/`startTestCase(...)`
for this same test **at this exact point is not guaranteed** — TestNG does
not guarantee relative invocation order between multiple `ITestListener`
implementations registered via different mechanisms (suite XML vs.
ServiceLoader). This is the *identical* class of problem already identified
and fixed for evidence-attachment in `Listener.afterInvocation` (see the
`OBS-Allure-fix` comment there) — but `applyTestMetadata()` still uses the
older, unprotected `Allure.getLifecycle()` check-then-act pattern.

During that narrow startup race window:
1. `getCurrentTestCase().isPresent()` can return `true` off a *stale or
   in-flight* uuid state,
2. but the subsequent `updateTestCase(...)` call re-resolves the "current"
   uuid via Allure's own internal lookup and finds the underlying storage
   entry not yet created for it,
3. Allure's own `AllureLifecycle.updateTestCase(...)` logs the ERROR itself
   (`"Could not update test case: test case with uuid X not found"`) and the
   intended `parentSuite`/`suite`/`subSuite` label update for that event is
   silently dropped.

Because the window only exists for the first ~2 events of each test (by the
time later step events fire, `AllureTestNg` has caught up and created the
case), the count naturally caps at ~2 occurrences per test rather than
scaling with total step count — consistent with the exact 68 = 34 × 2 count
observed.

## Impact

1. **Log noise**: 68 ERROR-level lines in one regression run, on top of the
   already-known `WebEventListener` noise issue — both erode the "read the
   log before touching code" mandatory RCA discipline consumer projects are
   required to follow.
2. **Silent, deterministic loss of Allure labels** on the *first ~2 events*
   of literally every test: the `parentSuite`/`suite`/`subSuite` labels these
   early events tried to apply are dropped for that window (later events in
   the same test do succeed once `AllureTestNg` has caught up, so the labels
   usually still end up correct overall via a later successful call — but
   this should be verified, not assumed, per item 3 below).

## Requested Fix

1. Apply the **same fix pattern** already proven for evidence-attachment:
   move (or additionally gate) `applyTestMetadata()`'s work to run through a
   TestNG/Allure hook that is guaranteed to fire only once the Allure test
   case actually exists — e.g. drive it from
   `AllureLabelLifecycleListener.beforeTestStart(TestResult)` /
   `beforeTestWrite(TestResult)` (already implemented, already
   ServiceLoader-registered as `io.qameta.allure.listener.TestLifecycleListener`,
   and already immune to this exact race per its own doc comment) **instead
   of** calling `Allure.getLifecycle().updateTestCase(...)` reactively from
   `AllureExecutionReporter.report()`.
2. If `AllureExecutionReporter.applyTestMetadata()` must remain as a
   fallback/secondary path, at minimum:
   - Downgrade the outcome to DEBUG when the underlying update fails because
     the test case isn't tracked yet/anymore (i.e. treat "not found" as an
     expected, benign race outcome, not a surprising ERROR) — this likely
     requires catching/suppressing at the `AllureExecutionReporter` layer
     since Allure's own `AllureLifecycle.updateTestCase` logs the ERROR
     itself; consider checking `getCurrentTestCase().isPresent()`
     immediately before *and* wrapping the call in a try/catch that swallows
     the resulting no-op cleanly, or find/use an Allure API that doesn't
     log at ERROR for this case.
   - Consider retrying the label application once, on the *next* event for
     the same test, if the first attempt raced.
3. **Verify** (do not assume) whether the dropped label-update attempts
   during the race window cause any test's final Allure report entry to be
   missing/incorrect `parentSuite`/`suite`/`subSuite` labels, or whether a
   later successful call in the same test always overwrites/completes them
   correctly. Add an assertion/test for this.
4. Add a regression test that exercises `AllureExecutionReporter.report()`
   with a mocked/real `AllureLifecycle` in the "test case not yet started"
   state to reproduce the race deterministically and assert no ERROR-level
   log line is emitted and/or that the retry/fallback path succeeds.
5. Bump SDK version per existing release process and update CHANGELOG.

## Context / Validation Performed

Found while validating **Poletop_Automation** against SDK **1.5.1**:
- `mvn compile test-compile`: clean, no errors.
- Full regression suite (34 tests, STG, chrome): 24 passed, 5 failed
  (pre-existing STG test-data staleness — identical failure set to the 1.5.0
  run, confirmed not an SDK regression), 5 skipped.
- Confirmed the `WebEventListener` ERROR-noise fix from the prior SDK fix
  request (`SDK-FIX-PROMPT-WebEventListener.md`) works as intended — no more
  `"Error in method [findElement]: null"` spam.
- Confirmed a **bonus** fix in 1.5.1: the previously-known
  `test-output/screenshots/screenshots/` nested-path bug for PNG screenshots
  is also resolved — screenshots now save directly alongside DOM dumps in
  `test-output/screenshots/`.
- This Allure `updateTestCase` race is a **newly-identified, still-open**
  issue — not part of the 1.5.1 fix set, and not a regression (identical
  occurrence count/pattern observed in the 1.5.0 run's log as well, just not
  previously root-caused).
