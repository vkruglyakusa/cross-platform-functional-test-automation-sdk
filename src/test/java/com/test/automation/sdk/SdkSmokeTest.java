package com.test.automation.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test -- verifies all key SDK public classes load without missing runtime dependencies.
 */
@DisplayName("SDK Smoke - all key classes loadable")
class SdkSmokeTest {

    @Test @DisplayName("SdkConfig loads")
    void sdkConfig() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.testbase.SdkConfig")); }

    @Test @DisplayName("TestBase loads")
    void testBase() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.testbase.TestBase")); }

    @Test @DisplayName("WebDriverFactory loads")
    void webDriverFactory() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.testbase.WebDriverFactory")); }

    @Test @DisplayName("PageContext loads")
    void pageContext() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.PageContext")); }

    @Test @DisplayName("Excel_Reader loads")
    void excelReader() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.Excel_Reader")); }

    @Test @DisplayName("Retry loads")
    void retry() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.listener.Retry")); }

    @Test @DisplayName("WebEventListener loads")
    void webEventListener() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.listener.WebEventListener")); }

    @Test @DisplayName("ExtentManager loads")
    void extentManager() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.reports.ExtentManager")); }

    @Test @DisplayName("YamlConfigReader loads")
    void yamlConfigReader() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.YamlConfigReader")); }

    @Test @DisplayName("InstructionExtractor loads")
    void instructionExtractor() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.InstructionExtractor")); }

    @Test @DisplayName("EmailTemplate loads")
    void emailTemplate() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.mailinator.EmailTemplate")); }

    @Test @DisplayName("EmailValueMask loads")
    void emailValueMask() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.mailinator.EmailValueMask")); }

    @Test @DisplayName("MailinatorTemplateReader loads")
    void mailinatorTemplateReader() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.mailinator.MailinatorTemplateReader")); }

    @Test @DisplayName("MailinatorEmailReader loads")
    void mailinatorEmailReader() { assertDoesNotThrow(() -> Class.forName("com.test.automation.sdk.utility.mailinator.MailinatorEmailReader")); }
}