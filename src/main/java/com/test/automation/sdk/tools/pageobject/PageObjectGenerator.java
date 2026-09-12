package com.test.automation.sdk.tools.pageobject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.test.automation.sdk.tools.crawler.web.ElementCrawler;
import com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo;
import com.test.automation.sdk.testbase.WebDriverFactory;
import com.test.automation.sdk.config.YamlConfigReader;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * ===========================================================================
 * PageObjectGenerator
 * ===========================================================================
 *
 * WHAT IT DOES
 * ------------
 * Given a URL (and optional login credentials), this tool:
 *
 *  1. Launches Chrome and navigates to the target page
 *  2. Uses ElementCrawler to discover ALL interactive elements
 *  3. Groups them: inputs / buttons / links / headings / alerts
 *  4. Generates a COMPLETE, compilable Page Object (.java) file that
 *     matches the exact project conventions in this codebase:
 *       * extends TestBase
 *       * PageFactory.initElements in constructor
 *       * @FindBy(xpath = "...") with XPath priority ladder
 *       * @CacheLookup on stable elements
 *       * Typed action methods per element (type(), click(), select(), etc.)
 *       * Proper package + imports
 *  5. Writes the generated file to:
 *       src/main/java/com/automation/poletop/uiActions/<ClassName>.java
 *     AND a discovery report to:
 *       test-output/crawler/<ClassName>_<timestamp>.txt
 *
 * HOW TO USE -- STANDALONE (recommended)
 * --------------------------------------
 *  Option A - Run main() directly from your IDE (right-click -> Run):
 *
 *    Pass arguments:  <pageClassName>  <url>  [loginEmail]  [loginPassword]
 *
 *    Example:
 *      PoletopDashboardPage
 *      https://poletop-stg.csc.nycnet/#/dashboard
 *      myuser@example.com
 *      MyPassword123
 *
 *  Option B - Maven command line:
 *
 *    mvn exec:java \
 *      -Dexec.mainClass="com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
 *      -Dexec.args="PoletopDashboardPage https://poletop-stg.csc.nycnet/#/dashboard myuser@example.com MyPass"
 *
 *  Option C - System properties (useful in CI or scripts):
 *
 *    mvn exec:java \
 *      -Dexec.mainClass="com.test.automation.sdk.tools.pageobject.PageObjectGenerator" \
 *      -Dpog.className=PoletopDashboardPage \
 *      -Dpog.url=https://poletop-stg.csc.nycnet/#/dashboard \
 *      -Dpog.email=myuser@example.com \
 *      -Dpog.password=MyPassword123
 *
 * HOW TO USE -- PROGRAMMATIC (from a test or setup method)
 * --------------------------------------------------------
 *    PageObjectGenerator gen = new PageObjectGenerator(driver);
 *    gen.generate("EnrollmentPage", "https://poletop-stg.csc.nycnet/#/enroll");
 *
 * WORKFLOW (intended development process)
 * ----------------------------------------
 *   1. Need a new Page Object?  Run this tool with the target URL.
 *   2. Review the generated  <ClassName>.java  in uiActions/.
 *   3. Open the app in DevTools to verify any structural XPath fallbacks.
 *   4. Rename fields if needed, remove duplicates, add business logic.
 *   5. Use the page object in your test class - done.
 *
 * @author vkruglyak
 */
public class PageObjectGenerator {

    private static final Logger log = LogManager.getLogger(PageObjectGenerator.class.getName());

    /**
     * Resolved at runtime -- in priority order:
     *  1. System property  -Dpog.package / -Dpog.outputDir / -Dpog.reportDir
     *  2. sdk-config.yaml  crawler.pageObject.package / outputDir / reportDir
     *  3. Hard-coded defaults (keep Poletop values so existing consumer is unaffected
     *     until they set the YAML keys)
     */
    private static String resolveUiActionsPackage() {
        String v = System.getProperty("pog.package");
        if (v != null && !v.isEmpty()) return v;
        v = YamlConfigReader.get("crawler.pageObject.package");
        if (v != null && !v.isEmpty()) return v;
        return "com.poletop.automation.uiActions";
    }

    private static String resolveOutputSrc() {
        String v = System.getProperty("pog.outputDir");
        if (v != null && !v.isEmpty()) {
            return v.endsWith("/") || v.endsWith("\\") ? v : v + "/";
        }
        v = YamlConfigReader.get("crawler.pageObject.outputDir");
        if (v != null && !v.isEmpty()) return v;
        return "src/main/java/com/poletop/automation/uiActions/";
    }

