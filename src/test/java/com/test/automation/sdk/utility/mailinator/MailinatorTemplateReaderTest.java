package com.test.automation.sdk.utility.mailinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MailinatorTemplateReader -- YAML parsing, template building,
 * field defaults, masks, textPattern, and singleton reset.
 *
 * No network or Mailinator API access required.
 */
@DisplayName("MailinatorTemplateReader -- YAML template loading")
class MailinatorTemplateReaderTest {

    @TempDir
    File tempDir;

    private String originalConfigDir;

    @BeforeEach
    void setup() {
        originalConfigDir = System.getProperty("sdk.config.dir");
        reset();
    }

    @AfterEach
    void teardown() {
        reset();
        if (originalConfigDir != null) {
            System.setProperty("sdk.config.dir", originalConfigDir);
        } else {
            System.clearProperty("sdk.config.dir");
        }
        reset();
    }

    // -- No file present -------------------------------------------------------

    @Test
    @DisplayName("getTemplate() returns null when no yaml file exists")
    void noFile_getTemplate_returnsNull() {
        pointToEmptyDir();
        assertNull(MailinatorTemplateReader.getTemplate("anything"));
    }

    @Test
    @DisplayName("getAllTemplates() returns empty map when no yaml file exists")
    void noFile_getAllTemplates_isEmpty() {
        pointToEmptyDir();
        assertTrue(MailinatorTemplateReader.getAllTemplates().isEmpty());
    }

    // -- Single template -------------------------------------------------------

