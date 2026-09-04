package com.test.automation.sdk.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstructionExtractor.
 *
 * Verifies that:
 *  - All expected instruction resources exist in the JAR classpath
 *  - Resource content is non-empty and well-formed (starts with expected markdown)
 *  - extractResource writes files correctly to the target directory
 *  - Missing resources are handled gracefully (no exception thrown)
 *  - Output directories are created automatically
 */
@DisplayName("InstructionExtractor -- JAR resource extraction")
class InstructionExtractorTest {

    private static final String RESOURCE_PREFIX = "sdk-instructions/";

    // -- Resource existence ----------------------------------------------------

    @ParameterizedTest(name = "Resource exists in JAR: {0}")
    @ValueSource(strings = {
        "test-creation.instructions.md",
        "page-object-creation.instructions.md",
        "locator-strategy.instructions.md",
        "formal-testcase-to-script.instructions.md",
        "sdk-migration.instructions.md",
        "test-fix.instructions.md",
        "test-case-gap.instructions.md",
        "failure-investigation.instructions.md",
        "test-data-dependency.instructions.md",
        "copilot-instructions.md"
    })
    @DisplayName("All 10 instruction resources exist on classpath")
    void allInstructionResourcesExistOnClasspath(String fileName) {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream(RESOURCE_PREFIX + fileName);
        assertNotNull(is, "Resource not found on classpath: " + RESOURCE_PREFIX + fileName);
        try { is.close(); } catch (IOException ignored) {}
    }

    // -- Resource content ------------------------------------------------------

    @ParameterizedTest(name = "Resource is non-empty: {0}")
    @ValueSource(strings = {
        "test-creation.instructions.md",
        "page-object-creation.instructions.md",
        "locator-strategy.instructions.md",
        "formal-testcase-to-script.instructions.md",
        "sdk-migration.instructions.md",
        "test-fix.instructions.md",
        "test-case-gap.instructions.md",
        "failure-investigation.instructions.md",
        "test-data-dependency.instructions.md",
        "copilot-instructions.md"
    })
    @DisplayName("All instruction resources have non-empty content")
    void allInstructionResourcesHaveContent(String fileName) throws IOException {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream(RESOURCE_PREFIX + fileName);
        assertNotNull(is);
        byte[] bytes = readAllBytes(is);
        is.close();
        assertTrue(bytes.length > 100,
            "Resource appears empty or too small: " + fileName + " (" + bytes.length + " bytes)");
    }

    @Test
    @DisplayName("test-creation.instructions.md starts with YAML front matter or heading")
    void testCreationResource_startsWithExpectedContent() throws IOException {
        String content = readResource("test-creation.instructions.md");
        assertTrue(content.startsWith("---") || content.startsWith("#"),
            "test-creation.instructions.md should start with --- or # heading");
    }

    @Test
    @DisplayName("sdk-migration.instructions.md contains migration keyword")
    void sdkMigrationResource_containsMigrationContent() throws IOException {
        String content = readResource("sdk-migration.instructions.md");
        assertTrue(content.contains("test-automation-sdk"),
            "sdk-migration.instructions.md should reference test-automation-sdk");
    }

    @Test
    @DisplayName("locator-strategy.instructions.md contains UNIQUE keyword")
    void locatorStrategyResource_containsUniqueKeyword() throws IOException {
        String content = readResource("locator-strategy.instructions.md");
        assertTrue(content.contains("UNIQUE"),
            "locator-strategy.instructions.md should contain UNIQUE marker");
    }

    @Test
    @DisplayName("copilot-instructions.md contains project identity section")
    void copilotInstructions_containsProjectIdentity() throws IOException {
        String content = readResource("copilot-instructions.md");
        assertTrue(content.contains("TestBase") || content.contains("Selenium"),
            "copilot-instructions.md should contain TestBase or Selenium reference");
    }

    @Test
    @DisplayName("PROMPT_FILES includes start.prompt.md and extracts 7 prompts")
    void promptFiles_includeStartPrompt() throws Exception {
        Field field = InstructionExtractor.class.getDeclaredField("PROMPT_FILES");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> promptFiles = (List<String>) field.get(null);

        assertTrue(promptFiles.contains("start.prompt.md"),
            "PROMPT_FILES should include start.prompt.md");
        assertEquals(7, promptFiles.size(),
            "PROMPT_FILES should contain exactly 7 prompts");
    }

