package com.test.automation.sdk.utility.reports;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.test.automation.sdk.utility.PropertiesReader;

public class ExtentManager {
	private static final Logger log = LogManager.getLogger(ExtentManager.class);
	private static String reportDir;
	private static ExtentReports extent;
	private static String reportFileName = "Test-Automaton-Report" + ".html";
	private static String reportFilepath;
	private static String reportFileLocation;

	public static ExtentReports getInstance() {

		if (extent == null)
			try {
				createInstance();
			} catch (IOException e) {
				log.error("Failed to create Extent report instance", e);
			}
		return extent;
	}

	// Create an extent report instance
	public static ExtentReports createInstance() throws IOException {
		PropertiesReader propReader = new PropertiesReader();
		reportDir = propReader.getProperty("extReportDir");
		reportFilepath = System.getProperty("user.dir") + reportDir;
		//System.out.println(reportFilepath);
		reportFileLocation = reportFilepath + reportFileName;
		//new File(reportFileLocation).mkdir();

		String fileName = getReportPath(reportFilepath);
		//System.out.println(fileName);
		ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter(fileName);
		htmlReporter.config().setTheme(Theme.STANDARD);
		htmlReporter.config().setDocumentTitle(reportFileName);
		htmlReporter.config().setEncoding("utf-8");
		htmlReporter.config().setReportName(reportFileName);
		htmlReporter.config().setTimeStampFormat("EEEE, MMMM dd, yyyy, hh:mm a '('zzz')'");

		extent = new ExtentReports();
		extent.attachReporter(htmlReporter);
		// Set environment details
		extent.setSystemInfo("OS", "Windows");
		extent.setSystemInfo("AUT", "QA");

		return extent;
	}

	// Create the report path
	private static String getReportPath(String path) {
		File testDirectory = new File(path);
		if (!testDirectory.exists()) {
			Path filePath = Paths.get(reportFileLocation);
			try {
				Files.createDirectories(filePath.getParent());
			} catch (IOException e) {
				log.error("Failed to create Extent report directory {}", filePath.getParent(), e);
			}
		} else {
			log.debug("Extent report directory already exists: {}", path);
		}
		return reportFileLocation;
	}
}
