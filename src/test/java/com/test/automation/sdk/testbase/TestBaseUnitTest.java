package com.test.automation.sdk.testbase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TestBase - pure logic methods (no browser required)")
class TestBaseUnitTest extends TestBase {

    @Test
    @DisplayName("randomEmailAddress() matches expected pattern")
    void randomEmailAddress_matchesPattern() {
        String email = randomEmailAddress();
        assertNotNull(email);
        assertTrue(email.matches("^test\\d+@doitt\\.nyc\\.gov$"),
                "Email did not match pattern: " + email);
    }

    @Test
    @DisplayName("randomPassword() starts with 'test'")
    void randomPassword_startsWithTest() {
        String pwd = randomPassword();
        assertNotNull(pwd);
        assertTrue(pwd.startsWith("test"), "Password should start with 'test': " + pwd);
    }

    @Test
    @DisplayName("newUniqueUsername() starts with 'user'")
    void newUniqueUsername_startsWithUser() {
        String username = newUniqueUsername();
        assertNotNull(username);
        assertTrue(username.startsWith("user"), "Username should start with 'user': " + username);
    }

    @Test
    @DisplayName("verifyText() passes when texts match")
    void verifyText_passes_onMatch() {
        assertDoesNotThrow(() -> verifyText("Hello", "Hello"));
    }

    @Test
    @DisplayName("verifyText() throws AssertionError when texts differ")
    void verifyText_throws_onMismatch() {
        assertThrows(AssertionError.class, () -> verifyText("Hello", "World"));
    }

    @Test
    @DisplayName("randomEmailAddress() generates unique values")
    void randomEmailAddress_generatesUnique() {
        String a = randomEmailAddress();
        String b = randomEmailAddress();
        assertNotEquals(a, b, "Two random emails should not be equal");
    }

    // -------------------------------------------------------------------------
    // listDifference
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listDifference() returns items in A missing from B")
    void listDifference_returnsMissingItems() {
        List<String> expected = Arrays.asList("Pole ID", "Borough", "Status");
        List<String> actual   = Arrays.asList("Pole ID", "Borough");
        List<String> missing  = listDifference(expected, actual);
        assertEquals(Arrays.asList("Status"), missing);
    }

    @Test
    @DisplayName("listDifference() returns empty list when A is subset of B")
    void listDifference_emptyWhenNoMissing() {
        List<String> expected = Arrays.asList("Pole ID", "Borough");
        List<String> actual   = Arrays.asList("Pole ID", "Borough", "Status");
        List<String> missing  = listDifference(expected, actual);
        assertTrue(missing.isEmpty(), "Expected no missing items");
    }

    @Test
    @DisplayName("listDifference() strips blank and space-only entries")
    void listDifference_stripsBlankEntries() {
        List<String> a = Arrays.asList("Pole ID", "", " ", "Borough");
        List<String> b = Arrays.asList("Pole ID", "Borough");
        List<String> diff = listDifference(a, b);
        assertFalse(diff.contains(""),  "Blank string should be removed");
        assertFalse(diff.contains(" "), "Space string should be removed");
    }

    @Test
    @DisplayName("listDifference() does not mutate input lists")
    void listDifference_doesNotMutateInputs() {
        List<String> a = new ArrayList<>(Arrays.asList("A", "B", "C"));
        List<String> b = new ArrayList<>(Arrays.asList("A"));
        listDifference(a, b);
        assertEquals(3, a.size(), "Input list A must not be mutated");
        assertEquals(1, b.size(), "Input list B must not be mutated");
    }

    // -------------------------------------------------------------------------
    // buildGroupItemXpath / buildGroupListXpath
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("buildGroupItemXpath() produces id-contains + text XPath")
    void buildGroupItemXpath_idType() {
        String xpath = buildGroupItemXpath("id", "field-label", "Pole ID");
        assertEquals(
            "//*[contains(@id,'field-label') and normalize-space(.)='Pole ID']",
            xpath, "id-stem locator must match expected pattern");
    }

