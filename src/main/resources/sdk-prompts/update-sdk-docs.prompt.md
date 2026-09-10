---
mode: "agent"
description: "Update SDK documentation after a TestBase change"
---

# Prompt: Update SDK Documentation

Use this prompt after **any** change to `TestBase.java` -- adding a method, changing a
signature, or removing a method. Documentation must be updated in the same iteration
as the code.

---

## Input required

Describe the change you made (or paste the diff):

```
<paste your change here>
```

---

## What this prompt does

1. Reads the current `TestBase.java` to understand the full method signature and Javadoc
2. Identifies which documentation section(s) need updating
3. Updates all three documentation targets in one pass:
   - `SDK-USER-GUIDE.md` (project root) -- Section 9 table
   - `src/main/resources/SDK-USER-GUIDE.md` -- identical copy of root
   - `TESTBASE-API.md` (project root) -- detailed API section

---

## Rules the agent must follow

### For a NEW method
- Determine the correct category from the structure map below
- Add a new row to the matching table in `SDK-USER-GUIDE.md` Section 9
- Add a full entry (method name, signature, timeout if applicable, description) to
  the matching section in `TESTBASE-API.md`
- Add a short usage example if the method is non-obvious
- Keep both `SDK-USER-GUIDE.md` copies identical

### For a CHANGED signature
- Update the signature in both documents
- Update ALL code examples in both documents that reference this method
- If it is a breaking change (parameter removed or renamed), add a migration note
  to `SDK-PUBLISHING.md` under the current version heading

### For a REMOVED method
- Remove the row from both documents
- Add a removal note to `SDK-PUBLISHING.md`

### Always
- Do NOT rewrite unrelated sections
- Do NOT change the section numbering or heading text in `SDK-USER-GUIDE.md`
- Verify both `SDK-USER-GUIDE.md` files are byte-for-byte identical after the edit
- All code snippets must be valid Java 20 or newer (`Java >=20`)

---

## Documentation structure map

Use this to decide which section to update:

| Method category | SDK-USER-GUIDE.md Section 9 table | TESTBASE-API.md Section |
|---|---|---|
| Visibility / presence wait | "Waiting -- before interacting" | Section 4 |
| Clickability wait | "Waiting -- before interacting" | Section 5 |
| Disappearance wait | "Waiting -- before interacting" | Section 6 |
| Attribute/value wait | "Waiting -- before interacting" | Section 7 |
| Page load wait | "Waiting -- before interacting" | Section 8 |
| Text input | "Text input" | Section 9 |
| Click | "Clicks" | Section 10 |
| Dropdown / Select | "Dropdowns" | Section 11 |
| Checkbox / Radio | "Checkboxes" | Section 12 |
| Mouse / Keyboard | "Mouse & Keyboard Actions" | Section 13 |
| Frame switching | "Frames" | Section 14 |
| Window / Tab | "Windows & Tabs" | Section 15 |
| Alert | "Alerts" | Section 16 |
| Scroll / JavaScript | "Scrolling" | Section 17 |
| Screenshot / Report | "Screenshots" | Section 18 |
| Text verification | "Verification" | Section 19 |
| Data generator | "Data Generators" | Section 20 |
| File upload | "File Upload" | Section 21 |
| Page reload | "Page Reload Helpers" | Section 22 |
| Lifecycle / Setup | SDK-USER-GUIDE Section 1 | TESTBASE-API Section 1 |

---

## Validation before completion

- [ ] `SDK-USER-GUIDE.md` (root) -- correct table row added/updated/removed
- [ ] `src/main/resources/SDK-USER-GUIDE.md` -- identical to root
- [ ] `TESTBASE-API.md` -- correct section entry added/updated/removed
- [ ] Code examples compile as Java 20 or newer
- [ ] No unrelated sections modified
- [ ] Markdown tables render without broken alignment
