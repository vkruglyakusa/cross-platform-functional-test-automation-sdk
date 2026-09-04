---
applyTo: "src/main/java/**/uiActions/*.java"
---

# Skill: Creating Page Objects with the Crawler

## Trigger
Apply this skill whenever creating or updating a `uiActions/` Page Object class.

## Golden Rule
**Never write `@FindBy` locators by hand.**  
Always run `PageObjectGenerator` / `LocatorInvestigator` first.  
Only use locators the crawler marks as **`UNIQUE [x]`**.

## ElementCrawler Instruction (Mandatory)
`ElementCrawler` is the locator engine behind both entry points:
- `LocatorInvestigator` -- TestNG class at `src/test/java/com/poletop/automation/tools/LocatorInvestigator.java`
- `PageObjectGenerator.main()` -- standalone utility at `src/main/java/com/automation/poletop/utility/PageObjectGenerator.java`

> [!]? `LocatorInvestigator` lives in the **`tools`** package (`com.test.automation.tools`), NOT in `testCases`.
> Always invoke it via `crawler_suite.xml` or with the fully qualified class name.
> It must never appear in `regression_suite.xml`.

Run one of these before creating/updating any `uiActions` class:

```bash
# Option A - dedicated crawler suite (recommended)
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=<email> -Dinv.password=<password>

# Option B - single investigation method
mvn test "-Dtest=com.test.automation.tools.LocatorInvestigator#<methodName>" \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=<email> -Dinv.password=<password>

# Option C - standalone PageObjectGenerator (no browser session needed)
mvn exec:java -Dexec.mainClass="com.automation.poletop.utility.PageObjectGenerator" \
              -Dexec.args="Poletop<PageName>Page <URL> <email> <password>"
```

Do not proceed with manual locators if the crawler report lacks `UNIQUE [x]` strategies for required elements.

---

## Step-by-Step Process

### Step 0 -- Check Existing Failure Artifacts Before Running the Crawler

> [!]? **If you are here because a test failed, do NOT run the crawler yet.**

Before invoking the crawler for a locator fix, check whether failure artifacts already
explain the problem. Running the crawler blindly wastes time and may solve the wrong issue.

**Artifact check sequence:**
1. Look for a screenshot and DOM dump from the failed run:
   ```
   test-output/screenshots/<testCaseName>_<timestamp>.png
   test-output/screenshots/<testCaseName>_<timestamp>_DOM.html
   ```
2. Open the screenshot -- confirm what the browser was showing at the moment of failure.
3. Search the DOM dump for the element's expected attribute (`@id`, `@formcontrolname`, `@placeholder`, etc.).

**Decision based on what you find:**

| Artifact finding | Action |
|---|---|
| Element attribute IS present in DOM with the same value | Locator is likely correct -- check timing/overlay, NOT a crawler issue |
| Element attribute IS present but with a **different** value | Update `@FindBy` to match new value -- run crawler to confirm `UNIQUE [x]` |
| Element attribute is **missing entirely** from DOM | UI changed -- run crawler to discover new stable locator |
| Screenshot shows wrong page / session expired | Environment/auth issue -- fix navigation or login before running crawler |
| Screenshot shows modal/overlay covering element | Add dismissal step in page object -- no crawler needed |
| No screenshot found (test did not reach failure capture) | Compile or early init error -- check surefire report first |

> [!]? **Only run the crawler when the artifact review confirms a locator change is needed
> (element attribute missing or changed). Running the crawler with purpose means you already
> know what element to look for and what strategy to target.**

---

### Step 1 -- Run the Crawler

> [!]? **`LocatorInvestigator` is in package `com.test.automation.tools`** -- use the fully qualified name or `crawler_suite.xml`.

```bash
# Option A - dedicated crawler suite (recommended)
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=user@example.com \
         -Dinv.password=<password>

# Option B - single page method
mvn test "-Dtest=com.test.automation.tools.LocatorInvestigator#generate<PageName>Page" \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=user@example.com -Dinv.password=<password>

# Option C - PageObjectGenerator standalone
mvn exec:java -Dexec.mainClass="com.automation.poletop.utility.PageObjectGenerator" \
              -Dexec.args="Poletop<PageName>Page <URL> <email> <password>"
```

