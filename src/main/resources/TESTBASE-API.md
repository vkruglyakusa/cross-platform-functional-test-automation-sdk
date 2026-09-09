# TestBase API Reference

`com.test.automation.sdk.testbase.TestBase`

All **Page Objects** (`uiActions` classes) and **Test Classes** must extend `TestBase`.  
This class provides the full Selenium helper library -- never use raw Selenium APIs directly in your classes.

---

## Table of Contents

1. [Lifecycle & Setup](#1-lifecycle--setup)
2. [Navigation](#2-navigation)
3. [Test Data (Excel)](#3-test-data-excel)
4. [Waiting -- Visibility & Presence](#4-waiting--visibility--presence)
5. [Waiting -- Clickability](#5-waiting--clickability)
6. [Waiting -- Disappearance](#6-waiting--disappearance)
7. [Waiting -- Attribute / Value](#7-waiting--attribute--value)
8. [Waiting -- Page Load](#8-waiting--page-load)
9. [Text Entry](#9-text-entry)
10. [Clicks](#10-clicks)
11. [Dropdowns & Select](#11-dropdowns--select)
12. [Checkboxes & Radio Buttons](#12-checkboxes--radio-buttons)
13. [Mouse & Keyboard Actions](#13-mouse--keyboard-actions)
14. [Frames](#14-frames)
15. [Windows & Tabs](#15-windows--tabs)
16. [Alerts](#16-alerts)
17. [Scrolling & JavaScript](#17-scrolling--javascript)
18. [Screenshots & Reporting](#18-screenshots--reporting)
19. [Text Verification](#19-text-verification)
20. [Data Generators](#20-data-generators)
21. [File Upload](#21-file-upload)
22. [Page Reload Helpers](#22-page-reload-helpers)
23. [Email Verification (Mailinator)](#23-email-verification-mailinator)
24. [Generic List Resolver & ElementAction](#24-generic-list-resolver--elementaction)
25. [Repeating Groups](#25-repeating-groups)
26. [Bootstrap / Custom Dropdowns](#26-bootstrap--custom-dropdowns)
27. [Table Helpers](#27-table-helpers)
28. [Label Harvesters](#28-label-harvesters)
29. [Collection Utilities](#29-collection-utilities)
30. [URL Waits & Reservation Scanner](#30-url-waits--reservation-scanner)
31. [Map Widgets (Google Maps / Leaflet / Mapbox GL / OpenLayers / Bing Maps)](#31-map-widgets-google-maps--leaflet--mapbox-gl--openlayers--bing-maps)

---

## 1. Lifecycle & Setup

These methods are called automatically by TestNG hooks -- you rarely call them directly.

| Method | Signature | Description |
|--------|-----------|-------------|
| `setUp` | `setUp(String environment, String browserName)` | `@BeforeClass` -- resolves environment, Excel file, and base URL, then opens the browser. |
| `afterClass` | `afterClass()` | `@AfterClass` -- closes browser and flushes Extent reports. |
| `beforeMethod` | `beforeMethod(Method result)` | `@BeforeMethod` -- handles retry logic by reinitializing the browser on retries. |
| `initialization` | `initialization(String browser, String baseUrl)` | Opens the browser and navigates to the base URL. Falls back to `config.properties` if empty strings are passed. |
| `loadData` | `loadData()` | Loads `config.properties` into the `Prop` field. Called automatically. |
| `closeBrowser` | `closeBrowser()` | Quits the WebDriver session and flushes reporting. |
| `initAccessibility` | `initAccessibility()` | Initializes the SDK accessibility reporter stack (Slf4j + Allure composite). Call once from `@BeforeSuite` when using accessibility scanning. |

---

## 2. Navigation

| Method | Signature | Description |
|--------|-----------|-------------|
| `getUrl` | `getUrl(String url)` | Navigates to the URL, maximizes the window, and waits for page load. |
| `setBaseUrl` | `setBaseUrl(String environment)` | Resolves `baseURL` from `config.properties` for the given environment key (`dev`, `tst`, `stg`, `nonprod`, `prod`). |
| `setXlsName` | `setXlsName(String environment)` | Resolves the Excel test data file name for the given environment. |

**Usage in test:**
```java
// Navigate back to home after a test
driver.get(baseURL);
waitUntillPageLoad();
```

---

## 3. Test Data (Excel)

| Method | Signature | Description |
|--------|-----------|-------------|
| `getData` | `getData(String sheetName)` | Reads the Excel file set by `setXlsName()` and returns a 2D data array for `@DataProvider`. |
| `getData` | `getData(String excelFileName, String sheetName)` | Reads an explicitly specified Excel file. |
| `selectExcelData` | `selectExcelData(String sheetName, String sqlQuery)` | Queries the Excel sheet with a SQL expression and returns filtered rows. |

**Typical usage in test:**
```java
@DataProvider(name = "myData")
public Object[][] myData() throws IOException {
    return getData("MySheet");
}
```

---

## 4. Waiting -- Visibility & Presence

Use these methods **in Page Objects** before reading an element's text or attributes.

| Method | Signature | Timeout | Description |
|--------|-----------|---------|-------------|
| `waitForElementPresent` | `waitForElementPresent(WebDriver driver, WebElement element)` | 60 s | Waits until the element is visible. **Preferred for Page Objects.** |
| `waitForElementPresent` | `waitForElementPresent(WebElement element)` | 60 s | Static shorthand -- uses the shared `driver`. |
| `waitForElementPresent` | `waitForElementPresent(WebElement element, int timeout)` | custom | Waits with a custom timeout. |
| `waitForElementPresent` | `waitForElementPresent(By locator)` | 120 s | Waits by `By` locator. |
| `waitForElement` | `waitForElement(WebDriver driver, int timeoutInSeconds, WebElement element)` | custom | Explicit wait by timeout value. |
| `waitForElement` | `waitForElement(WebDriver driver, WebElement element, long timeoutInSeconds)` | custom | Returns the element after it becomes clickable. |
| `fluentWaitForElementPresent` | `fluentWaitForElementPresent(WebElement element)` | 60 s / 2 s poll | FluentWait -- ignores `NoSuchElementException` and `StaleElementReferenceException`. |
| `fluentWaitForElementPresent` | `fluentWaitForElementPresent(WebDriver driver, WebElement element)` | 60 s / 2 s poll | Static overload. |
| `fluentWaitForElement` | `fluentWaitForElement(WebElement element)` | 120 s / 5 s poll | Waits for both visibility AND clickability. |
| `waitForTextPresent` | `waitForTextPresent(WebElement element, String text)` | 120 s | Waits until the element contains the expected text. |
| `waitUntilTitleIsPresent` | `waitUntilTitleIsPresent(WebDriver driver, String titleText)` | 120 s | Waits for the page title to contain the text. |

**Example in a Page Object:**
```java
public String getStatusText() {
    waitForElementPresent(driver, statusLabel);
    return statusLabel.getText().trim();
}
```

---

## 5. Waiting -- Clickability

Use these methods **before clicking** any element.

| Method | Signature | Timeout | Description |
|--------|-----------|---------|-------------|
| `fluentWaitUntilElementToBeClickable` | `fluentWaitUntilElementToBeClickable(WebElement element)` | 120 s / 5 s poll | **Primary method** -- waits for visibility then clickability. Ignores stale/timeout exceptions. |
| `waitUntilElementToBeClickable` | `waitUntilElementToBeClickable(WebElement element)` | 60 s | Standard `WebDriverWait` clickability check. |
| `waitUntilButtonIsClickable` | `waitUntilButtonIsClickable(By locator)` | 60 s | Waits by `By` locator and returns the element. |
| `elementIsClickable` | `elementIsClickable(WebElement element, WebDriver driver, long timeoutSeconds)` | custom | Returns `true`/`false` -- useful for conditional checks without throwing. |

**Example in a Page Object:**
```java
public void clickSubmit() {
    fluentWaitUntilElementToBeClickable(submitButton);
    submitButton.click();
}
```

---

## 6. Waiting -- Disappearance

| Method | Signature | Timeout | Description |
|--------|-----------|---------|-------------|
| `waitForElementToDisappear` | `waitForElementToDisappear(By locator)` | 60 s | Waits for element to become invisible or absent. |
| `waitForElementToDisappear` | `waitForElementToDisappear(By locator, long timeoutSeconds)` | custom | Same with custom timeout. |
| `waitForElementToDisappear` | `waitForElementToDisappear(WebElement element, long timeoutSeconds)` | custom | WebElement overload. |

**Example:**
```java
// Wait for a loading spinner to disappear
waitForElementToDisappear(By.xpath("//mat-spinner"), 30);
```

---

## 7. Waiting -- Attribute / Value

| Method | Signature | Description |
|--------|-----------|-------------|
| `waitForAttributeToBe` | `waitForAttributeToBe(WebElement element, String attribute, String expectedValue, long timeoutSeconds)` | Waits until `element.getAttribute(attribute)` equals `expectedValue`. |
| `waitForAttributeToContain` | `waitForAttributeToContain(WebElement element, String attribute, String substring, long timeoutSeconds)` | Waits until `element.getAttribute(attribute)` contains `substring`. |

**Example:**
```java
// Wait for the class attribute to include 'active'
waitForAttributeToContain(tabElement, "class", "active", 30);
```

---

## 8. Waiting -- Page Load

| Method | Signature | Timeout | Description |
|--------|-----------|---------|-------------|
| `waitUntillPageLoad` | `waitUntillPageLoad()` | 120 s | **Preferred.** Waits for `document.readyState === 'complete'`. Call after any navigation. |
| `waitForPageToLoad` | `waitForPageToLoad(long timeoutSeconds)` | custom | Same with configurable timeout. Asserts failure if the page doesn't load. |
| `waitForUrlContains` | `waitForUrlContains(String partial, int timeoutSeconds)` | custom | Blocks until `driver.getCurrentUrl()` contains `partial`. Use after SSO/SAML login to confirm the redirect has completed before proceeding. Throws `TimeoutException` on timeout. |

---

## 9. Text Entry

| Method | Signature | Description |
|--------|-----------|-------------|
| `clearAndType` | `clearAndType(WebElement element, String text)` | **Primary text input method.** Clears the field, then types. Retries up to 3× on stale/not-interactable exceptions. |
| `clearAndTypeAngular` | `clearAndTypeAngular(WebElement element, String text)` | Angular-aware text input. Types, dispatches `input`+`change` events with `bubbles:true`, then sends Keys.TAB to trigger blur validation. Use when Angular required-field validators stay active after `clearAndType()`. |

> **Never** use raw `element.sendKeys()` in Page Objects. Use `clearAndType()` (standard) or `clearAndTypeAngular()` (Angular reactive forms with `(blur)` validators).

**Example:**
```java
public void enterEmail(String email) {
    waitForElementPresent(driver, emailField);
    clearAndType(emailField, email);
}
```

---

## 10. Clicks

| Method | Signature | Description |
|--------|-----------|-------------|
| `safeClick` | `safeClick(WebElement element)` | Fluent-waits for clickability then clicks. Retries 3? on WebDriver exceptions. **Use for any click that may have timing issues.** |
| `clickOnElementbyJavaScript` | `clickOnElementbyJavaScript(WebElement element)` | Clicks via JavaScript executor. Use when normal click is intercepted by overlays. |

**Example:**
```java
public void clickSave() {
    fluentWaitUntilElementToBeClickable(saveButton);
    safeClick(saveButton);
}
```

---

## 11. Dropdowns & Select

These methods work with native HTML `<select>` elements.

| Method | Signature | Description |
|--------|-----------|-------------|
| `selectOptionInDropDownBox` | `selectOptionInDropDownBox(WebElement element, String value)` | Selects an option by matching visible text. |
| `visibleInDropDownList` | `visibleInDropDownList(WebElement element, String textValue)` | Selects by visible text using `Select.selectByVisibleText()`. |
| `selectByIndex` | `selectByIndex(WebElement element, int index)` | Selects by zero-based index. |
| `selectByValue` | `selectByValue(WebElement element, String value)` | Selects by the option's `value` attribute. |
| `selectByValueAngular` | `selectByValueAngular(WebElement element, String value)` | Angular-aware select. Sets value via JS and dispatches `input`+`change` events with `bubbles:true`. Use when `selectByValue()` silently has no effect on form Save (Angular two-way binding not notified). |
| `getSelectedOption` | `getSelectedOption(WebElement element)` | Returns the text of the currently selected option. |
| `getAllDropdownOptions` | `getAllDropdownOptions(WebElement element)` | Returns a `List<String>` of all option texts. |

> For Angular Material `<mat-select>` dropdowns, click the trigger element, then click the `mat-option` directly -- do not use `Select` API.
> For native `<select>` with Angular two-way binding, use `selectByValueAngular()`.

---

## 12. Checkboxes & Radio Buttons

| Method | Signature | Description |
|--------|-----------|-------------|
| `selectCheckbox` | `selectCheckbox(WebElement element)` | Clicks the checkbox only if it is **not** already selected (idempotent). |
| `deselectCheckbox` | `deselectCheckbox(WebElement element)` | Clicks the checkbox only if it **is** selected (idempotent). |

---

## 13. Mouse & Keyboard Actions

| Method | Signature | Description |
|--------|-----------|-------------|
| `hoverOverElement` | `hoverOverElement(WebElement element)` | Moves mouse over the element (hover). |
| `doubleClick` | `doubleClick(WebElement element)` | Double-clicks the element. |
| `rightClick` | `rightClick(WebElement element)` | Context-clicks (right-click) the element. |
| `dragAndDrop` | `dragAndDrop(WebElement source, WebElement target)` | Drags `source` and drops it on `target`. |
| `pressKey` | `pressKey(WebElement element, CharSequence key)` | Clicks the element then sends a special key (e.g. `Keys.TAB`, `Keys.ENTER`). |
| `highlightMe` | `highlightMe(WebDriver driver, WebElement element)` | Highlights an element with a yellow border (debugging only). |

**Example:**
```java
// Press TAB to move to next field
pressKey(emailField, Keys.TAB);
```

---

## 14. Frames

| Method | Signature | Description |
|--------|-----------|-------------|
| `switchtoFrame` | `switchtoFrame(String frameName)` | Switches to a named frame (returns to default content first). |
| `switchToFrame` | `switchToFrame(int index)` | Switches to a frame by index. |
| `switchToFrame` | `switchToFrame(WebElement frameElement)` | Switches to a frame identified by a `WebElement`. |
| `switchToChildFrame` | `switchToChildFrame(String parentFrame, String childFrame)` | Switches to parent then child frame. |
| `switchtoDefaultFrame` | `switchtoDefaultFrame()` | Switches back to the main page content. |

---

## 15. Windows & Tabs

| Method | Signature | Description |
|--------|-----------|-------------|
| `getAllWindows` | `getAllWindows()` | Returns an `Iterator<String>` over all open window handles. |
| `switchToWindow` | `switchToWindow(String windowHandle)` | Switches to the given handle, returns the previous handle. |
| `switchToNewWindow` | `switchToNewWindow()` | Stores the current handle in `parentWindow` and switches to the most recently opened window/tab. |
| `switchToParentWindow` | `switchToParentWindow()` | Switches back to the stored `parentWindow` handle. |
| `closeCurrentTabAndSwitchToParent` | `closeCurrentTabAndSwitchToParent()` | Closes the active tab and returns to the parent window. |
| `openNewTab` | `openNewTab()` | Opens a new blank tab via JavaScript and switches to it. |

---

## 16. Alerts

| Method | Signature | Timeout | Description |
|--------|-----------|---------|-------------|
| `waitForAlert` | `waitForAlert(long timeoutSeconds)` | custom | Waits for an alert to appear and returns the `Alert`. |
| `acceptAlert` | `acceptAlert()` | 10 s | Accepts (OK) the alert. |
| `dismissAlert` | `dismissAlert()` | 10 s | Dismisses (Cancel) the alert. |
| `getAlertText` | `getAlertText()` | 10 s | Returns the alert message text. |
| `sendKeysToAlert` | `sendKeysToAlert(String text)` | 10 s | Types text into a prompt alert. |

---

## 17. Scrolling & JavaScript

| Method | Signature | Description |
|--------|-----------|-------------|
| `scrollIntoView` | `scrollIntoView(WebElement element)` | **Primary scroll method.** Scrolls the element into the visible viewport using Selenium Actions wheel input. Calculates the minimum scroll offset needed -- scrolls down if the element bottom is below the viewport, scrolls up if the element top is above it, and does nothing if the element is already fully visible. Falls back to JavaScript `scrollIntoView(true)` on older drivers. Returns the element for fluent chaining. |
| `scrollToElement` | `scrollToElement(WebElement element)` | Scrolls the element into view via JavaScript with a brief pause for UI settling. |
| `scrollToElement` | `scrollToElement(By by, WebDriver driver)` | Scrolls to element located by `By` locator. |
| `scrollToTop` | `scrollToTop()` | Scrolls to the top of the page. |
| `scrollToBottom` | `scrollToBottom()` | Scrolls to the bottom of the page. |
| `executeScript` | `executeScript(String script, Object... args)` | Executes arbitrary JavaScript. Returns the script's return value or `null`. |

**Example in a Page Object:**
```java
public void clickItemBelowFold() {
    // scrollIntoView returns the element -- chain directly into click
    fluentWaitUntilElementToBeClickable(scrollIntoView(myButton));
    safeClick(myButton);
}

// or separate steps
public void scrollAndVerify() {
    scrollIntoView(resultPanel);
    waitForElementPresent(driver, resultPanel);
    return safeGetText(resultPanel);
}
```

---

## 18. Screenshots & Reporting

| Method | Signature | Description |
|--------|-----------|-------------|
| `getScreenShot` | `getScreenShot(String name)` | Captures a screenshot to the output directory with a timestamp. |
| `getScreenShot` | `getScreenShot(WebDriver driver, ITestResult result, String folderName)` | Captures a screenshot on test failure and attaches it to the Extent report. |
| `captureScreen` | `captureScreen(String fileName)` | Captures a screenshot and returns the absolute file path. |
| `saveDomDump` | `saveDomDump(WebDriver driver, String testName)` | Saves a DOM dump HTML file to `screenshots.outputDir`, paired with the screenshot artifact when used on failure. |
| `setCurrentTestCaseName` | `setCurrentTestCaseName(String name)` | Sets the current data-driven test case name so Listener can rename report entries and failure artifacts per DataProvider row. Call this at the start of every data-driven `@Test` method after the `runMode` check. |
| `getCurrentTestCaseName` | `getCurrentTestCaseName()` | Returns the current test case name tracked for the running test. Internal helper used by Listener and reporting flows, not intended for test authors. |
| `getresult` | `getresult(ITestResult result)` | Logs the TestNG result (PASS/FAIL/SKIP) into the Extent report. |
| `runAccessibilityScan` | `runAccessibilityScan(String pageName)` | Runs the full built-in 5-layer accessibility engine for the current page and writes artifacts under `reporting.accessibilityDir`. Safe to call from tests or hooks. |
| `assertNoAccessibilityViolations` | `assertNoAccessibilityViolations(String pageName)` | Runs an axe-core accessibility scan for the current page and fails the test if violations are found. |
| `getAccessibilityOutputDirectory` | `getAccessibilityOutputDirectory()` | Returns the resolved root output directory for accessibility JSON, Excel, and HTML artifacts. |

> Layer 1 of the accessibility engine (`runAccessibilityScan` / `assertNoAccessibilityViolations`) is
> powered by Deque Systems' official `axe-core` Selenium binding
> (`com.deque.html.axe-core:selenium:4.10.1`), bundled transitively -- no extra
> dependency needed. Full config keys, rule catalogue links, and API references:
> [SDK-USER-GUIDE.md §14](SDK-USER-GUIDE.md#14-accessibility-testing).

> Screenshots are saved to `reporting.screenshotsDir` (default: `test-output/screenshots`).
> DOM dumps are saved to `reporting.domDumpsDir` and use the same base filename as the screenshot when captured on failure.
> Accessibility artifacts are saved to `reporting.accessibilityDir`.

### Data-driven failure artifact flow

For DataProvider-based tests, call `setCurrentTestCaseName(testCaseName)` at the
start of the `@Test` method. Listener reads that value and:

- Renames the TestNG XML/HTML entry from `methodName` to `methodName.testCaseName`
- Names the failure screenshot after `testCaseName` instead of the raw method name
- Names the DOM dump with the same base name plus `_DOM.html`
- Logs a clickable `file:///` link to the DOM dump into the TestNG HTML report

On failure, `Listener.onTestFailure()` automatically captures both artifacts with
matching base names in `test-output/screenshots/`:

1. Screenshot PNG
2. DOM dump HTML

The DOM dump uses `JavascriptExecutor.executeScript("return document.documentElement.outerHTML")`
first so live Angular/React DOM state is preserved, then falls back to
`driver.getPageSource()` if JavaScript execution is unavailable. The saved HTML
includes header comments for test name, URL, and capture time.

## Data-Driven Test Reporting

When using `@DataProvider` (Excel-backed), every row runs the same `@Test` method.
By default all rows appear as a single test entry in reports and screenshots overwrite each other.

### How to fix it -- one line per test

Call `setCurrentTestCaseName(testCaseName)` at the start of every data-driven `@Test` method:

```java
@Test(dataProvider = "myData", priority = 1)
public void testLogin(String testCaseName, String email, String password, String runMode) {
    if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);
    setCurrentTestCaseName(testCaseName);   // add this line
    step("Open login page");
    // ... rest of test
}
```

### What happens automatically (no extra code needed)

| Artifact | Behavior without setCurrentTestCaseName | Behavior with setCurrentTestCaseName |
|---|---|---|
| TestNG XML report entry | `testLogin` (all rows look identical) | `testLogin.TC-001 Valid Login`, `testLogin.TC-002 Invalid Credentials` |
| Screenshot filename | `testLogin_19_08_2026.png` (overwritten each row) | `TC-001_Valid_Login_19_08_2026.png` |
| DOM dump filename | `testLogin_19_08_2026_DOM.html` | `TC-001_Valid_Login_19_08_2026_DOM.html` |
| TestNG HTML report | Screenshot + DOM link under generic method name | Screenshot + DOM link under each test case name |

### Failure artifacts (automatic -- no code needed)

On every test failure, `Listener` automatically captures:
1. **Screenshot** -- PNG saved to `test-output/screenshots/`
2. **DOM dump** -- HTML saved to the same `test-output/screenshots/` folder, paired with the screenshot by filename
   - Uses `JavascriptExecutor.outerHTML` to capture the live rendered DOM, including Angular/React state
   - Falls back to `driver.getPageSource()` if JS execution fails
   - Contains header comments: test name, URL at failure, timestamp
   - A clickable link appears in the TestNG HTML report

---

## 19. Text Verification

| Method | Signature | Description |
|--------|-----------|-------------|
| `verifyText` | `verifyText(String expectedText, String actualText)` | Asserts that `actualText` equals `expectedText`. Throws a descriptive `AssertionError` on mismatch. |
| `verifyTextOnThePage` | `verifyTextOnThePage(String text)` | Returns `true` if the text is present anywhere in the page source. |
| `safeGetText` | `safeGetText(WebElement element)` | Reads `element.getText()` with up to 3 retries on `StaleElementReferenceException`. Returns trimmed text. |

**Example in a test:**
```java
String actual = safeGetText(confirmationMessage);
verifyText("Application Submitted", actual);
```

---

## 20. Data Generators

| Method | Signature | Description |
|--------|-----------|-------------|
| `randomEmailAddress` | `randomEmailAddress()` | Generates a random `test<6-digits>@doitt.nyc.gov` email. |
| `randomEmailAddress` | `randomEmailAddress(String domain)` | Generates a random email on the provided domain. |
| `randomPassword` | `randomPassword()` | Generates a random `test<6-digits>` password string. |
| `newUniqueUsername` | `newUniqueUsername()` | Generates a timestamp-based unique username (`user<yyMMddhhmmssMs>`). |
| `randomString` | `randomString()` | Returns a random 5-digit numeric string. |

---

## 21. File Upload

| Method | Signature | Description |
|--------|-----------|-------------|
| `uploadFile` | `uploadFile(WebElement fileInput, String filePath)` | Sends the absolute file path to an `<input type="file">` element. Automatically configures `LocalFileDetector` for Selenium Grid runs. |

**Example:**
```java
uploadFile(fileInputElement, "C:\\TestData\\sample.pdf");
```

---

## 22. Page Reload Helpers

| Method | Signature | Description |
|--------|-----------|-------------|
| `reloadPageUntilTextVisible` | `reloadPageUntilTextVisible(String text)` | Refreshes the page up to 20 times until the given text appears in the page source. |
| `reloadPageUntilWebElementVisible` | `reloadPageUntilWebElementVisible(WebElement element)` | Refreshes the page up to 30 times until the element is displayed. |
| `explicitWait` | `explicitWait(int seconds)` | Sets the implicit wait on the WebDriver session. **Prefer targeted waits** over this -- it affects all subsequent `findElement` calls. |
| `implicitWait` | `implicitWait(int sec)` | Polls `document.readyState == "complete"` via FluentWait every 500 ms for up to `sec` seconds. Use as a reliable alternative to `driver.manage().timeouts().implicitlyWait()` when driver-level implicit wait behaves inconsistently. No `Thread.sleep`, no global driver state mutation. |
| `waitForNavigationOrElement` | `waitForNavigationOrElement(String urlBefore, By landmark, int timeout)` | Composite FluentWait: resolves as soon as the URL changes OR a landmark element appears. Use after a click in an Angular SPA instead of Thread.sleep(). |
| `waitForModalOrUrlChange` | `waitForModalOrUrlChange(String urlBefore, int timeout)` | Polls for Angular/CDK modal anchors (mat-dialog-container, role=dialog, cdk-overlay-pane) or URL change. Use after a click that opens a dialog overlay. |

---

## 23. Email Verification (Mailinator)

These methods let tests verify email-based flows (registration confirmation, password reset,
OTP codes, account IDs, etc.) without ever importing a Mailinator class directly.

All methods poll the inbox with configurable retry -- the inbox is checked after an initial
delay, then retried every few seconds until the email arrives or the timeout expires.
After reading, the processed message is automatically deleted so it cannot affect other tests
running against the same address.

> **Inbox isolation rule:** `deleteEmailById` removes only the single message your test
> processed. Never wipe an entire inbox -- other projects or parallel tests may share the
> same Mailinator domain.

### Configuration -- `configuration/sdk-config.yaml`

```yaml
api:
  mailinator:
    apiKey: "YOUR_MAILINATOR_API_KEY"
    domain: "mailinator.com"         # or your private domain
    privateDomain: true
    inboxInitialWaitSeconds: 5       # wait before first poll
    inboxPollIntervalSeconds: 3      # time between retries
    inboxPollTimeoutSeconds: 60      # total timeout
```

### Configuration -- `configuration/mailinator-email-templates.yaml`

Each template describes how to find and extract content from one type of email.
Copy `mailinator-email-templates.yaml.template` from the SDK defaults and customize:

```yaml
templates:

  confirmation:
    subjectContains: "Confirm Your Email"
    urlPathPatterns: "validateToken,validateReset"
    excludePatterns: "deactivate,amp;"
    textPattern: ""
    masks: ""

  otpCode:
    subjectContains: "Your Verification Code"
    urlPathPatterns: ""
    excludePatterns: ""
    textPattern: "Your code is: (\\d{6})"
    masks: "trim"

  accountId:
    subjectContains: "Account Created"
    urlPathPatterns: ""
    excludePatterns: ""
    textPattern: "Account ID: ([A-Za-z0-9-]+)"
    masks: "trim,uppercase"
```

**Template fields:**

| Field | Purpose |
|-------|---------|
| `subjectContains` | Email subject must contain this text (case-insensitive). Leave blank to match any subject. |
| `urlPathPatterns` | Comma-separated URL path segments -- the extracted link must contain at least one. Used by `getEmailUrl`. |
| `excludePatterns` | Comma-separated patterns that disqualify a URL match. |
| `textPattern` | Java regex with **exactly one** capture group. `group(1)` is returned. Used by `getEmailText`. |
| `masks` | Comma-separated ordered transformations applied after extraction. |

**Supported masks:**

| Mask token | Effect |
|------------|--------|
| `trim` | Removes leading/trailing whitespace |
| `uppercase` | Converts to UPPER CASE |
| `lowercase` | Converts to lower case |
| `substring:start:end` | Substring slice (`-1` = to end) |
| `dateFormat:inputPattern:outputPattern` | Reformats a date string (uses `SimpleDateFormat`) |
| `replaceAll:regex:replacement` | Regex replacement on the value |

### Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `getEmailUrl` | `getEmailUrl(String templateName, String baseURL, String emailAddress)` | Polls the inbox and returns the first URL from the email body matching the template's `urlPathPatterns`. Returns `baseURL` as fallback if no email or link is found. Auto-deletes the processed message. |
| `getEmailText` | `getEmailText(String templateName, String emailAddress)` | Polls the inbox and returns the value captured by the template's `textPattern` regex, after applying masks. Returns `null` if not found. Auto-deletes the processed message. |
| `getConfirmationEmailUrl` | `getConfirmationEmailUrl(String baseURL, String emailAddress)` | Shorthand for `getEmailUrl("confirmation", baseURL, emailAddress)`. |
| `getDeactivationEmailUrl` | `getDeactivationEmailUrl(String baseURL, String emailAddress)` | Shorthand for `getEmailUrl("deactivation", baseURL, emailAddress)`. |
| `deleteEmailById` | `deleteEmailById(String emailId)` | Deletes a specific message by its Mailinator message ID. Use for explicit cleanup when you need to remove a message that was not auto-deleted. |

### Examples

```java
// -- URL extraction -- navigate to a confirmation link ---------------------
step("Get confirmation URL from email");
String confirmUrl = getConfirmationEmailUrl(baseURL, "testuser@mailinator.com");
driver.get(confirmUrl);
waitUntillPageLoad();

// -- URL extraction -- custom template -------------------------------------
step("Get password reset link");
String resetUrl = getEmailUrl("passwordReset", baseURL, "testuser@mailinator.com");
driver.get(resetUrl);

// -- Text extraction -- OTP code --------------------------------------------
step("Get OTP code from email");
String otp = getEmailText("otpCode", "testuser@mailinator.com");
Assert.assertNotNull(otp, "OTP code was not received in email");
myPage.enterOtp(otp);

// -- Text extraction -- account number -------------------------------------
step("Get account number from welcome email");
String accountId = getEmailText("accountId", "testuser@mailinator.com");
verifyText(expectedAccountId, accountId);

// -- Manual delete -- when you need to clean up without reading -------------
// (auto-delete already happens after getEmailUrl / getEmailText)
deleteEmailById("testuser-15234567890");
```

> **Never use `MailinatorEmailReader` directly in test classes or page objects.**
> Always call these `protected` TestBase methods -- they are the only supported API.

---

## Quick Reference -- Page Object Cheat Sheet

```java
// ? Wait then read text
waitForElementPresent(driver, myElement);
String text = safeGetText(myElement);

// ? Wait then click
fluentWaitUntilElementToBeClickable(myButton);
safeClick(myButton);

// ? Type into a field
waitForElementPresent(driver, inputField);
clearAndType(inputField, "some value");

// ? JavaScript click (for intercepted elements)
clickOnElementbyJavaScript(overlayButton);

// ? Wait for page to settle after navigation
waitUntillPageLoad();

// ? Wait for a spinner to disappear
waitForElementToDisappear(By.xpath("//mat-spinner"), 30);

// ? Select from native dropdown
selectOptionInDropDownBox(statusDropdown, "Active");

// ? Select from Angular Material mat-select
fluentWaitUntilElementToBeClickable(matSelectTrigger);
matSelectTrigger.click();
waitForElementPresent(driver, matOptionActive);
matOptionActive.click();

// ? Assert text
verifyText("Expected Label", safeGetText(resultElement));
```

---

## Quick Reference -- Test Class Cheat Sheet

```java
public class Test_MyFeature extends TestBase {

    @DataProvider(name = "myData")
    public Object[][] myData() throws IOException {
        return getData("MySheet");          // reads ExcelName set by @BeforeClass
    }

    @Test(dataProvider = "myData", priority = 1)
    public void testScenario(String testCaseName, String inputField, String runMode) throws Exception {
        if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);

        step("Open the form page");
        MyPage page = new MyPage(driver);
        page.enterValue(inputField);

        step("Submit and verify result");
        page.clickSubmit();
        String result = safeGetText(page.resultLabel);
        verifyText("Success", result);
    }
}
```

---

*Framework Automation SDK -- `com.test.automation:cross-platform-functional-test-automation-sdk:1.1.0`*

---

## 24. Generic List Resolver & ElementAction

`actOnListElement` is the **primary method for interacting with any repeated element list** (nav items, menu items, table rows, tag groups). It replaces all hand-written loops.

### `ElementAction` enum

```java
public enum ElementAction { CLICK, TYPE, SELECT, FIND }
```

### `actOnListElement`

| Parameter | Type | Description |
|---|---|---|
| `elements` | `List<WebElement>` | Any `@FindBy` list |
| `matchText` | `String` | Case-insensitive contains match on element text |
| `action` | `ElementAction` | What to do when match is found |
| `value` | `String` | Value for `TYPE` or `SELECT`; `null` for `CLICK`/`FIND` |

**Returns:** matched `WebElement` or `null`.

```java
// In page object @FindBy:
@FindBy(xpath = "//*[@id='nav-primary']//ul//li")
public List<WebElement> navMenuItems;

// In action method:
public void goToEnroll() {
    actOnListElement(navMenuItems, "New Reservation", ElementAction.CLICK, null);
}
```

**How it works:**
1. Iterates the list, finds item where `getText()` contains `matchText` (case-insensitive).
2. Calls `resolveConfirmedXpath(element)` -- tests id/routerlink/href/text candidates live on the DOM.
3. Logs the `UNIQUE [x]` confirmed XPath.
4. Executes the requested action via the confirmed locator.

### `resolveConfirmedXpath`

| Signature | Description |
|---|---|
| `resolveConfirmedXpath(WebElement element)` | Tests XPath candidates against live DOM; returns first `UNIQUE [x]` XPath string or `null`. |

---

## 25. Repeating Groups

| Method | Signature | Description |
|---|---|---|
| `buildGroupItemXpath` | `buildGroupItemXpath(String idStem, String itemText)` | Builds `//*[contains(@id,'stem') and normalize-space(.)='text']` |
| `buildGroupListXpath` | `buildGroupListXpath(String idStem)` | Builds `//*[contains(@id,'stem')]` for the full group |
| `getGroupTexts` | `getGroupTexts(String idStem)` | Returns `List<String>` of all group item texts |
| `clickInGroupByText` | `clickInGroupByText(String idStem, String text)` | Clicks the group item whose text equals `text` |
| `findInGroupByText` | `findInGroupByText(String idStem, String text)` | Returns the `WebElement` matching `text`, or `null` |

---

## 26. Bootstrap / Custom Dropdowns

| Method | Signature | Description |
|---|---|---|
| `getDropdownMenuItems` | `getDropdownMenuItems(String menuId, String menuClass)` | Returns all `<li>` item texts from an open dropdown container. |
| `clickDropdownMenuItemByText` | `clickDropdownMenuItemByText(String menuId, String menuClass, String labelContains)` | Clicks the first `<li>` item whose text contains `labelContains`. |
| `clickDropdownOptionByRole` | `clickDropdownOptionByRole(String containerId, String itemRole, String labelContains)` | Iterates `role="option"` / `role="menuitem"` children; clicks matching item. |
| `selectFromCustomWidget` | `selectFromCustomWidget(WebElement trigger, By panelLocator, By optionLocator, String value)` | Select/verify/retry helper (3 attempts) for custom listbox/panel widgets (e.g. Angular Material). Opens `trigger` via `safeClick`, waits for `panelLocator`, searches `optionLocator` **relative to the panel** (must start with `.//`), clicks the matching option, and verifies `trigger`'s text equals `value` before returning. Retries the full sequence on `AssertionError`/`WebDriverException`. |

```java
selectFromCustomWidget(
    boroughTrigger,
    By.xpath("//div[@role='listbox']"),
    By.xpath(".//div[@role='option']"),
    "Manhattan");
```

---

## 27. Table Helpers

| Method | Signature | Description |
|---|---|---|
| `getTableRows` | `getTableRows(String tableXpath)` | Returns all body rows as `List<List<String>>`. |
| `clickTableRowByColumnValue` | `clickTableRowByColumnValue(String tableXpath, int columnIndex, String value, String clickChildTag)` | Clicks the first row where column `columnIndex` contains `value`. |
| `getTableColumnValues` | `getTableColumnValues(String tableXpath, int columnIndex)` | Returns all values in a single column. |

---

## 28. Label Harvesters

| Method | Signature | Description |
|---|---|---|
| `getPageLabels` | `getPageLabels(String scopeXpath)` | Returns `Map<String, WebElement>` (label text -> element) for all `<label>` elements inside scope. |
| `getPageLabels` | `getPageLabels()` | Whole-page overload -- scans `//label`. |
| `getPageLabelTexts` | `getPageLabelTexts(String scopeXpath)` | Returns only `List<String>` of label texts for assertions. |

---

## 29. Collection Utilities

| Method | Signature | Description |
|---|---|---|
| `getElementTexts` | `getElementTexts(List<WebElement> elements)` | Extracts trimmed `getText()` from any `List<WebElement>`. |
| `listDifference` | `listDifference(List<String> listA, List<String> listB)` | Returns items in A not present in B (non-destructive). |
| `safeAttr` | `safeAttr(WebElement element, String attribute)` | Returns attribute value or empty string if null. |
| `lastHrefSegment` | `lastHrefSegment(WebElement element)` | Returns the last path segment of the element's `href`. |

---

## 30. URL Waits & Reservation Scanner

### `waitForUrlContains`

Blocks until the current URL contains a given substring. Use this after any SSO/SAML login
flow to confirm the redirect has completed and the app is active before proceeding.

| Parameter | Type | Description |
|---|---|---|
| `partial` | `String` | Substring to wait for in `driver.getCurrentUrl()` |
| `timeoutSeconds` | `int` | Max wait in seconds |

**Throws:** `TimeoutException` if the URL does not contain `partial` within the timeout.

```java
// After loginAsPoletopUser() — confirm SAML redirect completed
loginPage.loginAsPoletopUser(email, password);
waitForUrlContains("dashboard", 30);
waitUntillPageLoad();
```

> **Note:** `PoletopLoginPage.loginAsPoletopUser()` already calls this internally.
> Use `waitForUrlContains` directly only when building a custom login flow or
> investigating with `LocatorInvestigator`.

---

### `scanReservationsByButtonTitle`

Navigates to each reservation ID in sequence and returns those where the details page
contains an action button with the given `title=` attribute. Designed for investigator
tooling and test data discovery — requires the driver to already be logged in.

| Parameter | Type | Description |
|---|---|---|
| `baseUrl` | `String` | App base URL (e.g. `"https://poletop-stg.csc.nycnet/"`) |
| `reservationIds` | `List<String>` | List of reservation ID strings to check |
| `buttonTitle` | `String` | Exact `title=` attribute value (e.g. `"Add SIF"`, `"Start Construction"`) |
| `renderWaitMs` | `int` | Max ms to wait for Angular to render action buttons (recommended: 1500–2000) |

**Returns:** `List<String>` — reservation IDs where the button was found in the DOM.

**Implementation notes:**
- Uses `FluentWait` + `driver.findElements()` — no `Thread.sleep`, no `getPageSource()`.
- Skips IDs where the driver was redirected away (inaccessible reservation).
- Catches and logs exceptions per ID without stopping the scan.

```java
loginPage.loginAsPoletopUser(email, password);

List<String> ids = new ArrayList<String>();
for (int i = 12000; i <= 12100; i++) ids.add(String.valueOf(i));

List<String> addSifCandidates = scanReservationsByButtonTitle(
    Prop.getProperty("stg_base_url"), ids, "Add SIF", 1500);

log.info("=== ADD SIF CANDIDATES: " + addSifCandidates + " ===");
```

---

## 31. Map Widgets (Google Maps / Leaflet / Mapbox GL / OpenLayers / Bing Maps)

Convenience wrappers around `com.test.automation.sdk.utility.MapWidgetHelper` for
interacting with map containers detected by `ElementCrawler` (see SDK-USER-GUIDE.md
section 7.1 for detection details and supported providers). Map libraries render
tiles/markers on canvas/WebGL with no stable DOM, so these methods use accessible
attributes (`aria-label`/`alt`/`title`) first, with a pixel-offset fallback for
markers with zero DOM presence.

| Method | Signature | Description |
|---|---|---|
| `waitForMapReady` | `waitForMapReady(WebElement mapContainer, long timeoutSeconds)` | Waits for the map to show rendered content (sized canvas or tile images), then a short settle buffer. |
| `waitForMapReady` | `waitForMapReady(WebElement mapContainer)` | Same as above with a 20-second default timeout. |
| `searchMapAddress` | `boolean searchMapAddress(WebElement mapContainer, String address)` | Types an address into the map's search/autocomplete box and submits it (clicks the first suggestion if a dropdown appears, else ENTER). Returns `false` if no search box was found. |
| `findMapMarkerByLabel` | `WebElement findMapMarkerByLabel(WebElement mapContainer, String label)` | Finds a marker by accessible label (`aria-label`, then `alt`, then `title`). Returns `null` if the marker has no DOM presence (canvas/WebGL-drawn). |

For the pixel-offset fallback (only when `findMapMarkerByLabel` returns `null`), call
`MapWidgetHelper.clickAtPixelOffset(driver, mapContainer, xOffset, yOffset)` directly.

```java
waitForMapReady(mapPage.mapWidget);
searchMapAddress(mapPage.mapWidget, "350 5th Ave, New York, NY");

WebElement pin = findMapMarkerByLabel(mapPage.mapWidget, "My Store");
if (pin != null) {
    pin.click();
} else {
    com.test.automation.sdk.utility.MapWidgetHelper.clickAtPixelOffset(driver, mapPage.mapWidget, 0, 0);
}
```

---

## 32. Shadow DOM (Web Components / Lit / Stencil / Salesforce Lightning-LWC)

Convenience wrapper for resolving elements inside an **open** shadow root, where
a single XPath cannot express the lookup (see SDK-USER-GUIDE.md section 7.2 for
background on why XPath cannot cross a shadow boundary).

| Method | Signature | Description |
|---|---|---|
| `findInShadowDom` | `WebElement findInShadowDom(By hostLocator, String cssSelector)` | Finds the shadow-root HOST element via `hostLocator` in the light DOM, then resolves `cssSelector` inside `host.getShadowRoot()`. Only CSS selectors are supported inside a shadow root -- never XPath. |

```java
WebElement submit = findInShadowDom(By.xpath("//my-form-component"), "button#submit");
submit.click();
```

**Limitation:** *closed* shadow roots (`attachShadow({mode: 'closed'})`) cannot be
discovered or traversed by any script or WebDriver command -- an intentional
browser security boundary with no workaround.

*API Reference updated for SDK v1.9.0*

