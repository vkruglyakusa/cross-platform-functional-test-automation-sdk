---
mode: agent
description: Document a test case that cannot be automated due to gaps, and create a gap report in docs/test-case-gaps/
---

# Report a Test Case Gap

A test case **cannot be fully automated** because of missing, ambiguous, or incomplete
information. This prompt guides you through documenting the gap and creating a
structured report in `docs/test-case-gaps/`.

## Required Information

- **Test case source**: ADO ID (e.g. `12345`) or local file path
- **Target test class name** (even if not yet created): e.g. `Test_EnrollDevice`
- **Description of the problem** (optional -- agent will discover if not provided)

---

## Execution Steps

### Step 1 -- Retrieve the Test Case
If ADO source:
```
Get test case: ${ADO Test Case ID}
Fields: title, steps, expected results, preconditions, state, attachments
```

If local file: read the file fully.

### Step 2 -- Identify All Gaps
Review every step and expected result. For each one, determine:

| Check | Question |
|---|---|
| Can this step be mapped to a specific UI action? | If NO -> **Ambiguous action step** gap |
| Does this step have a defined expected result? | If NO -> **No expected result** gap |
| Is all required test data specified? | If NO -> **Missing test data** gap |
| Is the UI element identifiable? | If NO -> **UI element not described** gap |
| Are preconditions clear and achievable? | If NO -> **Missing preconditions** gap |
| Are there external dependencies (API, DB, email)? | If YES and unavailable -> **Environment/dependency missing** gap |

### Step 3 -- Determine File Name
| Source | File Name |
|---|---|
| ADO test case | `ADO-${ID}-gap-report.md` |
| Local file | `${TestClassName}-gap-report.md` |

### Step 4 -- Create the Gap Report File
Create file: `docs/test-case-gaps/${fileName}`

Use the template at `docs/test-case-gaps/test-case-gap-report.md.template`
(or `src/main/resources/sdk-templates/test-case-gap-report.md.template` if template not yet extracted).

Fill in ALL required sections:
- **Identification table** (ID, title, source, target class, date = today, status = BLOCKED)
- **Blocking Gaps checklist** -- check every applicable gap type
- **Gap Detail** -- one section per gap: affected steps, what is missing, why it blocks
- **Partially Implementable Steps** table -- mark each step ?/?
- **Recommended Resolution** -- specific action items with owners
- **Next Steps After Resolution**

### Step 5 -- Handle the Test Class
**If no test class exists yet:** Do NOT create it. Gap report only.

**If a partial test class exists or was started:**
- Add `enabled = false` to blocked `@Test` methods
- Throw `SkipException` with reference to the gap report
- Set `runMode=N` in Excel for blocked rows
- Add comment above blocked method:

```java
// BLOCKED: Cannot implement -- see docs/test-case-gaps/${fileName}
// Gap: ${brief description of the gap}
```

### Step 6 -- Ensure docs/test-case-gaps/ Exists
```bash
mkdir -p docs/test-case-gaps
```

Copy the template for future use:
```bash
# If InstructionExtractor was run, template is already at:
docs/test-case-gaps/test-case-gap-report.md.template
```

### Step 7 -- Do NOT Run Tests
There is nothing to run -- the test is blocked.
The gap report IS the output of this task.

---

## Output Format
```
Gap Report Created : docs/test-case-gaps/${fileName}
Test Case          : ${ID} -- ${Title}
Gaps Found         : ${N} gap(s)
  1. ${Gap type} -- Step(s) ${affected steps}
  2. ${Gap type} -- Step(s) ${affected steps}

Blocked Test Class : ${ClassName}.java -- NOT created / partially created
Blocked Excel rows : runMode=N set for ${N} rows

Resolution Needed  : ${summary of what needs to be provided}
Assigned to        : ${owner -- or "TBD if unknown"}
```

---

## Re-Activating After Gap Resolution

Once the gap is resolved (ADO updated, test data provided, etc.):
1. Update the Resolution Log section in the gap report
2. Run `#create-test` or `#ado-sync-test` to implement the test
3. Delete or archive the gap report
4. Set `runMode=Y` in Excel
5. Remove `enabled = false` from blocked `@Test` methods