**Output produced:**
- `src/main/java/com/automation/poletop/uiActions/<ClassName>.java` -- generated page object
- `test-output/crawler/<ClassName>_<timestamp>.txt` -- full discovery report

### Step 2 -- Read the Discovery Report
Open `test-output/crawler/<ClassName>_<timestamp>.txt`.  
For each element check the **ALL LOCATOR STRATEGIES** table:

```
|  -- ALL LOCATOR STRATEGIES (6) ------------------------------
|  BY ID                              //input[@id='email']           <- UNIQUE [x] USE THIS
|  BY FORM-CONTROL-NAME               //input[@formcontrolname='email']
|  BY NAME + TYPE                     //input[@name='email' and @type='email']
|  BY ARIA-LABEL (exact)              //input[@aria-label='Email Address']
|  BY PLACEHOLDER (contains)          //input[contains(@placeholder,'Email')]
|  STRUCTURAL [fallback]              //form[1]/div[2]/input[1]      <- NEVER USE
```

### Step 3 -- Locator Selection Rules

#### ? USE (stable, unique)
| Strategy | Example | Use when |
|---|---|---|
| `BY ID` | `//input[@id='email']` | ID is meaningful (not auto-generated) |
| `BY DATA-TESTID` | `//button[@data-testid='submit']` | App has data-testid attributes |
| `BY FORM-CONTROL-NAME` | `//input[@formcontrolname='poleId']` | Angular reactive forms |
| `BY NAME + TYPE` | `//input[@name='email' and @type='email']` | No id/formcontrol |
| `BY ARIA-LABEL (exact)` | `//button[@aria-label='Close dialog']` | Accessibility label present |
| `BY PLACEHOLDER (exact)` | `//input[@placeholder='Enter Pole ID']` | Input with unique placeholder |
| `BY TEXT (normalize-space)` | `//button[normalize-space(.)='Submit']` | Button/link with unique label |
| `BY ROUTER-LINK` | `//a[@routerlink='/enroll']` | Angular navigation links |

#### ? NEVER USE (dynamic or non-unique)
| Pattern | Example | Why |
|---|---|---|
| Auto-generated Material ID | `//input[@id='mat-input-0']` | Changes on every page load |
| Auto-generated CDK ID | `//*[@id='cdk-overlay-3']` | Runtime-generated |
| Structural / positional | `//div[3]/input[1]` | Breaks on any UI change |
| Index-only | `(//input)[2]` | Non-semantic, fragile |
| Dynamic numeric suffix | `//input[@id='field-12345']` | Random at runtime |
| Pure class-based | `//*[@class='ng-pristine ng-valid']` | Angular lifecycle classes |

### Step 4 -- Review and Clean the Generated File

The generated file looks like this:
```java
// -- emailField ------------------------------------------
//   OPTIONS  ?  uncomment preferred strategy:
//   BY ID                       @FindBy(xpath = "//input[@id='email']")
//   BY FORM-CONTROL-NAME        @FindBy(xpath = "//input[@formcontrolname='email']")
//   BY NAME + TYPE              @FindBy(xpath = "//input[@name='email' and @type='email']")
//   BY ARIA-LABEL (exact)       @FindBy(xpath = "//input[@aria-label='Email Address']")
//   STRUCTURAL [fallback]       @FindBy(xpath = "//form[1]/div[2]/input[1]")
//
@FindBy(xpath = "//input[@id='email']")    // <- auto-selected primary
public WebElement emailField;
```

**Actions to take:**
1. Confirm the active `@FindBy` is `UNIQUE [x]` in the report
2. Delete all comment lines -- keep only the active `@FindBy`
3. Remove `[HIDDEN]` elements you don't need
4. Remove `[STRUCTURAL]` locators -- rerun crawler or inspect DevTools
5. Rename fields to match your domain language

