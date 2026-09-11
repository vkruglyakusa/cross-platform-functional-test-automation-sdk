package com.test.automation.sdk.utility.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;

public class ExtentTestManager {
	private static final ThreadLocal<ExtentTest> currentTest = new ThreadLocal<ExtentTest>();
	static ExtentReports extent = ExtentManager.getInstance();

	public static synchronized ExtentTest getTest() {
		return currentTest.get();
	}

	public static synchronized void endTest() {
		currentTest.remove();
		extent.flush();
	}

	public static synchronized ExtentTest startTest(String testName) {
		ExtentTest test = extent.createTest(testName);
		currentTest.set(test);
		return test;
	}

}
