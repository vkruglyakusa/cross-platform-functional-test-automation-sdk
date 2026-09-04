# Automation Project -- GitHub Copilot Workspace Instructions

## Project Identity
<!-- UPDATE THESE VALUES FOR YOUR PROJECT -->
- **Framework**: Selenium WebDriver + TestNG + Page Object Model (POM)
- **Language**: Java 8 -- no `var`, no lambdas in `driver.findElements`
- **Base class**: `com.test.automation.sdk.testbase.TestBase`
- **Page Objects**: `src/main/java/{your.package}/uiActions/`
- **Test classes**: `src/test/java/{your.package}/testCases/`
- **Test data**: Excel files in `src/test/resources/testData/`
- **Config**: `configuration/config.properties` + `configuration/sdk-config.yaml`

---

## SDK Prompts -- How to Load

All Copilot prompts and skill instructions are provided by the SDK JAR.
Run this once from the project root to extract them into `.github/`:

```bash
mvn exec:java -Dexec.mainClass="com.test.automation.sdk.utility.InstructionExtractor"
```

After extraction, prompts are available in `.github/prompts/` and instructions in `.github/instructions/`.
These files are SDK-managed -- do not edit them directly. Re-run after any SDK version upgrade.

---

## Getting Started -- Type `#start`

Not sure where to begin? Type **`#start`** in Copilot Chat.
It will ask what you want to do and collect everything needed before starting work.

---

## Available Prompts

| Invoke | Purpose |
|---|---|
| `#start` | **Start here** -- menu-driven launcher that collects all required params |
| `#create-test` | Create a new test class from an Azure DevOps test case |
| `#modify-test` | Add a scenario to an existing test class |
| `#fix-failed-test` | Diagnose and fix a failing test -- includes ADO pre-check |
| `#fix-broken-locator` | Heal page object locators after a UI change |
| `#ado-sync-test` | Align test script with an updated ADO test case |
| `#report-test-gap` | Document a test case that cannot be automated |

---

## Core Rules -- Always Follow

### 1. Never Write Locators by Hand -- Run the Crawler First
```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=<email> -Dinv.password=<password>
```
Use **only locators marked `UNIQUE [x]`** in the generated report.
Never use `NOT UNIQUE`, `DYNAMIC`, or `STRUCTURAL`.

### 2. Locator Rules -- Non-Negotiable
- XPath only in `@FindBy` -- no CSS selectors
- Never use auto-generated IDs (`mat-input-0`, `cdk-overlay-1`) or positional XPath (`//div[3]/input[1]`)
- Priority: `@id` -> `@data-testid` -> `@formcontrolname` -> `@name` -> `@aria-label` -> `@placeholder` -> `normalize-space(.)`
- Full rules: `.github/instructions/locator-strategy.instructions.md`

### 3. Page Object Pattern
```java
public class MyPage extends TestBase {
    public static final Logger log = LogManager.getLogger(MyPage.class.getName());

    @FindBy(xpath = "//input[@formcontrolname='email']")
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
- Always `extends TestBase`
- Use `clearAndType()` for text fields -- never raw `sendKeys`
- Use `fluentWaitUntilElementToBeClickable()` before any click
- Use `waitForElementPresent(driver, element)` before reading text

### 4. Test Class Pattern
```java
public class Test_MyFeature extends TestBase {
    @DataProvider(name = "myData")
    public Object[][] myData() throws IOException { return getData("SheetName"); }

    @Test(dataProvider = "myData", priority = 1)
    public void testMyScenario(String testCaseName, ..., String runMode) throws Exception {
        if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);
        step("Step description");
        Assert.assertTrue(condition, "Expected result message");
    }
}
```
- Always `extends TestBase` * always check `runMode` first * always use `Assert.*` * always use `step()`
- No raw `Thread.sleep()` -- use `waitForElementPresent()`, `fluentWaitUntilElementToBeClickable()`
- When implementing from ADO test cases: preserve step order, map every expected result to an assertion

### 5. Mandatory Validation After Every Change
1. `mvn compile test-compile` -- must pass with zero errors
2. `mvn test -Dtest=<ClassName> -Denvironment=stg -DbrowserName=chrome` -- must pass or skip intentionally
3. If locators changed -- re-run crawler and confirm `UNIQUE [x]`

### 6. NEVER Debug or Fix a Test Without Reading Artifacts First -- NON-NEGOTIABLE

> ⛔ **Never touch a single line of code to fix a test failure until ALL THREE artifacts have been read.**
> This rule applies to EVERY failure, EVERY time, with NO exceptions.

When any test fails, the SDK automatically captures 3 artifacts at the exact moment of failure:

| # | Artifact | Location |
|---|---|---|
| 1 | **Screenshot** | `test-output/screenshots/<testCaseName>_<timestamp>.png` |
| 2 | **DOM dump** | `test-output/screenshots/<testCaseName>_<timestamp>_DOM.html` |
| 3 | **Log file** | `test-output/logs/<testCaseName>_<timestamp>.log` |

**Mandatory sequence -- in this exact order -- before ANY code change:**

```
STEP 1: Re-run the test to produce FRESH artifacts
        mvn test -Dtest=<ClassName>#<method> -Denvironment=stg -DbrowserName=chrome

STEP 2: Open the SCREENSHOT -- what did the browser show at the moment of failure?

STEP 3: Open the DOM DUMP -- is the expected element in the DOM? With correct attributes?

STEP 4: Open the LOG FILE -- what was the last action before the exception?
        target/surefire-reports/<TestClassName>-output.txt  OR  console output

STEP 5: Only AFTER reading all 3 -- state your finding:
        Screenshot finding : <e.g. "Login page displayed instead of dashboard">
        DOM dump finding   : <e.g. "@formcontrolname='email' present but @id changed">
        Log finding        : <e.g. "Last action: Clicking Submit, then TimeoutException">

STEP 6: Only AFTER stating all 3 findings -- write the fix
```

**Why this is mandatory:**
- The screenshot shows WHAT the browser displayed (end state)
- The DOM dump shows WHAT HTML was present (element state)
- The log shows WHAT sequence of actions led there (execution path)
- All three together eliminate guessing entirely
- A fix without all three is a guess — it may pass once and fail again, or hide a real product bug

**If Copilot proposes a code change without first showing artifact findings — stop it and ask:**
> "Show me the screenshot finding, DOM dump finding, and log finding before making any changes."

Full RCA rules: `.github/instructions/test-fix.instructions.md` and
`.github/instructions/failure-investigation.instructions.md`
Fix workflow: use `#fix-failed-test` prompt

### 7. README.md Is the Live Test Coverage Status
Update `## Test Coverage` table on every create / fix / block action:

| Test Class | ADO ID | Scenario | Status | Last Updated |
|---|---|---|---|---|
| `Test_Login` | ADO-123 | Valid login | [x] Automated | 2026-08-14 |

Status values: `[x] Automated` * `[!] Partial -- <reason>` * `[!] Blocked` * ` Fixing -- <issue>`
