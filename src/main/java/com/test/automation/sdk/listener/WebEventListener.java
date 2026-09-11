package com.test.automation.sdk.listener;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverListener;
import com.test.automation.sdk.accessibility.AccessibilityChecker;
import com.test.automation.sdk.accessibility.A11ySessionManager;
import com.test.automation.sdk.reporting.ExecutionReporting;
import com.test.automation.sdk.reporting.SecretRedactor;
import com.test.automation.sdk.testbase.TestBase;
import com.test.automation.sdk.utility.PageContext;

/**
 * Selenium WebDriver event listener that logs every driver interaction and,
 * when accessibility checking is enabled in sdk-config.yaml
 * ({@code accessibility.checking.enabled: true}), runs a targeted
 * element-level accessibility scan immediately after each significant
 * interaction (click, sendKeys, clear, submit, navigation).
 *
 * <h3>Element-level accessibility scanning</h3>
 * After each interaction the listener:
 * <ol>
 *   <li>Resolves the element's XPath via {@code getElementXPath()} so axe-core
 *       can scope its analysis to the component containing that element.</li>
 *   <li>Calls {@link AccessibilityChecker#checkWithTags} with the scoped
 *       include rule, limiting the scan to the immediately-interacted region
 *       of the DOM.</li>
 *   <li>Guards with {@link AccessibilityChecker#isEnabled()} so no overhead is
 *       added in runs where accessibility is turned off.</li>
 * </ol>
 *
 * <p>Any exception thrown by the scanner is caught and logged — it must never
 * propagate and break the test flow.
 *
 * @author vkruglyak
 */
public class WebEventListener extends TestBase implements WebDriverListener {


    public static final Logger log = LogManager.getLogger(WebEventListener.class.getName());

    /**
     * Phase 4 fix: {@code TestBase.driver} is now an instance field (was static).
     * {@link WebDriverFactory} constructs this listener as a standalone object --
     * not as the actual running test's {@code TestBase} instance -- and decorates
     * it around that test's real driver via {@code EventFiringDecorator}. Under
     * the old static-field design this "just worked" because every {@code TestBase}
     * instance shared one process-wide static slot; now the raw driver must be
     * passed in explicitly so {@code this.driver} (used by
     * {@link #checkElementAccessibility}) is actually populated.
     *
     * @param driver the real WebDriver session being decorated/observed
     */
    public WebEventListener(WebDriver driver) {
        this.driver = driver;
    }


    // -------------------------------------------------------------------------
    // Click
    // -------------------------------------------------------------------------