    // -- File extraction -------------------------------------------------------

    @Test
    @DisplayName("extractResource writes file with correct content to target path")
    void extractResource_writesFileWithCorrectContent(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "test-creation.instructions.md");

        invokeExtractResource(
            RESOURCE_PREFIX + "test-creation.instructions.md",
            outputFile.getAbsolutePath()
        );

        assertTrue(outputFile.exists(), "Output file should be created");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        // Verify content matches the JAR resource
        byte[] fromJar = readResourceBytes("test-creation.instructions.md");
        byte[] fromFile = Files.readAllBytes(outputFile.toPath());
        assertArrayEquals(fromJar, fromFile,
            "Extracted file content should match JAR resource content exactly");
    }

    @Test
    @DisplayName("extractResource creates parent directories automatically")
    void extractResource_createsParentDirectories(@TempDir File tempDir) throws Exception {
        File nestedOutput = new File(tempDir, "sub/dir/.github/instructions/test.md");
        assertFalse(nestedOutput.getParentFile().exists(), "Parent dir should not pre-exist");

        invokeExtractResource(
            RESOURCE_PREFIX + "test-creation.instructions.md",
            nestedOutput.getAbsolutePath()
        );

        assertTrue(nestedOutput.exists(), "File should be created with all parent dirs");
    }

    @Test
    @DisplayName("extractResource handles missing resource gracefully -- no exception thrown")
    void extractResource_missingResource_doesNotThrow(@TempDir File tempDir) {
        assertDoesNotThrow(() ->
            invokeExtractResource(
                RESOURCE_PREFIX + "nonexistent-file.md",
                new File(tempDir, "output.md").getAbsolutePath()
            )
        );
    }

    @Test
    @DisplayName("extractResource does NOT create file when resource is missing")
    void extractResource_missingResource_doesNotCreateFile(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "output.md");
        invokeExtractResource(RESOURCE_PREFIX + "nonexistent-file.md", outputFile.getAbsolutePath());
        assertFalse(outputFile.exists(), "No file should be created for a missing resource");
    }

    @Test
    @DisplayName("extractResource handles a root-relative output path with no parent directory")
    void extractResource_rootRelativeOutputPath_doesNotThrow() throws Exception {
        // A bare filename (no directory component) has a null getParentFile() --
        // regression test for the azure-pipelines.yml.template extraction crash.
        String outputPath = "instruction-extractor-test-root-file.md";
        File outputFile = new File(outputPath);
        File hashFile = new File(outputPath + ".sdkhash");
        try {
            assertDoesNotThrow(() ->
                invokeExtractResource(RESOURCE_PREFIX + "test-creation.instructions.md", outputPath));
            assertTrue(outputFile.exists(), "File should be created at the root-relative path");
        } finally {
            outputFile.delete();
            hashFile.delete();
        }
    }

    // -- Safe re-extraction (customization protection) -------------------------

    @Test
    @DisplayName("First extraction writes a .sdkhash sidecar recording the content hash")
    void extractResource_firstExtraction_writesHashSidecar(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "test-creation.instructions.md");
        File hashFile = new File(tempDir, "test-creation.instructions.md.sdkhash");

        invokeExtractResource(RESOURCE_PREFIX + "test-creation.instructions.md", outputFile.getAbsolutePath());

        assertTrue(hashFile.exists(), "Hash sidecar should be created alongside the extracted file");
        assertTrue(new String(Files.readAllBytes(hashFile.toPath()), "UTF-8").trim().matches("[0-9a-f]{64}"),
            "Sidecar should contain a 64-char SHA-256 hex hash");
    }

    @Test
    @DisplayName("Unmodified file (hash matches sidecar) is safely updated to newer SDK content")
    void extractResource_unmodifiedSinceLastExtraction_isUpdated(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "shared.md");

        // First extraction: simulate an older SDK version.
        invokeExtractResource(RESOURCE_PREFIX + "test-creation.instructions.md", outputFile.getAbsolutePath());
        byte[] firstContent = Files.readAllBytes(outputFile.toPath());

        // Second extraction with different resource content, simulating a newer SDK
        // version of the same file -- local copy was never touched, so it must update.
        invokeExtractResource(RESOURCE_PREFIX + "locator-strategy.instructions.md", outputFile.getAbsolutePath());
        byte[] secondContent = Files.readAllBytes(outputFile.toPath());

        assertFalse(Arrays.equals(firstContent, secondContent),
            "Unmodified local file should be refreshed to the newer SDK content");
        byte[] expected = readResourceBytes("locator-strategy.instructions.md");
        assertArrayEquals(expected, secondContent, "Updated content should match the newer resource exactly");
    }

    @Test
    @DisplayName("Locally customized file is NOT overwritten -- new SDK content goes to .sdk-new instead")
    void extractResource_customizedFile_isNotOverwritten(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "test-fix.instructions.md");
        File sdkNewFile = new File(tempDir, "test-fix.instructions.md.sdk-new");

        // Simulate a pre-existing, hand-customized file with no prior sidecar
        // (e.g. a legacy file that predates this safety mechanism, or one the
        // consumer intentionally forked and git-tracked).
        String customContent = "# My Custom Instructions\nTeam-specific content that must be preserved.";
        Files.write(outputFile.toPath(), customContent.getBytes("UTF-8"));

        invokeExtractResource(RESOURCE_PREFIX + "test-fix.instructions.md", outputFile.getAbsolutePath());

        String stillCustom = new String(Files.readAllBytes(outputFile.toPath()), "UTF-8");
        assertEquals(customContent, stillCustom, "Customized file content must be preserved, not overwritten");
        assertTrue(sdkNewFile.exists(), "Newer SDK version should be written to a .sdk-new sibling for manual merge");
        byte[] expected = readResourceBytes("test-fix.instructions.md");
        assertArrayEquals(expected, Files.readAllBytes(sdkNewFile.toPath()),
            ".sdk-new file should contain the exact current SDK resource content");
    }

    @Test
    @DisplayName("-Dsdk.forceExtract=true overwrites a customized file and clears any stale .sdk-new")
    void extractResource_forceExtract_overwritesCustomizedFile(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "test-fix.instructions.md");
        Files.write(outputFile.toPath(), "custom content".getBytes("UTF-8"));

        // First run without force: produces a .sdk-new (customization detected).
        invokeExtractResource(RESOURCE_PREFIX + "test-fix.instructions.md", outputFile.getAbsolutePath());
        File sdkNewFile = new File(tempDir, "test-fix.instructions.md.sdk-new");
        assertTrue(sdkNewFile.exists(), "Precondition: .sdk-new should exist before forcing");

        System.setProperty("sdk.forceExtract", "true");
        try {
            invokeExtractResource(RESOURCE_PREFIX + "test-fix.instructions.md", outputFile.getAbsolutePath());
        } finally {
            System.clearProperty("sdk.forceExtract");
        }

        byte[] expected = readResourceBytes("test-fix.instructions.md");
        assertArrayEquals(expected, Files.readAllBytes(outputFile.toPath()),
            "Forced extraction should overwrite the customized file with the SDK default");
        assertFalse(sdkNewFile.exists(), "Forced extraction should clear the stale .sdk-new file");
    }

    @Test
    @DisplayName("Re-extracting identical content does not create a .sdk-new file")
    void extractResource_identicalContentReExtracted_noSdkNewCreated(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "test-creation.instructions.md");
        File sdkNewFile = new File(tempDir, "test-creation.instructions.md.sdk-new");

        invokeExtractResource(RESOURCE_PREFIX + "test-creation.instructions.md", outputFile.getAbsolutePath());
        invokeExtractResource(RESOURCE_PREFIX + "test-creation.instructions.md", outputFile.getAbsolutePath());

        assertFalse(sdkNewFile.exists(), "No .sdk-new should be created when content is already up to date");
    }

    // -- Helpers ---------------------------------------------------------------

    private String readResource(String fileName) throws IOException {
        byte[] bytes = readResourceBytes(fileName);
        return new String(bytes, "UTF-8");
    }

    private byte[] readResourceBytes(String fileName) throws IOException {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream(RESOURCE_PREFIX + fileName);
        assertNotNull(is, "Resource not found: " + fileName);
        try {
            return readAllBytes(is);
        } finally {
            is.close();
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = is.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Invokes InstructionExtractor.extractResource via reflection (it's private). */
    private void invokeExtractResource(String resourcePath, String outputPath) throws Exception {
        Method m = InstructionExtractor.class.getDeclaredMethod(
            "extractResource", String.class, String.class);
        m.setAccessible(true);
        m.invoke(null, resourcePath, outputPath);
    }
}
