# Poletop Automation - GitHub Copilot Workspace Instructions

## Project Identity
- **Framework**: Selenium WebDriver + TestNG + Page Object Model (POM)
- **Language**: Java (source compatibility: Java 8 - no `var`, no lambdas in `driver.findElements`)
- **Base class for all Page Objects and Tests**: `com.automation.poletop.testBase.TestBase`
- **Page Objects location**: `src/main/java/com/automation/poletop/uiActions/`
- **Test classes location**: `src/test/java/com/poletop/automation/testCases/`
- **Utilities location**: `src/main/java/com/automation/poletop/utility/`
- **Test data**: Excel files in `src/main/java/com/automation/poletop/testData/`
- **Config**: `configuration/config.properties`

---

## Core Rules -- Always Follow

### 1. Never Create a Page Object Without Running the Crawler First
Before writing any `uiActions` class for a new page:
1. Run `LocatorInvestigator` (or `PageObjectGenerator.main()`) against the target URL.
2. Use **only locators marked `UNIQUE [x]`** from the generated report.
3. Never use locators marked `NOT UNIQUE`, `DYNAMIC`, or `STRUCTURAL`.
4. The generated `.java` file in `uiActions/` is the **starting point** -- review before use.

#### ElementCrawler Run Instruction (Required)
`LocatorInvestigator` and `PageObjectGenerator` both use `ElementCrawler` internally.  
Use one of these commands before creating or editing any new page object:

```bash
# Option A (preferred for team workflow)
mvn test -Dtest=LocatorInvestigator -Denvironment=stg -DbrowserName=chrome -Dinv.email=<email> -Dinv.password=<password>

# Option B (standalone generator)
mvn exec:java -Dexec.mainClass="com.automation.poletop.utility.PageObjectGenerator" -Dexec.args="Poletop<PageName>Page <URL> <email> <password>"
```

Expected outputs:
- `src/main/java/com/automation/poletop/uiActions/Poletop<PageName>Page.java`
- `test-output/crawler/Poletop<PageName>Page_<timestamp>.txt`

If the report does not contain stable `UNIQUE [x]` locators for key fields/actions, stop and request a better attribute strategy instead of guessing.

### 2. Locator Rules -- Non-Negotiable
- **Only unique, stable XPaths** -- each locator must match exactly 1 element on the page.
- **Never use dynamic locators**: auto-incremented IDs (`mat-input-0`, `cdk-overlay-1`), 
  index-based position (`//div[3]/input[1]`), or Angular-generated attributes.
- **XPath priority order** (use the first that is unique):
  `@id` -> `@data-testid` -> `@formcontrolname` -> `@name+@type` -> `@name` -> 
  `@aria-label` -> `@placeholder` -> `normalize-space(.)` -> `@routerlink` -> `@href`
- For pages with multiple UI layouts, use a single union XPath with `|` only when each branch is stable and the final locator is `UNIQUE [x]` (example in `.github/instructions/locator-strategy.instructions.md`).
- If no stable locator exists, **ask** -- do not guess.

### 3. Page Object Conventions
```java
public class MyPage extends TestBase {

    public static final Logger log = LogManager.getLogger(MyPage.class.getName());

    @FindBy(xpath = "//input[@id='email']")   // unique, stable
    public WebElement emailField;

    public MyPage(WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(driver, this);
        PageContext.currentPage.set("MyPage");
    }

    public void enterEmail(String value) {
        waitForElementPresent(driver, emailField);
        clearAndType(emailField, value);
    }
}
```
- Always `extends TestBase` -- never import WebDriver waits directly in page objects.
- Use `clearAndType()` for text fields (never raw `sendKeys` alone).
- Use `fluentWaitUntilElementToBeClickable()` before any click.
- Use `waitForElementPresent(driver, element)` before reading text.
- Use `@CacheLookup` only on stable, never-reloaded elements.

