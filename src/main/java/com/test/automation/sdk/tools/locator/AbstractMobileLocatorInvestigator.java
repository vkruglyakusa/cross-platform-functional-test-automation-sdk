package com.test.automation.sdk.tools.locator;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;
import com.test.automation.sdk.tools.crawler.mobile.MobileElementCrawler;
import com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot;
import com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
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
 * Mobile analogue of {@link AbstractLocatorInvestigator} -- reusable base class for
 * declaratively driving {@link MobileElementCrawler} + {@link MobilePageObjectGenerator}
 * over a single Appium session, for consumer projects targeting Android/iOS.
 *
 * <p>Mirrors the web investigator's role-based fail-fast login model and single-session
 * crawl summary, swapping {@code crawlPage(...)} for {@link #crawlScreen(String, String)}
 * and Selenium's {@code WebDriver} for an {@link AppiumDriver}.
 *
 * <p>Consumer project only needs to override three hooks:
 * <pre>
 *   protected void performLogin(String username, String password) throws Exception
 *   protected boolean isSessionAlive()
 *   protected void defineCrawlSteps() throws Exception
 * </pre>
 *
 * <p>Optional overrides:
 * <ul>
 *   <li>{@link #registerRoles()} -- register credentials for multiple roles
 *       (default: registers "default" role from -Dinv.email / -Dinv.password)</li>
 *   <li>{@link #getPostLoginLandmark()} -- locator waited on after login succeeds</li>
 * </ul>
 *
 * <p>Consumer project usage:
 * <pre>
 *   public class MobileLocatorInvestigator extends AbstractMobileLocatorInvestigator {
 *
 *     {@literal @}Override
 *     protected void performLogin(String username, String password) throws Exception {
 *         // interact with YOUR app's native login screen
 *     }
 *
 *     {@literal @}Override
 *     protected boolean isSessionAlive() {
 *         return !mobileDriver.findElements(By.xpath("//*[@resource-id='main-nav']")).isEmpty();
 *     }
 *
 *     {@literal @}Override
 *     protected void defineCrawlSteps() throws Exception {
 *         crawlScreen("login", "LoginScreen");
 *
 *         if (loginAs("default")) {
 *             crawlScreen("dashboard", "DashboardScreen");
 *         }
 *     }
 *   }
 * </pre>
 *
 * <p>Run via the mobile crawler suite, supplying {@code mobileOS}/{@code deviceName}
 * TestNG parameters (see {@code mobile_crawler_suite.xml}):
 * <pre>
 *   mvn test -Dsurefire.suiteXmlFiles=mobile_crawler_suite.xml \
 *            -Denvironment=stg -DmobileOS=android \
 *            -Dinv.email=&lt;email&gt; -Dinv.password=&lt;password&gt;
 * </pre>
 *
 * @since 1.4.0
 */
public abstract class AbstractMobileLocatorInvestigator extends MobileTestBase {

    /** Mobile page object generator entry point -- static utility, no instance state. */
    protected MobileElementCrawler crawler;

    /** Convenience typed accessor -- same underlying session as the inherited {@code driver} field. */
    protected AppiumDriver mobileDriver;

    // -- Role registry --------------------------------------------------------

    /** role name -> [username, password] */
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
     * @param username credential for the role being logged in
     * @param password credential for the role being logged in
     * @throws Exception if the login interaction itself throws
     */
    protected abstract void performLogin(String username, String password) throws Exception;

    /**
     * Returns {@code true} if a post-login landmark element is visible,
     * confirming the current session is still active.
     */
    protected abstract boolean isSessionAlive();

    /**
     * Defines all screens to crawl in order.
     * Called once from {@link #runFullCrawl()}.
     * Use {@link #crawlScreen(String, String)} and {@link #loginAs(String)}.
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
     */
    protected void registerRoles() {
        registerRole("default",
            System.getProperty("inv.email",    ""),
            System.getProperty("inv.password", ""));
    }

    /**
     * Locator for a reliable element that is only present when a user is
     * logged in. {@link #loginAs(String)} waits for this element after
     * {@link #performLogin(String, String)} returns.
     *
     * <p>Override with an element specific to your application (e.g. an
     * {@code accessibility id} or {@code resource-id}/{@code name} landmark).
     */
    protected By getPostLoginLandmark() {
        return By.xpath("//*[@resource-id='main-content' or @name='main-content']");
    }

    // =========================================================================
    // Lifecycle -- @BeforeClass / @AfterClass / @Test
    // =========================================================================

    @Parameters({"mobileOS", "deviceName"})
    @BeforeClass(alwaysRun = true)
    @Override
    public void setUpDriver(@Optional("android") String mobileOS, @Optional("") String device) {
        super.setUpDriver(mobileOS, device);
        mobileDriver = (AppiumDriver) driver;
        crawler      = new MobileElementCrawler(mobileDriver);
        registerRoles();
        log.info("MobileLocatorInvestigator ready | class=" + getClass().getSimpleName()
                + " | os=" + mobileOsName + " | device=" + deviceName);
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
     * Single test method -- all screens crawled in one continuous Appium session.
     */
    @Test(description = "Full app crawl -- single session, login once per role")
    public void runFullCrawl() throws Exception {
        // Rebind crawler to the current driver in case of a mid-class retry.
        crawler = new MobileElementCrawler(mobileDriver);
        defineCrawlSteps();
    }

    // =========================================================================
    // Role management -- final (not overridable)
    // =========================================================================

    /**
     * Register a named role with credentials. Must be called from
     * {@link #registerRoles()} during {@code @BeforeClass}.
     */
    protected final void registerRole(String role, String username, String password) {
        roles.put(role, new String[]{username, password});
    }

    /**
     * Logs in as the given role using the same fail-fast rules as the web
     * investigator: never retries a role once it has failed or its session
     * has expired mid-crawl.
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

        // Single login attempt (app relaunch/reset is the consumer's responsibility
        // inside performLogin -- mobile apps rarely expose a mid-session role switch).
        log.info("Login attempt for role='" + role + "'");
        try {
            performLogin(creds[0], creds[1]);

            new WebDriverWait(mobileDriver, Duration.ofSeconds(60))
                .until(ExpectedConditions.presenceOfElementLocated(getPostLoginLandmark()));

            loggedIn    = true;
            currentRole = role;
            log.info("Login SUCCESS for role='" + role + "'");
            return true;

        } catch (Exception e) {
            log.error("Login FAILED for role='" + role + "': " + e.getMessage());
            failedRoles.add(role);
            loggedIn = false;
            return false;
        }
    }

    // =========================================================================
    // Crawl helpers
    // =========================================================================

    /**
     * Crawls the current screen if it is not filtered out by {@code -Dinv.page}.
     * Adds the screen name to {@link #crawled} or {@link #skipped} accordingly.
     *
     * <p>Navigate to the screen <em>before</em> calling this method.
     *
     * @param screenKey  key matched against the {@code -Dinv.page} filter (e.g. "dashboard")
     * @param screenName class name written by {@link MobilePageObjectGenerator} (e.g. "DashboardScreen")
     */
    protected final void crawlScreen(String screenKey, String screenName) {
        if (!shouldSkip(screenKey)) {
            log.info("=== Crawling: " + screenName + " ===");
            MobileScreenSnapshot snapshot = crawler.crawlCurrentScreen();
            MobilePageObjectGenerator.writeToFile(screenName, snapshot);
            crawled.add(screenName);
        } else {
            skipped.add(screenName);
        }
    }

    /**
     * Clicks an element by locator and logs the outcome -- the mobile equivalent
     * of the web investigator's {@code clickStep}.
     *
     * @return {@code true} if the element was found and clicked
     */
    protected boolean clickStep(By locator, String description) {
        List<WebElement> els = mobileDriver.findElements(locator);
        if (els.isEmpty()) {
            log.warn(description + " not found -- skipping");
            return false;
        }
        els.get(0).click();
        log.info("Clicked: " + description);
        return true;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Returns {@code true} if NONE of the given keys match the {@code -Dinv.page}
     * filter, meaning this screen should be skipped.
     */
    protected final boolean shouldSkip(String... screenKeys) {
        String filter = System.getProperty("inv.page", "all").toLowerCase().trim();
        if ("all".equals(filter)) return false;
        for (String key : screenKeys) {
            if (filter.contains(key.toLowerCase())) return false;
        }
        return true;
    }
}