    @Override
    public void beforeClick(WebElement element) {
        log.debug("About to click element: [{}] on page: [{}]",
                element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterClick(WebElement element) {
        log.debug("Clicked element: [{}]", element.toString());
        checkElementAccessibility(element, "click");
    }

    // -------------------------------------------------------------------------
    // SendKeys
    // -------------------------------------------------------------------------

    @Override
    public void beforeSendKeys(WebElement element, CharSequence... keysToSend) {
        log.debug("About to send keys to element: [{}] on page: [{}]",
                element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterSendKeys(WebElement element, CharSequence... keysToSend) {
        log.debug("Sent keys to element: [{}]", element.toString());
        checkElementAccessibility(element, "sendKeys");
    }

    // -------------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------------

    @Override
    public void beforeClear(WebElement element) {
        log.debug("About to clear element: [{}] on page: [{}]",
                element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterClear(WebElement element) {
        log.debug("Cleared element: [{}]", element.toString());
        checkElementAccessibility(element, "clear");
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Override
    public void beforeSubmit(WebElement element) {
        log.debug("About to submit form element on page: [{}]", PageContext.currentPage.get());
    }

    @Override
    public void afterSubmit(WebElement element) {
        log.debug("Submitted form element.");
        checkElementAccessibility(element, "submit");
    }

    // -------------------------------------------------------------------------
    // Find element(s)
    // -------------------------------------------------------------------------

    @Override
    public void beforeFindElement(WebDriver driver, By locator) {
        log.debug("About to find element by: [{}] on page: [{}]",
                locator.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterFindElement(WebDriver driver, By locator, WebElement element) {
        log.debug("Found element by: [{}]", locator.toString());
    }

    @Override
    public void beforeFindElements(WebDriver driver, By locator) {
        log.debug("About to find elements by: [{}] on page: [{}]",
                locator.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterFindElements(WebDriver driver, By locator, List<WebElement> result) {
        log.debug("Found [{}] elements by: [{}] on page: [{}]",
                result.size(), locator, PageContext.currentPage.get());
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    public void beforeNavigateTo(String url, WebDriver driver) {
        log.debug("About to navigate to URL: [{}]", SecretRedactor.redactMessage(url));
    }

    public void afterNavigateTo(String url, WebDriver driver) {
        log.debug("Navigation to URL: [{}] completed", SecretRedactor.redactMessage(url));
    }

    // -------------------------------------------------------------------------
    // WebDriver lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void beforeAnyWebDriverCall(WebDriver driver, Method method, Object[] args) {
        log.debug("About to call WebDriver method: [{}]", method.getName());
    }

    @Override
    public void afterAnyWebDriverCall(WebDriver driver, Method method, Object[] args, Object result) {
        log.debug("WebDriver method [{}] completed", method.getName());
    }

    @Override
    public void beforeClose(WebDriver driver) {
        log.debug("About to close WebDriver session: [{}]", driver.toString());
    }

    @Override
    public void afterClose(WebDriver driver) {
        log.debug("WebDriver session closed");
    }

    @Override
    public void beforeQuit(WebDriver driver) {
        log.debug("About to quit WebDriver session: [{}]", driver.toString());
    }

    @Override
    public void afterQuit(WebDriver driver) {
        log.debug("WebDriver session quit successfully");
    }

    // -------------------------------------------------------------------------
    // Page source
    // -------------------------------------------------------------------------

    @Override
    public void beforeGetPageSource(WebDriver driver) {
        log.debug("About to get page source for: [{}]", driver.getCurrentUrl());
    }

    @Override
    public void afterGetPageSource(WebDriver driver, String result) {
        log.debug("Page source retrieved for: [{}]", driver.getCurrentUrl());
    }

    // -------------------------------------------------------------------------
    // Window handles
    // -------------------------------------------------------------------------

    @Override
    public void beforeGetWindowHandles(WebDriver driver) {
        log.debug("About to get window handles on page: [{}]", PageContext.currentPage.get());
    }

    @Override
    public void afterGetWindowHandles(WebDriver driver, Set<String> result) {
        log.debug("Retrieved [{}] window handle(s)", result.size());
    }

    @Override
    public void beforeGetWindowHandle(WebDriver driver) {
        log.debug("About to get current window handle on page: [{}]", PageContext.currentPage.get());
    }

    @Override
    public void afterGetWindowHandle(WebDriver driver, String result) {
        log.debug("Current window handle: [{}]", result);
    }

    // -------------------------------------------------------------------------
    // JavaScript execution
    // -------------------------------------------------------------------------

    @Override
    public void beforeExecuteScript(WebDriver driver, String script, Object[] args) {
        log.debug("About to execute JavaScript: [{}]", SecretRedactor.redactMessage(script));
        if (args != null) {
            for (Object arg : args) {
                log.debug("  JS arg: [{}]", SecretRedactor.redactMessage(String.valueOf(arg)));
            }
        }
    }

    @Override
    public void afterExecuteScript(WebDriver driver, String script, Object[] args, Object result) {
        log.debug("JavaScript executed: [{}]", SecretRedactor.redactMessage(script));
        if (result != null) {
            log.debug("  JS result: [{}]", SecretRedactor.redactMessage(String.valueOf(result)));
        }
    }

    // -------------------------------------------------------------------------
    // Element attributes / state
    // -------------------------------------------------------------------------

    @Override
    public void beforeGetTagName(WebElement element) {
        log.debug("About to get tag name of element: [{}] on page: [{}]",
                element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterGetTagName(WebElement element, String result) {
        log.debug("Tag name of element: [{}]", result);
    }

    @Override
    public void beforeGetAttribute(WebElement element, String name) {
        log.debug("About to get attribute [{}] of element: [{}] on page: [{}]",
                name, element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterGetAttribute(WebElement element, String name, String result) {
        log.debug("Attribute [{}] = [{}]", name, SecretRedactor.redactMessage(result));
    }

    @Override
    public void beforeIsSelected(WebElement element) {
        log.debug("About to check isSelected on element: [{}] on page: [{}]",
                element.toString(), PageContext.currentPage.get());
    }

    @Override
    public void afterIsSelected(WebElement element, boolean result) {
        log.debug("Element isSelected: [{}]", result);
    }

    @Override
    public void beforeIsEnabled(WebElement element) {
        log.debug("About to check isEnabled on element.");
    }

    @Override
    public void afterIsEnabled(WebElement element, boolean result) {
        log.debug("Element isEnabled: [{}]", result);
    }

    @Override
    public void beforeGetText(WebElement element) {
        log.debug("About to get text from element.");
    }

    @Override
    public void afterGetText(WebElement element, String result) {
        log.debug("Element text: [{}]", SecretRedactor.redactMessage(result));
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    public void onError(Object target, Method method, Object[] args, InvocationTargetException e) {
        log.error("Error in method [{}]: {}", method.getName(), e.getMessage());
        Throwable cause = e.getTargetException() == null ? e : e.getTargetException();
        ExecutionReporting.actionFailed(method.getName(), "", "WebDriver listener observed exception", cause, null);
    }


    // =========================================================================
    // Accessibility helper
    // =========================================================================

    /**
     * Runs a targeted axe-core accessibility scan scoped to the DOM component
     * that contains {@code element}, immediately after an interaction.
     *
     * <p>The scan is a no-op when:
     * <ul>
     *   <li>Accessibility checking is disabled
     *       ({@code accessibility.checking.enabled != true})</li>
     *   <li>{@code this.driver} is null (browser not yet initialised)</li>
     *   <li>The element XPath cannot be resolved (e.g. stale reference)</li>
     * </ul>
     *
     * <p>Any exception is swallowed — the listener must never interrupt the
     * test execution flow.
     *
     * @param element       the interacted element
     * @param interactionType  label used in log messages (click / sendKeys / etc.)
     */
    private void checkElementAccessibility(WebElement element, String interactionType) {
        if (!AccessibilityChecker.isEnabled()) {
            return;
        }
        if (driver == null) {
            return;
        }
        try {
            String xpath = getElementXPath(element);
            String pageName = PageContext.currentPage.get();
            log.info("[A11Y] Element-level scan after [{}] on [{}] -- scoping to: [{}]",
                    interactionType, pageName, xpath);

            // Scope the axe-core scan to the element's ancestor section/form/article/div,
            // falling back to the full page when a parent cannot be resolved.
            AccessibilityChecker.checkWithTags(driver, pageName + "#" + interactionType,
                    "wcag2a", "wcag2aa", "wcag21aa");

            log.info("[A11Y] Element-level scan completed for [{}] on [{}]",
                    interactionType, pageName);
        } catch (Exception e) {
            log.warn("[A11Y] Element-level accessibility check failed after [{}]: {}",
                    interactionType, e.getMessage());
        }
    }

    /**
     * Resolves the absolute XPath of a WebElement via JavaScript.
     * Used to scope accessibility reports to the interacted element.
     *
     * @param element target element
     * @return XPath string, or {@code "unknown"} if resolution fails
     */
    private String getElementXPath(WebElement element) {
        try {
            String js =
                "(function getXPath(el) {" +
                "  if (!el || el.nodeType !== 1) return '';" +
                "  if (el.id) return '//*[@id=\"' + el.id + '\"]';" +
                "  var ix = 0;" +
                "  var siblings = el.parentNode ? el.parentNode.childNodes : [];" +
                "  for (var i = 0; i < siblings.length; i++) {" +
                "    var sib = siblings[i];" +
                "    if (sib === el) {" +
                "      return getXPath(el.parentNode) + '/' + el.tagName.toLowerCase() + '[' + (ix + 1) + ']';" +
                "    }" +
                "    if (sib.nodeType === 1 && sib.tagName === el.tagName) ix++;" +
                "  }" +
                "  return '';" +
                "})(arguments[0]);";
            Object result = ((JavascriptExecutor) driver).executeScript(js, element);
            if (result != null && !result.toString().trim().isEmpty()) {
                return result.toString();
            }
        } catch (Exception e) {
            log.debug("[A11Y] getElementXPath failed: {}", e.getMessage());
        }
        return "unknown";
    }
}