### Step 5 -- Page Object Quality Checklist
Before using the generated page object in tests:
- [ ] Every `@FindBy` is verified `UNIQUE [x]` in the crawler report
- [ ] No dynamic IDs (`mat-input-N`, `cdk-*`, numeric-only IDs)
- [ ] No structural/positional XPaths
- [ ] No CSS selectors -- XPath only
- [ ] Constructor has `PageFactory.initElements(driver, this)`
- [ ] Constructor sets `PageContext.currentPage.set("<ClassName>")`
- [ ] All action methods use `TestBase` helpers (not raw Selenium calls)
- [ ] **Every action method follows the interaction safety pattern (see below)**
- [ ] File compiles: `mvn compile -q`

---

## [!]? Mandatory Interaction Safety Pattern

> **Rule: Before interacting with ANY element, the page/DOM must be fully ready.**

Every action method MUST follow this sequence -- no exceptions:

### After a click that causes navigation or DOM change:
```java
element.click();
waitUntillPageLoad();                    // 1. wait for page/DOM to settle
waitForElementPresent(driver, nextEl);   // 2. confirm next element is in DOM
fluentWaitUntilElementToBeClickable(nextEl); // 3. confirm it is interactable
```

### Before reading or typing into any element:
```java
waitUntillPageLoad();                    // DOM must be settled
waitForElementPresent(driver, element);  // element must be in DOM
// then interact:
clearAndType(element, value);            // or safeGetText(element)
```

---

## [!]? Mandatory Method-Level Retry Rule

> **Rule: Every element interaction must retry up to 3 times before failing.**  
> This guards against transient DOM re-renders, stale references, and timing races in Angular/SPA apps.

### Use SDK retry helpers -- never raw Selenium calls in page objects:

| Operation | Use this | Never use this |
|---|---|---|
| Click any element | `safeClick(element)` | `element.click()` |
| Type into a field | `clearAndType(element, value)` | `element.sendKeys(value)` |
| Read element text | `safeGetText(element)` | `element.getText()` |

All three helpers are built into `TestBase` with **3-attempt retry** and appropriate waits built in.

### Complete example -- correct pattern:
```java
public void navigateToCheckLocation() {
    waitForElementPresent(driver, mapsMenuLink);
    safeClick(mapsMenuLink);                              // retry-safe click
    waitUntillPageLoad();
    waitForElementPresent(driver, checkLocationMenuOption);
    safeClick(checkLocationMenuOption);                   // retry-safe click
    waitUntillPageLoad();
    waitForElementPresent(driver, checkLocationPageTitle); // confirm arrival
}

public void enterXYCoordinates(String x, String y) {
    waitForElementPresent(driver, xyRadioButton);
    if (!xyRadioButton.isSelected()) {
        safeClick(xyRadioButton);                         // retry-safe click
        waitUntillPageLoad();
    }
    waitForElementPresent(driver, xCoordinateField);
    clearAndType(xCoordinateField, x);                    // 3-retry type
    waitForElementPresent(driver, yCoordinateField);
    clearAndType(yCoordinateField, y);
}

public String getStatus() {
    waitUntillPageLoad();
    waitForElementPresent(driver, statusBadge);
    return safeGetText(statusBadge);                      // 3-retry getText
}
```

### Forbidden patterns:
```java
// ? WRONG -- raw click, no retry, no wait
element.click();
nextElement.sendKeys("value");

// ? WRONG -- no waitUntillPageLoad after navigation click
menuLink.click();
anotherElement.click();

// ? WRONG -- raw Thread.sleep() 
element.click();
Thread.sleep(3000);
nextElement.getText();

// ? WRONG -- raw getText without wait or retry
String text = element.getText();  // may be stale or empty
```

---

## [!]? Select/Verify Retry Pattern for Custom Widgets (e.g., Angular Material dropdowns)

> **Rule:** For widgets that can't use `safeClick`/`clearAndType` alone (custom
> listbox/dropdown panels, multi-step pickers, drag targets), implement a bounded
> **select -> verify -> retry** loop instead of a single-shot interaction.
> This is the generalized form of the "retry for flakiness" concept -- always
> build it on top of SDK waits/helpers, never as an ad-hoc recursive workaround.

