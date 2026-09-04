---
applyTo: "src/test/java/**/*.java"
---

# Skill: Grouping and Sequencing Data-Dependent Tests

## Core Principle

**If a test's expected data state was produced by another test (not by its own
setup), the two tests are data-dependent and must be explicitly grouped,
sequenced, and made resilient to re-runs -- never left to accidentally pass
because of execution order the test suite happens to use today.**

A data dependency exists whenever Test B's preconditions describe a state that
only Test A (or a manual/previous run) produces -- e.g. "reservation must
already be in PERMITS ISSUED stage," "account must already have a confirmed
enrollment," "record must already contain a submitted SIF." Treat this as a
first-class design concern, not an incidental ordering detail.

---

## Step 1 -- Identify and Document the Chain

Before writing the test, draw the dependency chain explicitly as a comment
(suite XML header comment, or class Javadoc):

```
Test_SetupAccountToActive  (creates/advances the shared record)
       |
       v
Test_EnterDetails          (depends on: account ACTIVE)
       |
       v
Test_CompleteEnrollment    (depends on: account ACTIVE + details entered)
```

Every dependent test's class Javadoc must state:
- **Precondition**: the exact state required before this test's logic is valid
- **Produced by**: which class/helper is responsible for reaching that state
- **Suite order**: where this class sits in the sequential chain

---

## Step 2 -- Sequence at the Suite Level, Not by Accident

TestNG does **not** guarantee execution order across separate `<class>` entries
unless you enforce it. For a chain of dependent test classes:

- Run the suite with **`parallel="none"`** for any `<test>` block containing a
  dependency chain -- parallel execution silently breaks sequential
  preconditions.
- List the dependent classes as separate `<test>` blocks **in the exact
  required order** in the suite XML, with a comment diagram above them (see
  Step 1).
- Within a **single class**, prefer `@Test(priority = N)` or
  `dependsOnMethods`/`dependsOnGroups` over relying on declaration order.
- Never depend on alphabetical or file-system ordering of test classes to
  produce the correct sequence -- it is not guaranteed and breaks silently
  when a new test class is added.

---

## Step 3 -- Mandatory: Idempotent "Setup-to-State" Helper

**Every test in a dependency chain must call a shared, idempotent
`setupXToStateY(...)` helper as the first action inside its `@Test` method --
even though the suite XML already sequences the classes correctly.**

```java
@Test(dataProvider = "enterDetailsData", priority = 1)
public void testEnterDetails(String testCaseName, String recordId, /* ... */ String runMode)
        throws Exception {

    if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);
    setCurrentTestCaseName(testCaseName);

    // MANDATORY: force the shared record into the required precondition state.
    // Do NOT assume a previous test in the suite already did this.
    setupAccountToActive(recordId);

    // ... rest of test logic, now guaranteed to start from a known state
}
```

This is required even when the suite XML enforces the correct class order,
because it makes the test:
- **Independently runnable** -- `mvn test -Dtest=Test_EnterDetails` still
  passes when run alone, not only as part of the full suite
- **Re-run safe** -- retries (`RetryListener`) and manual re-runs don't fail
  because the record was left in a different state by a prior partial run
- **Order-independent** as defense in depth, even though suite order is also
  enforced -- if someone reorders the suite XML later, the test still works

> **A dependent test with no precondition-setup call, that only passes
> "because the other test runs first," is a bug waiting for the next suite
> reorder, parallel-execution change, or CI retry to expose.**

---

## Step 4 -- Passing Data Between Tests in the Chain

If Test A generates a value Test B needs (an ID, a reference number), rank
the options from best to worst:

1. **Look the record back up by a stable business key** (applicant name +
   date, account email, etc.) instead of depending on a generated ID at all.
2. **Use a pre-provisioned, reset-before-each-run shared fixture ID** -- a
   fixed record number that both Excel sheets/test classes reference
   intentionally, combined with the reset step in Step 5. This is the most
   common pattern for UI-driven Selenium suites.
3. **Only if neither is possible**, pass the value through a test-run-scoped
   shared holder (e.g. a small class with `static` fields cleared at suite
   start, or a file under `test-output/`) that downstream tests read --
   document this coupling explicitly in both classes' Javadoc, since it is
   fragile if the suite is ever parallelized.

Never silently assume both Excel sheets happen to reference the same ID by
coincidence -- state the shared-fixture intent in a comment in both places.

---

## Step 5 -- Reset Shared Test Data Before the Chain Runs

If multiple test classes in the chain reuse the **same** underlying record,
that record accumulates state across every run (today's stage, yesterday's
stage, last week's stage). The dependency chain will only be reliably
re-runnable if the shared record is reset to a known baseline before use:

- Prefer making the Step 3 setup helper itself **idempotent and corrective** --
  it should inspect the record's current state and drive it to the required
  state regardless of where it currently is (this is the same helper, doing
  double duty as both precondition-guarantee and reset).
- If the reset requires an out-of-band action (API call, DB reset, admin UI
  step) that cannot be expressed as a simple "advance to state X" helper, add
  a dedicated reset step and call it out explicitly in the class Javadoc and
  suite XML comment.
- **Never assume "this record has never been touched."** Shared fixtures used
  by more than one test class must always be treated as dirty until proven
  otherwise by the setup helper.

---

## Excel / Test Data Notes

- When dependent tests share a record via Excel-driven data, add a comment (in
  the Excel sheet or the test class) noting that the shared ID is intentional
  and coordinated across sheets -- not an accidental collision.
- If the DataProvider row order matters for state progression, do not rely on
  implicit Excel row order alone -- add a `sequence`/`order` column so the
  intent is explicit, or keep one data-driven row per state-transition test
  and let the suite XML enforce class order instead.

---

## Forbidden Patterns

| Pattern | Why Forbidden |
|---|---|
| Relying on TestNG's default class declaration/alphabetical order across separate classes with no explicit suite XML sequence | Not guaranteed; breaks silently when a class is added/renamed |
| A dependent test with no precondition-setup call, passing only because another test happened to run first | Fails on individual run, retry, reorder, or parallelization |
| Sharing a mutable record across tests with no reset/idempotent-setup mechanism | Test fails on re-run, individual run, or out-of-order run |
| Passing dynamic IDs between tests via global mutable statics with no documentation of the coupling | Fragile; breaks when tests are later parallelized or split across suites |
| `parallel="methods"` or `parallel="classes"` in a suite containing a documented sequential dependency chain | Silently breaks the precondition chain |

---

## Checklist

- [ ] Dependency chain diagram/comment exists (suite XML header and/or class Javadoc)
- [ ] Suite XML lists the dependent classes in the exact required order, with `parallel="none"` for that block
- [ ] Every dependent test calls an idempotent `setupXToStateY(...)` precondition helper as its first testing action
- [ ] Shared record IDs are documented as intentional, with a reset/idempotent-setup mechanism
- [ ] The dependent test still passes when run alone: `mvn test -Dtest=<ClassName>`
- [ ] The dependent test still passes when re-run immediately after itself (retry/re-run safety)