    @Test
    @DisplayName("buildGroupItemXpath() produces class-contains + text XPath")
    void buildGroupItemXpath_classType() {
        String xpath = buildGroupItemXpath("class", "nav-item", "New Reservation");
        assertEquals(
            "//*[contains(@class,'nav-item') and normalize-space(.)='New Reservation']",
            xpath, "class-stem locator must match expected pattern");
    }

    @Test
    @DisplayName("buildGroupListXpath() returns id-contains XPath for id type")
    void buildGroupListXpath_idType() {
        assertEquals("//*[contains(@id,'field-label')]",
            buildGroupListXpath("id", "field-label"));
    }

    @Test
    @DisplayName("buildGroupListXpath() returns class-contains XPath for class type")
    void buildGroupListXpath_classType() {
        assertEquals("//*[contains(@class,'tab-header')]",
            buildGroupListXpath("class", "tab-header"));
    }

    @Test
    @DisplayName("buildGroupItemXpath() is case-insensitive on attrType")
    void buildGroupItemXpath_caseInsensitive() {
        assertEquals(
            buildGroupItemXpath("id",  "field-label", "Borough"),
            buildGroupItemXpath("ID",  "field-label", "Borough"),
            "attrType must be handled case-insensitively");
    }

    // -------------------------------------------------------------------------
    // actOnListElement -- ElementAction enum accessibility
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ElementAction enum has all four expected values")
    void elementAction_enumValues() {
        ElementAction[] values = ElementAction.values();
        assertEquals(4, values.length);
        assertEquals(ElementAction.CLICK,  values[0]);
        assertEquals(ElementAction.TYPE,   values[1]);
        assertEquals(ElementAction.SELECT, values[2]);
        assertEquals(ElementAction.FIND,   values[3]);
    }

    @Test
    @DisplayName("actOnListElement() returns null and logs warning on empty list")
    void actOnListElement_emptyList_returnsNull() {
        List<WebElement> empty = new ArrayList<>();
        WebElement result = actOnListElement(empty, "anything", ElementAction.FIND, null);
        assertNull(result, "Should return null when list is empty");
    }

    // -------------------------------------------------------------------------
    // getPageLabels -- method presence (no browser needed)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getPageLabels() overload without scope is present on TestBase")
    void getPageLabels_noArgOverload_isCallable() {
        assertTrue(
            Arrays.stream(this.getClass().getMethods())
                  .anyMatch(m -> m.getName().equals("getPageLabels")),
            "getPageLabels() method must be present on TestBase");
    }

    @Test
    @DisplayName("setCurrentTestCaseName() stores and returns per-thread value")
    void currentTestCaseName_roundTrip() {
        TestBase.setCurrentTestCaseName("TC-101 Valid Login");
        assertEquals("TC-101 Valid Login", TestBase.getCurrentTestCaseName());
    }

    @Test
    @DisplayName("saveDomDump() writes HTML file using configured dom dump directory")
    void saveDomDump_writesHtmlFile() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        String originalConfigDir = System.getProperty("sdk.config.dir");
        File workDir = new File("target\\test-work\\saveDomDump");
        if (!workDir.exists()) {
            assertTrue(workDir.mkdirs() || workDir.exists(), "Failed to create work directory");
        }
        File configDir = new File(workDir, "configuration");
        if (!configDir.exists()) {
            assertTrue(configDir.mkdirs() || configDir.exists(), "Failed to create config directory");
        }

        // Screenshots dir is resolved as user.dir + outputDir from YAML
        File screenshotsDir = new File(workDir, "screenshots");
        if (screenshotsDir.exists()) {
            org.apache.commons.io.FileUtils.cleanDirectory(screenshotsDir);
        }
        screenshotsDir.mkdirs();

        File yaml = new File(configDir, "sdk-config.yaml");
        org.apache.commons.io.FileUtils.writeStringToFile(
                yaml,
                "screenshots:\n" +
                "  outputDir: \"screenshots\"\n",
                "UTF-8");

