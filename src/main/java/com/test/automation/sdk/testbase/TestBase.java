package com.test.automation.sdk.testbase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.openqa.selenium.By;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import com.test.automation.sdk.listener.WebEventListener;
import com.test.automation.sdk.utility.Excel_Reader;
import com.test.automation.sdk.utility.QueryExcelFile;
import com.test.automation.sdk.utility.YamlConfigReader;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.test.automation.sdk.utility.reports.ExtentManager;
import com.test.automation.sdk.utility.reports.ExtentTestManager;
import com.test.automation.sdk.utility.mailinator.MailinatorEmailReader;
import com.test.automation.sdk.accessibility.AccessibilityChecker;
import com.test.automation.sdk.accessibility.A11ySessionManager;
import com.test.automation.sdk.accessibility.report.AllureA11yReporter;
import com.test.automation.sdk.accessibility.report.CompositeReporter;
import com.test.automation.sdk.accessibility.report.Slf4jReporter;

/**
 *
 * @author vkruglyak
 *
 */

public class TestBase {

    public static final Logger log = LogManager.getLogger(TestBase.class.getName());
    private static final int DEFAULT_WAIT_SECONDS = 60;
    private static final ThreadLocal<String> currentTestCaseName = new ThreadLocal<String>();
	public static WebDriver driver;
	Object[][] excelData;
	static Excel_Reader testData;
	public File f;
	public FileInputStream FI;
	public WebEventListener eventListener;
	public Properties Prop = new Properties();
	public static ExtentReports extent;
	public static ExtentTest test;
	public ITestResult result;
	public static String baseURL;
	public String browser;
	protected String ExcelName;
	public static int testRetryCount = 0;
	ITestContext context;
	public static String parentWindow;

	/**
	 * Sets the current data-driven test case name. Call this at the start of every
	 * @Test method that uses a DataProvider, passing the testCaseName column value.
	 * The Listener picks it up to rename the test in XML reports and screenshots.
	 *
	 * Example: setCurrentTestCaseName(testCaseName);
	 *
	 * @param name the human-readable test case name from the Excel data row
	 */
	public static void setCurrentTestCaseName(String name) {
		currentTestCaseName.set(name);
	}

	/**
	 * Returns the current data-driven test case name, or null if not set.
	 *
	 * @return current test case name or null
	 */
	public static String getCurrentTestCaseName() {
		return currentTestCaseName.get();
	}


	/**
	 * Creates a new test base instance and configures Log4j.
	 */
    public TestBase() {
        configureLogging();
	}

	/**
	 * Configures Log4j from the SDK logging properties file.
	 */
	private void configureLogging() {
		File file = new File(SdkConfig.LOG4J_PROPERTIES);
		org.apache.logging.log4j.core.LoggerContext context =
				(org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
		context.setConfigLocation(file.toURI());
	}

	/**
	 * Framework-internal -- loads SDK config properties. Called automatically by setUp and getData.
	 * Loads the SDK configuration properties into the {@code Prop} field.
	 *
	 * @throws IOException if the configuration file cannot be read
	 */
	protected void loadData() throws IOException {
		f = new File(SdkConfig.CONFIG_PROPERTIES);
		FI = new FileInputStream(f);
		Prop.load(FI);

	}

	/**
	 * Clicks the provided element using JavaScript.
	 *
	 * @param element target element
	 */
	public void clickOnElementbyJavaScript(WebElement element) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("arguments[0].click();", element);
		log.info("Successfully Clicked WebElement: " + element);

	}

	/**
	 * Framework-internal -- initializes the WebDriver session. Called automatically by setUp; do not call directly from tests.
	 * Method will be initialize session of WebDriver and Web Browser based on
	 * supplied parameters or parameters readed from configuration file
	 *
	 * @param browser
	 * @param baseUrl
	 * @throws IOException
	 */
	protected void initialization(String browser, String baseUrl) throws IOException {
		loadData();
		configureLogging();
		String resolvedBrowser = browser.isEmpty() ? Prop.getProperty("browser") : browser;
		String resolvedUrl = baseUrl.isEmpty() ? Prop.getProperty("tst_base_url") : baseUrl;
		driver = WebDriverFactory.getWebDriver(resolvedBrowser);
		TestBase.baseURL = resolvedUrl;
		this.baseURL = resolvedUrl;
		this.browser = resolvedBrowser;
		getUrl(resolvedUrl);
		log.info("[TestBase] Session initialized -- browser={} url={}", resolvedBrowser, resolvedUrl);
	}



	/**
	 * Navigates to the provided URL, maximizes the browser window, and waits for
	 * the page to finish loading.
	 *
	 * @param url target URL
	 */
	public void getUrl(String url) {
		log.info("navigating to :-" + url);
		driver.get(url);
		driver.manage().window().maximize();
		waitUntillPageLoad();
	}

	/**
	 * Loads test data from an explicit Excel file name instead of the session-level
	 * {@code ExcelName} field.
	 *
	 * @param ExcelNameFormTest explicit Excel file name
	 * @param sheetname sheet name to read
	 * @return Excel test data
	 * @throws IOException if the file cannot be read
	 */
	public Object[][] getData(String ExcelNameFormTest, String sheetname) throws IOException {
		loadData();
		log.info("read excel file - " + ExcelNameFormTest + "and sheet name is " + sheetname);
		String path = System.getProperty("user.dir") + Prop.getProperty("testDataDir") + ExcelNameFormTest;
		excelData = Excel_Reader.getDataFromSheet(path, sheetname);
		return excelData;
	}

	/**
	 * Method is reading excel data file and return array of testing data
	 *
	 * @param ExcelName
	 * @param sheetname
	 * @return
	 * @throws IOException
	 */
	public Object[][] getData(String sheetname) throws IOException {
		loadData();
		log.info("read excel file - " + ExcelName + " and sheet name is " + sheetname);
		String path = System.getProperty("user.dir") + Prop.getProperty("testDataDir") + ExcelName;
		excelData = Excel_Reader.getDataFromSheet(path, sheetname);
		return excelData;
	}

