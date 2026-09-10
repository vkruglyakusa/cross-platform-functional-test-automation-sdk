package com.test.automation.sdk.utility;

import java.io.FileInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Excel_Reader {
	private static final Logger log = LogManager.getLogger(Excel_Reader.class);

	public String path;
	static FileInputStream fis;
	static XSSFWorkbook workbook;
	static XSSFSheet sheet;
	static XSSFRow row;
	XSSFCell cell;

	/**
	 * Constructs an Excel_Reader and opens the workbook at the given path.
	 *
	 * @param filePath absolute path to the .xlsx file
	 */
	public Excel_Reader(String filePath) {
		this.path = filePath;
		try {
			fis = new FileInputStream(filePath);
			workbook = new XSSFWorkbook(fis);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getCellData(String sheetName, String colName, int rowNum) {

		try {
			int col_Num = 0;
			int index = workbook.getSheetIndex(sheetName);
			sheet = workbook.getSheetAt(index);
			XSSFRow row = sheet.getRow(0);
			for (int i = 0; i < row.getLastCellNum(); i++) {
				if (row.getCell(i).getStringCellValue().equals(colName)) {
					col_Num = i;
				}
			}
			row = sheet.getRow(rowNum - 1);
			// System.out.println(col_Num);
			XSSFCell cell = row.getCell(col_Num);

			if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
				return cell.getStringCellValue();
			} else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
				return String.valueOf(cell.getNumericCellValue());
			} else if (cell.getCellType() == Cell.CELL_TYPE_BOOLEAN) {
				return String.valueOf(cell.getBooleanCellValue());
			} else if (cell.getCellType() == Cell.CELL_TYPE_BLANK) {
				return "";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getCellData(String sheetName, int cellNum, int rowNum) {

		try {
			int index = workbook.getSheetIndex(sheetName);
			sheet = workbook.getSheetAt(index);
			XSSFRow row = sheet.getRow(0);
			row = sheet.getRow(rowNum - 1);
			XSSFCell cell = row.getCell(cellNum);

			if (cell == null || cell.getCellType() == Cell.CELL_TYPE_BLANK && cell.getStringCellValue().isEmpty()) {
				return "";
			}else if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
				return cell.getStringCellValue();
			} else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
				return String.valueOf(cell.getNumericCellValue());
			} else if (cell.getCellType() == Cell.CELL_TYPE_BOOLEAN) {
				return String.valueOf(cell.getBooleanCellValue());
			} else if (cell.getCellType() == Cell.CELL_TYPE_BLANK) {
				return "";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static int getRowCount(String sheetName) {
		try {
			int index = workbook.getSheetIndex(sheetName);
			if (index == -1) {
				return 0;
			} else {
				sheet = workbook.getSheetAt(index);
				int number = sheet.getLastRowNum() + 1;
				return number;
			}
		} catch (Exception e) {
			// Expected for an empty/malformed sheet -- getDataFromSheet validates
			// rowNum/colNum before use and raises a descriptive IllegalStateException,
			// so this is logged at debug level only and never surfaced as a raw trace.
			log.debug("getRowCount('{}') could not read row count: {}", sheetName, e.getMessage());
		}
		return 0;

	}

	public static int getColumnCount(String sheetName) {
		try {
			int index = workbook.getSheetIndex(sheetName);
			if (index == -1) {
				return 0;
			} else {
				sheet = workbook.getSheet(sheetName);
				row = sheet.getRow(0);
				return row.getLastCellNum();
			}

		} catch (Exception e) {
			// Expected for an empty/malformed sheet (e.g. no header row) -- see note
			// above in getRowCount(). getDataFromSheet raises the descriptive exception.
			log.debug("getColumnCount('{}') could not read column count: {}", sheetName, e.getMessage());
		}
		return 0;

	}

	public static Object[][] getDataFromSheet(String path,String sheetName) {
		try {
			fis = new FileInputStream(path);
			workbook = new XSSFWorkbook(fis);
		} catch (Exception e) {
			throw new IllegalStateException(
					"Unable to open Excel workbook '" + path + "' while reading sheet '" + sheetName + "': "
							+ e.getMessage(), e);
		}

		int sheetIndex = workbook.getSheetIndex(sheetName);
		if (sheetIndex == -1) {
			throw new IllegalStateException(
					"Excel workbook '" + path + "' does not contain a sheet named '" + sheetName + "'.");
		}

		int rowNum = getRowCount(sheetName);
		int colNum = getColumnCount(sheetName);

		// rowNum counts the header row plus data rows; rowNum <= 1 means there are
		// zero usable data rows below the header (including a completely empty sheet,
		// where rowNum is 0). Fail with a descriptive message instead of allocating a
		// negatively-sized array (previously surfaced as a raw NegativeArraySizeException).
		if (rowNum <= 1 || colNum <= 0) {
			throw new IllegalStateException(
					"Excel sheet '" + sheetName + "' in workbook '" + path
							+ "' contains no usable data rows below the header (found " + Math.max(rowNum, 0)
							+ " row(s) and " + Math.max(colNum, 0) + " column(s)).");
		}

		Object sampleData[][] = new Object[rowNum - 1][colNum];
		for (int i = 2; i <= rowNum; i++) {
			for (int j = 0; j < colNum; j++) {
				//To do - add logic to filter out records where RunMode = N
				sampleData[i - 2][j] = getCellData(sheetName, j, i);
			}
		}
		return sampleData;
	}

}
