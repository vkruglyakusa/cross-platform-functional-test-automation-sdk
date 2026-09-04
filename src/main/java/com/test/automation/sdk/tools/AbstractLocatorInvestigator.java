package com.test.automation.sdk.tools;

import com.test.automation.sdk.testbase.TestBase;
import com.test.automation.sdk.utility.ElementCrawler;
import com.test.automation.sdk.utility.PageObjectGenerator;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AbstractLocatorInvestigator -- reusable base class for all crawler/page-object
 * generator tools in consumer projects.
 *
 * <p>Provides the complete crawl infrastructure (single session, fail-fast login,
 * role blacklist, nav helpers, crawl summary) so consumer projects only need to
 * override three project-specific hooks:
 *
 * <pre>
 *   protected void performLogin(String email, String password) throws Exception
 *   protected boolean isSessionAlive()
 *   protected void defineCrawlSteps() throws Exception
 * </pre>
 *
 * <p>Optional overrides:
 * <ul>
 *   <li>{@link #registerRoles()} -- register credentials for multiple roles
 *       (default: registers "default" role from -Dinv.email / -Dinv.password)</li>
 *   <li>{@link #getPostLoginLandmark()} -- XPath waited on after login succeeds</li>
 * </ul>
 *
 * <p>Consumer project usage:
 * <pre>
 *   public class LocatorInvestigator extends AbstractLocatorInvestigator {
 *
 *     {@literal @}Override
 *     protected void performLogin(String email, String password) throws Exception {
 *         // interact with YOUR app's login form
 *     }
 *
 *     {@literal @}Override
 *     protected boolean isSessionAlive() {
 *         return !driver.findElements(By.xpath("//nav[@id='main-nav']")).isEmpty();
 *     }
 *
 *     {@literal @}Override
 *     protected void defineCrawlSteps() throws Exception {
 *         driver.get(baseURL);
 *         crawler.waitForPageReady();
 *         crawlPage("login", "LoginPage");
 *
 *         if (loginAs("default")) {
 *             crawlPage("dashboard", "DashboardPage");
 *         }
 *     }
 *   }
 * </pre>
 *
 * @since 1.3.0
 */
public abstract class AbstractLocatorInvestigator extends TestBase {

    /** Page object generator -- rebound at start of {@link #runFullCrawl()} */
    protected PageObjectGenerator generator;

    /** Element crawler -- rebound at start of {@link #runFullCrawl()} */
    protected ElementCrawler crawler;

    // -- Role registry --------------------------------------------------------

    /** role name -> [email, password] */
    private final Map<String, String[]> roles = new LinkedHashMap<String, String[]>();

    private String  currentRole = "";
    private boolean loggedIn    = false;

    /** Roles that failed login -- never retried (account lockout protection). */
    private final Set<String> failedRoles = new HashSet<String>();

    // -- Crawl summary --------------------------------------------------------

    protected final List<String> crawled = new ArrayList<String>();
    protected final List<String> skipped = new ArrayList<String>();

    // =========================================================================
    // Abstract hooks -- consumer project must implement these
    // =========================================================================

    /**
     * Perform the actual login interaction for this application.
     * Called at most once per role per run. Must NOT retry on failure.
     *
     * @param email    credential for the role being logged in
     * @param password credential for the role being logged in
     * @throws Exception if the login interaction itself throws
     */
    protected abstract void performLogin(String email, String password) throws Exception;

    /**
     * Returns {@code true} if a post-login landmark element is visible,
     * confirming the current session is still active.
     */
    protected abstract boolean isSessionAlive();

    /**
     * Defines all pages to crawl in order.
     * Called once from {@link #runFullCrawl()}.
     * Use {@link #crawlPage(String, String)}, {@link #loginAs(String)},
     * {@link #openNavDropdownAndClick(String, String)}, and {@link #clickStep(By, String)}.
     *
     * @throws Exception if any navigation step throws
     */
    protected abstract void defineCrawlSteps() throws Exception;

    // =========================================================================
    // Optional overrides
    // =========================================================================

    /**
     * Register credentials for all roles used by this application.
     * Default implementation registers a single "default" role from
     * {@code -Dinv.email} / {@code -Dinv.password}.
     *
     * <p>Override to add additional roles:
     * <pre>
     *   {@literal @}Override
     *   protected void registerRoles() {
     *       registerRole("admin",
     *           System.getProperty("inv.email",              ""),
     *           System.getProperty("inv.password",           ""));
     *       registerRole("franchisee",
     *           System.getProperty("inv.franchisee.email",   ""),
     *           System.getProperty("inv.franchisee.password",""));
     *   }
     * </pre>
     */
    protected void registerRoles() {
        registerRole("default",
            System.getProperty("inv.email",    ""),
            System.getProperty("inv.password", ""));
    }

    /**
     * XPath expression for a reliable element that is only present when a user
     * is logged in.  {@link #loginAs(String)} waits for this element after
     * {@link #performLogin(String, String)} returns.
     *
     * <p>Override with an element specific to your application.
     */
    protected String getPostLoginLandmark() {
        return "//*[@id='main-content'] | //nav[@id='main-nav'] | //div[@id='dashboard']";
    }

    // =========================================================================
    // Lifecycle -- @BeforeClass / @AfterClass / @Test
    // =========================================================================

    @Parameters({"environment", "browserName"})
    @BeforeClass
    @Override
    public void setUp(@Optional("") String environment,
                      @Optional("") String browserName) throws IOException {
        String env  = environment.isEmpty() ? "stg"    : environment;
        String brws = browserName.isEmpty()  ? "chrome" : browserName;
        setXlsName(env);
        setBaseUrl(env);
        browser = brws;
        initialization(brws, baseURL);
        generator = new PageObjectGenerator(driver);
        crawler   = new ElementCrawler(driver);
        registerRoles();
        log.info("LocatorInvestigator ready | class=" + getClass().getSimpleName()
                + " | env=" + env + " | url=" + baseURL);
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void afterClass() throws IOException {
        log.info("=== Crawl summary: crawled=" + crawled
                + " | skipped=" + skipped
                + " | failedRoles=" + failedRoles + " ===");
        closeBrowser();
    }

    /**
     * Single test method -- all pages crawled in one continuous browser session.
     * {@code @BeforeMethod} from TestBase fires exactly once (one {@code @Test}),
     * so driver churn is eliminated.
     */
    @Test(description = "Full site crawl -- single session, login once per role")
    public void runFullCrawl() throws Exception {
        // Rebind generator/crawler to the current driver.
        // TestBase @BeforeMethod fires once before this test and creates a fresh
        // WebDriver session -- reinitialising here ensures we never use a stale reference.
        generator = new PageObjectGenerator(driver);
        crawler   = new ElementCrawler(driver);
        defineCrawlSteps();
    }

    // =========================================================================
    // Role management -- final (not overridable)
    // =========================================================================

    /**
     * Register a named role with credentials.  Must be called from
     * {@link #registerRoles()} during {@code @BeforeClass}.
     */
    protected final void registerRole(String role, String email, String password) {
        roles.put(role, new String[]{email, password});
    }

    /**
     * Logs in as the given role using fail-fast rules:
     * <ol>
     *   <li>Role already in {@code failedRoles} -> return {@code false} immediately.</li>
     *   <li>No credentials registered for role -> blacklist + return {@code false}.</li>
     *   <li>Session alive for same role -> reuse, no network call.</li>
     *   <li>Session expired -> blacklist + return {@code false} (never retry).</li>
     *   <li>Switching roles -> logout, then attempt single login.</li>
     *   <li>Login attempt fails -> blacklist + return {@code false}.</li>
     * </ol>
     *
     * @param role name as registered via {@link #registerRole(String, String, String)}
     * @return {@code true} if session is ready for the given role
     */
    protected final boolean loginAs(String role) {
        if (failedRoles.contains(role)) {
            log.warn("Role '" + role + "' previously failed -- skipping (no retry, account protection)");
            return false;
        }

        String[] creds = roles.get(role);
        if (creds == null || creds[0].isEmpty()) {
            log.warn("No credentials for role '" + role
                    + "' -- register via -Dinv.* system properties");
            failedRoles.add(role);
            return false;
        }

        // Reuse existing session
        if (loggedIn && role.equals(currentRole)) {
            if (isSessionAlive()) {
                log.info("Reusing active session for role='" + role + "'");
                return true;
            }
            log.warn("Session for role='" + role + "' expired mid-crawl -- blacklisting (no retry)");
            loggedIn = false;
            failedRoles.add(role);
            return false;
        }

        // Switch role -- logout first
        if (loggedIn && !role.equals(currentRole)) {
            log.info("Switching role: '" + currentRole + "' -> '" + role + "'");
            performLogout();
        }

        // Single login attempt
        log.info("Login attempt for role='" + role + "' email='" + creds[0] + "'");
        try {
            driver.get(baseURL);
            crawler.waitForPageReady();
            performLogin(creds[0], creds[1]);

            new WebDriverWait(driver, Duration.ofSeconds(60))
                .until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(getPostLoginLandmark())));

            loggedIn    = true;
            currentRole = role;
            log.info("Login SUCCESS for role='" + role + "' | URL: " + driver.getCurrentUrl());
            return true;

        } catch (Exception e) {
            log.error("Login FAILED for role='" + role + "': " + e.getMessage());
            failedRoles.add(role);
            loggedIn = false;
            return false;
        }
    }

    private void performLogout() {
        try {
            List<WebElement> links = driver.findElements(By.xpath(
                "//a[contains(normalize-space(.),'Sign Out')"
                + " or contains(normalize-space(.),'Log Out')"
                + " or contains(normalize-space(.),'Logout')]"));
            if (!links.isEmpty()) {
                links.get(0).click();
                crawler.waitForPageReady();
            }
        } catch (Exception ignored) {}
        loggedIn = false;
    }

    // =========================================================================
    // Crawl helpers
    // =========================================================================

    /**
     * Crawls the current page if it is not filtered out by {@code -Dinv.page}.
     * Adds the page name to {@link #crawled} or {@link #skipped} accordingly.
     *
     * <p>Navigate to the page <em>before</em> calling this method.
     *
     * @param pageKey  key matched against the {@code -Dinv.page} filter (e.g. "dashboard")
     * @param pageName class name written by {@link PageObjectGenerator} (e.g. "DashboardPage")
     */
    protected final void crawlPage(String pageKey, String pageName) {
        if (!shouldSkip(pageKey)) {
            log.info("=== Crawling: " + pageName + " ===");
            generator.generateFromCurrentPage(pageName);
            crawled.add(pageName);
        } else {
            skipped.add(pageName);
        }
    }

    // =========================================================================
    // Navigation helpers
    // =========================================================================

    /**
     * Opens a nav toggle by element id and clicks a sub-menu item by label text.
     * Logs UNIQUE / NOT UNIQUE candidates for every discovered attribute strategy.
     *
     * @param toggleId   {@code id} attribute of the toggle anchor
     * @param itemLabel  text fragment to match in the sub-menu item
     * @return {@code true} if the item was found and clicked
     */
    protected boolean openNavDropdownAndClick(String toggleId, String itemLabel) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[@id='" + toggleId + "']")));
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0,0);");
            driver.findElement(By.xpath("//a[@id='" + toggleId + "']")).click();
            new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//a[@id='" + toggleId + "']/ancestor::li[1]//ul")));
        } catch (Exception e) {
            log.warn("Nav toggle [" + toggleId + "] not found or dropdown did not open");
            return false;
        }

        String parentXpath = "//a[@id='" + toggleId + "']/ancestor::li[1]";
        List<WebElement> items = driver.findElements(
            By.xpath(parentXpath + "//ul//li"));
        if (items.isEmpty()) {
            items = driver.findElements(
                By.xpath("//*[contains(@class,'dropdown-menu')]//li[a]"));
        }

        log.info("=== Sub-menu items under '" + toggleId + "' (" + items.size() + ") ===");
        for (WebElement li : items) {
            log.info("  item: '" + li.getText().trim() + "'");
        }
        if (items.isEmpty()) {
            log.warn("No sub-menu items found under toggle [" + toggleId + "]");
            return false;
        }
        return iterateAndClick(items, itemLabel);
    }

    private boolean iterateAndClick(List<WebElement> items, String labelContains) {
        for (WebElement li : items) {
            List<WebElement> anchors = li.findElements(By.tagName("a"));
            WebElement target = anchors.isEmpty() ? li : anchors.get(0);
            String text = target.getText().trim();
            if (!text.toLowerCase().contains(labelContains.toLowerCase())) continue;

            String href       = safeAttr(target, "href");
            String routerLink = safeAttr(target, "routerlink");
            String id         = safeAttr(target, "id");

            String[][] strategies = {
                { "id",            !id.isEmpty()
                    ? "//a[@id='" + id + "']" : null },
                { "routerlink",    !routerLink.isEmpty()
                    ? "//a[@routerlink='" + routerLink + "']" : null },
                { "href",          !href.isEmpty() && !href.contains("javascript")
                    ? "//a[contains(@href,'" + lastSegment(href) + "')]" : null },
                { "text-exact",    "//a[normalize-space(.)='" + text + "']" },
                { "text-contains", "//a[contains(normalize-space(.),'" + text + "')]" }
            };

            log.info("--- Locator candidates for '" + text + "' ---");
            for (String[] s : strategies) {
                if (s[1] == null) continue;
                int count = driver.findElements(By.xpath(s[1])).size();
                String marker = count == 1 ? "UNIQUE \u2713" : "NOT UNIQUE (" + count + ")";
                log.info("  [" + marker + "] [" + s[0] + "] " + s[1]);
                if (count == 1) {
                    log.info("CONFIRMED @FindBy(xpath = \"" + s[1] + "\")");
                    break;
                }
            }

            target.click();
            crawler.waitForPageReady();
            log.info("Navigated to: " + driver.getCurrentUrl());
            return true;
        }
        log.warn("Item not found containing: '" + labelContains + "'");
        return false;
    }

    /**
     * Clicks an element by locator and waits for the page to be ready.
     *
     * @return {@code true} if the element was found and clicked
     */
    protected boolean clickStep(By locator, String description) {
        List<WebElement> els = driver.findElements(locator);
        if (els.isEmpty()) {
            log.warn(description + " not found -- skipping");
            return false;
        }
        els.get(0).click();
        crawler.waitForPageReady();
        log.info("Clicked: " + description + " | URL: " + driver.getCurrentUrl());
        return true;
    }

    /**
     * Logs all top-level nav items and their ids -- useful for discovering
     * toggle ids to pass to {@link #openNavDropdownAndClick(String, String)}.
     *
     * @param navContainerXpath XPath to the nav container element
     */
    protected void logNavStructure(String navContainerXpath) {
        List<WebElement> items = driver.findElements(
            By.xpath(navContainerXpath + "//ul//li[not(ancestor::li)]"));
        log.info("=== Nav structure for role='" + currentRole
                + "' (" + items.size() + " top-level items) ===");
        for (WebElement li : items) {
            String text = li.getText().replace("\n", " ").trim();
            List<WebElement> link = li.findElements(By.tagName("a"));
            String id = link.isEmpty() ? "" : link.get(0).getAttribute("id");
            log.info("  nav-item id='" + id + "' text='" + text + "'");
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Returns {@code true} if NONE of the given keys match the {@code -Dinv.page}
     * filter, meaning this page should be skipped.
     */
    protected final boolean shouldSkip(String... pageKeys) {
        String filter = System.getProperty("inv.page", "all").toLowerCase().trim();
        if ("all".equals(filter)) return false;
        for (String key : pageKeys) {
            if (filter.contains(key.toLowerCase())) return false;
        }
        return true;
    }

    private String safeAttr(WebElement el, String attr) {
        try {
            String v = el.getAttribute(attr);
            return v != null ? v.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String lastSegment(String href) {
        String clean = href.replaceAll("[#?].*", "");
        String[] parts = clean.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) return parts[i];
        }
        return href;
    }
}