### 4. Test Class Conventions
```java
public class Test_MyFeature extends TestBase {

    @DataProvider(name = "myData")
    public Object[][] myData() throws IOException {
        return getData("SheetName");
    }

    @Test(dataProvider = "myData", priority = 1)
    public void testMyScenario(String testCaseName, ..., String runMode) throws Exception {
        if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);
        setCurrentTestCaseName(testCaseName);  // required for distinct report entries and paired failure artifacts per DataProvider row
        step("Step description");
        // test logic
    }
}
```
- Always `extends TestBase`.
- Always use `@DataProvider` backed by Excel sheet.
- Always check `runMode` first -- skip if `"N"`.
- Always call `setCurrentTestCaseName(testCaseName)` after the `runMode` check in every data-driven `@Test`.
- Use `step("...")` from Allure for every logical step.
- Use `Assert.assertTrue/assertFalse/assertEquals` -- never `if` without assertion.
- Clean up: always `driver.get(baseURL)` or `logout()` at end.

### 4.1 Formal Test Case Processing (Files or Azure DevOps MCP)
- When implementing from formal test cases, preserve original step order.
- Map each expected result to an explicit assertion in code.
- Keep traceability to source test case ID/title in class or method comments.
- For Azure DevOps sources, use MCP-retrieved steps as the source of truth.
- If required steps/expected results are missing, stop and request clarification.

### 5. Code Style
- Java 8 compatible -- no `var`, no `instanceof` pattern matching, no text blocks.
- No CSS selectors in `@FindBy` -- XPath only.
- No raw `Thread.sleep()` -- use `waitUntillPageLoad()`, `waitForElementPresent()`, or `fluentWait`.
- Log every action: `log.info("Clicking submit button")`.
- Comment only when logic is non-obvious.

### 6. Mandatory Validation After Every Modification
After **any** change to a `uiActions` class or a test class:
1. **Compile**: `mvn compile test-compile` -- fix all errors before anything else
2. **Execute**: `mvn test -Dtest=<ClassName> -Denvironment=stg -DbrowserName=chrome`
3. **Verify**: confirm tests pass (or are intentionally skipped via `runMode=N`)
4. **Locators**: if `@FindBy` changed, re-run crawler and confirm `UNIQUE [x]`
5. **Do not mark work complete** if compile fails, tests error, or tests were never run after changes

### 7. README.md Is the Live Project Status Page -- Always Update It

`README.md` in the project root is the **single source of truth** for the current
state of test automation coverage. It must be updated **every time** a test is
created, modified, fixed, or blocked.

**Required section in README.md:**

```markdown
## Test Coverage

| Test Class | ADO ID | Scenario | Status | Last Updated |
|---|---|---|---|---|
| `Test_Login` | ADO-12345 | Valid login / invalid credentials | ? Automated | 2026-08-13 |
| `Test_Enrollment` | ADO-12346 | Device enrollment flow | [!]? Partial -- Step 7 blocked | 2026-08-13 |
| `Test_ExportPdf` | ADO-12347 | Export to PDF | ? Blocked -- see blocker report | 2026-08-12 |
```

**Status values:**

| Status | Meaning |
|---|---|
| `? Automated` | All steps automated and passing |
| `[!]? Partial -- <reason>` | Some steps automated; blockers documented in `docs/test-case-gaps/` |
| `? Blocked -- see blocker report` | Cannot be automated; blocker report in `docs/test-case-gaps/` |
| `?? Fixing -- <issue>` | Test exists but currently failing; fix in progress |

**Rules:**
- Create the `## Test Coverage` section if it does not exist
- One row per test class -- update the existing row if it already exists
- Always show the **diff** of what changed in README.md as part of the completion report
- The table must reflect the **current actual state** -- never leave a stale row

---

## Development Process for a New Feature