    @Test
    @DisplayName("Parses a single template name correctly")
    void singleTemplate_nameLoaded() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"Confirm Your Email\"\n" +
            "    urlPathPatterns: \"validateToken,validateReset\"\n" +
            "    excludePatterns: \"deactivate,amp;\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("confirmation");
        assertNotNull(t);
        assertEquals("confirmation", t.getName());
    }

    @Test
    @DisplayName("Parses subjectContains correctly")
    void singleTemplate_subjectContains() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"Confirm Your Email\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        assertEquals("Confirm Your Email",
                MailinatorTemplateReader.getTemplate("confirmation").getSubjectContains());
    }

    @Test
    @DisplayName("Parses urlPathPatterns as list split by comma")
    void singleTemplate_urlPathPatterns_splitByCsv() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"validateToken,validateReset,validateChangeEmail\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("confirmation");
        assertEquals(3, t.getUrlPathPatterns().size());
        assertTrue(t.getUrlPathPatterns().contains("validateToken"));
        assertTrue(t.getUrlPathPatterns().contains("validateReset"));
        assertTrue(t.getUrlPathPatterns().contains("validateChangeEmail"));
    }

    @Test
    @DisplayName("Parses excludePatterns as list split by comma")
    void singleTemplate_excludePatterns_splitByCsv() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"deactivate,amp;\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("confirmation");
        assertEquals(2, t.getExcludePatterns().size());
        assertTrue(t.getExcludePatterns().contains("deactivate"));
        assertTrue(t.getExcludePatterns().contains("amp;"));
    }

    @Test
    @DisplayName("Parses textPattern correctly")
    void singleTemplate_textPattern() throws IOException {
        writeYaml(
            "templates:\n" +
            "  otpCode:\n" +
            "    subjectContains: \"Your Code\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"Your code is: (\\d{6})\"\n" +
            "    masks: \"\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("otpCode");
        assertNotNull(t);
        assertEquals("Your code is: (\\d{6})", t.getTextPattern());
    }

    @Test
    @DisplayName("Parses masks as ordered list")
    void singleTemplate_masks_parsedInOrder() throws IOException {
        writeYaml(
            "templates:\n" +
            "  dateMask:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"trim,uppercase,dateFormat:MM/dd/yyyy:yyyy-MM-dd\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("dateMask");
        assertEquals(3, t.getMasks().size());
        assertEquals("trim",                              t.getMasks().get(0));
        assertEquals("uppercase",                         t.getMasks().get(1));
        assertEquals("dateFormat:MM/dd/yyyy:yyyy-MM-dd", t.getMasks().get(2));
    }

    // -- Empty / missing fields default to empty -------------------------------

    @Test
    @DisplayName("Empty urlPathPatterns field yields empty list")
    void emptyUrlPathPatterns_yieldsEmptyList() throws IOException {
        writeYaml(
            "templates:\n" +
            "  t:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        assertTrue(MailinatorTemplateReader.getTemplate("t").getUrlPathPatterns().isEmpty());
    }

    @Test
    @DisplayName("Missing optional field textPattern defaults to empty string")
    void missingTextPattern_defaultsToEmpty() throws IOException {
        writeYaml(
            "templates:\n" +
            "  t:\n" +
            "    subjectContains: \"Subject\"\n" +
            "    urlPathPatterns: \"path\"\n" +
            "    excludePatterns: \"\"\n" +
            "    masks: \"\"\n"
        );
        EmailTemplate t = MailinatorTemplateReader.getTemplate("t");
        assertNotNull(t);
        assertEquals("", t.getTextPattern());
    }

    // -- Multiple templates ----------------------------------------------------

    @Test
    @DisplayName("Loads multiple templates from one file")
    void multipleTemplates_allLoaded() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"Confirm\"\n" +
            "    urlPathPatterns: \"validateToken\"\n" +
            "    excludePatterns: \"deactivate\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n" +
            "  deactivation:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"deactivate\"\n" +
            "    excludePatterns: \"amp;\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n" +
            "  otpCode:\n" +
            "    subjectContains: \"Your Code\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"Code: (\\d{6})\"\n" +
            "    masks: \"trim\"\n"
        );
        Map<String, EmailTemplate> all = MailinatorTemplateReader.getAllTemplates();
        assertEquals(3, all.size());
        assertTrue(all.containsKey("confirmation"));
        assertTrue(all.containsKey("deactivation"));
        assertTrue(all.containsKey("otpCode"));
    }

    @Test
    @DisplayName("Each template has its own independent fields")
    void multipleTemplates_fieldsAreIndependent() throws IOException {
        writeYaml(
            "templates:\n" +
            "  alpha:\n" +
            "    subjectContains: \"Alpha Subject\"\n" +
            "    urlPathPatterns: \"alphaPath\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n" +
            "  beta:\n" +
            "    subjectContains: \"Beta Subject\"\n" +
            "    urlPathPatterns: \"betaPath\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        assertEquals("Alpha Subject", MailinatorTemplateReader.getTemplate("alpha").getSubjectContains());
        assertEquals("Beta Subject",  MailinatorTemplateReader.getTemplate("beta").getSubjectContains());
        assertEquals("alphaPath", MailinatorTemplateReader.getTemplate("alpha").getUrlPathPatterns().get(0));
        assertEquals("betaPath",  MailinatorTemplateReader.getTemplate("beta").getUrlPathPatterns().get(0));
    }

    // -- getTemplate() unknown name --------------------------------------------

    @Test
    @DisplayName("getTemplate() returns null for unknown template name")
    void getTemplate_unknownName_returnsNull() throws IOException {
        writeYaml(
            "templates:\n" +
            "  confirmation:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        assertNull(MailinatorTemplateReader.getTemplate("nonexistent"));
    }

    // -- getAllTemplates() is unmodifiable -------------------------------------

    @Test
    @DisplayName("getAllTemplates() returns unmodifiable map")
    void getAllTemplates_isUnmodifiable() throws IOException {
        writeYaml(
            "templates:\n" +
            "  t:\n" +
            "    subjectContains: \"\"\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        Map<String, EmailTemplate> all = MailinatorTemplateReader.getAllTemplates();
        assertThrows(UnsupportedOperationException.class,
                () -> all.put("injected", null));
    }

    // -- Comments and blank lines ----------------------------------------------

    @Test
    @DisplayName("Inline YAML comments are stripped from values")
    void parse_inlineComments_stripped() throws IOException {
        writeYaml(
            "templates:\n" +
            "  t:\n" +
            "    subjectContains: \"My Subject\" # inline comment\n" +
            "    urlPathPatterns: \"\"\n" +
            "    excludePatterns: \"\"\n" +
            "    textPattern: \"\"\n" +
            "    masks: \"\"\n"
        );
        assertEquals("My Subject",
                MailinatorTemplateReader.getTemplate("t").getSubjectContains());
    }

    // -- Helpers ---------------------------------------------------------------

    private void pointToEmptyDir() {
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        reset();
    }

    private void writeYaml(String content) throws IOException {
        File yaml = new File(tempDir, "mailinator-email-templates.yaml");
        FileWriter fw = new FileWriter(yaml);
        try {
            fw.write(content);
        } finally {
            fw.close();
        }
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        reset();
    }

    private static void reset() {
        MailinatorTemplateReader.reset();
    }
}
