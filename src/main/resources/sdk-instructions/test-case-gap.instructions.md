---
applyTo: "docs/test-case-gaps/**"
---

# Skill: Test Case Gap Reporting

## Purpose
This instruction governs the creation and maintenance of gap report files
in `docs/test-case-gaps/`. A gap report documents why a test case **cannot
be fully automated** and what is needed to unblock it.

---

## When to Create a Gap Report

Create a gap report **instead of (or alongside) partial implementation** when:

1. One or more test case steps **cannot be mapped to a UI action** due to missing description
2. An expected result is **absent, ambiguous, or unmeasurable** (no assertion can be written)
3. Required **test data is not specified** in the test case or Excel sheet
4. A step requires **external dependencies** (API key, email service, database state) that are not configured
5. The test case references **UI elements** that do not exist or cannot be found by the crawler
6. Two or more steps **contradict each other** or have unclear sequence
7. The ADO test case state is `Draft` or `Design` -- not yet approved for automation

> [!]? **Rule:** Never write a partial test with placeholder assertions (`assertTrue(true)`)
> or empty `step()` calls. Either implement fully or create a gap report.

---

## File Naming Convention

| Source | File Name Format | Example |
|---|---|---|
| Azure DevOps test case -- gap (incomplete spec) | `<ID>-gap-report.md` | `12345-gap-report.md` |
| Local file test case -- gap (incomplete spec) | `<ClassName>-gap-report.md` | `Test_EnrollDevice-gap-report.md` |
| Multiple related cases | `<ID1>-<ID2>-gap-report.md` | `100-101-gap-report.md` |
| Cannot automate (technical blocker, full or partial) | `<ClassName>-blocker-report.md` | `Test_ExportPdf-blocker-report.md` |
| ADO test case -- technical blocker | `<ID>-blocker-report.md` | `12345-blocker-report.md` |

> File names **must start with the ADO test case number** so that reports sort
> naturally by ID in the file system and are easy to locate at a glance.

---

## Separation of Concerns — Gap Reports vs. SDK Discovery Reports

**This is a mandatory rule. Mixing test-case blockers with SDK discoveries pollutes both documents.**

### Gap / Blocker Reports (`docs/test-case-gaps/`)
Contain **only** information specific to ONE test case:
- Why specific steps of THAT test case cannot be automated
- What spec information is missing from THAT test case
- What locators for THAT page need crawler validation
- What data is missing from THAT test case's Excel sheet
- Resolution steps specific to THAT test case

### SDK Discovery Reports (`docs/sdk/`)
Contain **only** information about SDK or crawler limitations:
- Crawler bugs or missing features discovered during test implementation
- Proposals for new SDK capabilities (new crawler modes, new TestBase helpers, new config keys)
- Known SDK workarounds that apply across multiple test cases
- Bugs filed against `functional-test-automation-sdk`

### Rules
1. **Never put SDK enhancement proposals inside a gap/blocker report.**
   - ❌ Wrong: "Resolution needed in SDK — allow configurable download dir"
   - ✅ Right: "See `docs/sdk/crawler-enhancement-proposals.md` — Enhancement #7"
2. **Never put test-case-specific step details inside an SDK discovery doc.**
3. **Cross-reference by link only** — gap reports may link to `docs/sdk/` for context; SDK docs may reference which test cases triggered the discovery.
4. **One gap report per test case** — do NOT create a single gap report for multiple unrelated test cases. Use `ADO-<ID1>-<ID2>-gap-report.md` only when two test cases share the exact same blocker.
5. **SDK discoveries found during test implementation** must be extracted to `docs/sdk/` immediately — do not leave them in the gap report as a note.

### Practical Workflow
```
While implementing TC-XXXXX you discover a crawler limitation:
  |
  +-- Create/update docs/sdk/crawler-enhancement-proposals.md  <- SDK issue goes here
  |       with the discovered problem + proposed fix
  |
  +-- In the gap/blocker report for TC-XXXXX:                   <- test-case issue goes here
          Add one line: "Blocked by SDK limitation — see docs/sdk/crawler-enhancement-proposals.md #N"
          Do NOT copy the SDK proposal text into the gap report.
```

**Gap vs Blocker -- which to use?**

| Situation | File Type |
|-----------|-----------|
| Test case steps or expected results are missing / ambiguous | `gap-report` -- spec must be fixed first |
| Test case is complete but a step technically cannot be automated | `blocker-report` -- explains the technical barrier |
| Mix of both | Create a `gap-report` for spec issues AND a `blocker-report` for technical ones |

All files go in the **configured gap output directory**.

**Directory resolution order** (highest priority first):
1. `-Dsdk.gapOutputDir=<path>` system property (runtime override)
2. `sdk-config.yaml` -> `reporting.gapOutputDir`
3. Default: `docs/test-case-gaps/`

Configure in `configuration/sdk-config.yaml`:
```yaml
reporting:
  gapOutputDir: "docs/test-case-gaps/"   # change to suit your project layout
```

---

## Required Sections in Every Gap Report

Every gap report **must** contain all of the following:

1. **Identification table** -- test case ID, title, source, target class, date, reporter, status = `BLOCKED`
2. **Blocking Gaps checklist** -- at least one item checked
3. **Gap Detail** -- one section per checked gap with: affected steps, what is missing, why it blocks
4. **Partially Implementable Steps** table -- even if all steps are blocked
5. **Recommended Resolution** -- at least one action item with an owner
6. **Next Steps After Resolution** -- standard steps to re-activate the test

Use the template: `docs/test-case-gaps/test-case-gap-report.md.template`

---

## Relationship to Test Class

### If no test class exists yet:
- Do **not** create the test class
- Create the gap report only
- Add a TODO comment at the top of any related page object (if it exists):

```java
// TODO: Test class blocked -- see docs/test-case-gaps/ADO-12345-gap-report.md
```

### If a partial test class was started:
- Set `runMode=N` in Excel for all blocked rows
- Add a comment above each blocked `@Test` method:

```java
// BLOCKED: Cannot implement -- see docs/test-case-gaps/ADO-12345-gap-report.md
// Gap: Missing expected result for Step 3 (error message text not specified)
@Test(dataProvider = "loginData", priority = 2, enabled = false)
public void testInvalidLoginErrorMessage(...)  {
    throw new SkipException("Blocked -- see docs/test-case-gaps/ADO-12345-gap-report.md");
}
```

---

## Gap Report Lifecycle

```
Test case received
       |
       ?
Gap identified during #create-test or #ado-sync-test
       |
       ?
Gap report created -> docs/test-case-gaps/ADO-XXXXX-gap-report.md
       |
       ?
Gap report committed to the project repository (NOT gitignored)
       |
       ?
Resolution requested from Product Owner / BA / QA
       |
       ?
Gap resolved -> ADO test case updated
       |
       ?
Run #create-test or #ado-sync-test to implement/update the test
       |
       ?
Gap report archived or deleted
       |
       ?
runMode=Y set in Excel, test runs and passes ?
```

---

## Committed to Repository -- Not Gitignored

Gap reports **must be committed** to the project repository.
They are project artifacts, not generated files.

Do NOT add `docs/test-case-gaps/` to `.gitignore`.

---

## Quality Rules for Gap Reports

- [ ] File name follows naming convention
- [ ] At least one gap type checked in the checklist
- [ ] Every checked gap has a corresponding Detail section
- [ ] Resolution section has at least one action item with an owner
- [ ] Blocked test methods in Java are annotated with `enabled = false` and `SkipException`
- [ ] Blocked Excel rows have `runMode=N`
- [ ] Gap report is committed to source control