    private static String resolveOutputReport() {
        String v = System.getProperty("pog.reportDir");
        if (v != null && !v.isEmpty()) {
            return v.endsWith("/") || v.endsWith("\\") ? v : v + "/";
        }
        v = YamlConfigReader.get("crawler.pageObject.reportDir");
        if (v != null && !v.isEmpty()) return v;
        return "test-output/crawler/";
    }

    private final WebDriver      driver;
    private final ElementCrawler crawler;
    private final String         uiActionsPackage;
    private final String         outputSrc;
    private final String         outputReport;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public PageObjectGenerator(WebDriver driver) {
        this.driver          = driver;
        this.crawler         = new ElementCrawler(driver);
        this.uiActionsPackage = resolveUiActionsPackage();
        this.outputSrc       = resolveOutputSrc();
        this.outputReport    = resolveOutputReport();
        log.info("[PageObjectGenerator] package={} outputDir={}", uiActionsPackage, outputSrc);
    }

    // -------------------------------------------------------------------------
    // Standalone entry point
    // -------------------------------------------------------------------------

    /**
     * Run as a standalone program.
     *
     * args[0] = page class name  (e.g. "PoletopDashboardPage")
     * args[1] = URL              (e.g. "https://poletop-stg.csc.nycnet/#/dashboard")
     * args[2] = login email      (optional)
     * args[3] = login password   (optional)
     */
    public static void main(String[] args) {
        // Resolve params from args or -D system properties
        String className = resolve(args, 0, "pog.className", null);
        String url       = resolve(args, 1, "pog.url",       null);
        String email     = resolve(args, 2, "pog.email",     "");
        String password  = resolve(args, 3, "pog.password",  "");

        if (className == null || url == null) {
            System.err.println("Usage: PageObjectGenerator <ClassName> <URL> [email] [password]");
            System.err.println("  or set -Dpog.className=... -Dpog.url=... -Dpog.email=... -Dpog.password=...");
            System.exit(1);
        }

        System.out.println("=".repeat(70));
        System.out.println("  PageObjectGenerator");
        System.out.println("  Class : " + className);
        System.out.println("  URL   : " + url);
        System.out.println("=".repeat(70));

        WebDriver driver = null;
        try {
            driver = WebDriverFactory.getWebDriver("chrome");

            PageObjectGenerator gen = new PageObjectGenerator(driver);

            // Optional login before crawling
            if (!email.isEmpty() && !password.isEmpty()) {
                gen.performLogin(url, email, password);
            }

            gen.generate(className, url);

        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Main generate method
    // -------------------------------------------------------------------------

    /**
     * Crawls {@code url} and writes a Page Object Java file for {@code className}.
     *
     * @param className  simple class name, e.g. "PoletopDashboardPage"
     * @param url        full URL of the page to crawl
     * @return           absolute path of the generated .java file
     */
    public String generate(String className, String url) {
        log.info("Generating page object [" + className + "] from: " + url);

        List<ElementInfo> elements = crawler.crawlUrl(url);
        return writePageObject(className, url, elements);
    }

    /**
     * Crawls the current page (driver already on the right URL).
     */
    public String generateFromCurrentPage(String className) {
        log.info("Generating page object [" + className + "] from current page.");
        List<ElementInfo> elements = crawler.crawlCurrentPage();
        return writePageObject(className, driver.getCurrentUrl(), elements);
    }

    /**
     * Crawls the current page and also checks for an open Angular modal/overlay.
     * If a modal is detected (mat-dialog-container, role=dialog, or cdk-overlay-pane),
     * a second page object is generated for the modal subtree, named className + "_Modal".
     *
     * Use this after a click that does not change the URL but opens a dialog -- the plain
     * generateFromCurrentPage() would crawl the background page instead of the modal.
     *
     * @param className base class name; modal class will be className + "_Modal"
     * @return path of the background page object; modal path is logged separately
     */
    public String generateFromCurrentPageWithModalCheck(String className) {
        log.info("generateFromCurrentPageWithModalCheck: checking for modal on current page");
        String currentUrl = driver.getCurrentUrl();

        // Check for open modal
        List<org.openqa.selenium.WebElement> modalRoots = driver.findElements(
            org.openqa.selenium.By.xpath(
                "//mat-dialog-container | //*[@role='dialog'] | //*[contains(@class,'cdk-overlay-pane')]"));

        if (!modalRoots.isEmpty()) {
            log.info("Modal detected -- generating separate modal page object: " + className + "_Modal");
            List<ElementInfo> modalElements = crawler.crawlSubtree(modalRoots.get(0));
            writePageObject(className + "_Modal", currentUrl + "#modal", modalElements);
        }

        // Always also generate the background page object
        List<ElementInfo> bgElements = crawler.crawlCurrentPage();
        return writePageObject(className, currentUrl, bgElements);
    }

    /**
     * Generate a Page Object from elements already collected by a {@link DataDrivenCrawler}.
     * Use this when you ran {@code DataDrivenCrawler.crawlTestCase()} or
     * {@code DataDrivenCrawler.crawl()} and want to turn the merged result into a .java file.
     *
     * Elements tagged with stepTag / scenarioTag will have origin comments in the output:
     *   // [Visible after: Step 3: Select Category = Noise]
     *   // [Scenario: Case Type = Complaint]
     *
     * @param className page object class name, e.g. "DynamicsCaseFormPage"
     * @param sourceUrl URL that was crawled (for the file header comment)
     * @param elements  merged element list from DataDrivenCrawler
     * @return absolute path of the generated .java file
     */
    public String generateFromElements(String className, String sourceUrl,
                                        List<ElementInfo> elements) {
        log.info("Generating page object [" + className + "] from "
            + elements.size() + " pre-crawled elements.");
        return writePageObject(className, sourceUrl, elements);
    }

    // -------------------------------------------------------------------------
    // Java file writer
    // -------------------------------------------------------------------------

    private String writePageObject(String className,
                                    String sourceUrl,
                                    List<ElementInfo> elements) {

        className = JavaIdentifier.requireTypeName(className, "className");

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Group elements into categories
        Groups g = groupElements(elements);

        // De-duplicate field names across all groups
        NameRegistry names = new NameRegistry();

        // Build file content
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, className, sourceUrl, timestamp);
        appendFields(sb, g, names);
        appendConstructor(sb, className);
        appendActions(sb, g, names);
        appendFooter(sb);

        // Write .java file
        new File(outputSrc).mkdirs();
        String javaPath = outputSrc + className + ".java";

        // Safety check: if a hand-crafted file already exists, write to _Crawled
        // variant instead of overwriting it. Caller can diff and merge manually.
        if (new File(javaPath).exists()) {
            String safeClassName = className.endsWith("_Crawled") ? className : className + "_Crawled";
            log.warn("File already exists: " + javaPath
                + " -- writing to " + safeClassName + ".java to avoid overwrite.");
            className = safeClassName;
            javaPath  = outputSrc + className + ".java";
            // Rebuild with safe class name
            sb.setLength(0);
            appendHeader(sb, className, sourceUrl, timestamp);
            appendFields(sb, g, names);
            appendConstructor(sb, className);
            appendActions(sb, g, names);
            appendFooter(sb);
        }
        writeFile(javaPath, sb.toString());

        // Write discovery report
        new File(outputReport).mkdirs();
        String reportPath = outputReport + className + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".txt";
        writeDiscoveryReport(reportPath, className, sourceUrl, elements);

        System.out.println("\n>>> Page Object  : " + new File(javaPath).getAbsolutePath());
        System.out.println(">>> Report       : " + new File(reportPath).getAbsolutePath() + "\n");

        return new File(javaPath).getAbsolutePath();
    }

    // -------------------------------------------------------------------------
    // Java source sections
    // -------------------------------------------------------------------------

    private void appendHeader(StringBuilder sb, String className,
                               String sourceUrl, String timestamp) {
        sb.append("package ").append(uiActionsPackage).append(";\n\n");
        sb.append("import org.apache.logging.log4j.LogManager;\n");
        sb.append("import org.apache.logging.log4j.Logger;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n");
        sb.append("import org.openqa.selenium.WebElement;\n");
        sb.append("import org.openqa.selenium.support.CacheLookup;\n");
        sb.append("import org.openqa.selenium.support.FindBy;\n");
        sb.append("import org.openqa.selenium.support.PageFactory;\n");
        sb.append("import org.openqa.selenium.support.ui.Select;\n");
        sb.append("import com.test.automation.sdk.testbase.TestBase;\n");
        sb.append("import com.test.automation.sdk.utility.PageContext;\n");
        sb.append("\n");
        sb.append("/**\n");
        sb.append(" * AUTO-GENERATED by PageObjectGenerator\n");
        sb.append(" *\n");
        sb.append(" * Source URL : ").append(sourceUrl).append("\n");
        sb.append(" * Generated  : ").append(timestamp).append("\n");
        sb.append(" *\n");
        sb.append(" * REVIEW BEFORE USE:\n");
        sb.append(" *   - Verify XPaths marked [STRUCTURAL] against DevTools\n");
        sb.append(" *   - Rename fields to match your domain language\n");
        sb.append(" *   - Remove duplicates / hidden elements you don't need\n");
        sb.append(" *   - Add missing business-logic action methods\n");
        sb.append(" *\n");
        sb.append(" * @author PageObjectGenerator (vkruglyak)\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" extends TestBase {\n\n");
        sb.append("    public static final Logger log = LogManager.getLogger(")
          .append(className).append(".class.getName());\n\n");
    }

    private void appendFields(StringBuilder sb, Groups g, NameRegistry names) {

        // Headings
        if (!g.headings.isEmpty()) {
            sb.append("    // -- Page Headings ").append("-".repeat(60)).append("\n\n");
            for (ElementInfo el : g.headings) {
                String field = names.register(el, "heading");
                appendField(sb, el, field, true);
            }
        }

        // Inputs / Textareas / Selects
        if (!g.inputs.isEmpty()) {
            sb.append("\n    // -- Input Fields / Dropdowns ").append("-".repeat(50)).append("\n\n");
            for (ElementInfo el : g.inputs) {
                String field = names.register(el, "field");
                appendField(sb, el, field, false);
            }
        }

        // Buttons
        if (!g.buttons.isEmpty()) {
            sb.append("\n    // -- Buttons ").append("-".repeat(67)).append("\n\n");
            for (ElementInfo el : g.buttons) {
                String field = names.register(el, "button");
                appendField(sb, el, field, false);
            }
        }

        // Links
        if (!g.links.isEmpty()) {
            sb.append("\n    // -- Links ").append("-".repeat(69)).append("\n\n");
            for (ElementInfo el : g.links) {
                if (el.text.isEmpty() && el.href.isEmpty()) continue;
                String field = names.register(el, "link");
                appendField(sb, el, field, false);
            }
        }

        // Alert / message containers
        if (!g.alerts.isEmpty()) {
            sb.append("\n    // -- Messages / Alerts ").append("-".repeat(57)).append("\n\n");
            for (ElementInfo el : g.alerts) {
                String field = names.register(el, "message");
                appendField(sb, el, field, true);
            }
        }

        // Map / canvas widgets (Google Maps, Leaflet, Mapbox GL, OpenLayers, etc.)
        if (!g.maps.isEmpty()) {
            sb.append("\n    // -- Map / Canvas Widgets ").append("-".repeat(52)).append("\n\n");
            for (ElementInfo el : g.maps) {
                String field = names.register(el, "mapWidget");
                appendMapField(sb, el, field);
            }
        }

        // Shadow DOM elements (Web Components / Lit / Stencil / Salesforce LWC)
        if (!g.shadowElements.isEmpty()) {
            sb.append("\n    // -- Shadow DOM Elements ").append("-".repeat(53)).append("\n\n");
            for (ElementInfo el : g.shadowElements) {
                String field = names.register(el, "shadowElement");
                appendShadowMethod(sb, el, field);
            }
        }
    }

    private void appendField(StringBuilder sb, ElementInfo el,
                              String fieldName, boolean cacheLookup) {

        sb.append("    // -- ").append(fieldName).append(" ------------------------------------------\n");
        if (!el.visible) sb.append("    // [HIDDEN ELEMENT]\n");
        if (!el.hasUniqueLocator())
            sb.append("    // [!]  NO UNIQUE LOCATOR - verify manually in DevTools\n");

        // Scenario / step origin tag (set by DataDrivenCrawler)
        if (el.stepTag != null && !el.stepTag.isEmpty()) {
            sb.append("    // [Visible after: ").append(el.stepTag).append("]\n");
        } else if (el.scenarioTag != null && !el.scenarioTag.isEmpty()
                && !el.scenarioTag.equals("Baseline") && !el.scenarioTag.equals("All scenarios")) {
            sb.append("    // [Scenario: ").append(el.scenarioTag).append("]\n");
        }

        sb.append("    //   LOCATOR OPTIONS  (check = unique and safe to use):\n");
        for (Map.Entry<String, String> entry : el.allXpaths.entrySet()) {
            String label = entry.getKey();
            String xpath = entry.getValue().replace("\"", "'");
            boolean good = label.contains(ElementCrawler.LABEL_UNIQUE);
            sb.append("    //   ").append(good ? "[OK] " : "[--] ")
              .append(String.format("%-55s", label))
              .append("  ").append(xpath).append("\n");
        }
        sb.append("    //\n");

        if (cacheLookup) sb.append("    @CacheLookup\n");

        if (el.hasUniqueLocator()) {
            sb.append("    @FindBy(xpath = \"")
              .append(el.xpath.replace("\"", "'")).append("\")\n");
        } else {
            sb.append("    // TODO: replace with a unique xpath verified in DevTools\n");
            sb.append("    // @FindBy(xpath = \"")
              .append(el.xpath.replace("\"", "'")).append("\")\n");
        }
        sb.append("    public WebElement ").append(fieldName).append(";\n\n");
    }

    private void appendMapField(StringBuilder sb, ElementInfo el, String fieldName) {
        sb.append("    // -- ").append(fieldName).append(" ------------------------------------------\n");
        sb.append("    // [MAP WIDGET] Detected provider: ").append(el.mapProvider).append("\n");
        sb.append("    // Map tiles/markers are frequently canvas/WebGL-rendered with no stable DOM --\n");
        sb.append("    // do NOT attempt to generate per-tile/per-marker locators here. Use\n");
        sb.append("    // com.test.automation.sdk.utility.MapWidgetHelper for interaction, e.g.:\n");
        sb.append("    //   MapWidgetHelper.waitForMapReady(driver, ").append(fieldName)
          .append(", Duration.ofSeconds(15));\n");
        sb.append("    //   MapWidgetHelper.searchAddress(driver, ").append(fieldName)
          .append(", \"350 5th Ave, New York, NY\");\n");
        sb.append("    //   WebElement pin = MapWidgetHelper.findMarkerByLabel(driver, ").append(fieldName)
          .append(", \"My Marker\");\n");
        sb.append("    //   if (pin == null) MapWidgetHelper.clickAtPixelOffset(driver, ").append(fieldName)
          .append(", 0, 0); // canvas-only marker fallback\n");
        sb.append("    //\n");

        if (el.hasUniqueLocator()) {
            sb.append("    @FindBy(xpath = \"")
              .append(el.xpath.replace("\"", "'")).append("\")\n");
        } else {
            sb.append("    // TODO: container locator not unique -- verify manually in DevTools\n");
            sb.append("    // @FindBy(xpath = \"")
              .append(el.xpath.replace("\"", "'")).append("\")\n");
        }
        sb.append("    public WebElement ").append(fieldName).append(";\n\n");
    }

    private void appendShadowMethod(StringBuilder sb, ElementInfo el, String fieldName) {
        sb.append("    // -- ").append(fieldName).append(" ------------------------------------------\n");
        sb.append("    // [SHADOW DOM] XPath cannot cross a shadow boundary (W3C spec limitation) --\n");
        sb.append("    // this is resolved via a two-step lookup: light-DOM host xpath, then a\n");
        sb.append("    // CSS selector inside host.getShadowRoot(). Verify the relative CSS below\n");
        sb.append("    // is unique within the shadow root before relying on it.\n");
        sb.append("    //   host xpath     : ").append(el.shadowHostXpath.replace("\"", "'")).append("\n");
        sb.append("    //   relative CSS   : ").append(el.shadowRelativeCss.replace("\"", "'")).append("\n");
        sb.append("    //\n");
        sb.append("    public WebElement ").append(fieldName).append("() {\n");
        sb.append("        WebElement host = driver.findElement(By.xpath(\"")
          .append(el.shadowHostXpath.replace("\"", "'")).append("\"));\n");
        sb.append("        return host.getShadowRoot().findElement(By.cssSelector(\"")
          .append(el.shadowRelativeCss.replace("\"", "'")).append("\"));\n");
        sb.append("    }\n\n");
    }

    private void appendConstructor(StringBuilder sb, String className) {
        sb.append("\n    // -- Constructor ").append("-".repeat(62)).append("\n\n");
        sb.append("    public ").append(className).append("(WebDriver driver) {\n");
        sb.append("        this.driver = driver;\n");
        sb.append("        PageFactory.initElements(driver, this);\n");
        sb.append("        PageContext.currentPage.set(\"").append(className).append("\");\n");
        sb.append("    }\n");
    }

    private void appendActions(StringBuilder sb, Groups g, NameRegistry names) {
        sb.append("\n    // -- Page Actions ").append("-".repeat(61)).append("\n");

        // Getter for page heading
        if (!g.headings.isEmpty()) {
            String f = names.getFirst(g.headings);
            sb.append("\n    public String getPageHeading() {\n");
            sb.append("        waitForElementPresent(").append(f).append(");\n");
            sb.append("        return ").append(f).append(".getText();\n");
            sb.append("    }\n");
        }

        // type() method per visible input / textarea
        for (ElementInfo el : g.inputs) {
            if (!el.visible) continue;
            String f = names.get(el);
            if (f == null) continue;

            String tag  = el.tag.toLowerCase();
            String type = el.type.toLowerCase();

            if (tag.equals("select") || tag.equals("mat-select")) {
                // selectByVisibleText
                String method = "select" + capitalize(f);
                sb.append("\n    public void ").append(method)
                  .append("(String visibleText) {\n");
                sb.append("        log.info(\"Selecting [\" + visibleText + \"] in ")
                  .append(f).append("\");\n");
                if (tag.equals("select")) {
                    sb.append("        waitForElementPresent(driver, ").append(f).append(");\n");
                    sb.append("        new Select(").append(f)
                      .append(").selectByVisibleText(visibleText);\n");
                } else {
                    sb.append("        fluentWaitUntilElementToBeClickable(").append(f).append(");\n");
                    sb.append("        ").append(f).append(".click();\n");
                    sb.append("        driver.findElement(org.openqa.selenium.By.xpath(\n");
                    sb.append("            \"//mat-option[normalize-space(.)='\" + visibleText + \"']\")).click();\n");
                }
                sb.append("    }\n");

            } else if (type.equals("checkbox") || type.equals("radio")) {
                // selectCheckbox / selectRadio
                String method = "select" + capitalize(f);
                sb.append("\n    public void ").append(method).append("() {\n");
                sb.append("        log.info(\"Selecting ").append(f).append("\");\n");
                sb.append("        selectCheckbox(").append(f).append(");\n");
                sb.append("    }\n");

            } else if (type.equals("submit") || type.equals("button")) {
                // skip - handled in buttons
            } else {
                // clearAndType
                String method = "enter" + capitalize(f);
                sb.append("\n    public void ").append(method)
                  .append("(String value) {\n");
                sb.append("        log.info(\"Entering [\" + value + \"] into ")
                  .append(f).append("\");\n");
                sb.append("        waitForElementPresent(driver, ").append(f).append(");\n");
                sb.append("        clearAndType(").append(f).append(", value);\n");
                sb.append("    }\n");
            }
        }

        // click() method per visible button
        for (ElementInfo el : g.buttons) {
            if (!el.visible) continue;
            String f = names.get(el);
            if (f == null) continue;

            String method = "click" + capitalize(f);
            sb.append("\n    public void ").append(method).append("() {\n");
            sb.append("        log.info(\"Clicking ").append(f).append("\");\n");
            sb.append("        fluentWaitUntilElementToBeClickable(").append(f).append(");\n");
            sb.append("        ").append(f).append(".click();\n");
            sb.append("        waitUntillPageLoad();\n");
            sb.append("    }\n");
        }

        // click() method per visible link with text
        for (ElementInfo el : g.links) {
            if (!el.visible || el.text.isEmpty()) continue;
            String f = names.get(el);
            if (f == null) continue;

            String method = "clickOn" + capitalize(f);
            sb.append("\n    public void ").append(method).append("() {\n");
            sb.append("        log.info(\"Clicking link: ").append(el.text).append("\");\n");
            sb.append("        fluentWaitUntilElementToBeClickable(").append(f).append(");\n");
            sb.append("        ").append(f).append(".click();\n");
            sb.append("        waitUntillPageLoad();\n");
            sb.append("    }\n");
        }

        // getText() for alert/message elements
        for (ElementInfo el : g.alerts) {
            String f = names.get(el);
            if (f == null) continue;

            String method = "get" + capitalize(f) + "Text";
            sb.append("\n    public String ").append(method).append("() {\n");
            sb.append("        waitForElementPresent(").append(f).append(");\n");
            sb.append("        return ").append(f).append(".getText();\n");
            sb.append("    }\n");

            String isMethod = "is" + capitalize(f) + "Displayed";
            sb.append("\n    public boolean ").append(isMethod).append("() {\n");
            sb.append("        try {\n");
            sb.append("            waitForElementPresent(").append(f).append(", 10);\n");
            sb.append("            return ").append(f).append(".isDisplayed();\n");
            sb.append("        } catch (Exception e) { return false; }\n");
            sb.append("    }\n");
        }
    }

    private void appendFooter(StringBuilder sb) {
        sb.append("}\n");
    }

    // -------------------------------------------------------------------------
    // Discovery report (human-readable text file)
    // -------------------------------------------------------------------------

    private void writeDiscoveryReport(String path, String className,
                                       String url, List<ElementInfo> elements) {
        try (PrintWriter w = new PrintWriter(new FileWriter(path, false))) {
            w.println("=".repeat(100));
            w.println("  ELEMENT DISCOVERY REPORT  -  " + className);
            w.println("  URL       : " + url);
            w.println("  Elements  : " + elements.size());
            w.println("  Generated : "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            w.println("=".repeat(100));

            for (ElementInfo el : elements) {
                w.println();
                w.println("  +- " + el.tag.toUpperCase() + (el.visible ? "" : "  [HIDDEN]"));
                if (!el.id.isEmpty())              w.println("  |  id              : " + el.id);
                if (!el.name.isEmpty())            w.println("  |  name            : " + el.name);
                if (!el.type.isEmpty())            w.println("  |  type            : " + el.type);
                if (!el.placeholder.isEmpty())     w.println("  |  placeholder     : " + el.placeholder);
                if (!el.formControlName.isEmpty()) w.println("  |  formControlName : " + el.formControlName);
                if (!el.ariaLabel.isEmpty())       w.println("  |  aria-label      : " + el.ariaLabel);
                if (!el.dataTestId.isEmpty())      w.println("  |  data-testid     : " + el.dataTestId);
                if (!el.href.isEmpty())            w.println("  |  href            : " + el.href);
                if (!el.routerLink.isEmpty())      w.println("  |  routerLink      : " + el.routerLink);
                if (!el.cssClass.isEmpty())        w.println("  |  class           : " + el.cssClass);
                if (!el.text.isEmpty())            w.println("  |  text            : " + el.text);
                if (el.isMapWidget)                w.println("  |  mapProvider     : " + el.mapProvider);
                if (el.inShadowDom) {
                    w.println("  |  shadowHostXpath : " + el.shadowHostXpath);
                    w.println("  |  shadowRelativeCss: " + el.shadowRelativeCss);
                }
                if (el.stepTag != null && !el.stepTag.isEmpty())
                    w.println("  |  stepTag         : " + el.stepTag);
                if (el.scenarioTag != null && !el.scenarioTag.isEmpty())
                    w.println("  |  scenarioTag     : " + el.scenarioTag);
                w.println("  |");
                w.println("  |  -- ALL LOCATOR STRATEGIES (" + el.allXpaths.size() + ") ------------------------------");
                for (Map.Entry<String, String> entry : el.allXpaths.entrySet()) {
                    w.println("  |  " + String.format("%-42s", entry.getKey())
                            + "  " + entry.getValue());
                }
                w.println("  |");
                w.println("  |  PRIMARY @FindBy  :  " + el.findByAnnotation());
                w.println("  +------------------------------------------------------------------------------");
            }
            w.println();
            w.println("=".repeat(100));
            w.println("  END OF REPORT");
            w.println("=".repeat(100));
        } catch (IOException e) {
            log.error("Could not write discovery report: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Grouping
    // -------------------------------------------------------------------------

    private Groups groupElements(List<ElementInfo> all) {
        Groups g = new Groups();
        for (ElementInfo el : all) {
            String t = el.tag.toLowerCase();
            if (el.inShadowDom)
                g.shadowElements.add(el);
            else if (el.isMapWidget)
                g.maps.add(el);
            else if (t.matches("h[1-6]"))
                g.headings.add(el);
            else if (t.equals("input") || t.equals("textarea")
                    || t.equals("select") || t.contains("mat-select")
                    || t.contains("mat-checkbox") || t.contains("mat-radio"))
                g.inputs.add(el);
            else if (t.equals("button"))
                g.buttons.add(el);
            else if (t.equals("a"))
                g.links.add(el);
            else
                g.alerts.add(el);
        }
        return g;
    }

    private static class Groups {
        List<ElementInfo> headings       = new ArrayList<>();
        List<ElementInfo> inputs         = new ArrayList<>();
        List<ElementInfo> buttons        = new ArrayList<>();
        List<ElementInfo> links          = new ArrayList<>();
        List<ElementInfo> alerts         = new ArrayList<>();
        List<ElementInfo> maps           = new ArrayList<>();
        List<ElementInfo> shadowElements = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Field name registry - ensures unique camelCase names
    // -------------------------------------------------------------------------

    private static class NameRegistry {
        private final Map<ElementInfo, String> map   = new LinkedHashMap<>();
        private final Map<String, Integer>     counts = new LinkedHashMap<>();

        String register(ElementInfo el, String fallbackSuffix) {
            String base = suggestName(el, fallbackSuffix);
            int cnt = counts.getOrDefault(base, 0) + 1;
            counts.put(base, cnt);
            String name = cnt == 1 ? base : base + cnt;
            map.put(el, name);
            return name;
        }

        String get(ElementInfo el)    { return map.get(el); }

        String getFirst(List<ElementInfo> list) {
            if (list.isEmpty()) return null;
            return map.get(list.get(0));
        }

        private String suggestName(ElementInfo el, String fallback) {
            String raw = "";
            if (!el.id.isEmpty())             raw = el.id;
            else if (!el.formControlName.isEmpty()) raw = el.formControlName;
            else if (!el.name.isEmpty())       raw = el.name;
            else if (!el.ariaLabel.isEmpty())  raw = el.ariaLabel;
            else if (!el.placeholder.isEmpty())raw = el.placeholder;
            else if (!el.dataTestId.isEmpty()) raw = el.dataTestId;
            else if (!el.text.isEmpty())       raw = el.text.split("\\s")[0];
            else raw = el.tag + fallback;

            return JavaIdentifier.toFieldName(raw);
        }
    }

    // -------------------------------------------------------------------------
    // File writer
    // -------------------------------------------------------------------------

    private void writeFile(String path, String content) {
        try (PrintWriter w = new PrintWriter(new FileWriter(path, false))) {
            w.print(content);
            log.info("Written: " + path);
        } catch (IOException e) {
            log.error("Could not write file [" + path + "]: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Login helper (used when page requires auth)
    // -------------------------------------------------------------------------

    private void performLogin(String url, String email, String password) {
        log.info("Performing login for: " + email);
        driver.get(url);
        crawler.waitForPageReady();

        for (String xp : new String[] {
                "//a[contains(@class,'has-icon') and contains(normalize-space(.),'Sign In')]",
                "//a[contains(@href,'authorize.htm') and contains(normalize-space(.),'Sign In')]",
                "//a[normalize-space(.)='Sign In']" }) {
            try {
                List<WebElement> els = driver.findElements(org.openqa.selenium.By.xpath(xp));
                if (!els.isEmpty() && els.get(0).isDisplayed()) {
                    els.get(0).click();
                    crawler.waitForPageReady();
                    break;
                }
            } catch (Exception ignored) {}
        }

        String[][] fieldCandidates = {
            { "//input[@id='gigya-loginID']", "//input[@name='loginID']",
              "//input[@type='email']", "//input[@id='email']",
              "//input[@name='email']", "//input[@type='text'][1]" },
            { "//input[@type='password']", "//input[@id='password']",
              "//input[@name='password']" }
        };
        String[] values = { email, password };
        for (int i = 0; i < fieldCandidates.length; i++) {
            for (String xp : fieldCandidates[i]) {
                try {
                    List<WebElement> els = driver.findElements(org.openqa.selenium.By.xpath(xp));
                    if (!els.isEmpty() && els.get(0).isDisplayed()) {
                        els.get(0).clear();
                        els.get(0).sendKeys(values[i]);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        for (String xp : new String[] {
                "//input[@type='submit']",
                "//button[@type='submit']",
                "//button[contains(translate(.,'LOGIN','login'),'login')]" }) {
            try {
                List<WebElement> els = driver.findElements(org.openqa.selenium.By.xpath(xp));
                if (!els.isEmpty() && els.get(0).isDisplayed()) {
                    els.get(0).click();
                    crawler.waitForPageReady();
                    break;
                }
            } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String resolve(String[] args, int idx, String prop, String def) {
        String sys = System.getProperty(prop);
        if (sys != null && !sys.isEmpty()) return sys;
        if (args != null && args.length > idx && !args[idx].isEmpty()) return args[idx];
        return def;
    }
}
