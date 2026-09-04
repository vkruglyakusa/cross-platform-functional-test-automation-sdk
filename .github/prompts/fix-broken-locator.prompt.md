---
mode: agent
description: Heal broken locators in a page object after a product UI change
---

# Fix Broken Locators

The product UI has changed and one or more `@FindBy` locators in a page object
are no longer working. Follow this workflow to heal them using the crawler.

## Required Information

- **Page object class**: e.g. `PoletopLoginPage.java`
- **Target URL** (the page in the current environment)
- **Broken elements** (optional -- agent will discover from failure if not provided)
- **Credentials** for login if the page requires authentication

---

## Execution Steps

### Step 1 -- Identify Broken Locators
If a test failure was provided, read the surefire report:
```
target/surefire-reports/<TestClassName>.txt
```

Locate `NoSuchElementException` or `TimeoutException` lines.
Extract the XPath that failed.
Map it back to the `@FindBy` field in the page object.

If no failure provided, read the page object and list all `@FindBy` fields to be reviewed.

### Step 2 -- Run the Crawler on the Affected Page
```bash
mvn test -Dsurefire.suiteXmlFiles=crawler_suite.xml \
         -Denvironment=stg -DbrowserName=chrome \
         -Dinv.email=${email} -Dinv.password=${password}
```

The crawler produces:
- `src/main/java/.../uiActions/<ClassName>.java` -- updated page object draft
- `test-output/crawler/<ClassName>_<timestamp>.txt` -- full discovery report

### Step 3 -- Review the Discovery Report
Open `test-output/crawler/<ClassName>_<timestamp>.txt`.

For each previously broken element, find its new locator strategies.
Apply the **Locator Priority Ladder** from `locator-strategy.instructions.md`:
```
1. @id (not auto-generated)
2. @data-testid
3. @formcontrolname
4. @name + @type
5. @aria-label (exact)
6. @placeholder (exact)
7. normalize-space(.) for button/link text
8. @routerlink
```

Accept **only `UNIQUE [x]`** strategies.
Reject any locator marked `NOT UNIQUE`, `DYNAMIC`, or `STRUCTURAL`.

### Step 4 -- Classify Each Broken Element

| Element | Old XPath | New UNIQUE [x] XPath | Action |
|---|---|---|---|
| `emailField` | `//input[@id='email']` | `//input[@formcontrolname='email']` | Replace |
| `submitButton` | `//button[@id='btn-submit']` | `//button[normalize-space(.)='Sign In']` | Replace |
| `errorMessage` | `//div[@id='error-12345']` | ? NOT FOUND | Stop -- ask |

**If an element has no `UNIQUE [x]` strategy:**
- Do NOT use structural/positional fallback
- Stop and report: `"Element '<fieldName>' has no stable locator -- needs product team input"`

### Step 5 -- Update the Page Object
Replace only the broken `@FindBy` annotations with new `UNIQUE [x]` locators.
Do not change unbroken locators, method names, or logic.

```java
// Before (broken):
@FindBy(xpath = "//input[@id='mat-input-0']")  // dynamic -- no longer resolves
public WebElement emailField;

// After (healed):
@FindBy(xpath = "//input[@formcontrolname='email']")  // UNIQUE [x] from crawler report
public WebElement emailField;
```

### Step 6 -- Validate (MANDATORY)
```bash
# Compile
mvn compile test-compile -q

# Re-run all tests that use this page object
mvn test -Dtest=${AffectedTestClass} -Denvironment=stg -DbrowserName=chrome
```

If multiple test classes use the page object, run them all.

### Step 7 -- Update CHANGELOG.md and README.md (MANDATORY)

### CHANGELOG.md
After tests pass, append one entry to `CHANGELOG.md` in the project root:

```markdown
## [Unreleased]
### Fixed
- `${PageObjectClass}` -- healed ${N} broken locator(s) after UI change on ${page name} (${date})
```

### README.md
Update the `## Test Coverage` table in `README.md` for every test class that uses
the healed page object. Update **Status** and **Last Updated**:

```
README.md changes:
  ~ | `${TestClassName}` | ${ADO-ID} | ${scenario} | ? Automated | ${date} |
```

If a test was showing `?? Fixing` because of this locator issue, update it to `? Automated`.
Always show the before->after diff in the output.

---

## Output Format
```
Page Object   : ${ClassName}
Elements Healed:
  emailField     : OLD //input[@id='mat-input-0']  -> NEW //input[@formcontrolname='email']
  submitButton   : OLD //button[@id='btn-12345']    -> NEW //button[normalize-space(.)='Sign In']

Elements Not Found (need review):
  errorMessage   : no UNIQUE [x] locator -- escalate to product team

Compile        : ?
Re-run results : ? Test_Login (3 tests)
                 ? Test_ForgotPassword (2 tests)
CHANGELOG.md   : ? Updated
README.md      : ? Updated (diff shown above)
```
