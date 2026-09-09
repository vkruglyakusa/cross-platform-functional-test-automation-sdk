package com.test.automation.sdk.listener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.internal.BaseTestMethod;

import com.aventstack.extentreports.Status;


import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;

import com.test.automation.sdk.testbase.*;
import com.test.automation.sdk.utility.reports.ExtentManager;
import com.test.automation.sdk.utility.reports.ExtentTestManager;

public class Listener extends TestBase implements ITestListener, ISuiteListener, IInvokedMethodListener {

	//WebDriver driver;
	String testMethodParameters = null;

	public void onFinish(ITestContext arg0) {
		Reporter.log("\n ========Test is finished:" + arg0.getName()+"=========== \n", true);
		ExtentTestManager.endTest();
		ExtentManager.getInstance().flush();
		// NOTE (Phase 7 -- Session Isolation / Parallel Safety): testRetryCount was
		// converted from a global static to a TestBase instance field, so this
		// Listener instance's copy is a distinct object from the actual running
		// test class instance and resetting it here has no effect on that
		// instance's counter. No reset is needed: each test class instance
		// already starts with testRetryCount = 0 via its field initializer.
	}

	public void onStart(ITestContext context) {
		ExtentTestManager.startTest(context.getName());
		Reporter.log("\n ========About to begin executing Test " + context.getName() + "========= \n", true);
	}

	public void onTestFailedButWithinSuccessPercentage(ITestResult arg0) {

		printTestResults(arg0);

	}
	@Override
	public void onTestFailure(ITestResult result) {
		printTestResults(result);
		ExtentTestManager.getTest().log(Status.FAIL, "Test Failed");

		// Phase 4 fix: TestBase.driver is now an instance field (was static), so it
		// can no longer be read via the class-qualified TestBase.driver. Listener is
		// TestNG-instantiated separately from the actual running test class, so the
		// only correct way to reach that specific test's driver is via the ITestResult's
		// own instance -- this is also more correct for parallel execution than a
		// single shared static ever was.
		WebDriver driver = null;
		Object testInstance = result.getInstance();
		if (testInstance instanceof TestBase) {
			driver = ((TestBase) testInstance).driver;
		}
		if (driver != null) {
			try {
				// Guard: check session is still alive before taking screenshot
				driver.getWindowHandles();
				String testCaseName = TestBase.getCurrentTestCaseName();
				String captureName = (testCaseName != null && !testCaseName.trim().isEmpty())
						? testCaseName : getTestMethodName(result);
				getScreenShot(driver, result);
				saveDomDump(driver, captureName);
				log.info("Screenshot and DOM dump captured for: {}", captureName);
				takeScreenshot(driver);
			} catch (Exception e) {
				log.warn("Screenshot/DOM dump skipped -- driver session no longer active: " + e.getMessage());
			}
		}
		saveTextLog(getTestMethodName(result) + " failed and screenshot taken!");
	}

	public void onTestSkipped(ITestResult result) {
		Reporter.log("Test is skipped:" + result.getMethod().getMethodName());
		ExtentTestManager.getTest().log(Status.SKIP, " test is skipped and skip reason is:-" + result.getThrowable());
	}

	public void onTestStart(ITestResult result) {
		testMethodParameters = getTestInputArguments(result);
		setTestNameInXml(result);
		ExtentTestManager.startTest(StringUtils.substringAfterLast(result.getTestClass().getName(), ".") + "." + result.getMethod().getMethodName());
	}

	public void onTestSuccess(ITestResult result) {
		printTestResults(result);
		ExtentTestManager.getTest().log(Status.PASS, "Test passed");
	}

	private void printTestResults(ITestResult result) {

		Reporter.log("Test Method resides in " + result.getTestClass().getName(), true);

		if (result.getParameters().length != 0) {

			String params = "";

			Reporter.log("Test Method had the following parameters : " + getTestInputArguments(result), true);
			log.info("Test Method had the following parameters : " + getTestInputArguments(result));

		}

		String status = null;

		switch (result.getStatus()) {

		case ITestResult.SUCCESS:

			status = "Pass";

			break;

		case ITestResult.FAILURE:

			status = "Failed";

			break;

		case ITestResult.SKIP:

			status = "Skipped";

		}

		Reporter.log("Test Status: " + status, true);

	}

	public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
	}

	public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
	}
	private String getTestInputArguments(ITestResult result) {

		StringBuilder inputArguments = new StringBuilder();

		Object[] inputArgs = result.getParameters();
		inputArguments.append("( ");
		if (inputArgs != null && inputArgs.length > 0) {
			for (Object inputArg : inputArgs) {
				if (inputArg == null) {
					inputArguments.append("null");
				} else {
					inputArguments.append(inputArg.toString());
				}
				inputArguments.append(", ");
			}
			inputArguments.delete(inputArguments.length() - 2, inputArguments.length() - 1); // removing the last comma
		}
		inputArguments.append(")");

		return (inputArguments.toString().substring(1, inputArguments.toString().length()-1));
	}

	public void onStart(ISuite suite) {
		Reporter.log("About to begin executing Suite " + suite.getName(), true);
	}

	public void onFinish(ISuite suite) {
		Reporter.log("==========About to end executing Suite " + suite.getName() + "==============", true);
	}
	
	private void setTestNameInXml(ITestResult result) {
	String testCaseName = TestBase.getCurrentTestCaseName();
	if (testCaseName == null || testCaseName.trim().isEmpty()) {
		return;
	}
	String methodName = "";
		try {
			BaseTestMethod bm = (BaseTestMethod) result.getMethod().clone();
			Field f = bm.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			methodName = bm.getMethodName();
			if (methodName.contains(".")) {
				methodName = methodName.substring(0, methodName.indexOf("."));
			}
			f.set(bm, methodName + "." + testCaseName);
			f = result.getClass().getDeclaredField("m_method");
			f.setAccessible(true);
			f.set(result, bm);
			log.info("[Listener] Test renamed in XML: {}.{}", methodName, testCaseName);
		} catch (Exception ex) {
			log.warn("[Listener] Could not rename test in XML: {}", ex.getMessage());
		}
	}
	
	@Attachment(value = "Page screenshot", type = "image/png")
	private void takeScreenshot(WebDriver webDriver) throws IOException {
		  File screenshotAs = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.FILE);
		  Allure.addAttachment("Screenshot", FileUtils.openInputStream(screenshotAs));
		 }
		

	// Text attachments for Allure
	@Attachment(value = "{0}", type = "text/plain")
	public static String saveTextLog(String message) {
		return message;
	}
	
	private static String getTestMethodName(ITestResult iTestResult) {
		return iTestResult.getMethod().getConstructorOrMethod().getName();
	}

}