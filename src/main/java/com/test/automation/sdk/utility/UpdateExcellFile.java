package com.test.automation.sdk.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class UpdateExcellFile {

	private static XSSFWorkbook workbook;
	static Cell cell;

	public static void updateCell(String ExcelName, String sheetName, int rowNum, int colNum, String value) throws IOException {
		PropertiesReader prop = new PropertiesReader();
		System.out.println("Modify excel file - " + ExcelName + " and sheet name is " + sheetName);
		String path = System.getProperty("user.dir") + prop.getProperty("testDataDir") + ExcelName;
		System.out.println(path);
		try {

			FileInputStream file = new FileInputStream(path);

			workbook = new XSSFWorkbook(file);
			int index = workbook.getSheetIndex(sheetName);
			XSSFSheet sheet = workbook.getSheetAt(index);

			// Update the value of cell
			cell = sheet.getRow(rowNum).getCell(colNum);
			if (cell == null) {		   

				cell = sheet.getRow(rowNum).createCell(colNum);
			}

			cell.setCellValue(value);
			file.close();

			FileOutputStream outFile = new FileOutputStream(new File(path));
			workbook.write(outFile);
			outFile.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {

		updateCell("POLETOP_NONPROD_TestData.xlsx", "ProfileChangePwd", 1, 2, "test12347");
		//		updateCell("POLETOP_NONPROD_TestData.xlsx", "ProfileChangePwd", 2, 2,"E2E_apply_marriage_license");


	}

}
