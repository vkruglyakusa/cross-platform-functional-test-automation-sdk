---
applyTo: "src/test/java/**/*.java"
---

# Skill: Formal Test Case to Automation Script

## Trigger
Apply this skill when creating or updating automated tests from formal test cases.

## Autonomous Mode Consent
Request this confirmation before starting and proceed autonomously once granted:

> **"I will proceed autonomously. I accept all standard implementation decisions
> without further questions. Interrupt me only when: (1) a step cannot be automated
> due to a missing stable locator or inaccessible system, (2) a step is ambiguous
> and two different implementations would produce materially different test outcomes."**

## Supported Test Case Sources
1. Local files (for example: `test-cases/*.md`, docs, exported case sheets)
2. Azure DevOps test cases via MCP service

## Intake Workflow
1. Collect test case source (file path or ADO work item/test case ID).
2. Extract structured fields:
   - test case ID/title
   - preconditions
   - test data
   - ordered steps
   - expected result per step
3. Convert each expected result into assertions.
4. Map UI actions to existing `uiActions` methods.
5. If required Page Object methods/locators are missing, update `uiActions` first using crawler rules.

## Azure DevOps MCP Workflow
When source is Azure DevOps:
1. Use MCP tools for Azure DevOps to fetch the test case details and steps.
2. Preserve the original step order and wording intent.
3. Keep traceability in code:
   - class/method JavaDoc includes ADO test case ID
   - `testCaseName` data value includes the same ID/title
4. If MCP data is incomplete (missing steps/expected), stop and request clarification.

## Script Generation Rules
- One `@Test` method per formal scenario -- see **Test Case Generation Contract** below for the strict, non-negotiable form of this rule.
- Use `@DataProvider` backed by Excel if test data varies by row.
- Always enforce `runMode` skip logic first.
- **Add `step("...")` for every formal test case step -- 1-to-1 mapping, no merging.**
- **Every formal expected result must have a matching `Assert.*` call -- no expected result may be skipped.**
- Steps where the expected result is "no error / page loads" require an explicit element-presence or title assertion -- `assertTrue(true)` is never acceptable.
- No hidden logic branches without assertions.

## Test Case Generation Contract (Strict 1:1 -- Non-Negotiable)

The mapping between Azure Test Cases and generated automated tests is **strictly 1:1**.

**RULE: One Azure Test Case ID MUST produce exactly one `@Test` method.**

Do not split one Azure Test Case into multiple `@Test` methods just because it has
multiple test steps, multiple expected results, multiple validations, multiple UI
screens, multiple actions, preconditions, setup steps, or cleanup steps. All steps
belonging to the same Azure Test Case ID must execute inside the **same** automated
`@Test` method.

Allowed:
```java
@Test
public void TC_123456_verifySomething() {
    login();
    createRequest();
    validateRequest();
}

private void login() { ... }
private void createRequest() { ... }
private void validateRequest() { ... }
```
Helper methods are encouraged for readability but **must never** be annotated with `@Test`.

NOT allowed:
```java
@Test
public void TC_123456_step1() { ... }

@Test
public void TC_123456_step2() { ... }

@Test
public void TC_123456_validation() { ... }
```

**Multiple `@Test` methods are allowed only when the input contains multiple distinct
Azure Test Case IDs** -- one `@Test` per distinct TC ID (e.g. TC 123456, TC 123457,
TC 123458 -> exactly three `@Test` methods, one each).

Therefore: `number of generated @Test annotations` MUST equal
`number of distinct Azure Test Case IDs provided as input`.

### Mandatory Self-Validation (perform before returning generated code)
1. Count distinct Azure Test Case IDs in the input.
2. Count `@Test` annotations in the generated code.
3. Verify the two counts are equal.
4. Verify every `@Test` maps to exactly one TC ID.
5. Verify no TC ID maps to more than one `@Test`.

If validation fails, correct the generated code before returning it -- do not return
code that violates this contract.

## Negative Scenario Derivation (Optional -- Only When User Explicitly Requests)

**Do NOT auto-derive negative scenarios unless the user explicitly asks.**
When the user says "add negative scenarios", "add negative cases", or similar:

1. **Propose one scenario at a time** -- never implement a batch without approval
2. **Wait for user confirmation** on each before adding it to the Excel sheet and test
3. **Ask for the expected error message** -- never assume what validation text the app shows

### Derivation Guide (use when requested)

For each input field or action in the positive steps, suggest negative variants:

| Positive Step | Possible Negative Scenarios to Suggest |
|---|---|
| Enter valid username + password | Empty username; empty password; wrong password; inactive account |
| Fill required field and submit | Empty field; spaces only; exceeds max length |
| Select dropdown and proceed | Leave unselected; select value triggering validation |
| Upload a file | Wrong file type; file too large |
| Enter a date | Past date when future required; invalid format |
| Search for a record | No results; special characters |
| Action requiring a role | Insufficient permissions |

### Conversation Flow (one scenario at a time)

```
Agent:  "Should I also add a negative scenario for empty password field?
         Expected result would be an error message -- what text does the app show?"

User:   "Yes, it shows 'Password is required'"

Agent:  -> adds ONE Excel row + assertion for that scenario, then asks about the next
```

### Implementation Rules (when approved)
- Each approved negative scenario = one Excel data row, same `@Test` method
- `testCaseName` clearly identifies it: `"ADO-123 -- Login -- Empty Password"`
- Assertion must check the **specific error message text** confirmed by the user
- Never implement a negative scenario that was not explicitly approved

## When a Step Cannot Be Automated -- Blocker Output
If any step or expected result cannot be automated:

1. **Do NOT silently skip it** -- create `${reporting.gapOutputDir}` (default: `docs/test-case-gaps/`) `${ID}-blocker-report.md`:
   ```markdown
   # Implementation Blocker Report
   **Test Class**: ${TestClassName}
   **Source**: ${ADO-ID or file}
   **Generated**: ${timestamp}

   ## Blocked Steps
   | Step # | Description | Expected Result | Reason | Suggested Fix |
   |--------|-------------|-----------------|--------|---------------|
   | N | ... | ... | No stable locator / env not reachable / ambiguous | ... |

   ## Implementable Steps
   All steps NOT listed above were successfully automated.
   ```

2. Mark the blocked test method with `@Test(enabled=false)` and throw `SkipException`
   with a reference to the blocker file.

3. Implement all other steps normally.

## Traceability Template (required)
```java
/***********************************************************************
 * Test Case ID : ADO-12345
 * Source       : Azure DevOps / test-cases/<file>.md
 * Objective    : <formal objective text>
 ***********************************************************************/
```

## Completion Checklist
- [ ] Autonomous mode confirmed by user
- [ ] Formal steps fully mapped to automation steps (1-to-1)
- [ ] Test Case Generation Contract self-validation performed: `@Test` count == distinct Azure Test Case ID count, each TC ID maps to exactly one `@Test`
- [ ] Every expected result covered by an `Assert.*` call
- [ ] Page object actions reused (no duplicated Selenium logic in test class)
- [ ] Locators updated through crawler flow when needed
  - Crawler: `com.test.automation.tools.LocatorInvestigator` via `crawler_suite.xml`
- [ ] Blocker report created for any steps that could not be automated
- [ ] `mvn compile test-compile` passes with zero errors
- [ ] Impacted tests **executed** via `mvn test -Dtest=<ClassName>` and passed (or skipped intentionally)
- [ ] Completion report produced (see `test-creation.instructions.md` Step 8 format)
- [ ] Task is NOT marked complete while any test fails or was never run