**Requirements for any such method:**
1. **Bounded loop, not recursion** -- use a `for`/`while` loop with a local attempt counter (never an instance field; instance fields leak retry state across calls/threads).
2. **Scope every locator to its container** -- always start nested XPath with `.//`, never a bare `//` inside a `findElements` call on a sub-element (a bare `//` searches the whole DOM, not the container).
3. **Wait for the panel/listbox to be present** before searching its options -- `waitForElementPresent(driver, panel)`.
4. **Break immediately after a successful match/click** -- do not keep iterating stale option elements.
5. **Verify the result** (e.g., assert the container now shows the selected value) and only retry if verification fails.
6. **Any recovery action taken before a retry** (e.g., closing a stuck panel) must be a named helper with a comment explaining why -- never an unexplained blind click.
7. **Fail with a clear message** after the max attempts, including the key/value being selected.

### Reference template:
```java
public void selectFromCustomDropdown(String key, String value, WebElement trigger, WebElement outputElement) {
    final int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            waitForElementPresent(driver, trigger);
            safeClick(trigger);                                   // opens the panel, retry-safe
            WebElement panel = waitForElementPresent(driver, By.xpath("//div[@role='listbox']"));
            List<WebElement> options = panel.findElements(By.xpath(".//div[@role='option']")); // scoped, not bare //
            boolean matched = false;
            for (WebElement option : options) {
                if (value.equals(option.getText())) {
                    safeClick(option);
                    matched = true;
                    break;                                        // stop iterating after match
                }
            }
            Assert.assertTrue(matched, "Option not found in dropdown: " + value);
            Assert.assertEquals(safeGetText(trigger), value.trim());
            inputDataMap.put(key, value.trim());
            outputElementMap.put(key, outputElement);
            return;                                                // success -- exit loop
        } catch (AssertionError | Exception e) {
            log.warn("Dropdown selection attempt " + attempt + " failed for value '" + value + "'", e);
            if (attempt == maxAttempts) {
                log.error("FAILURE - could not select '" + value + "' after " + maxAttempts + " attempts", e);
                Assert.fail("Could not select '" + value + "' from dropdown after " + maxAttempts + " attempts");
            }
            // recovery before retry, if the panel is left open/stuck -- name it, comment why:
            closeOpenDropdownIfPresent();  // e.g. clicks outside the panel to dismiss it
        }
    }
}
```

### Forbidden variants of this pattern:
```java
// ? WRONG -- recursive retry instead of bounded loop
public void selectX(...) { ... selectX(...); }   // stack growth, no clear exit

// ? WRONG -- instance-field retry counter (not thread-safe, leaks across calls)
int counter = 0;

// ? WRONG -- unscoped XPath on a sub-element search
container.findElements(By.xpath("//div[@role='option']"));  // searches whole DOM

// ? WRONG -- unexplained recovery action
catch (Exception e) { GENERAL.click(); GENERAL.click(); retry(); }
```

---

## Test-Level Retry

Every `@Test` method automatically gets **3 retry attempts** via `RetryListener` registered in `regression_suite.xml`:
```xml
<listener class-name="com.test.automation.sdk.listener.RetryListener"/>
```

`RetryListener` auto-applies `Retry.class` to every `@Test` -- **no annotation needed on individual methods**.  
If a test fails due to transient infrastructure/network/timing issues it will retry up to 3 times before reporting failure.

**Do NOT** add `retryAnalyzer = Retry.class` manually to `@Test` -- it is already applied globally.

### Step 6 -- Re-Validate After Every Modification (Required)
Any time a `uiActions` class is edited (even small locator/action changes):
1. Re-run locator validation with crawler (`LocatorInvestigator` or `PageObjectGenerator`)
2. Keep only `UNIQUE [x]` locators in active `@FindBy` annotations
3. Re-run compile check: `mvn compile test-compile`
4. Re-run impacted tests that use the changed page object
5. Only then treat the update as complete
