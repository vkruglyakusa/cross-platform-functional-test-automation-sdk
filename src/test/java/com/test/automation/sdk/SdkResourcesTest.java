package com.test.automation.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all SDK resource files (templates + instructions) are present
 * on the classpath -- i.e., they are correctly packaged inside the JAR.
 *
 * This test acts as a packaging gate: if any resource is missing from
 * src/main/resources, it will fail here before the JAR is published.
 */
@DisplayName("SDK Resources -- all templates and instructions packaged in JAR")
class SdkResourcesTest {

    // -- sdk-defaults templates -------------------------------------------------

    @ParameterizedTest(name = "sdk-defaults template exists: {0}")
    @ValueSource(strings = {
        "sdk-defaults/sdk-config.yaml.template",
        "sdk-defaults/log4j2.xml.template",
        "sdk-defaults/config.properties.template",
        "sdk-defaults/log4j2.properties.template"
    })
    @DisplayName("All sdk-defaults template files are packaged in JAR")
    void sdkDefaultsTemplatesExist(String resourcePath) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Template resource missing from JAR: " + resourcePath);
        assertTrue(is.read() != -1, "Template resource is empty: " + resourcePath);
        is.close();
    }

    // -- sdk-templates (gap report template) ----------------------------------

    @ParameterizedTest(name = "sdk-templates file exists: {0}")
    @ValueSource(strings = {
        "sdk-templates/test-case-gap-report.md.template"
    })
    @DisplayName("All sdk-templates files are packaged in JAR")
    void sdkTemplateFilesExist(String resourcePath) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Template resource missing from JAR: " + resourcePath);
        assertTrue(is.read() != -1, "Template resource is empty: " + resourcePath);
        is.close();
    }

    @ParameterizedTest(name = "test-case-gap-report.md.template contains section: {0}")
    @ValueSource(strings = {
        "Identification",
        "Blocking Gaps",
        "Gap Detail",
        "Partially Implementable Steps",
        "Recommended Resolution",
        "Next Steps After Resolution",
        "Resolution Log"
    })
    @DisplayName("test-case-gap-report.md.template contains all required sections")
    void gapReportTemplate_containsAllSections(String section) throws IOException {
        String content = readResource("sdk-templates/test-case-gap-report.md.template");
        assertTrue(content.contains(section),
            "Gap report template missing section: " + section);
    }

    // -- sdk-instructions files -------------------------------------------------

    @ParameterizedTest(name = "sdk-instructions file exists: {0}")
    @ValueSource(strings = {
        "sdk-instructions/test-creation.instructions.md",
        "sdk-instructions/page-object-creation.instructions.md",
        "sdk-instructions/locator-strategy.instructions.md",
        "sdk-instructions/formal-testcase-to-script.instructions.md",
        "sdk-instructions/sdk-migration.instructions.md",
        "sdk-instructions/test-fix.instructions.md",
        "sdk-instructions/test-case-gap.instructions.md",
        "sdk-instructions/copilot-instructions.md"
    })
    @DisplayName("All sdk-instructions files are packaged in JAR")
    void sdkInstructionFilesExist(String resourcePath) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Instruction resource missing from JAR: " + resourcePath);
        assertTrue(is.read() != -1, "Instruction resource is empty: " + resourcePath);
        is.close();
    }

    // -- Prompt files ----------------------------------------------------------

    @ParameterizedTest(name = "sdk-prompts file exists: {0}")
    @ValueSource(strings = {
        "sdk-prompts/create-test.prompt.md",
        "sdk-prompts/modify-test.prompt.md",
        "sdk-prompts/fix-failed-test.prompt.md",
        "sdk-prompts/ado-sync-test.prompt.md",
        "sdk-prompts/fix-broken-locator.prompt.md",
        "sdk-prompts/report-test-gap.prompt.md"
    })
    @DisplayName("All sdk-prompts files are packaged in JAR")
    void sdkPromptFilesExist(String resourcePath) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Instruction resource missing from JAR: " + resourcePath);
        assertTrue(is.read() != -1, "Instruction resource is empty: " + resourcePath);
        is.close();
    }

    // -- AI assets (ai/prompts, ai/skills, ai/schemas) --------------------------

    @ParameterizedTest(name = "ai/prompts file exists: {0}")
    @ValueSource(strings = {
        "ai/README.md",
        "ai/prompts/element-discovery/analyze-locator-candidates.md",
        "ai/skills/element-discovery/element-discovery.skill.yaml",
        "ai/schemas/discovery-result-v1.schema.json",
        "ai/schemas/locator-recommendation-v1.schema.json"
    })
    @DisplayName("All ai/ asset files are packaged in JAR")
    void aiAssetFilesExist(String resourcePath) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "AI asset resource missing from JAR: " + resourcePath);
        assertTrue(is.read() != -1, "AI asset resource is empty: " + resourcePath);
        is.close();
    }

    @Test
    @DisplayName("analyze-locator-candidates.md prompt has required YAML front-matter fields")
    void analyzeLocatorCandidatesPrompt_hasFrontMatter() throws IOException {
        String content = readResource("ai/prompts/element-discovery/analyze-locator-candidates.md");
        assertTrue(content.trim().startsWith("---"), "Prompt must start with YAML front matter");
        for (String field : new String[] {"name:", "version:", "capability:", "inputSchema:", "outputSchema:", "requiredTools:"}) {
            assertTrue(content.contains(field), "Prompt front matter missing field: " + field);
        }
    }

    // -- Content checks on key templates --------------------------------------

    @ParameterizedTest(name = "sdk-config.yaml.template contains section: {0}")
    @ValueSource(strings = {
        "browser:", "proxy:", "api:", "logging:", "screenshots:"
    })
    @DisplayName("sdk-config.yaml.template contains all required sections")
    void sdkConfigTemplate_containsAllSections(String expectedSection) throws IOException {
        String content = readResource("sdk-defaults/sdk-config.yaml.template");
        assertTrue(content.contains(expectedSection),
            "sdk-config.yaml.template missing section: " + expectedSection);
    }

    @Test
    @DisplayName("sdk-config.yaml.template contains reporting.domDumpsDir")
    void sdkConfigTemplate_containsDomDumpDir() throws IOException {
        String content = readResource("sdk-defaults/sdk-config.yaml.template");
        assertTrue(content.contains("domDumpsDir:"),
            "sdk-config.yaml.template missing domDumpsDir (moved to reporting: section)");
    }

    @ParameterizedTest(name = "log4j2.xml.template contains appender: {0}")
    @ValueSource(strings = {
        "SdkFileAppender", "SeleniumFileAppender", "BrowserFileAppender", "Console"
    })
    @DisplayName("log4j2.xml.template contains all required appenders")
    void log4j2Template_containsAllAppenders(String appenderName) throws IOException {
        String content = readResource("sdk-defaults/log4j2.xml.template");
        assertTrue(content.contains(appenderName),
            "log4j2.xml.template missing appender: " + appenderName);
    }

    @ParameterizedTest(name = "log4j2.xml.template contains logger for: {0}")
    @ValueSource(strings = {
        "com.test.automation.sdk",
        "org.openqa.selenium",
        "io.github.bonigarcia"
    })
    @DisplayName("log4j2.xml.template defines loggers for key packages")
    void log4j2Template_containsKeyLoggers(String loggerName) throws IOException {
        String content = readResource("sdk-defaults/log4j2.xml.template");
        assertTrue(content.contains(loggerName),
            "log4j2.xml.template missing logger for: " + loggerName);
    }

    // -- Helpers ---------------------------------------------------------------

    private String readResource(String path) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(is, "Resource not found: " + path);
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = is.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        is.close();
        return out.toString("UTF-8");
    }
}
