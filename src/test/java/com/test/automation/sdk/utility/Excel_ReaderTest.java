package com.test.automation.sdk.utility;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Excel_Reader using a programmatically-created .xlsx fixture.
 * <p>
 * Sheet layout: "TestData"
 * Row 0 (header): TestCaseName | Username | RunMode
 * Row 1 (data):   TC_001       | user1    | Y
 * Row 2 (data):   TC_002       | user2    | N
 */
@DisplayName("Excel_Reader - read cell data from xlsx fixture")
class Excel_ReaderTest {

    private static Path fixtureFile;
    private static Excel_Reader reader;

    @BeforeAll
    static void createFixture() throws Exception {
        fixtureFile = Files.createTempFile("sdk-test-data-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("TestData");

            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("TestCaseName");
            header.createCell(1).setCellValue("Username");
            header.createCell(2).setCellValue("RunMode");

            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("TC_001");
            row1.createCell(1).setCellValue("user1");
            row1.createCell(2).setCellValue("Y");

            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("TC_002");
            row2.createCell(1).setCellValue("user2");
            row2.createCell(2).setCellValue("N");

            try (OutputStream os = Files.newOutputStream(fixtureFile)) {
                wb.write(os);
            }
        }
        reader = new Excel_Reader(fixtureFile.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.deleteIfExists(fixtureFile);
    }

    @Test
    @DisplayName("getCellData by column name returns correct value (row 1)")
    void getCellDataByColName_row1() {
        // rowNum=2 -> sheet row index 1 = first data row (row 0 is header)
        assertEquals("TC_001", reader.getCellData("TestData", "TestCaseName", 2));
    }

    @Test
    @DisplayName("getCellData by column name returns correct value (row 2)")
    void getCellDataByColName_row2() {
        assertEquals("user2", reader.getCellData("TestData", "Username", 3));
    }

    @Test
    @DisplayName("getCellData RunMode row1 is Y")
    void getRunMode_row1() {
        assertEquals("Y", reader.getCellData("TestData", "RunMode", 2));
    }

    @Test
    @DisplayName("getCellData RunMode row2 is N")
    void getRunMode_row2() {
        assertEquals("N", reader.getCellData("TestData", "RunMode", 3));
    }
}