	/**
	 * Framework-internal -- prefer waitForElementPresent(driver, element) instead.
	 * Explicitly waits up to the specified timeout for an element to become visible.
	 *
	 * @param driver active driver
	 * @param timeOutInSeconds timeout in seconds
	 * @param element target element
	 */
	protected void waitForElement(WebDriver driver, int timeOutInSeconds, WebElement element) {
		log.info("wait for element ...");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeOutInSeconds));
		wait.until(ExpectedConditions.visibilityOf(element));
	}

	/**
	 * Captures a screenshot into the configured screenshots output directory.
	 *
	 * @param name screenshot name prefix
	 */
	public void getScreenShot(String name) {

		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat formater = new SimpleDateFormat("dd_MM_yyyy_hh_mm_ss");
		log.info("Getting screenshot ...");

		try {
			File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			File reportDirectory = getScreenshotOutputDirectory();
			File destFile = new File(reportDirectory, name + "_" + formater.format(calendar.getTime()) + ".png");
			FileUtils.copyFile((File) scrFile, destFile);
			Reporter.log("<a href='" + destFile.getAbsolutePath() + "'> <img src='" + destFile.getAbsolutePath()
			+ "' height='100' width='100'/> </a>");
		} catch (IOException e) {
			log.error("[TestBase] Failed to capture screenshot {}", name, e);
		}
	}

	/**
	 * Framework-internal debug utility. Not intended for direct use in test classes.
	 * Method will highlite specified element
	 *
	 * @param driver
	 * @param element
	 * @throws InterruptedException
	 */
	protected static void highlightMe(WebDriver driver, WebElement element) throws InterruptedException {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("arguments[0].style.border='4px solid yellow'", element);
	}

	/**
	 * Returns an iterator over all open browser window handles.
	 *
	 * @return window handle iterator
	 */
	public Iterator<String> getAllWindows() {
		Set<String> windows = driver.getWindowHandles();
		Iterator<String> itr = windows.iterator();
		return itr;
	}

	/**
	 * Framework-internal -- captures a screenshot for a test result. Called by the test listener on failure.
	 * Captures a screenshot for a TestNG result into the configured screenshots output directory.
	 *
	 * @param driver active driver
	 * @param result current test result
	 * @param folderName logical report folder name
	 */
	protected void getScreenShot(WebDriver driver, ITestResult result, String folderName) {
		if (driver == null) return;
		try { driver.getWindowHandles(); } catch (Exception e) {
			log.warn("getScreenShot skipped -- session no longer active");
			return;
		}
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat formater = new SimpleDateFormat("dd_MM_yyyy_hh_mm_ss");

		String testCaseName = getCurrentTestCaseName();
		String methodName = (testCaseName != null && !testCaseName.trim().isEmpty())
				? sanitizeFileName(testCaseName)
				: result.getName();

		File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		try {
			File reportDirectory = getScreenshotOutputDirectory();
			File folder = new File(reportDirectory, folderName);
			ensureDirectoryExists(folder);
			File destFile = new File(folder, methodName + "_" + formater.format(calendar.getTime()) + ".png");
			FileUtils.copyFile(scrFile, destFile);

			Reporter.log("<a href='" + destFile.getAbsolutePath() + "'> <img src='" + destFile.getAbsolutePath()
			+ "' height='100' width='100'/> </a>");
			String screenshotPath = destFile.getAbsolutePath();
			ExtentTestManager.getTest().fail("Screenshot",
					MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());

		} catch (IOException e) {
			log.error("[TestBase] Failed to capture test result screenshot {}", methodName, e);
		}
	}

	/**
	 * Saves the current page DOM (page source) to a .html file in test-output/dom-dumps/.
	 * Called automatically by the Listener on test failure.
	 * Also available to test authors for manual capture.
	 *
	 * @param driver active WebDriver session
	 * @param testName file name prefix (test case name or method name)
	 */
	public void saveDomDump(WebDriver driver, String testName) {
		if (driver == null) return;
		try {
			// Try JS outerHTML first -- captures live Angular/React rendered DOM.
			// Fall back to driver.getPageSource() if JS execution fails.
			String pageSource = "";
			try {
				pageSource = String.valueOf(
					((org.openqa.selenium.JavascriptExecutor) driver)
						.executeScript("return document.documentElement.outerHTML;"));
			} catch (Exception jsError) {
				log.warn("[TestBase] JS DOM capture failed, falling back to getPageSource(): {}", jsError.getMessage());
				try {
					pageSource = driver.getPageSource();
				} catch (Exception fallbackError) {
					log.warn("[TestBase] getPageSource() also failed: {}", fallbackError.getMessage());
				}
			}

			String currentUrl = "";
			try { currentUrl = driver.getCurrentUrl(); } catch (Exception ignore) { /* session may be closing */ }

			String timestamp = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss").format(new Date());
			String safeName  = sanitizeFileName(testName);

			// DOM dump goes in the same folder as screenshots so they are always paired.
			File dir = getScreenshotOutputDirectory();
			ensureDirectoryExists(dir);

			StringBuilder dom = new StringBuilder();
			dom.append("<!-- DOM captured at the moment of failure -->\n");
			dom.append("<!-- Test: ").append(testName).append(" -->\n");
			dom.append("<!-- URL:  ").append(currentUrl).append(" -->\n");
			dom.append("<!-- Time: ").append(timestamp).append(" -->\n\n");
			dom.append(pageSource == null ? "" : pageSource);

			File dumpFile = new File(dir, safeName + "_" + timestamp + "_DOM.html");
			FileUtils.writeStringToFile(dumpFile, dom.toString(), "UTF-8");

			Reporter.log("<a href='file:///" + dumpFile.getAbsolutePath().replace("\\", "/")
				+ "' target='_blank'>DOM snapshot: " + dumpFile.getName() + "</a>");
			log.info("[TestBase] DOM dump saved: {}", dumpFile.getAbsolutePath());
		} catch (Exception e) {
			log.warn("[TestBase] Failed to save DOM dump for {}: {}", testName, e.getMessage());
		}
	}

	/**
	 * Framework-internal -- captures a screenshot and returns its absolute path. Used by reporting hooks.
	 * Captures a screenshot and returns the absolute path to the saved file.
	 *
	 * @param fileName screenshot name prefix
	 * @return absolute screenshot path or empty string when capture fails
	 */
	protected String captureScreen(String fileName) {
		if (fileName == null || fileName.isEmpty()) {
			fileName = "blank";
		}
		log.info("Capturing screenshot .... ");
		File destFile = null;
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat formater = new SimpleDateFormat("dd_MM_yyyy_hh_mm_ss");

		File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

		try {
			File reportDirectory = getScreenshotOutputDirectory();
			destFile = new File(reportDirectory, fileName + "_" + formater.format(calendar.getTime()) + ".png");
			FileUtils.copyFile(scrFile, destFile);
			Reporter.log("<a href='" + destFile.getAbsolutePath() + "'> <img src='" + destFile.getAbsolutePath()
			+ "' height='100' width='100'/> </a>");
		} catch (IOException e) {
			log.error("[TestBase] Failed to capture screenshot {}", fileName, e);
			return "";
		}
		return destFile == null ? "" : destFile.toString();
	}

	/**
	 * Framework-internal -- logs the TestNG result to the Extent report. Called by the test listener.
	 * Logs the current TestNG result to the Extent report.
	 *
	 * @param result current test result
	 */
	protected void getresult(ITestResult result) {
		if (result.getStatus() == ITestResult.SUCCESS) {
			ExtentTestManager.getTest().log(Status.PASS, "Test passed");
		} else if (result.getStatus() == ITestResult.SKIP) {
			ExtentTestManager.getTest().log(Status.SKIP,
					result.getName() + " test is skipped and skip reason is:-" + result.getThrowable());
		} else if (result.getStatus() == ITestResult.FAILURE) {
			ExtentTestManager.getTest().log(Status.FAIL, result.getName() + " test is failed" + result.getThrowable());
		} else if (result.getStatus() == ITestResult.STARTED) {
			ExtentTestManager.startTest(result.getMethod().getMethodName() + " test is started");
		}
	}

	/**
	 * Framework-internal TestNG lifecycle hook. Do not call directly.
	 * TestNG setup hook that resolves environment data and starts the browser session for the test class.
	 *
	 * @param environment target environment
	 * @param browserName browser name override
	 * @throws IOException if configuration loading fails
	 */
	@Parameters({ "environment", "browserName" })
	@BeforeClass
	protected void setUp(@Optional("") String environment, @Optional("") String browserName) throws IOException {
		if (environment.isEmpty()) {
			setXlsName("tst");
			setBaseUrl("tst");
		} else {
			setXlsName(environment);
			setBaseUrl(environment);
		}
		// added for retry
		browser = browserName;
		initialization(browserName, baseURL);
	}

	/**
	 * Framework-internal TestNG lifecycle hook. Do not call directly.
	 * TestNG teardown hook that closes the browser and flushes reporting.
	 *
	 * @throws IOException if browser shutdown triggers an I/O failure
	 */
	@AfterClass(alwaysRun = true)
	protected void afterClass() throws IOException {
		closeBrowser();
	}

	/**
	 * Framework-internal TestNG lifecycle hook. Do not call directly.
	 * TestNG hook that reinitializes the browser when a retry attempt is detected.
	 *
	 * @param result test method metadata
	 * @throws IOException if reinitialization fails
	 */
	@BeforeMethod()
	protected void beforeMethod(Method result) throws IOException {
		testRetryCount++;
		if (testRetryCount > 1 && result.isAnnotationPresent(Test.class)) {
			log.info("Retry count: " + (testRetryCount - 1));
			closeBrowser();
			initialization(browser, baseURL);
		}
	}

	/**
	 * Framework-internal -- resolves the Excel test data filename for the environment. Called automatically by setUp.
	 * method will set .xls file name for test data provider
	 *
	 * @param data_source
	 * @return
	 * @throws IOException
	 */
	protected String setXlsName(String data_source) throws IOException {
		log.info("Testing data set loading for [" + data_source + "] environment ");
		loadData();
		Map<String, String> envMap = new HashMap<String, String>();
		envMap.put("dev", Prop.getProperty("dev_data_set"));
		envMap.put("tst", Prop.getProperty("tst_data_set"));
		envMap.put("stg", Prop.getProperty("stg_data_set"));
		envMap.put("nonprod", Prop.getProperty("nonprod_data_set"));
		envMap.put("prod", Prop.getProperty("prod_data_set"));
		ExcelName = envMap.get(data_source);
		log.info("Test data source file is - " + ExcelName);
		return ExcelName;
	}

	/**
	 * Framework-internal -- resolves and stores the base URL for the given environment. Called automatically by setUp.
	 * Resolves and stores the base URL for the given environment.
	 *
	 * @param environment environment key
	 * @return resolved base URL
	 * @throws IOException if configuration loading fails
	 */
	protected String setBaseUrl(String environment) throws IOException {
		loadData();
		Map<String, String> envMap = new HashMap<String, String>();
		envMap.put("dev", Prop.getProperty("dev_base_url"));
		envMap.put("tst", Prop.getProperty("tst_base_url"));
		envMap.put("stg", Prop.getProperty("stg_base_url"));
		envMap.put("nonprod", Prop.getProperty("nonprod_base_url"));
		envMap.put("prod", Prop.getProperty("prod_base_url"));
		baseURL = envMap.get(environment);
		log.info("Specified BaseURL is - " + baseURL);
		return baseURL;
	}

	/**
	 * Framework-internal -- closes the browser and flushes reporting. Called automatically by afterClass.
	 * Closes the browser session and flushes Extent reporting.
	 *
	 * @throws IOException declared for API compatibility
	 */
	protected void closeBrowser() throws IOException {
		log.info("browser is closing");
		try {
			ExtentTestManager.endTest();
			ExtentManager.getInstance().flush();
			if (driver != null) {
				driver.quit();
			}

			log.info("browser is closed");
		} catch (Exception e) {
			log.error("Caught message " + e.getMessage(), e);
		}

	}

	/**
	 * This method will compare of text from supplied WebElement with expected text
	 *
	 * @param desiredText
	 * @param actualTextFromWebElement
	 * @throws IOException
	 */
	public void verifyText(String desiredText, String actualTextFromWebElement) {
		log.info("Verifying text: expected=[" + desiredText + "] actual=[" + actualTextFromWebElement + "]");
		Assert.assertEquals(actualTextFromWebElement, desiredText,
				"Text mismatch: expected [" + desiredText + "] but was [" + actualTextFromWebElement + "]");
	}

	/**
	 * explicit wait
	 *
	 * @param driver
	 * @param element
	 * @param timeOutInSeconds
	 * @return
	 */
	public WebElement waitForElement(WebDriver driver, WebElement element, long timeOutInSeconds) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeOutInSeconds));
		wait.until(ExpectedConditions.elementToBeClickable(element));
		return element;
	}

	/**
	 * Method will select option from drop_down box with specified text_value
	 *
	 * @param element
	 * @param value
	 * @throws InterruptedException
	 */
	public void selectOptionInDropDownBox(WebElement element, String value) {
		Select select = new Select(element);
		List<WebElement> allOptions = select.getOptions();
		log.info("Number of options in DropBox - " + allOptions.size());
		log.info("DropDown Items:");
		for (WebElement option : allOptions) {
			log.info(option.getText().trim());
			if (option.getText().trim().equals(value)) {
				option.click();
			}
		}
	}

	/**
	 * Method will select text from specified web element (select-box) by visible
	 * text that is defined by the Text_Value parameter
	 *
	 * @param element
	 * @param value
	 * @throws InterruptedException
	 */
	public void visibleInDropDownList(WebElement element, String textValue) {
		Select droplist = new Select(element);
		log.info(droplist.getFirstSelectedOption().getText());
		droplist.selectByVisibleText(textValue);
	}

	/**
	 * Method will generate random email address
	 *
	 * @return
	 */
	public String randomEmailAddress() {
		String emailAddress = null;
		Random random = new Random();
		int number = random.nextInt(100000);
		String randoms = String.format("%06d", number);
		emailAddress = "test" + randoms + "@doitt.nyc.gov";
		log.info("System has generated password: " + emailAddress);
		return emailAddress;
	}

	/**
	 * Generates a random email address for the supplied domain name.
	 *
	 * @param emailDomainName domain name to use
	 * @return generated email address
	 */
	public String randomEmailAddress(String emailDomainName) {
		String emailAddress = null;
		Random random = new Random();
		int number = random.nextInt(10000000);
		String randoms = String.format("%08d", number);
		emailAddress = "test" + randoms + "@" + emailDomainName;
		// emailAddress = "test" + randoms + "@" + "doitt.nyc.gov";
		log.info("System has generated email address: " + emailAddress);
		return emailAddress;
	}

	/**
	 * Method will generate random password address
	 *
	 * @return
	 */
	public String randomPassword() {
		Random random = new Random();
		int number = random.nextInt(100000);
		String randoms = "test" + String.format("%06d", number);
		return randoms;
	}

	/**
	 * Method will generate unique username based on the current date
	 *
	 * @return
	 */
	public String newUniqueUsername() {
		Date currentDate = new Date();
		SimpleDateFormat ft = new SimpleDateFormat("yyMMddhhmmssMs");
		String datetime = ft.format(currentDate);
		String Username = "user" + datetime;
		log.info("Unique Username : " + datetime);
		return Username;
	}

	/**
	 * Method will wait button until(for up to 60 sec) a button become clickable
	 * WebElement, defined By locator, is loaded
	 *
	 * @param locator
	 * @return
	 */
	public WebElement waitUntilButtonIsClickable(By locator) {
		WebElement element = null;
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		long startTime = System.currentTimeMillis();
		element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("waiting for button when it become clickable :" + element);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold ( for up to 60 sec) until desired
	 *
	 * @param locator
	 * @return
	 */
	public WebElement waitForElementPresent(By locator) {
		WebElement element = null;
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));
		long startTime = System.currentTimeMillis();
		element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("waiting for loading of element :" + element);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold ( for up to 60 sec) until desired web
	 * element is loaded
	 *
	 * @param element
	 * @return
	 */
	public WebElement fluentWaitForElementPresent(WebElement element) {
		FluentWait<WebDriver> fWait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofSeconds(60))
				.pollingEvery(Duration.ofSeconds(2))
				.ignoring(NoSuchElementException.class)
				.ignoring(StaleElementReferenceException.class);
		log.info("waiting for element present ....." + element.toString());
		long startTime = System.currentTimeMillis();
		fWait.until(ExpectedConditions.visibilityOf(element));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Element loading time is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold ( for up to 60 sec) until desired web
	 * element is loaded
	 *
	 * @param element
	 * @return
	 */
	public static WebElement fluentWaitForElementPresent(WebDriver driver, WebElement element) {
		FluentWait<WebDriver> fWait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofSeconds(60))
				.pollingEvery(Duration.ofSeconds(2))
				.ignoring(NoSuchElementException.class)
				.ignoring(StaleElementReferenceException.class);
		log.info("waiting for element present ....." + element.toString());
		long startTime = System.currentTimeMillis();
		try {
			fWait.until(ExpectedConditions.visibilityOf(element));
		} catch (Exception e) {
			log.error("[TestBase] Element not found", e);
		}
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold ( for up to 60 sec) until desired web
	 * element is loaded
	 *
	 * @param element
	 * @return
	 */
	public static WebElement waitForElementPresent(WebDriver driver, WebElement element) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		long startTime = System.currentTimeMillis();
		wait.until(ExpectedConditions.visibilityOf(element));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("waiting for loading of element :" + element);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold ( for up to 60 sec) until desired web
	 * element is loaded
	 *
	 * @param element
	 * @return
	 */
	public static WebElement waitForElementPresent(WebElement element) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		long startTime = System.currentTimeMillis();
		wait.until(ExpectedConditions.visibilityOf(element));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("waiting for loading of element :" + element);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Waits for the page title to contain the specified text.
	 *
	 * @param driver active driver
	 * @param titleText expected title fragment
	 */
	public void waitUntilTitleIsPresent(WebDriver driver, String titleText) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));
		wait.until(ExpectedConditions.titleContains(titleText));
	}

	/**
	 * Waits for an element to become visible and clickable.
	 *
	 * @param element target element
	 * @return the same element
	 */
	public static WebElement fluentWaitForElement(WebElement element) {
		log.info("fluent waiting for present of element present - " + element.toString());
		long startTime = System.currentTimeMillis();
		FluentWait<WebDriver> fWait = new FluentWait<WebDriver>(driver).withTimeout(Duration.ofSeconds(120))
				.pollingEvery(Duration.ofSeconds(5)).ignoring(NoSuchElementException.class, TimeoutException.class)
				.ignoring(StaleElementReferenceException.class);

		try {
			fWait.until(ExpectedConditions.visibilityOf(element));
			fWait.until(ExpectedConditions.elementToBeClickable(element));
			log.info("element is present");
		} catch (Exception e) {
			log.info("Element Not found trying again - " + element.toString());
			log.error("[TestBase] Element not clickable", e);
		}
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Element loading time is - " + duration + " milliseconds");
		return element;

	}

	/**
	 * Method will put test execution on hold until desired webelement loaded or
	 * timeout expired
	 *
	 * @param pageName
	 * @param timeout
	 * @return
	 */
	public WebElement waitForElementPresent(WebElement element, int timeout) {
		log.info("waiting for loading of element :" + element.toString());
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout));
		long startTime = System.currentTimeMillis();
		wait.until(ExpectedConditions.visibilityOf(element));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Element loading time is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold until page is loaded
	 *
	 */
	public static WebElement fluentWaitUntilElementToBeClickable(WebElement element) {
		log.info("waiting for state of element to be clickable :" + element.toString());
		FluentWait<WebDriver> fWait = new FluentWait<WebDriver>(driver).withTimeout(Duration.ofSeconds(120))
				.pollingEvery(Duration.ofSeconds(5)).ignoring(NoSuchElementException.class, TimeoutException.class)
				.ignoring(StaleElementReferenceException.class);

		long startTime = System.currentTimeMillis();
		try {
			fWait.until(ExpectedConditions.visibilityOf(element));
			fWait.until(ExpectedConditions.elementToBeClickable(element));
		} catch (Exception e) {
			log.info("Element Not found trying again - " + element.toString());
			log.error("[TestBase] Element not clickable", e);
		}
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Waiting time for element to become clickable is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Method will put test execution on hold until page is loaded
	 *
	 */
	public static WebElement waitUntilElementToBeClickable(WebElement element) {
		log.info("waiting for state of element to be clickable :" + element.toString());
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		long startTime = System.currentTimeMillis();
		wait.until(ExpectedConditions.elementToBeClickable(element));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Element state genging time is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Waits until the current page reports {@code document.readyState=complete}.
	 *
	 * @param timeOutSeconds timeout in seconds
	 */
	public void waitForPageToLoad(long timeOutSeconds) {
		log.info("Waiting for page [ " + driver.getCurrentUrl() + "] to load....");
		long startTime = System.currentTimeMillis();
		try {
			FluentWait<WebDriver> wait = new FluentWait<WebDriver>(driver)
					.withTimeout(Duration.ofSeconds(timeOutSeconds))
					.pollingEvery(Duration.ofSeconds(2))
					.ignoring(NoSuchElementException.class);
			wait.until(webDriver -> ((JavascriptExecutor) webDriver)
					.executeScript("return document.readyState").equals("complete"));
		} catch (Throwable error) {
			log.error("Timeout waiting for page load to complete after " + timeOutSeconds + " seconds");
			Assert.fail("Page did not load within " + timeOutSeconds + " seconds");
		}

		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Page loading time is - " + duration + " milliseconds");

	}

	/**
	 * Scrolls to the element located by the supplied locator.
	 *
	 * @param by locator for the target element
	 * @param driver active driver
	 */
	public void scrollToElement(By by, WebDriver driver) {
		log.info("Scroling to element - " + by.toString());
		WebElement element = driver.findElement(by);
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
	}

	/**
	 * Scrolls the target element into view and briefly pauses for UI settling.
	 *
	 * @param element target element
	 */
	public void scrollToElement(WebElement element) {
	        log.info("Scroling to element - " + element.getLocation());

	        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
	        try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("scrollToElement interrupted during sleep", e);
            }
	}

	/**
	 * Method will put test execution on hold until page is loaded
	 */
	public static void waitUntillPageLoad() {
		log.info("Waiting for page [" + driver.getCurrentUrl() + "] to load....");
		long startTime = System.currentTimeMillis();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));
		wait.until(webDriver -> ((JavascriptExecutor) webDriver)
				.executeScript("return document.readyState").equals("complete"));

		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("Page loading time is: " + duration + " ms");
	}

	/**
	 * Blocks until the current URL contains the given substring, or throws
	 * TimeoutException if the timeout elapses.
	 *
	 * Typical use: wait for SAML/SSO redirect to complete before proceeding.
	 *
	 * @param partial        substring to wait for in the current URL
	 * @param timeoutSeconds max seconds to wait
	 */
	public void waitForUrlContains(final String partial, int timeoutSeconds) {
		log.info("waitForUrlContains: waiting up to " + timeoutSeconds + "s for URL to contain '" + partial + "'");
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(new com.google.common.base.Function<WebDriver, Boolean>() {
					public Boolean apply(WebDriver d) {
						return d.getCurrentUrl().contains(partial);
					}
				});
		log.info("waitForUrlContains: URL now contains '" + partial + "' -> " + driver.getCurrentUrl());
	}

	/**
	 * Navigates to each reservation ID in sequence and returns those where the
	 * reservation details page contains an action button with the given title attribute.
	 *
	 * Requires the driver to already be logged in and on the app domain.
	 *
	 * @param baseUrl        app base URL (e.g. "https://poletop-stg.csc.nycnet/")
	 * @param reservationIds list of reservation ID strings to check
	 * @param buttonTitle    exact value of the title= attribute to look for
	 *                       (e.g. "Start Construction", "Add SIF", "View SIF")
	 * @param renderWaitMs   extra ms to wait after page load for Angular to render
	 *                       action buttons (recommended: 1500–2000). Uses FluentWait,
	 *                       not Thread.sleep.
	 * @return list of reservation IDs that have the requested button visible in the DOM
	 */
	public List<String> scanReservationsByButtonTitle(
			String baseUrl,
			List<String> reservationIds,
			final String buttonTitle,
			int renderWaitMs) {

		WebDriverWait headingWait = new WebDriverWait(driver, Duration.ofSeconds(15));
		FluentWait<WebDriver> renderWait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofMillis(renderWaitMs))
				.pollingEvery(Duration.ofMillis(200))
				.ignoring(Exception.class);
		final By buttonLocator = By.xpath("//button[@title='" + buttonTitle + "']");
		List<String> matches = new ArrayList<String>();

		for (String id : reservationIds) {
			try {
				driver.navigate().to(baseUrl + "#/reservation/" + id);
				headingWait.until(ExpectedConditions.presenceOfElementLocated(
						By.xpath("//h1[contains(.,'Reservation #')]")));

				// Wait up to renderWaitMs for the action button to appear
				boolean hasButton = false;
				try {
					renderWait.until(new com.google.common.base.Function<WebDriver, Boolean>() {
						public Boolean apply(WebDriver d) {
							return !d.findElements(buttonLocator).isEmpty();
						}
					});
					hasButton = true;
				} catch (org.openqa.selenium.TimeoutException ignored) {
					// button did not appear within renderWaitMs — treated as absent
				}

				if (!driver.getCurrentUrl().contains("/reservation/" + id)) {
					log.info("SCAN SKIP " + id + " -- redirected (not accessible)");
					continue;
				}

				log.info("SCAN " + id + " [" + buttonTitle + "] = " + hasButton);
				if (hasButton) matches.add(id);

			} catch (Exception e) {
				log.warn("SCAN ERROR " + id + ": " + e.getMessage());
			}
		}
		return matches;
	}

	/**
	 * Framework-internal -- mutates the global implicit wait. Prefer waitForElementPresent() or fluentWaitForElementPresent() in tests.
	 * Applies an implicit wait to the WebDriver session for the given number of seconds.
	 */
	protected void explicitWait(int sec) {
		log.info("Setting implicit wait to " + sec + " seconds");
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(sec));
	}

	/**
	 * Polls the DOM until the page is in a ready state, for up to {@code sec} seconds.
	 * Use this as a reliable alternative to driver.manage().timeouts().implicitlyWait()
	 * when the driver-level implicit wait does not behave consistently with the current
	 * browser/driver version.
	 *
	 * Implementation: FluentWait polls document.readyState == "complete" every 500 ms.
	 * Does not use Thread.sleep. Does not mutate the global WebDriver timeout.
	 *
	 * @param sec maximum time to wait in seconds
	 */
	public void implicitWait(int sec) {
		log.info("implicitWait: polling for page ready state (up to " + sec + "s)");
		FluentWait<WebDriver> wait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofSeconds(sec))
				.pollingEvery(Duration.ofMillis(500))
				.ignoring(Exception.class);
		wait.until(new com.google.common.base.Function<WebDriver, Boolean>() {
			public Boolean apply(WebDriver d) {
				return "complete".equals(
					((org.openqa.selenium.JavascriptExecutor) d)
						.executeScript("return document.readyState"));
			}
		});
		log.info("implicitWait: page ready state reached");
	}

	/**
	 * Waits after a click for either a URL change or the appearance of a landmark
	 * element -- whichever comes first. Use this instead of Thread.sleep() after
	 * clicks in Angular SPAs where the URL hash may change without a full page reload.
	 *
	 * @param urlBefore  the URL captured before the click (driver.getCurrentUrl())
	 * @param landmark   a By locator for a known element on the target page/modal;
	 *                   pass null to wait for URL change only
	 * @param timeout    maximum seconds to wait
	 */
	public void waitForNavigationOrElement(final String urlBefore, final By landmark, int timeout) {
		log.info("waitForNavigationOrElement: waiting up to " + timeout + "s for URL change or landmark");
		FluentWait<WebDriver> wait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofSeconds(timeout))
				.pollingEvery(Duration.ofMillis(300))
				.ignoring(Exception.class);
		wait.until(new com.google.common.base.Function<WebDriver, Boolean>() {
			public Boolean apply(WebDriver d) {
				if (!d.getCurrentUrl().equals(urlBefore)) return true;
				if (landmark != null && !d.findElements(landmark).isEmpty()) return true;
				return false;
			}
		});
		log.info("waitForNavigationOrElement: condition met");
	}

	/**
	 * Waits after a click that is expected to open a modal overlay. Polls for
	 * common Angular/CDK modal anchors: mat-dialog-container, role=dialog,
	 * cdk-overlay-pane. Returns as soon as any is present.
	 *
	 * @param timeout maximum seconds to wait
	 */
	public void waitForModalOrUrlChange(final String urlBefore, int timeout) {
		log.info("waitForModalOrUrlChange: waiting up to " + timeout + "s");
		FluentWait<WebDriver> wait = new FluentWait<WebDriver>(driver)
				.withTimeout(Duration.ofSeconds(timeout))
				.pollingEvery(Duration.ofMillis(300))
				.ignoring(Exception.class);
		wait.until(new com.google.common.base.Function<WebDriver, Boolean>() {
			public Boolean apply(WebDriver d) {
				if (!d.getCurrentUrl().equals(urlBefore)) return true;
				if (!d.findElements(By.xpath(
						"//mat-dialog-container | //*[@role='dialog'] | //*[contains(@class,'cdk-overlay-pane')]"))
						.isEmpty()) return true;
				return false;
			}
		});
		log.info("waitForModalOrUrlChange: condition met");
	}

	/**
	 * Method will first switch to the parent frame and then we need to switch to
	 * the child frame.
	 */
	public void switchToChildFrame(String ParentFrame, String ChildFrame) {
		try {
			driver.switchTo().frame(ParentFrame).switchTo().frame(ChildFrame);
			log.info("Navigated to innerframe with id " + ChildFrame + "which is present on frame with id"
					+ ParentFrame);
		} catch (NoSuchFrameException e) {
			log.error("Unable to locate frame with id " + ParentFrame + " or " + ChildFrame, e);
		} catch (Exception e) {
			log.error("Unable to navigate to innerframe with id " + ChildFrame
					+ "which is present on frame with id" + ParentFrame, e);
		}
	}

	/**
	 * Code snippet to switch back to the default content
	 * driver.switchTo.frame("Frame_ID");
	 */
	public void switchtoDefaultFrame() {
		try {
			driver.switchTo().defaultContent();
			log.info("Navigated back to webpage from frame");
		} catch (Exception e) {
			log.error("unable to navigate back to main webpage from frame", e);
		}
	}

	/**
	 * Method switch drive to specific frame
	 *
	 * @param frameName
	 */
	public void switchtoFrame(String frameName) {
		try {
			driver.switchTo().defaultContent();
			driver.switchTo().frame(frameName);
			log.info("Navigated to frame " + frameName);
		} catch (Exception e) {
			log.error("unable switch to frame" + frameName, e);
		}
	}

	/**
	 * Method verifying if the text present on the page
	 */
	protected boolean verifyTextOnThePage(String TextForVerification) {
		try {
			boolean b = driver.getPageSource().contains(TextForVerification);
			return b;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Method will generate random string
	 *
	 * @return
	 */
	public String randomString() {
		int length = 5;
		boolean useLetters = false;
		boolean useNumbers = true;
		String generatedString = RandomStringUtils.random(length, useLetters, useNumbers);
		return generatedString;

	}

	/**
	 * method will refresh a page until specified text is visible
	 *
	 * @param textForVerificatin
	 * @throws InterruptedException
	 */
	public void reloadPageUntilTextVisible(String textForVerificatin) {
		int reloadCounter = 0;
		do {
			driver.navigate().refresh();
			log.info("Page is reloading until text [" + textForVerificatin + "] is available");
			waitUntillPageLoad();
			reloadCounter = reloadCounter + 1;
		} while (!driver.getPageSource().contains(textForVerificatin) && reloadCounter <= 20);
	}

	/**
	 * method will refresh a page until specified WebElement is visible
	 *
	 * @param element
	 */
	public static void reloadPageUntilWebElementVisible(WebElement element) {
		int reloadCounter = 0;
		do {
			driver.navigate().refresh();
			log.info("Page is reloading until WebElement [" + element.toString() + "] is isDisplayed");
			waitUntillPageLoad();
			reloadCounter = reloadCounter + 1;
		} while (!element.isDisplayed() && reloadCounter <= 30);
	}

	/**
	 * method will check whether the specified WebElement is clickable
	 *
	 * @param locator
	 * @param timeoutInSeconds
	 */
	public static boolean elementIsClickable(WebElement element, WebDriver driver, long timeoutSeconds) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

			wait.until(ExpectedConditions.elementToBeClickable(element));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Method will wait until text is present.
	 *
	 * @param driver
	 * @param element
	 * @param address
	 * @return WebElement element
	 */
	public WebElement waitForTextPresent(WebElement element, String text) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));
		long startTime = System.currentTimeMillis();
		wait.until(ExpectedConditions.textToBePresentInElement(element, text));
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		log.info("waiting for loading of element :" + element);
		log.info("Waiting time for element is - " + duration + " milliseconds");
		return element;
	}

	/**
	 * Framework-internal -- queries the Excel test data file using SQL. Use getData() instead.
	 * Method will return Array[][] with set of testing data that will be queried for .xls file
	 *
	 * @param excelFile
	 * @param sheetName
	 * @param sqlQuery
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InvalidFormatException
	 * @throws SQLException
	 */
	protected Object[][] selectExcelData(String sheetName, String sqlQuery)
			throws IOException, ClassNotFoundException, InvalidFormatException, SQLException {
		loadData();
		Object[][] data = null;
		/**
		 * Check if excelFile contains external path.
		 */
		if (new File(ExcelName).isDirectory()) {
			data = QueryExcelFile.getDataFromSheet(ExcelName, sheetName, sqlQuery);
		} else {
			String documentName = System.getProperty("user.dir") + Prop.getProperty("testDataDir") + ExcelName;
			data = QueryExcelFile.getDataFromSheet(documentName, sheetName, sqlQuery);
		}

		return data;

	}

	// -------------------------------------------------------------------------
	// Text Entry
	// -------------------------------------------------------------------------

	/**
	 * Clears the field, then types the given text. Retries once on
	 * StaleElementReferenceException.
	 */
	public void clearAndType(WebElement element, String text) {
		log.info("clearAndType on element [" + element.toString() + "] with text [" + text + "]");
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				waitUntilElementToBeClickable(element);
				element.clear();
				element.sendKeys(text);
				return;
			} catch (StaleElementReferenceException | ElementNotInteractableException e) {
				log.warn("clearAndType attempt " + attempt + "/" + maxAttempts
						+ " failed on [" + element.toString() + "]: " + e.getMessage());
				if (attempt == maxAttempts) throw e;
			}
		}
	}

	/**
	 * Clicks an element with up to 3 retry attempts.
	 * After each attempt waits for the page to settle before retrying.
	 * Use this for any click that may encounter stale element or timing issues.
	 *
	 * @param element the target element
	 */
	public void safeClick(WebElement element) {
		log.info("safeClick on element [" + element.toString() + "]");
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				fluentWaitUntilElementToBeClickable(element);
				element.click();
				return;
			} catch (WebDriverException e) {
				log.warn("safeClick attempt " + attempt + "/" + maxAttempts
						+ " failed on [" + element.toString() + "]: " + e.getMessage());
				if (attempt == maxAttempts) throw e;
				waitUntillPageLoad();
			}
		}
	}

	/**
	 * Reads getText() from an element with up to 3 retry attempts.
	 * Handles StaleElementReferenceException that can occur in Angular apps
	 * when the DOM re-renders between wait and read.
	 *
	 * @param element the target element
	 * @return the trimmed visible text of the element
	 */
	public String safeGetText(WebElement element) {
		log.info("safeGetText on element [" + element.toString() + "]");
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				waitForElementPresent(driver, element);
				return element.getText().trim();
			} catch (StaleElementReferenceException e) {
				log.warn("safeGetText attempt " + attempt + "/" + maxAttempts
						+ " failed: " + e.getMessage());
				if (attempt == maxAttempts) throw e;
			}
		}
		return "";
	}

	/**
	 * Searches a list of already-fetched option elements for one whose visible
	 * text matches the given value. Extracted from
	 * {@link #selectFromCustomWidget(WebElement, By, By, String)} so the match
	 * logic can be unit tested without a browser/driver.
	 *
	 * @param options candidate option elements, already scoped to the open panel
	 * @param value   visible text to match
	 * @return the first matching WebElement, or null if none match
	 */
	WebElement findMatchingOption(List<WebElement> options, String value) {
		for (WebElement option : options) {
			if (value.equals(option.getText())) {
				return option;
			}
		}
		return null;
	}

	/**
	 * Selects an option from a custom (non-native) dropdown/listbox widget - e.g.
	 * an Angular Material select panel - using a bounded select -> verify -> retry
	 * loop. This guards against the transient DOM re-renders and timing races
	 * common in SPA apps, the same class of flakiness that {@link #safeClick} and
	 * {@link #safeGetText} guard against for simple interactions.
	 * <p>
	 * The option locator is always searched relative to the freshly-fetched panel
	 * element (never as a bare "//" against the whole document), so a stray panel
	 * left open elsewhere on the page cannot be matched by mistake.
	 * <p>
	 * Retries up to 3 times, on each attempt re-opening the panel and re-fetching
	 * its options, if the option is not found or the trigger's text does not
	 * match the requested value after clicking (verify step).
	 *
	 * @param trigger       element that opens the dropdown panel when clicked
	 * @param panelLocator  locator for the panel/listbox that appears after opening
	 * @param optionLocator locator for options WITHIN the panel; must be relative
	 *                      (e.g. {@code By.xpath(".//div[@role='option']")})
	 * @param value         visible text of the option to select
	 */
	public void selectFromCustomWidget(WebElement trigger, By panelLocator, By optionLocator, String value) {
		log.info("selectFromCustomWidget: selecting value [" + value + "]");
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				safeClick(trigger);
				WebElement panel = waitForElementPresent(panelLocator);
				List<WebElement> options = panel.findElements(optionLocator);
				WebElement match = findMatchingOption(options, value);
				if (match == null) {
					throw new NoSuchElementException("No option with text [" + value + "] found in panel");
				}
				safeClick(match);
				String actual = safeGetText(trigger);
				verifyText(value.trim(), actual);
				return;
			} catch (AssertionError | WebDriverException e) {
				log.warn("selectFromCustomWidget attempt " + attempt + "/" + maxAttempts
						+ " failed for value [" + value + "]: " + e.getMessage());
				if (attempt == maxAttempts) {
					log.error("FAILURE - could not select [" + value + "] from custom widget after "
							+ maxAttempts + " attempts", e);
					if (e instanceof AssertionError) throw (AssertionError) e;
					throw (WebDriverException) e;
				}
				waitUntillPageLoad();
			}
		}
	}

	// -------------------------------------------------------------------------
	// Wait - Invisibility / Disappearance
	// -------------------------------------------------------------------------

	/**
	 * Waits up to timeoutSeconds for the element identified by the locator to
	 * become invisible or absent from the DOM.
	 */
	public void waitForElementToDisappear(By locator, long timeoutSeconds) {
		log.info("Waiting for element [" + locator + "] to disappear (timeout=" + timeoutSeconds + "s)");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
		log.info("Element [" + locator + "] is no longer visible.");
	}

	/**
	 * Waits up to 60 seconds for the element to become invisible or absent.
	 */
	public void waitForElementToDisappear(By locator) {
		waitForElementToDisappear(locator, 60);
	}

	/**
	 * Waits up to timeoutSeconds for the element to become invisible.
	 */
	public void waitForElementToDisappear(WebElement element, long timeoutSeconds) {
		log.info("Waiting for WebElement to disappear (timeout=" + timeoutSeconds + "s)");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		wait.until(ExpectedConditions.invisibilityOf(element));
		log.info("WebElement is no longer visible.");
	}

	// -------------------------------------------------------------------------
	// Wait - Attribute / Value
	// -------------------------------------------------------------------------

	/**
	 * Waits up to timeoutSeconds for the given attribute of the element to equal
	 * expectedValue.
	 */
	public void waitForAttributeToBe(WebElement element, String attribute, String expectedValue, long timeoutSeconds) {
		log.info("Waiting for attribute [" + attribute + "] to be [" + expectedValue + "] on element ["
				+ element.toString() + "]");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		wait.until(ExpectedConditions.attributeToBe(element, attribute, expectedValue));
	}

	/**
	 * Waits up to timeoutSeconds for the given attribute to contain the expected
	 * substring.
	 */
	public void waitForAttributeToContain(WebElement element, String attribute, String substring, long timeoutSeconds) {
		log.info("Waiting for attribute [" + attribute + "] to contain [" + substring + "] on element ["
				+ element.toString() + "]");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		wait.until(ExpectedConditions.attributeContains(element, attribute, substring));
	}

	// -------------------------------------------------------------------------
	// Map Widgets (Google Maps / Leaflet / Mapbox GL / OpenLayers / Bing Maps)
	// -------------------------------------------------------------------------

	/**
	 * Waits up to timeoutSeconds for the map container to show rendered content
	 * (sized canvas or tile images), then applies a short settle buffer. Delegates
	 * to {@link com.test.automation.sdk.utility.MapWidgetHelper#waitForMapReady}.
	 * See that class for why map libraries need heuristic, not event-based, waits.
	 */
	public void waitForMapReady(WebElement mapContainer, long timeoutSeconds) {
		com.test.automation.sdk.utility.MapWidgetHelper.waitForMapReady(
				driver, mapContainer, Duration.ofSeconds(timeoutSeconds));
	}

	/** Waits up to 20 seconds for the map container to render. */
	public void waitForMapReady(WebElement mapContainer) {
		waitForMapReady(mapContainer, 20);
	}

	/**
	 * Types the given address into the map's search/autocomplete input and
	 * submits it (clicking the first suggestion when a dropdown appears, else
	 * pressing ENTER), then waits for the map to re-render. Delegates to
	 * {@link com.test.automation.sdk.utility.MapWidgetHelper#searchAddress}.
	 *
	 * @return true when a search box was found and the address was submitted.
	 */
	public boolean searchMapAddress(WebElement mapContainer, String address) {
		log.info("Searching map address: " + address);
		return com.test.automation.sdk.utility.MapWidgetHelper.searchAddress(driver, mapContainer, address);
	}

	/**
	 * Finds a map marker/pin by its accessible label (aria-label, alt, or title,
	 * tried in that order). Returns null when the marker has no DOM presence
	 * (canvas/WebGL-drawn) -- in that case use a pixel-offset click instead via
	 * {@link com.test.automation.sdk.utility.MapWidgetHelper#clickAtPixelOffset}.
	 */
	public WebElement findMapMarkerByLabel(WebElement mapContainer, String label) {
		return com.test.automation.sdk.utility.MapWidgetHelper.findMarkerByLabel(driver, mapContainer, label);
	}

	// -------------------------------------------------------------------------
	// Shadow DOM (Web Components / Lit / Stencil / Salesforce Lightning-LWC)
	// -------------------------------------------------------------------------

	/**
	 * Resolves an element inside an <em>open</em> shadow root using the
	 * two-step lookup XPath cannot express: find the shadow HOST in the normal
	 * (light) DOM, then query inside {@code host.getShadowRoot()} with a CSS
	 * selector (shadow roots only support CSS lookups, never XPath). This is
	 * the same pattern {@code PageObjectGenerator} emits for elements
	 * ElementCrawler tags with {@code ElementInfo.inShadowDom = true}.
	 *
	 * @param hostLocator locates the shadow-root host element in the light DOM
	 * @param cssSelector CSS selector for the target element within the shadow root
	 */
	public WebElement findInShadowDom(By hostLocator, String cssSelector) {
		WebElement host = driver.findElement(hostLocator);
		return host.getShadowRoot().findElement(By.cssSelector(cssSelector));
	}

	// -------------------------------------------------------------------------
	// Window / Tab Management
	// -------------------------------------------------------------------------

	/**
	 * Switches to the window identified by handle and returns the previous window
	 * handle so the caller can switch back.
	 */
	public String switchToWindow(String windowHandle) {
		String currentHandle = driver.getWindowHandle();
		log.info("Switching to window handle [" + windowHandle + "] from [" + currentHandle + "]");
		driver.switchTo().window(windowHandle);
		return currentHandle;
	}

	/**
	 * Switches to the most recently opened window/tab (last handle in the set).
	 * Stores the parent handle for later use.
	 */
	public void switchToNewWindow() {
		parentWindow = driver.getWindowHandle();
		Set<String> allWindows = driver.getWindowHandles();
		for (String handle : allWindows) {
			if (!handle.equals(parentWindow)) {
				log.info("Switching to new window [" + handle + "]");
				driver.switchTo().window(handle);
			}
		}
	}

	/**
	 * Closes the current tab/window and switches back to the stored parent window.
	 */
	public void closeCurrentTabAndSwitchToParent() {
		log.info("Closing current tab and switching back to parent window [" + parentWindow + "]");
		driver.close();
		driver.switchTo().window(parentWindow);
	}

	/**
	 * Switches back to the stored parent window handle.
	 */
	public void switchToParentWindow() {
		log.info("Switching back to parent window [" + parentWindow + "]");
		driver.switchTo().window(parentWindow);
	}

	/**
	 * Opens a new browser tab via JavaScript and switches focus to it.
	 */
	public void openNewTab() {
		log.info("Opening new browser tab via JavaScript");
		((JavascriptExecutor) driver).executeScript("window.open('about:blank','_blank');");
		switchToNewWindow();
	}

	// -------------------------------------------------------------------------
	// Frame Switching - additional overloads
	// -------------------------------------------------------------------------

	/**
	 * Switches to a frame by its zero-based index.
	 */
	public void switchToFrame(int index) {
		try {
			driver.switchTo().frame(index);
			log.info("Switched to frame at index " + index);
		} catch (Exception e) {
			log.error("Unable to switch to frame at index " + index, e);
		}
	}

	/**
	 * Switches to a frame identified by a WebElement.
	 */
	public void switchToFrame(WebElement frameElement) {
		try {
			driver.switchTo().frame(frameElement);
			log.info("Switched to frame element: " + frameElement.toString());
		} catch (Exception e) {
			log.error("Unable to switch to frame element: " + frameElement.toString(), e);
		}
	}

	// -------------------------------------------------------------------------
	// Alert Handling
	// -------------------------------------------------------------------------

	/**
	 * Waits up to timeoutSeconds for an alert to appear and returns the Alert.
	 */
	public Alert waitForAlert(long timeoutSeconds) {
		log.info("Waiting for alert (timeout=" + timeoutSeconds + "s)");
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return wait.until(ExpectedConditions.alertIsPresent());
	}

	/**
	 * Accepts (OK) the current alert. Waits up to 10 seconds for it to appear.
	 */
	public void acceptAlert() {
		log.info("Accepting alert");
		Alert alert = waitForAlert(10);
		alert.accept();
	}

	/**
	 * Dismisses (Cancel) the current alert. Waits up to 10 seconds for it to appear.
	 */
	public void dismissAlert() {
		log.info("Dismissing alert");
		Alert alert = waitForAlert(10);
		alert.dismiss();
	}

	/**
	 * Returns the text of the current alert. Waits up to 10 seconds for it.
	 */
	public String getAlertText() {
		Alert alert = waitForAlert(10);
		String text = alert.getText();
		log.info("Alert text: [" + text + "]");
		return text;
	}

	/**
	 * Sends keys to the current alert/prompt. Waits up to 10 seconds for it.
	 */
	public void sendKeysToAlert(String text) {
		log.info("Sending keys [" + text + "] to alert");
		Alert alert = waitForAlert(10);
		alert.sendKeys(text);
	}

	// -------------------------------------------------------------------------
	// Actions API - Mouse & Keyboard
	// -------------------------------------------------------------------------

	/**
	 * Moves the mouse over (hovers) the element without clicking.
	 */
	public void hoverOverElement(WebElement element) {
		log.info("Hovering over element: " + element.toString());
		new Actions(driver).moveToElement(element).perform();
	}

	/**
	 * Double-clicks the element.
	 */
	public void doubleClick(WebElement element) {
		log.info("Double-clicking element: " + element.toString());
		new Actions(driver).doubleClick(element).perform();
	}

	/**
	 * Right-clicks (context-clicks) the element.
	 */
	public void rightClick(WebElement element) {
		log.info("Right-clicking element: " + element.toString());
		new Actions(driver).contextClick(element).perform();
	}

	/**
	 * Drags the source element and drops it on the target element.
	 */
	public void dragAndDrop(WebElement source, WebElement target) {
		log.info("Drag from [" + source.toString() + "] and drop on [" + target.toString() + "]");
		new Actions(driver).dragAndDrop(source, target).perform();
	}

	/**
	 * Sends a special key (e.g. Keys.TAB, Keys.ENTER) to the element using
	 * Actions so it bubbles through JavaScript event handlers.
	 */
	public void pressKey(WebElement element, CharSequence key) {
		log.info("Pressing key [" + key + "] on element: " + element.toString());
		new Actions(driver).click(element).sendKeys(key).perform();
	}

	// -------------------------------------------------------------------------
	// Select / Dropdown - additional helpers
	// -------------------------------------------------------------------------

	/**
	 * Selects a dropdown option by its zero-based index.
	 */
	public void selectByIndex(WebElement element, int index) {
		log.info("Selecting option at index [" + index + "] in dropdown: " + element.toString());
		new Select(element).selectByIndex(index);
	}

	/**
	 * Selects a dropdown option by its value attribute.
	 */
	public void selectByValue(WebElement element, String value) {
		log.info("Selecting option with value [" + value + "] in dropdown: " + element.toString());
		new Select(element).selectByValue(value);
	}

	/**
	 * Selects a native HTML select option by value and dispatches Angular-compatible
	 * change and input events so ngModel / [(ngModel)] bindings detect the change.
	 * Use this instead of selectByValue() when the form uses Angular two-way binding
	 * and Save remains silently blocked after a plain Select.selectByValue() call.
	 *
	 * @param element the select element
	 * @param value   the option value to select
	 */
	public void selectByValueAngular(WebElement element, String value) {
		log.info("selectByValueAngular: setting value [" + value + "]");
		((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
			"arguments[0].value = arguments[1];" +
			"arguments[0].dispatchEvent(new Event('input',  {bubbles:true}));" +
			"arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
			element, value);
	}

	/**
	 * Types a value into an input field and dispatches Angular-compatible input/change
	 * events, then sends TAB to trigger blur validation. Use this instead of
	 * clearAndType() when the Angular form validates on (blur) and the required-field
	 * validator stays active after plain sendKeys() (blocking Save silently).
	 *
	 * @param element the input element
	 * @param text    the value to type
	 */
	public void clearAndTypeAngular(WebElement element, String text) {
		log.info("clearAndTypeAngular on element [" + element.toString() + "] with text [" + text + "]");
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				waitUntilElementToBeClickable(element);
				element.clear();
				element.sendKeys(text);
				((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
					"arguments[0].dispatchEvent(new Event('input',  {bubbles:true}));" +
					"arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
					element);
				element.sendKeys(org.openqa.selenium.Keys.TAB);
				return;
			} catch (StaleElementReferenceException | ElementNotInteractableException e) {
				log.warn("clearAndTypeAngular attempt " + attempt + "/3 failed: " + e.getMessage());
				if (attempt == maxAttempts) throw e;
			}
		}
	}

	/**
	 * Returns the text of the currently selected option in a dropdown.
	 */
	public String getSelectedOption(WebElement element) {
		String selected = new Select(element).getFirstSelectedOption().getText();
		log.info("Currently selected option: [" + selected + "]");
		return selected;
	}

	/**
	 * Returns all option texts for a dropdown as a List.
	 */
	public List<String> getAllDropdownOptions(WebElement element) {
		List<WebElement> options = new Select(element).getOptions();
		List<String> texts = new ArrayList<>();
		for (WebElement option : options) {
			texts.add(option.getText().trim());
		}
		log.info("Dropdown has " + texts.size() + " options: " + texts);
		return texts;
	}

	// -------------------------------------------------------------------------
	// Bootstrap / Custom Dropdown (non-<select> menus)
	// -------------------------------------------------------------------------

	/**
	 * Returns all visible item texts from an open Bootstrap / custom dropdown.
	 *
	 * <p>Builds a dynamic XPath at runtime using {@code menuId} or {@code menuClass}
	 * to locate the container, then iterates every {@code <li>} inside it.
	 * Pattern:  {@code //*[@id='<menuId>']//li}
	 *
	 * <p>Call this <em>after</em> the toggle has been clicked and the menu is open.
	 *
	 * @param menuId    value of the container's {@code id} attribute; pass {@code null}
	 *                  to locate by class instead
	 * @param menuClass value of the container's {@code class} fragment (uses
	 *                  {@code contains()}); ignored when {@code menuId} is provided
	 * @return ordered list of visible item texts (trimmed); empty when not found
	 */
	public List<String> getDropdownMenuItems(String menuId, String menuClass) {
		String containerXpath = menuId != null && !menuId.isEmpty()
				? "//*[@id='" + menuId + "']"
				: "//*[contains(@class,'" + menuClass + "')]";
		String itemsXpath = containerXpath + "//li";

		List<WebElement> liList = driver.findElements(By.xpath(itemsXpath));
		log.info("Dropdown [" + itemsXpath + "] -- " + liList.size() + " <li> items");

		List<String> items = new ArrayList<>();
		for (WebElement li : liList) {
			List<WebElement> anchors = li.findElements(By.tagName("a"));
			String text = anchors.isEmpty() ? li.getText().trim() : anchors.get(0).getText().trim();
			log.info("  item: '" + text + "'");
			items.add(text);
		}
		return items;
	}

	/**
	 * Clicks the first matching item in an open Bootstrap / custom dropdown by
	 * iterating its {@code <li>} children and comparing visible text.
	 *
	 * <p>Dynamic XPath is built at runtime:
	 * <pre>
	 *   //*[@id='<menuId>']//li            (when menuId is provided)
	 *   //*[contains(@class,'<menuClass>')]//li  (fallback)
	 * </pre>
	 *
	 * @param menuId         container {@code id}; pass {@code null} to use class
	 * @param menuClass      container {@code class} fragment; used when menuId is null
	 * @param labelContains  substring to match against each item's visible text
	 *                       (case-insensitive)
	 * @return {@code true} when the item was found and clicked
	 */
	public boolean clickDropdownMenuItemByText(String menuId, String menuClass,
			String labelContains) {
		String containerXpath = menuId != null && !menuId.isEmpty()
				? "//*[@id='" + menuId + "']"
				: "//*[contains(@class,'" + menuClass + "')]";
		String itemsXpath = containerXpath + "//li";

		List<WebElement> liList = driver.findElements(By.xpath(itemsXpath));
		log.info("Searching dropdown [" + itemsXpath + "] for '" + labelContains
				+ "' -- " + liList.size() + " items");

		for (WebElement li : liList) {
			List<WebElement> anchors = li.findElements(By.tagName("a"));
			WebElement target = anchors.isEmpty() ? li : anchors.get(0);
			String text = target.getText().trim();
			if (text.toLowerCase().contains(labelContains.toLowerCase())) {
				log.info("Clicking: '" + text + "'");
				target.click();
				return true;
			}
		}
		log.warn("Item not found containing '" + labelContains + "' in [" + itemsXpath + "]");
		return false;
	}

	/**
	 * Overload: locates the dropdown container by {@code role} attribute (e.g.
	 * {@code role="listbox"} or {@code role="menu"}) and iterates child items
	 * by {@code role="option"} or {@code role="menuitem"}.
	 *
	 * <p>Dynamic XPath pattern (matches the user-supplied example):
	 * <pre>
	 *   //*[@id='<containerId>']//*[@role='option']
	 * </pre>
	 *
	 * @param containerId  {@code id} of the options container element
	 * @param itemRole     {@code role} of each option element, e.g. {@code "option"}
	 *                     or {@code "menuitem"}
	 * @param labelContains text to match (case-insensitive)
	 * @return {@code true} when matching item was clicked
	 */
	public boolean clickDropdownOptionByRole(String containerId, String itemRole,
			String labelContains) {
		String optionsXpath = "//*[@id='" + containerId + "']"
				+ "//*[@role='" + itemRole + "']";

		List<WebElement> optionsList = driver.findElements(By.xpath(optionsXpath));
		log.info("Options [" + optionsXpath + "] -- " + optionsList.size() + " items");

		for (WebElement option : optionsList) {
			String text = option.getText().trim();
			log.info("  option: '" + text + "'");
			if (text.toLowerCase().contains(labelContains.toLowerCase())) {
				log.info("Clicking option: '" + text + "'");
				option.click();
				return true;
			}
		}
		log.warn("Option not found containing '" + labelContains + "' in [" + optionsXpath + "]");
		return false;
	}

	// -------------------------------------------------------------------------
	// Table Row Iteration
	// -------------------------------------------------------------------------

	/**
	 * Returns the texts of all cells in every body row of a table.
	 *
	 * <p>Use this to inspect available rows before deciding which one to click.
	 * Each inner {@link List} contains the trimmed text of each {@code <td>} in
	 * that row, in column order.
	 *
	 * @param tableXpath XPath of the {@code <table>} element,
	 *                   e.g. {@code "//table[@id='reservationTable']"}
	 * @return list of rows; each row is a list of cell texts
	 */
	public List<List<String>> getTableRows(String tableXpath) {
		List<List<String>> result = new ArrayList<>();
		List<WebElement> rows = driver.findElements(By.xpath(tableXpath + "//tbody/tr"));
		log.info("Table rows found: " + rows.size());
		for (WebElement row : rows) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			List<String> rowData = new ArrayList<>();
			for (WebElement cell : cells) {
				rowData.add(cell.getText().trim());
			}
			log.info("  row: " + rowData);
			result.add(rowData);
		}
		return result;
	}

	/**
	 * Finds the first table body row where the cell at {@code columnIndex} contains
	 * {@code value} (case-insensitive) and clicks it (or its first {@code <a>} /
	 * {@code <button>} when {@code clickChildTag} is provided).
	 *
	 * @param tableXpath   XPath of the {@code <table>} element
	 * @param columnIndex  zero-based column index to match against
	 * @param value        text that the target cell must contain
	 * @param clickChildTag tag name of a child to click instead of the row itself,
	 *                      e.g. {@code "a"} or {@code "button"}; pass {@code null}
	 *                      to click the row element directly
	 * @return {@code true} when a matching row was found and clicked
	 */
	public boolean clickTableRowByColumnValue(String tableXpath, int columnIndex,
			String value, String clickChildTag) {
		List<WebElement> rows = driver.findElements(By.xpath(tableXpath + "//tbody/tr"));
		log.info("Searching table for column[" + columnIndex + "] containing '" + value
				+ "' -- " + rows.size() + " rows");
		for (WebElement row : rows) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			if (columnIndex >= cells.size()) continue;
			String cellText = cells.get(columnIndex).getText().trim();
			if (cellText.toLowerCase().contains(value.toLowerCase())) {
				log.info("Match found: '" + cellText + "' -- clicking row");
				if (clickChildTag != null && !clickChildTag.isEmpty()) {
					List<WebElement> children = row.findElements(By.tagName(clickChildTag));
					if (!children.isEmpty()) {
						children.get(0).click();
						return true;
					}
				}
				row.click();
				return true;
			}
		}
		log.warn("No table row found where column[" + columnIndex + "] contains '" + value + "'");
		return false;
	}

	/**
	 * Returns the text values of a single column across all body rows of a table.
	 * Useful for assertions -- e.g., verify all results in the Status column equal
	 * "Active".
	 *
	 * @param tableXpath  XPath of the {@code <table>} element
	 * @param columnIndex zero-based column index
	 * @return list of trimmed cell texts for the specified column
	 */
	public List<String> getTableColumnValues(String tableXpath, int columnIndex) {
		List<String> values = new ArrayList<>();
		List<WebElement> rows = driver.findElements(By.xpath(tableXpath + "//tbody/tr"));
		for (WebElement row : rows) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			if (columnIndex < cells.size()) {
				values.add(cells.get(columnIndex).getText().trim());
			}
		}
		log.info("Column[" + columnIndex + "] values (" + values.size() + "): " + values);
		return values;
	}

	// -------------------------------------------------------------------------
	// Element Text Extraction & List Comparison
	// -------------------------------------------------------------------------

	/**
	 * Extracts the trimmed visible text from each element in a list.
	 *
	 * <p>Generic replacement for page-specific label collectors. Pass any
	 * {@code List<WebElement>} -- e.g. from a {@code @FindBy(xpath="//label")}
	 * field -- and get back an ordered list of strings ready for assertion.
	 *
	 * <pre>
	 *   List<String> actual = getElementTexts(formLabels);
	 *   Assert.assertEquals(actual, expectedLabels);
	 * </pre>
	 *
	 * @param elements list of web elements to read text from
	 * @return ordered list of trimmed text values; empty strings are included
	 */
	public List<String> getElementTexts(List<WebElement> elements) {
		List<String> texts = new ArrayList<>();
		log.info("-- Element texts (" + elements.size() + " elements) --");
		for (WebElement el : elements) {
			String text = el.getText().trim();
			log.info("  '" + text + "'");
			texts.add(text);
		}
		return texts;
	}

	/**
	 * Returns elements from {@code listA} that are NOT present in {@code listB}
	 * (set difference: A minus B).
	 *
	 * <p>Operates on a defensive copy so neither input list is mutated.
	 * Blank strings ({@code ""} and {@code " "}) are excluded from the result.
	 *
	 * <p>Typical use: verify that every expected label appears on the page.
	 * <pre>
	 *   List<String> missing = listDifference(expectedLabels, actualLabels);
	 *   Assert.assertTrue(missing.isEmpty(),
	 *       "Labels missing from page: " + missing);
	 * </pre>
	 *
	 * @param listA source list (e.g. expected values)
	 * @param listB reference list to subtract (e.g. actual values)
	 * @return items in A that are absent from B; empty list when A is a subset of B
	 */
	public List<String> listDifference(List<String> listA, List<String> listB) {
		List<String> diff = new ArrayList<>(listA);
		diff.removeAll(listB);
		diff.remove("");
		diff.remove(" ");
		log.info("List difference (A minus B): " + diff + " (size=" + diff.size() + ")");
		return diff;
	}

	/**
	 * Harvests every {@code <label>} element on the current page and returns a
	 * map of trimmed label text -> {@code WebElement}.
	 *
	 * <p>Two overloads are provided:
	 * <ul>
	 *   <li>{@link #getPageLabels()} -- scans the whole page ({@code //label})</li>
	 *   <li>{@link #getPageLabels(String)} -- scans inside a scoping container
	 *       (e.g. a form or section) to avoid picking up nav / footer labels</li>
	 * </ul>
	 *
	 * <p>Typical usage in a test:
	 * <pre>
	 *   Map<String, WebElement> labels = getPageLabels("//form[@id='enrollForm']");
	 *   Assert.assertTrue(labels.containsKey("Pole ID"),      "Pole ID label missing");
	 *   Assert.assertTrue(labels.containsKey("Borough"),      "Borough label missing");
	 *   log.info("All labels found: " + labels.keySet());
	 * </pre>
	 *
	 * <p>Blank-text labels (hidden or icon-only) are silently skipped.
	 *
	 * @param scopeXpath XPath of the container to search within, e.g.
	 *                   {@code "//form[@id='enrollForm']"} or {@code "//main"};
	 *                   pass {@code null} or empty string to scan the whole page
	 * @return ordered {@code LinkedHashMap} of label text -> element; preserves DOM order
	 */
	public Map<String, WebElement> getPageLabels(String scopeXpath) {
		String xpath = (scopeXpath != null && !scopeXpath.isEmpty())
				? scopeXpath + "//label"
				: "//label";
		List<WebElement> labelElements = driver.findElements(By.xpath(xpath));
		Map<String, WebElement> labelMap = new java.util.LinkedHashMap<>();
		log.info("-- Page labels [" + xpath + "] -- " + labelElements.size() + " found --");
		for (WebElement label : labelElements) {
			String text = label.getText().trim();
			if (text.isEmpty()) continue;
			log.info("  label: '" + text + "'");
			labelMap.put(text, label);
		}
		return labelMap;
	}

	/**
	 * Harvests every {@code <label>} on the current page (whole-page scan).
	 *
	 * @return ordered map of label text -> element; blank-text labels are excluded
	 * @see #getPageLabels(String)
	 */
	public Map<String, WebElement> getPageLabels() {
		return getPageLabels(null);
	}

	/**
	 * Returns only the text keys from {@link #getPageLabels(String)}.
	 * Convenience method when you only need the strings (e.g. for assertions).
	 *
	 * @param scopeXpath container XPath or {@code null} for whole page
	 * @return ordered list of label texts; blank labels excluded
	 */
	public List<String> getPageLabelTexts(String scopeXpath) {
		return new ArrayList<>(getPageLabels(scopeXpath).keySet());
	}

	// -------------------------------------------------------------------------
	// Repeating Group -- Dynamic Locator Resolution & Interaction
	// -------------------------------------------------------------------------

	/**
	 * Builds a dynamic XPath at runtime by combining a shared attribute stem
	 * with a specific visible text value, then returns the matching element.
	 *
	 * <p>This is the generic resolver for repeating element groups detected by
	 * the crawler (e.g. all form labels sharing {@code contains(@id,'field-label')}).
	 * The pattern mirrors the user-supplied example:
	 * <pre>
	 *   //*[contains(@id,'field-label') and normalize-space(.)='Pole ID']
	 * </pre>
	 *
	 * <p>Two attribute types are supported:
	 * <ul>
	 *   <li>{@code "id"}    -> {@code //*[contains(@id,'stem') and normalize-space(.)='text']}</li>
	 *   <li>{@code "class"} -> {@code //*[contains(@class,'stem') and normalize-space(.)='text']}</li>
	 * </ul>
	 *
	 * @param attrType  {@code "id"} or {@code "class"}
	 * @param stem      shared attribute fragment, e.g. {@code "field-label"} or {@code "nav-item"}
	 * @param itemText  exact visible text of the target element
	 * @return the matching {@link WebElement}
	 * @throws org.openqa.selenium.NoSuchElementException when no match is found
	 */
	public WebElement findInGroupByText(String attrType, String stem, String itemText) {
		String xpath = buildGroupItemXpath(attrType, stem, itemText);
		log.info("findInGroupByText: " + xpath);
		return driver.findElement(By.xpath(xpath));
	}

	/**
	 * Finds an item in a repeating group by text and clicks it.
	 *
	 * <p>Waits up to 10 seconds for the element to be clickable before clicking.
	 * Use this for nav items, dropdown options, tab headers, list rows -- any set
	 * of elements sharing a common {@code id} or {@code class} stem.
	 *
	 * <pre>
	 *   // Click "New Reservation" from nav -- stem derived from crawler group report
	 *   clickInGroupByText("id", "nav-item", "New Reservation");
	 *
	 *   // Click a specific form label
	 *   clickInGroupByText("id", "field-label", "Pole ID");
	 *
	 *   // Click a tab by class stem
	 *   clickInGroupByText("class", "tab-header", "Documents");
	 * </pre>
	 *
	 * @param attrType  {@code "id"} or {@code "class"}
	 * @param stem      shared attribute fragment
	 * @param itemText  exact visible text of the target item
	 * @return {@code true} when element was found and clicked
	 */
	public boolean clickInGroupByText(String attrType, String stem, String itemText) {
		String xpath = buildGroupItemXpath(attrType, stem, itemText);
		log.info("clickInGroupByText: " + xpath);
		try {
			new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(10))
				.until(org.openqa.selenium.support.ui.ExpectedConditions
					.elementToBeClickable(By.xpath(xpath)));
			driver.findElement(By.xpath(xpath)).click();
			log.info("Clicked group item: '" + itemText + "'");
			return true;
		} catch (Exception e) {
			log.warn("clickInGroupByText failed for '" + itemText + "': " + e.getMessage());
			return false;
		}
	}

	/**
	 * Returns all visible text values from a repeating element group.
	 *
	 * <p>Builds the dynamic list XPath {@code //*[contains(@id,'stem')]} or
	 * {@code //*[contains(@class,'stem')]}, collects every element, and
	 * returns their trimmed text values. Blank texts are excluded.
	 *
	 * <pre>
	 *   List<String> labels = getGroupTexts("id", "field-label");
	 *   // -> ["Pole ID", "Borough", "Applicant Name", ...]
	 *   Assert.assertEquals(labels, expectedLabels);
	 * </pre>
	 *
	 * @param attrType {@code "id"} or {@code "class"}
	 * @param stem     shared attribute fragment
	 * @return ordered list of non-blank text values
	 */
	public List<String> getGroupTexts(String attrType, String stem) {
		String xpath = buildGroupListXpath(attrType, stem);
		List<WebElement> elements = driver.findElements(By.xpath(xpath));
		log.info("getGroupTexts [" + xpath + "] -- " + elements.size() + " elements");
		List<String> texts = new ArrayList<>();
		for (WebElement el : elements) {
			String text = el.getText().trim();
			log.info("  '" + text + "'");
			if (!text.isEmpty()) texts.add(text);
		}
		return texts;
	}

	/**
	 * Builds the dynamic XPath for a single item within a repeating group.
	 * This is the core runtime locator builder -- matches the crawler-generated
	 * template: {@code //*[contains(@id,'stem') and normalize-space(.)='text']}
	 *
	 * @param attrType {@code "id"} or {@code "class"}
	 * @param stem     shared attribute fragment
	 * @param itemText exact visible text
	 * @return fully formed XPath string
	 */
	public String buildGroupItemXpath(String attrType, String stem, String itemText) {
		String condition = "id".equalsIgnoreCase(attrType)
			? "contains(@id,'" + stem + "')"
			: "contains(@class,'" + stem + "')";
		return "//*[" + condition + " and normalize-space(.)='" + itemText + "']";
	}

	/**
	 * Builds the dynamic XPath for the full repeating group (list locator).
	 * Equivalent to the {@code @FindBy} list annotation generated by the crawler.
	 *
	 * @param attrType {@code "id"} or {@code "class"}
	 * @param stem     shared attribute fragment
	 * @return XPath matching all group members
	 */
	public String buildGroupListXpath(String attrType, String stem) {
		return "id".equalsIgnoreCase(attrType)
			? "//*[contains(@id,'" + stem + "')]"
			: "//*[contains(@class,'" + stem + "')]";
	}

	// -------------------------------------------------------------------------
	// Generic List Element Resolver -- iterate, match, build locator, act
	// -------------------------------------------------------------------------

	/**
	 * Action types supported by {@link #actOnListElement}.
	 */
	public enum ElementAction {
		/** Native click -- for links, buttons, nav items, dropdown options, tabs */
		CLICK,
		/** Type text into an input or textarea */
		TYPE,
		/** Select an {@code <option>} inside a {@code <select>} by visible text */
		SELECT,
		/** Returns the element without performing any action -- for assertions */
		FIND
	}

	/**
	 * Generic list element resolver.
	 *
	 * <p>Accepts any {@code List<WebElement>} obtained from a {@code @FindBy} field,
	 * iterates it, finds the first element whose visible text contains
	 * {@code matchText} (case-insensitive), builds a confirmed dynamic XPath from
	 * its real runtime attributes, then performs the requested {@link ElementAction}.
	 *
	 * <p>Element type is detected automatically:
	 * <ul>
	 *   <li>{@code <a>}, {@code <li>}, {@code <button>}, {@code <mat-option>},
	 *       {@code <div>/@role=option} -> {@link ElementAction#CLICK}</li>
	 *   <li>{@code <input>}, {@code <textarea>} -> {@link ElementAction#TYPE}
	 *       (requires {@code value} param)</li>
	 *   <li>{@code <select>} -> {@link ElementAction#SELECT}
	 *       (requires {@code value} param)</li>
	 * </ul>
	 *
	 * <p>Dynamic XPath priority (built from matched element's real attributes):
	 * <pre>
	 *   @id (exact)  ->  @id (contains stem)  ->  @routerlink  ->
	 *   @href (last segment)  ->  @formcontrolname  ->
	 *   normalize-space(.) exact  ->  normalize-space(.) contains
	 * </pre>
	 *
	 * <p>Usage examples:
	 * <pre>
	 *   // Click "New Reservation" from nav list
	 *   actOnListElement(navItems, "New Reservation", ElementAction.CLICK, null);
	 *
	 *   // Type into the "Pole ID" labelled input (list of all form inputs)
	 *   actOnListElement(formInputs, "Pole ID", ElementAction.TYPE, "13137");
	 *
	 *   // Select "Manhattan" from borough dropdowns
	 *   actOnListElement(boroughSelects, "Borough", ElementAction.SELECT, "Manhattan");
	 *
	 *   // Just find and return the element
	 *   WebElement el = actOnListElement(fieldLabels, "Status", ElementAction.FIND, null);
	 * </pre>
	 *
	 * @param elements  list of web elements (from {@code @FindBy} or
	 *                  {@code driver.findElements()})
	 * @param matchText text to match against each element's visible text
	 *                  (case-insensitive, substring match)
	 * @param action    action to perform on the matched element
	 * @param value     value for TYPE / SELECT actions; ignored for CLICK / FIND
	 * @return the matched {@link WebElement}, or {@code null} when not found
	 */
	public WebElement actOnListElement(List<WebElement> elements,
			String matchText, ElementAction action, String value) {

		log.info("actOnListElement: searching " + elements.size()
				+ " elements for '" + matchText + "' action=" + action);

		for (WebElement el : elements) {
			String text = el.getText().trim();
			log.info("  checking: '" + text + "' tag=" + el.getTagName());

			if (!text.toLowerCase().contains(matchText.toLowerCase())) continue;

			// --- Match found: build confirmed dynamic XPath ---
			String confirmedXpath = resolveConfirmedXpath(el);
			log.info("  CONFIRMED xpath: " + confirmedXpath);

			// --- Detect element type and resolve actual target ---
			String tag  = el.getTagName().toLowerCase();
			String role = safeAttr(el, "role");

			// For <li> containers: the real clickable is the <a> or <button> inside
			WebElement target = el;
			if (tag.equals("li") || tag.equals("div") || tag.equals("span")) {
				List<WebElement> anchors  = el.findElements(By.tagName("a"));
				List<WebElement> buttons  = el.findElements(By.tagName("button"));
				List<WebElement> inputs   = el.findElements(By.tagName("input"));
				if (!anchors.isEmpty())       target = anchors.get(0);
				else if (!buttons.isEmpty())  target = buttons.get(0);
				else if (!inputs.isEmpty())   target = inputs.get(0);
			}

			// --- Execute action ---
			switch (action) {
				case CLICK:
					log.info("  clicking: '" + text + "'");
					new org.openqa.selenium.support.ui.WebDriverWait(
							driver, java.time.Duration.ofSeconds(10))
						.until(org.openqa.selenium.support.ui.ExpectedConditions
							.elementToBeClickable(target));
					target.click();
					break;

				case TYPE:
					log.info("  typing '" + value + "' into: '" + text + "'");
					new org.openqa.selenium.support.ui.WebDriverWait(
							driver, java.time.Duration.ofSeconds(10))
						.until(org.openqa.selenium.support.ui.ExpectedConditions
							.visibilityOf(target));
					target.clear();
					target.sendKeys(value != null ? value : "");
					break;

				case SELECT:
					log.info("  selecting '" + value + "' from: '" + text + "'");
					new org.openqa.selenium.support.ui.Select(target)
						.selectByVisibleText(value != null ? value : "");
					break;

				case FIND:
					log.info("  found: '" + text + "'");
					break;

				default:
					log.warn("Unknown action: " + action);
			}

			return target;
		}

		log.warn("actOnListElement: no element found matching '" + matchText + "'");
		return null;
	}

	/**
	 * Builds the best confirmed dynamic XPath for a matched element by testing
	 * candidate strategies against the live DOM in priority order.
	 * Logs each candidate with UNIQUE [x] / NOT UNIQUE marker.
	 *
	 * Priority: @id (exact) -> @id (contains stem) -> @routerlink ->
	 *           @href -> @formcontrolname -> text exact -> text contains
	 *
	 * @param el the matched element
	 * @return first UNIQUE [x] XPath, or text-contains fallback
	 */
	public String resolveConfirmedXpath(WebElement el) {
		String tag   = el.getTagName().toLowerCase();
		String id    = safeAttr(el, "id");
		String fcn   = safeAttr(el, "formcontrolname");
		String rl    = safeAttr(el, "routerlink");
		String href  = safeAttr(el, "href");
		String text  = el.getText().trim();

		// Strip numeric suffix from id to get stem: "field-label-3" -> "field-label"
		String idStem = id.replaceAll("[-_]?\\d+$", "");

		List<String[]> candidates = new ArrayList<>();
		if (!id.isEmpty())
			candidates.add(new String[]{ "id-exact",    "//" + tag + "[@id='" + id + "']" });
		if (!idStem.isEmpty() && !idStem.equals(id))
			candidates.add(new String[]{ "id-stem",
				"//*[contains(@id,'" + idStem + "') and normalize-space(.)='" + text + "']" });
		if (!rl.isEmpty())
			candidates.add(new String[]{ "routerlink",  "//" + tag + "[@routerlink='" + rl + "']" });
		if (!href.isEmpty() && !href.equals("#") && !href.startsWith("javascript"))
			candidates.add(new String[]{ "href",
				"//" + tag + "[contains(@href,'" + lastHrefSegment(href) + "')]" });
		if (!fcn.isEmpty())
			candidates.add(new String[]{ "formcontrolname",
				"//" + tag + "[@formcontrolname='" + fcn + "']" });
		if (!text.isEmpty())
			candidates.add(new String[]{ "text-exact",
				"//" + tag + "[normalize-space(.)='" + text + "']" });
		if (!text.isEmpty())
			candidates.add(new String[]{ "text-contains",
				"//" + tag + "[contains(normalize-space(.),'" + text + "')]" });

		for (String[] c : candidates) {
			int count = driver.findElements(By.xpath(c[1])).size();
			String marker = count == 1 ? "UNIQUE [x]" : "NOT UNIQUE (" + count + ")";
			log.info("    [" + marker + "] [" + c[0] + "] " + c[1]);
			if (count == 1) return c[1];
		}

		// Absolute fallback
		return candidates.isEmpty() ? "//" + tag : candidates.get(candidates.size() - 1)[1];
	}

	/** Safe attribute reader -- returns empty string instead of null. */
	private String safeAttr(WebElement el, String attr) {
		try {
			String v = el.getAttribute(attr);
			return v != null ? v.trim() : "";
		} catch (Exception e) { return ""; }
	}

	/** Extracts last meaningful path segment from href for contains() matching. */
	private String lastHrefSegment(String href) {
		String clean = href.replaceAll("[#?].*", "");
		String[] parts = clean.split("/");
		for (int i = parts.length - 1; i >= 0; i--)
			if (!parts[i].isEmpty()) return parts[i];
		return href;
	}

	// -------------------------------------------------------------------------
	// Checkbox / Radio Button
	// -------------------------------------------------------------------------

	/**
	 * Checks a checkbox or radio button only if it is not already selected
	 * (idempotent).
	 */
	public void selectCheckbox(WebElement element) {
		log.info("Selecting checkbox: " + element.toString());
		if (!element.isSelected()) {
			element.click();
			log.info("Checkbox selected.");
		} else {
			log.info("Checkbox was already selected; no action taken.");
		}
	}

	/**
	 * Deselects a checkbox only if it is currently selected (idempotent).
	 */
	public void deselectCheckbox(WebElement element) {
		log.info("Deselecting checkbox: " + element.toString());
		if (element.isSelected()) {
			element.click();
			log.info("Checkbox deselected.");
		} else {
			log.info("Checkbox was already deselected; no action taken.");
		}
	}

	// -------------------------------------------------------------------------
	// JavaScript Utilities
	// -------------------------------------------------------------------------

	/**
	 * Executes arbitrary JavaScript with optional arguments. Returns whatever the
	 * script returns, or null.
	 */
	public Object executeScript(String script, Object... args) {
		log.info("Executing JavaScript: " + script);
		return ((JavascriptExecutor) driver).executeScript(script, args);
	}

	/**
	 * Scrolls the page to the very top.
	 */
	public void scrollToTop() {
		log.info("Scrolling to top of page");
		executeScript("window.scrollTo(0, 0);");
	}

	/**
	 * Scrolls the page to the very bottom.
	 */
	public void scrollToBottom() {
		log.info("Scrolling to bottom of page");
		executeScript("window.scrollTo(0, document.body.scrollHeight);");
	}

	// -------------------------------------------------------------------------
	// File Upload
	// -------------------------------------------------------------------------

	/**
	 * Uploads a file by sending the absolute file path to the input element. When
	 * running on a RemoteWebDriver (e.g. Selenium Grid) the LocalFileDetector is
	 * set automatically so the file is transferred to the remote node.
	 *
	 * @param fileInput the <input type="file"> element
	 * @param filePath  absolute path to the file on the local machine
	 */
	public void uploadFile(WebElement fileInput, String filePath) {
		log.info("Uploading file [" + filePath + "] via element: " + fileInput.toString());
		if (driver instanceof RemoteWebDriver) {
			((RemoteWebDriver) driver).setFileDetector(new LocalFileDetector());
		}
		fileInput.sendKeys(filePath);
	}

	/**
	 * Resolves the configured screenshot output directory and ensures it exists.
	 *
	 * @return screenshot output directory
	 */
	private File getScreenshotOutputDirectory() {
		String outputDir = YamlConfigReader.get("reporting.screenshotsDir",
				YamlConfigReader.get("screenshots.outputDir", "test-output/screenshots"));
		File directory = new File(System.getProperty("user.dir"), outputDir.replace("/", File.separator));
		ensureDirectoryExists(directory);
		return directory;
	}

	/**
	 * Returns the configured output directory for DOM dump files.
	 * Reads {@code reporting.domDumpsDir} from sdk-config.yaml.
	 *
	 * @return resolved, created directory
	 */
	private File getDomDumpsOutputDirectory() {
		String outputDir = YamlConfigReader.get("reporting.domDumpsDir",
				YamlConfigReader.get("screenshots.domDumpDir", "test-output/dom-dumps"));
		File directory = new File(System.getProperty("user.dir"), outputDir.replace("/", File.separator));
		ensureDirectoryExists(directory);
		return directory;
	}

	/**
	 * Returns the configured root output directory for accessibility artifacts
	 * (JSON scan files, Excel report, HTML summary).
	 * Reads {@code reporting.accessibilityDir} from sdk-config.yaml.
	 *
	 * @return resolved, created directory
	 */
	public File getAccessibilityOutputDirectory() {
		String outputDir = YamlConfigReader.get("reporting.accessibilityDir",
				YamlConfigReader.get("accessibility.output.dir", "test-output/accessibility"));
		File directory = new File(System.getProperty("user.dir"), outputDir.replace("/", File.separator));
		ensureDirectoryExists(directory);
		return directory;
	}

	/**
	 * Creates the supplied directory when it does not already exist.
	 *
	 * @param directory directory to create
	 */
	private void ensureDirectoryExists(File directory) {
		if (!directory.exists() && !directory.mkdirs()) {
			log.warn("[TestBase] Failed to create directory {}", directory.getAbsolutePath());
		}
	}

	private static String sanitizeFileName(String name) {
		if (name == null) return "unknown";
		return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
	}

	/**
	 * Waits for an element to become clickable using the provided timeout.
	 *
	 * @param element target element
	 * @param timeoutSeconds timeout in seconds
	 */
	private void waitUntilElementClickable(WebElement element, int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.elementToBeClickable(element));
	}

	/**
	 * Attempts to relocate a stale element using the provided locator.
	 *
	 * @param element stale element reference
	 * @param locator locator used to relocate the element
	 * @return relocated element or {@code null} when relocation fails
	 */
	private WebElement relocateElementHelper(WebElement element, By locator) {
		if (locator == null) {
			return null;
		}
		try {
			WebElement relocated = driver.findElement(locator);
			log.info("[TestBase] Relocated stale element via locator: {}", locator);
			return relocated;
		} catch (NoSuchElementException e) {
			log.warn("[TestBase] Could not relocate element with locator: {}", locator);
			return null;
		}
	}

	/**
	 * Retries clicking an element while handling intercepted and stale element
	 * failures.
	 *
	 * @param element target element
	 * @param locator locator used to re-find stale elements
	 * @param clickByJS whether to use JavaScript clicking
	 * @param retries remaining retry attempts
	 */
	private void elementClickHelper(WebElement element, By locator, boolean clickByJS,
	                                int retries) {
		retries = retries > 5 ? 5 : retries; // Maximum retries

		try {
			waitUntilElementClickable(element, DEFAULT_WAIT_SECONDS);
			scrollIntoView(element);

			if (clickByJS) {
				clickOnElementbyJavaScript(element);
			} else {
				element.click();
			}
		} catch (NoSuchElementException | TimeoutException e) {
			log.error("Element not found: {}", element);
			throw e;
		} catch (ElementClickInterceptedException e) {
			if (retries < 1) {
				log.error("Element click intercepted for element: {}", element);

				throw e;
			}

			clickByJS = !clickByJS;

			if (clickByJS) {
				log.info("Retries clicking the element by JavaScript");
			} else {
				log.info("Retries clicking the element");
			}

			elementClickHelper(element, locator, clickByJS, retries - 1);
		} catch (StaleElementReferenceException e) {
			log.info("Element is a stale element. Try to relocate the element with locator");

			element = relocateElementHelper(element, locator);

			if (retries < 1 || element == null) {
				throw e;
			}

			elementClickHelper(element, locator, clickByJS, retries - 1);

		} catch (Exception e) {
			if (retries < 1) {
				log.error("Failed to click the element: {}", element);

				throw e;
			}

			log.info("Retry clicking element");

			elementClickHelper(element, locator, clickByJS, retries - 1);
		}
	}

	// -------------------------------------------------------------------------
	// Email Verification (Mailinator)
	// -------------------------------------------------------------------------

	/**
	 * Extracts a URL from an email using a named template defined in
	 * {@code configuration/mailinator-email-templates.yaml}.
	 *
	 * <p>The inbox is polled with configurable retry (see {@code sdk-config.yaml}
	 * {@code api.mailinator.inboxPollTimeoutSeconds}) until the email arrives or
	 * the timeout expires.
	 *
	 * <p>Common use cases: registration confirmation links, password-reset links,
	 * deactivation links.
	 *
	 * <p>Example usage in a test:
	 * <pre>
	 * String confirmUrl = getEmailUrl("confirmation", baseURL, "user123@mailinator.com");
	 * driver.get(confirmUrl);
	 * </pre>
	 *
	 * @param templateName name of the template in {@code mailinator-email-templates.yaml}
	 *                     (e.g. {@code "confirmation"}, {@code "passwordReset"})
	 * @param baseURL      fallback URL returned when no matching email or link is found
	 * @param emailAddress Mailinator inbox address to poll
	 * @return extracted URL string, or {@code baseURL} as a fallback
	 * @throws Exception if the Mailinator API cannot be reached
	 */
	protected String getEmailUrl(String templateName, String baseURL, String emailAddress)
			throws Exception {
		return MailinatorEmailReader.getUrlFromEmail(templateName, baseURL, emailAddress);
	}

	/**
	 * Extracts a text value from an email body using a named template defined in
	 * {@code configuration/mailinator-email-templates.yaml}.
	 *
	 * <p>The template must declare a {@code textPattern} -- a Java regex with exactly
	 * one capture group. The captured value is returned after applying any configured
	 * {@code masks} (e.g. {@code uppercase}, {@code trim}, {@code dateFormat}).
	 *
	 * <p>Common use cases: OTP/verification codes, confirmation numbers, account IDs,
	 * token strings, formatted dates.
	 *
	 * <p>Example usage in a test:
	 * <pre>
	 * String otp = getEmailText("otpCode", "user123@mailinator.com");
	 * enterOtpField(otp);
	 * </pre>
	 *
	 * @param templateName name of the template in {@code mailinator-email-templates.yaml}
	 *                     (e.g. {@code "otpCode"}, {@code "accountNumber"})
	 * @param emailAddress Mailinator inbox address to poll
	 * @return extracted text value after masks applied, or {@code null} if not found
	 * @throws Exception if the Mailinator API cannot be reached, or the template has no
	 *                   {@code textPattern} defined
	 */
	protected String getEmailText(String templateName, String emailAddress) throws Exception {
		return MailinatorEmailReader.getTextFromEmail(templateName, emailAddress);
	}

	/**
	 * Extracts the confirmation/registration URL from an email.
	 * Shorthand for {@link #getEmailUrl(String, String, String)} with template {@code "confirmation"}.
	 *
	 * <p>Requires the {@code confirmation} template to be defined in
	 * {@code mailinator-email-templates.yaml}.
	 *
	 * @param baseURL      fallback URL returned when no matching email/link is found
	 * @param emailAddress Mailinator inbox address to poll
	 * @return confirmation URL, or {@code baseURL} as fallback
	 * @throws Exception if the Mailinator API cannot be reached
	 */
	protected String getConfirmationEmailUrl(String baseURL, String emailAddress) throws Exception {
		return MailinatorEmailReader.getConfirmationUrl(baseURL, emailAddress);
	}

	/**
	 * Extracts the deactivation/unsubscribe URL from an email.
	 * Shorthand for {@link #getEmailUrl(String, String, String)} with template {@code "deactivation"}.
	 *
	 * <p>Requires the {@code deactivation} template to be defined in
	 * {@code mailinator-email-templates.yaml}.
	 *
	 * @param baseURL      fallback URL returned when no matching email/link is found
	 * @param emailAddress Mailinator inbox address to poll
	 * @return deactivation URL, or {@code baseURL} as fallback
	 * @throws Exception if the Mailinator API cannot be reached
	 */
	protected String getDeactivationEmailUrl(String baseURL, String emailAddress) throws Exception {
		return MailinatorEmailReader.getDeactivationUrl(baseURL, emailAddress);
	}

	/**
	 * Deletes a specific email message by its Mailinator message ID.
	 *
	 * <p>Use this when you need surgical cleanup -- removing exactly one message
	 * rather than wiping the entire inbox.
	 *
	 * <p>The message ID is embedded in the URL returned by {@link #getEmailUrl}:
	 * extract it when you need targeted deletion, or use
	 * {@link #deleteAllEmailsForAddress(String)} to wipe the full inbox.
	 *
	 * @param emailId Mailinator message ID (e.g. {@code "user123-15234567890"})
	 * @return {@code true} if deletion was confirmed by the API
	 */
	protected boolean deleteEmailById(String emailId) {
		return MailinatorEmailReader.deleteEmailById(emailId);
	}

	/**
	 * Scroll {@code element} into view. The method will first attempt to scroll using action wheel
	 * input. If failed, it will fall back to JavaScript scrolling.
	 *
	 * @param element
	 * @return element scrolled into view
	 */
	/**
	 * Scrolls the element into the visible viewport using the Selenium Actions wheel input.
	 * Falls back to JavaScript {@code scrollIntoView(true)} on {@link WebDriverException}
	 * (e.g. older browser/driver versions that do not support {@code WheelInput.ScrollOrigin}).
	 *
	 * <p>Scroll strategy:
	 * <ul>
	 *   <li>If the element bottom is <em>below</em> the visible viewport, scrolls down by the
	 *       minimum amount required to bring the bottom edge into view.</li>
	 *   <li>If the element top is <em>above</em> the visible viewport (negative Y), scrolls up
	 *       by the element's top Y offset.</li>
	 *   <li>If the element is already fully within the viewport, no scroll is performed.</li>
	 * </ul>
	 *
	 * @param element target element to scroll into view; must be attached to the DOM
	 * @return the same element, enabling fluent chaining before an interaction
	 */
	public WebElement scrollIntoView(WebElement element) {
		log.info("Scrolling element into view: " + element);
		try {
			int viewportHeight = ((Long) ((JavascriptExecutor) driver)
					.executeScript("return window.innerHeight;")).intValue();

			int elementTopY    = element.getRect().getY();
			int elementBottomY = elementTopY + element.getSize().getHeight();

			int scrollY = 0;
			if (elementBottomY > viewportHeight) {
				// Element bottom is below the visible area -- scroll down minimally.
				scrollY = elementBottomY - viewportHeight;
			} else if (elementTopY < 0) {
				// Element top is above the visible area -- scroll up.
				scrollY = elementTopY;
			}
			// scrollY == 0 means the element is fully in the viewport; Actions still runs
			// fromElement which centres the wheel origin without moving the page.

			new Actions(driver)
					.scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(element), 0, scrollY)
					.perform();

			log.info("Scroll completed successfully");

		} catch (WebDriverException e) {
			log.info("Actions wheel scroll failed -- falling back to JavaScript scrollIntoView. Reason: " + e.getMessage());
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
		}
		return element;
	}

	// =========================================================================
	// Accessibility Testing API
	// =========================================================================

	/**
	 * Initialises the global accessibility reporter once per test suite.
	 *
	 * <p>Composed of two reporters: SLF4J (always active, logged to the normal
	 * test log) and Allure (active when Allure is on the classpath). Call this
	 * from {@code @BeforeSuite} in the consumer test suite, or let the
	 * {@code A11yTestNGListener} call it automatically.
	 *
	 * <p>Subsequent calls are safe: they are no-ops if accessibility checking is
	 * disabled in {@code sdk-config.yaml} ({@code accessibility.checking.enabled: false}).
	 */
	public void initAccessibility() {
			if (!"true".equalsIgnoreCase(System.getProperty("accessibility.checking.enabled", "false"))) {
				log.info("[A11Y] Accessibility checking disabled -- skipping initAccessibility()");
				return;
			}
			AccessibilityChecker.setReporter(
					new CompositeReporter(new Slf4jReporter(), new AllureA11yReporter()));
			log.info("[A11Y] Accessibility reporter initialised (SLF4J + Allure)");
	}

	/**
	 * Runs the full 5-layer accessibility scan for the current page.
	 *
	 * <p>Delegates to {@link A11ySessionManager} which enforces deduplication,
	 * per-URL scan limits, cooldowns, and the DOM-fingerprint change guard so
	 * that the same page is never scanned twice per test run.
	 *
	 * <p>The scan is skipped automatically when:
	 * <ul>
	 *   <li>{@code accessibility.checking.enabled=false} in sdk-config.yaml</li>
	 *   <li>{@code driver} is null (e.g. the browser was closed)</li>
	 * </ul>
	 *
	 * @param pageName  human-readable label for this page in reports; use
	 *                  {@code PageContext.currentPage.get()} when calling from
	 *                  a lifecycle hook, or pass the page class name explicitly
	 */
	public void runAccessibilityScan(String pageName) {
			if (!"true".equalsIgnoreCase(System.getProperty("accessibility.checking.enabled", "false"))) {
				return;
			}
			if (driver == null) {
				log.warn("[A11Y] driver is null — skipping accessibility scan for page: {}", pageName);
				return;
			}
			try {
				String resolvedName = A11ySessionManager.resolvePageName(driver, pageName);
				log.info("[A11Y] Running accessibility scan for page: {}", resolvedName);
				A11ySessionManager.checkFullSuite(driver, resolvedName);
			} catch (Exception e) {
				log.error("[A11Y] Accessibility scan threw an unexpected error for page [{}]: {}",
						pageName, e.getMessage(), e);
			}
	}

	/**
	 * Runs axe-core Layer 1 and asserts that no violations are found.
	 *
	 * <p>This is an asserting variant of {@link #runAccessibilityScan}: it will
	 * cause the test to fail if any WCAG violations are detected. Use it inside
	 * a test method body, not in a lifecycle hook.
	 *
	 * <p>The assertion is skipped automatically when accessibility checking is
	 * disabled in sdk-config.yaml.
	 *
	 * @param pageName  human-readable label, used in the assertion failure message
	 */
	public void assertNoAccessibilityViolations(String pageName) {
			if (!"true".equalsIgnoreCase(System.getProperty("accessibility.checking.enabled", "false"))) {
				return;
			}
			if (driver == null) {
				log.warn("[A11Y] driver is null — skipping assertNoAccessibilityViolations for: {}", pageName);
				return;
			}
			try {
				AccessibilityChecker.assertNoViolations(driver, pageName);
			} catch (AssertionError ae) {
				log.error("[A11Y] Accessibility violations found on page [{}]: {}", pageName, ae.getMessage());
				throw ae;
			} catch (Exception e) {
				log.error("[A11Y] assertNoAccessibilityViolations threw for page [{}]: {}", pageName, e.getMessage(), e);
			}
	}
}
