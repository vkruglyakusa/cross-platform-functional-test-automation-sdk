package com.test.automation.sdk.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.Reporter;


/**
 * 
 * @author vkruglyak
 *
 */

public class Retry implements IRetryAnalyzer {
	public static final Logger log = LogManager.getLogger(Retry.class.getName());
	private int retryCount = 1;
	private int maxRetryCount = 3;

	public boolean retry(ITestResult result) {
		if (result.getStatus() == ITestResult.SUCCESS) {
			return false;
		}
		if (retryCount < maxRetryCount) {
			String data = "Retrying test " + result.getName() + " with status "
					+ getResultStatusName(result.getStatus()) + " for the " + (retryCount + 1) + " time(s).";
			log.info(data);
			Reporter.log(data);
			retryCount++;
			return true;
		}
		return false;
	}

	public static String getResultStatusName(int status) {
		if (status == ITestResult.SUCCESS) return "SUCCESS";
		if (status == ITestResult.FAILURE) return "FAILURE";
		if (status == ITestResult.SKIP)    return "SKIP";
		return "UNKNOWN";
	}

}
