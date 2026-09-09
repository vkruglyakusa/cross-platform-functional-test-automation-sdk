package com.test.automation.sdk.utility;
import com.test.automation.sdk.config.SdkConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.monitorjbl.xlsx.StreamingReader;

public class CSVUtils {

    public static final Logger log = LogManager.getLogger( CSVUtils.class.getName());

	/**
	 * Method will convert specific sheet from .xls file to flat .csv file
	 * @param excelFile
	 * @param sheetName
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public static void convertExcelToCSV(String excelFile, String sheetName) throws InvalidFormatException, IOException {

		String log4jConfPath = SdkConfig.LOG4J_PROPERTIES;
        File file = new File(log4jConfPath);
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(file.toURI());;

		File f = new File(excelFile);

		String outputFile = System.getProperty("user.dir") + "/src/main/resources/sdk-defaults/dynamicCsv/" + f.getName();

		BufferedWriter output = new BufferedWriter( new FileWriter(outputFile.substring(0, outputFile.lastIndexOf(".")) + ".csv"));

		deleteFile(outputFile.substring(0, outputFile.lastIndexOf(".")) + ".csv"); //delete existing csv file before new file will be created.

		InputStream is = new FileInputStream(excelFile);

		Workbook wb = StreamingReader.builder().open(is);

		int index = wb.getSheetIndex(sheetName);

		Sheet sheet = wb.getSheetAt(index);
		int nomOfColumns = 0;
		int rowCount = 0;
		DataFormatter dataFormatter = new DataFormatter();
		log.info("The name of the sheet of processed xls document is: " + sheet.getSheetName());
		for (Row row : sheet) {
			rowCount++;
			if(rowCount == 1) {
				nomOfColumns =  row.getLastCellNum(); //count only number columns in the header of .xls
			}

			for (int i = 0; i < nomOfColumns; i++) {
				Cell cell = row.getCell(i);
				String value = dataFormatter.formatCellValue(cell);
				String buf = "";
				if (i > 0) {
					buf = ",";
				}
				if (cell == null) {
					output.write(buf);
				} else {
					output.write(buf + toCSV(value));
				}
			}
			output.write("\n");
		}

		is.close();
		output.close();
		log.info("Conversion of " + f.getName() + " to flat file: "
				+ f.getName().substring(0, f.getName().lastIndexOf(".")) + ".csv" + " is completed");

	}

	/*
	 * Method is returning value for output to a CSV file. Assumes the value
	 * does not have a double quote wrapper.
	 * 
	 * @return
	 */
	public static String toCSV(String value) {

		String v = null;
		boolean doWrap = false;

		if (value != null) {

			v = value;

			if (v.contains("\"")) {
				v = v.replace("\"", "\"\""); // escape embedded double quotes
				doWrap = true;
			}

			if (v.contains(",") || v.contains("\n")) {
				doWrap = true;
			}

			if (doWrap) {
				v = "\"" + v + "\""; // wrap with double quotes to hide the comma
			}
		}

		return v;
	}

	public static void deleteFile(String excelFile) throws InvalidFormatException, IOException {
	    String log4jConfPath = SdkConfig.LOG4J_PROPERTIES;
        File file = new File(log4jConfPath);
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(file.toURI());;

		// Get a file name from path
		File f = new File(excelFile);

		// Define path to the csv file
		String fl = System.getProperty("user.dir") + "/src/main/resources/sdk-defaults/dynamicCsv/"
				+ f.getName();

		File csvFile = new File(fl.substring(0, fl.lastIndexOf(".")) + ".csv");

		// delete converted csv existing file from directory
		if (csvFile.exists() && !csvFile.isDirectory()) {
			if (csvFile.delete()) {
				log.info("Existing file " + csvFile.getName() + " has been deleted successfully");
			} else {
				log.info("Existing file " + csvFile.getName() + " has been deleted successfully");
			}
		}

	}


	public static void main(String[] args) throws InvalidFormatException, IOException {

		String fileName = "POLETOP_NONPROD_TestData.xlsx";
		//System.out.println(System.getProperty("user.dir"));
		String pathToFile = System.getProperty("user.dir") + "/src/main/resources/sdk-defaults/";

		String file = pathToFile + fileName;
		//System.out.println(file);
		deleteFile(file.substring(0, file.lastIndexOf(".")) + ".csv");
		convertExcelToCSV(file, "Location Type");
	}

}