        try {
            System.setProperty("user.dir", workDir.getAbsolutePath());
            System.setProperty("sdk.config.dir", configDir.getAbsolutePath());
            resetYamlConfigReaderSingleton();
            WebDriver mockedDriver = Mockito.mock(WebDriver.class);
            Mockito.when(mockedDriver.getPageSource()).thenReturn("<html><body>hello</body></html>");
            Mockito.when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");

            saveDomDump(mockedDriver, "Case: 1/2");

            // DOM dump now co-located with screenshots, named *_DOM.html
            File[] htmlFiles = screenshotsDir.listFiles(new java.io.FilenameFilter() {
                public boolean accept(File dir, String name) { return name.endsWith("_DOM.html"); }
            });
            assertNotNull(htmlFiles, "DOM dump directory should exist");
            assertEquals(1, htmlFiles.length, "Expected one DOM dump file");
            assertTrue(htmlFiles[0].getName().startsWith("Case_ 1_2_"), "Filename should be sanitized");
            String content = org.apache.commons.io.FileUtils.readFileToString(htmlFiles[0], "UTF-8");
            assertTrue(content.contains("hello"), "DOM dump should contain page source");
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
            if (originalConfigDir != null) {
                System.setProperty("sdk.config.dir", originalConfigDir);
            } else {
                System.clearProperty("sdk.config.dir");
            }
            resetYamlConfigReaderSingleton();
        }
    }

    // -------------------------------------------------------------------------
    // findMatchingOption -- used by selectFromCustomWidget's retry loop
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMatchingOption() returns the option whose text matches")
    void findMatchingOption_returnsMatch() {
        WebElement option1 = Mockito.mock(WebElement.class);
        WebElement option2 = Mockito.mock(WebElement.class);
        Mockito.when(option1.getText()).thenReturn("Manhattan");
        Mockito.when(option2.getText()).thenReturn("Brooklyn");
        List<WebElement> options = Arrays.asList(option1, option2);

        WebElement match = findMatchingOption(options, "Brooklyn");
        assertSame(option2, match, "Should return the option with matching text");
    }

    @Test
    @DisplayName("findMatchingOption() returns null when no option matches")
    void findMatchingOption_returnsNull_whenNoMatch() {
        WebElement option1 = Mockito.mock(WebElement.class);
        Mockito.when(option1.getText()).thenReturn("Manhattan");
        List<WebElement> options = Arrays.asList(option1);

        assertNull(findMatchingOption(options, "Queens"), "Should return null when no option matches");
    }

    @Test
    @DisplayName("findMatchingOption() returns null for an empty option list")
    void findMatchingOption_returnsNull_whenEmpty() {
        assertNull(findMatchingOption(new ArrayList<WebElement>(), "Bronx"));
    }

    @Test
    @DisplayName("findMatchingOption() returns first match when duplicate text exists")
    void findMatchingOption_returnsFirstMatch_onDuplicates() {
        WebElement option1 = Mockito.mock(WebElement.class);
        WebElement option2 = Mockito.mock(WebElement.class);
        Mockito.when(option1.getText()).thenReturn("Staten Island");
        Mockito.when(option2.getText()).thenReturn("Staten Island");
        List<WebElement> options = Arrays.asList(option1, option2);

        assertSame(option1, findMatchingOption(options, "Staten Island"),
                "Should return the first matching option, and not iterate past it");
    }

    @Test
    @DisplayName("selectFromCustomWidget() is present on TestBase with expected signature")
    void selectFromCustomWidget_methodIsPresent() throws NoSuchMethodException {
        assertNotNull(TestBase.class.getMethod("selectFromCustomWidget",
                WebElement.class, org.openqa.selenium.By.class, org.openqa.selenium.By.class, String.class),
                "selectFromCustomWidget(WebElement, By, By, String) must be public on TestBase");
    }

    private void resetYamlConfigReaderSingleton() throws Exception {
        Class<?> clazz = Class.forName("com.test.automation.sdk.utility.YamlConfigReader");
        java.lang.reflect.Field field = clazz.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}