```
1. GET the URL of the page to automate
       |
       ?
2. RUN LocatorInvestigator -> generates uiActions/<PageName>.java
                           -> generates test-output/crawler/<PageName>_report.txt
       |
       ?
3. REVIEW generated file
   * Keep only UNIQUE [x] locators
   * Delete DYNAMIC / NOT UNIQUE / STRUCTURAL entries
   * Rename fields to match business domain
       |
       ?
4. CREATE test class in testCases/
   * Extend TestBase
   * Add @DataProvider backed by Excel sheet
   * One @Test method per scenario
       |
       ?
5. ADD Excel test data sheet with correct columns
       |
       ?
6. RUN mvn compile test-compile to verify
       |
       ?
7. EXECUTE tests and verify assertions pass
```

---

## Main Prompt Template for Test Script Creation

Use this as the primary prompt for **single** or **group** test automation:

```text
Create automation script(s) for Poletop using Selenium + TestNG + POM.

Scope:
- Mode: <single | group>
- Source type: <local file | Azure DevOps MCP>
- Source reference(s): <file path(s) or ADO test case IDs>
- Target test class name(s): <Test_ClassName>
- Excel sheet name(s): <SheetName>

Mandatory implementation rules:
1. Follow .github/instructions/formal-testcase-to-script.instructions.md
2. Follow .github/instructions/test-creation.instructions.md
3. Follow .github/instructions/page-object-creation.instructions.md
4. Follow .github/instructions/locator-strategy.instructions.md
5. Use only XPath in @FindBy
6. For new/updated page objects, run LocatorInvestigator via `crawler_suite.xml` (package: `com.poletop.automation.tools`) and keep only UNIQUE [x] locators
7. Use TestBase helpers (no raw Thread.sleep)
8. Add runMode skip logic, DataProvider, Allure step("..."), and assertions for every expected result
9. Keep traceability to formal test case ID/title in comments and testCaseName data

Validation before completion (MANDATORY -- task is incomplete without these):
- Run: mvn compile test-compile  (must succeed with zero errors)
- Run: mvn test -Dtest=<ClassName> -Denvironment=stg -DbrowserName=chrome  (must pass or skip intentionally)
- If locators changed: run crawler_suite.xml and confirm UNIQUE [x] in report

Deliverables:
- Updated/created uiActions classes (if needed)
- Updated/created test class(es)
- Required Excel data mapping notes
- Brief summary of what was automated and validated
```

---

## Prompt Catalog -- Reusable Copilot Prompts

These prompts are available in `.github/prompts/` and can be invoked directly in GitHub Copilot
using the `#` reference syntax (e.g. type `#create-test` in the chat).

To extract all prompts to your project, run:
```bash
mvn exec:java -Dexec.mainClass="com.test.automation.sdk.utility.InstructionExtractor"
```

| Prompt File | Invoke With | When to Use |
|---|---|---|
| `create-test.prompt.md` | `#create-test` | Creating a brand-new test class from scratch |
| `modify-test.prompt.md` | `#modify-test` | Adding or changing a test method or page object |
| `fix-failed-test.prompt.md` | `#fix-failed-test` | A test is failing -- systematic triage and fix |
| `ado-sync-test.prompt.md` | `#ado-sync-test` | Check if test script has drifted from ADO test case |
| `fix-broken-locator.prompt.md` | `#fix-broken-locator` | Product UI changed, locators stopped working |

---

### When to Use Which Prompt

```
Test is FAILING?
  +-- Locator error (NoSuchElement / Timeout)  -> #fix-broken-locator
  +-- Assertion failure (expected X, got Y)    -> #fix-failed-test  (Type B)
  +-- Test data issue (NPE in getData)         -> #fix-failed-test  (Type C)
  +-- General failure / unknown cause          -> #fix-failed-test

Test is PASSING but may be out of date?
  +-- ADO test case was recently updated       -> #ado-sync-test

Need to ADD new coverage?
  +-- New page / new feature                   -> #create-test
  +-- New scenario on existing page            -> #modify-test

UI CHANGED (not a test failure yet)?
  +-- Proactively update locators              -> #fix-broken-locator
```
