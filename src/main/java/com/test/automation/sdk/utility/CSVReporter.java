package com.test.automation.sdk.utility;
import com.test.automation.sdk.testbase.SdkConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.testng.ITestContext;

import com.opencsv.CSVWriter;

public class CSVReporter {
	public static String outputFile;
	private static String path;
	public static final Logger log = LogManager.getLogger(CSVReporter.class.getName());
	static Properties Prop = new Properties();
	static Calendar calendar;
	static File file;
	
	public CSVReporter() throws IOException {
        String log4jConfPath = SdkConfig.LOG4J_PROPERTIES;
        File file = new File(log4jConfPath);
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(file.toURI());;
	}
	
	/**
	 * Method creates CSV report file in predefined directory with a list of parameters specified in the test class
	 * @param fileName
	 * @param columnsList
	 * @param customFields
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void createCsvFileWithHaders(String fileName, String columnsList)
			throws IOException, InterruptedException {
		SimpleDateFormat formater = new SimpleDateFormat("dd_MM_yyyy");
		loadData();
		path = Prop.getProperty("pathToCsvReport");
		outputFile = System.getProperty("user.dir") + path + fileName + "_"+ formater.format(calendar.getTime())+".csv";
		System.out.println("Report Directory: " + outputFile);
		File file = new File(outputFile);
		if (!file.exists()) {
			CSVWriter csvWrite = new CSVWriter(new FileWriter(file, true));
			String[] record = columnsList.split(",");
			csvWrite.writeNext(record);
			csvWrite.close();
			log.info("CsvReportFile has been created and file neme is - " + fileName + ".csv");
		} else {
			log.info("File already exists.");
		}
	}

	/**
	 * Method adds the row to CSV report.
	 * @param row
	 * @throws IOException
	 */
	public static void addRowToCsvReport(String row) throws IOException {

		CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true));

		String[] record = row.split(",");

		writer.writeNext(record);

		writer.close();

	}

	/** 
	 * Method is responsible to add custom values to csv report. Expected parameters [Custom fields names ] and [Values] as a strings. 
	 * @param context
	 * @param fieldsNames
	 * @param values
	 */
	public static void addCustomFeldsValuesToReport(ITestContext context,String fieldsNames, String values) {
		context.setAttribute(fieldsNames,values);
	}
	
	public static void loadData() throws IOException {
		calendar = Calendar.getInstance();
		//Get output directory of CSV report from configuration file
		File file = new File(System.getProperty("user.dir") + "/configuration/config.properties");
		FileInputStream FI = new FileInputStream(file);
		Prop.load(FI);
	}

}