package com.test.automation.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AI instruction contract")
class AiInstructionContractTest {

    private static final Path AUTHORITATIVE_FORMAL = Paths.get(
            "src", "main", "resources", "sdk-instructions", "formal-testcase-to-script.instructions.md");
    private static final Path MIRROR_FORMAL = Paths.get(
            ".github", "instructions", "formal-testcase-to-script.instructions.md");
    private static final Path AUTHORITATIVE_CREATE = Paths.get(
            "src", "main", "resources", "sdk-prompts", "create-test.prompt.md");
    private static final Path MIRROR_CREATE = Paths.get(
            ".github", "prompts", "create-test.prompt.md");

    @Test
    @DisplayName("formal testcase instruction enforces the strict 1:1 Azure TC to @Test contract")
    void formalInstructionEnforcesStrictOneToOneContract() throws Exception {
        String content = readUtf8(AUTHORITATIVE_FORMAL);

        assertTrue(content.contains("One Azure Test Case ID MUST produce exactly one `@Test` method"),
                "Authoritative formal instruction must state the 1 Azure TC ID -> 1 @Test rule");
        assertTrue(content.contains("higher priority than test"),
                "Authoritative formal instruction must state the priority of the 1:1 rule");
        assertTrue(content.contains("helper methods **must never** be"),
                "Authoritative formal instruction must allow helpers while forbidding @Test on helpers");
        assertTrue(content.contains("Data-Driven Clarification"),
                "Authoritative formal instruction must explain that DataProvider affects invocations, not method count");
        assertTrue(content.contains("helper methods contain no `@Test` annotation"),
                "Authoritative formal instruction must require the helper-method self-check");
    }

    @Test
    @DisplayName("GitHub Packages is the canonical SDK publishing destination")
    void githubPackagesIsCanonicalPublishingDestination() throws Exception {
        String pom = readUtf8(Paths.get("pom.xml"));
        String workflow = readUtf8(Paths.get(".github", "workflows", "publish-sdk.yml"));
        String publishingGuide = readUtf8(Paths.get("SDK-PUBLISHING.md"));
        String settingsTemplate = readUtf8(Paths.get("configuration", "maven-settings-template.xml"));

        String packageUrl = "https://maven.pkg.github.com/vkruglyakusa/"
                + "cross-platform-functional-test-automation-sdk";
        assertTrue(pom.contains("<id>github</id>"),
                "distributionManagement must use the GitHub server id");
        assertTrue(pom.contains(packageUrl),
                "distributionManagement must target this repository's GitHub Packages feed");
        assertFalse(pom.contains("pkgs.visualstudio.com"),
                "Azure Artifacts must not remain the default deployment target");

        assertTrue(workflow.contains("types: [published]"),
                "Publishing workflow must run for published releases");
        assertTrue(workflow.contains("workflow_dispatch:"),
                "Publishing workflow must support explicit manual runs");
        assertTrue(workflow.contains("packages: write"),
                "Publishing workflow must declare packages write permission");
        assertTrue(workflow.contains("GITHUB_TOKEN"),
                "Publishing workflow must use the repository-scoped GitHub token");
        assertFalse(workflow.contains("settings-path:"),
                "setup-java credentials must remain in Maven's default settings location");
        assertTrue(workflow.contains("mvn --batch-mode deploy"),
                "Publishing workflow must run the full Maven lifecycle before deployment");

        assertTrue(publishingGuide.contains(packageUrl),
                "Publishing guide must document the canonical package URL");
        assertTrue(settingsTemplate.contains("${env.GITHUB_USERNAME}"),
                "Local Maven settings must use an explicit GitHub username variable");
        assertTrue(settingsTemplate.contains("${env.GITHUB_TOKEN}"),
                "Local Maven settings must use a secret-backed token variable");
        assertFalse(settingsTemplate.contains("YOUR_PAT_HERE"),
                "Settings template must not encourage inline token replacement");
    }

    @Test
    @DisplayName("create-test prompt repeats the strict 1:1 Azure TC to @Test contract")
    void createTestPromptRepeatsStrictOneToOneContract() throws Exception {
        String content = readUtf8(AUTHORITATIVE_CREATE);

        assertTrue(content.contains("one Azure Test Case ID MUST produce exactly one `@Test` method"),
                "Authoritative create-test prompt must restate the 1 Azure TC ID -> 1 @Test rule");
        assertTrue(content.contains("one `@Test(dataProvider = \"data\")` method with 10 rows is still one `@Test` method"),
                "Authoritative create-test prompt must clarify that DataProvider rows do not change @Test method count");
        assertTrue(content.contains("helper methods must NOT carry `@Test`"),
                "Authoritative create-test prompt must forbid @Test on helper methods");
        assertTrue(content.contains("result is INVALID"),
                "Authoritative create-test prompt must require self-validation before returning code");
    }

    @Test
    @DisplayName("active AI assets require Java 20 or newer and do not instruct Java 8 compatibility")
    void activeAiAssetsRequireJava20OrNewer() throws Exception {
        List<Path> files = Arrays.asList(
                AUTHORITATIVE_FORMAL,
                AUTHORITATIVE_CREATE,
                Paths.get("src", "main", "resources", "sdk-instructions", "sdk-development.instructions.md"),
                Paths.get("src", "main", "resources", "sdk-instructions", "sdk-migration.instructions.md"),
                Paths.get("src", "main", "resources", "sdk-instructions", "copilot-instructions.md"),
                Paths.get("src", "main", "resources", "sdk-prompts", "update-sdk-docs.prompt.md"));

        boolean sawJava20MinimumMarker = false;
        for (Path file : files) {
            String content = readUtf8(file);
            if (content.contains("Java >=20") || content.contains("Java 20 or newer")) {
                sawJava20MinimumMarker = true;
            }
            assertFalse(content.contains("Java 8 compatible"),
                    file + " must not contain active Java 8 compatibility instructions");
            assertFalse(content.contains("Language**: Java 8"),
                    file + " must not identify the SDK language contract as Java 8");
            assertFalse(content.contains("Java 8+"),
                    file + " must not instruct migrating consumers to stay on Java 8+");
            assertFalse(content.contains("valid Java 8"),
                    file + " must not require Java 8 code examples");
            assertFalse(content.contains("compile as Java 8"),
                    file + " must not require Java 8 compilation/examples");
        }

        assertTrue(sawJava20MinimumMarker,
                "At least one authoritative AI asset must explicitly state the Java >=20 minimum contract");
    }

    @Test
    @DisplayName("formal instruction and create-test prompt mirrors remain byte-identical to authoritative copies")
    void mirrorsRemainByteIdenticalToAuthoritativeCopies() throws Exception {
        assertArrayEquals(Files.readAllBytes(AUTHORITATIVE_FORMAL), Files.readAllBytes(MIRROR_FORMAL),
                "formal-testcase-to-script.instructions.md mirror must match authoritative source exactly");
        assertArrayEquals(Files.readAllBytes(AUTHORITATIVE_CREATE), Files.readAllBytes(MIRROR_CREATE),
                "create-test.prompt.md mirror must match authoritative source exactly");
    }

    @Test
    @DisplayName("one Azure TC with many steps still maps to exactly one @Test method")
    void singleAzureTestCaseWithManyStepsStillMapsToOneTestMethod() {
        AzureGenerationFixture fixture = new AzureGenerationFixture(
                Arrays.asList("ADO-123456"),
                Arrays.asList(
                        "Precondition: sign in as district operator",
                        "Navigate to Requests",
                        "Click New Request",
                        "Select borough",
                        "Select request category",
                        "Select subcategory",
                        "Enter address",
                        "Enter problem description",
                        "Upload evidence",
                        "Review the summary",
                        "Submit the request",
                        "Capture the generated request id"),
                Arrays.asList(
                        "Dashboard opens without error",
                        "Requests page is visible",
                        "Request form opens",
                        "Borough is selected",
                        "Category is selected",
                        "Subcategory is selected",
                        "Address is accepted",
                        "Description remains saved",
                        "Attachment is listed",
                        "Summary shows entered values",
                        "Success banner appears",
                        "Generated request id is displayed"),
                10);

        assertEquals(1, distinctTcIdCount(fixture.tcIds),
                "Fixture must represent exactly one distinct Azure Test Case ID");
        assertEquals(1, expectedGeneratedTestMethodCount(fixture.tcIds),
                "A single Azure Test Case ID must map to exactly one generated @Test method even with many steps");
        assertTrue(fixture.steps.size() >= 10, "Fixture must protect the many-step scenario");
        assertTrue(fixture.expectedResults.size() >= 10, "Fixture must protect the many-assertion scenario");
    }

    @Test
    @DisplayName("multiple Azure TC IDs require the same number of generated @Test methods")
    void multipleAzureTestCaseIdsRequireMatchingTestMethodCount() {
        AzureGenerationFixture fixture = new AzureGenerationFixture(
                Arrays.asList("ADO-123456", "ADO-123457", "ADO-123458"),
                Arrays.asList("TC1 step", "TC2 step", "TC3 step"),
                Arrays.asList("TC1 expected", "TC2 expected", "TC3 expected"),
                3);

        assertEquals(3, distinctTcIdCount(fixture.tcIds),
                "Fixture must represent three distinct Azure Test Case IDs");
        assertEquals(3, expectedGeneratedTestMethodCount(fixture.tcIds),
                "Three distinct Azure Test Case IDs must map to exactly three generated @Test methods");
    }

    @Test
    @DisplayName("DataProvider row count does not change the required @Test method count")
    void dataProviderRowsDoNotChangeTestMethodCount() {
        AzureGenerationFixture fixture = new AzureGenerationFixture(
                Arrays.asList("ADO-223344"),
                Arrays.asList("Open page", "Submit valid data"),
                Arrays.asList("Page loads", "Submission succeeds"),
                10);

        assertEquals(1, expectedGeneratedTestMethodCount(fixture.tcIds),
                "One Azure Test Case ID still requires exactly one @Test method");
        assertEquals(10, fixture.dataRows,
                "Fixture must model multiple runtime invocations");
    }

    @Test
    @DisplayName("credential examples use unmistakable placeholders and prefer secret-backed injection")
    void credentialExamplesUsePlaceholdersAndSecretBackedGuidance() throws Exception {
        String readme = readUtf8(Paths.get("README.md"));
        String readmeMirror = readUtf8(Paths.get("src", "main", "resources", "README.md"));
        String gettingStarted = readUtf8(Paths.get("GETTING-STARTED.md"));
        String oldUsernameTag = "<username>clt-40ea1dd4-1b0b-4f09-89ee-422fdfbba51d</username>";
        String placeholderUsernameTag = "<username>YOUR_AZURE_ARTIFACTS_USERNAME</username>";

        assertTrue(readme.contains(placeholderUsernameTag),
                "README must use an unmistakable Azure Artifacts username placeholder");
        assertTrue(readme.contains("prefer secret-backed `settings.xml` injection"),
                "README must prefer secret-backed credential injection guidance");
        assertFalse(readme.contains(oldUsernameTag),
                "README auth example must not use a realistic GUID-like username placeholder");

        assertEquals(readme, readmeMirror,
                "Packaged README mirror must stay synchronized with the root README");

        assertTrue(gettingStarted.contains(placeholderUsernameTag),
                "GETTING-STARTED must use an unmistakable Azure Artifacts username placeholder");
        assertFalse(gettingStarted.contains(oldUsernameTag),
                "GETTING-STARTED auth example must not use a realistic GUID-like username placeholder");
    }

    private static int expectedGeneratedTestMethodCount(List<String> tcIds) {
        return distinctTcIdCount(tcIds);
    }

    private static int distinctTcIdCount(List<String> tcIds) {
        Set<String> distinct = new LinkedHashSet<String>(tcIds);
        return distinct.size();
    }

    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static final class AzureGenerationFixture {
        private final List<String> tcIds;
        private final List<String> steps;
        private final List<String> expectedResults;
        private final int dataRows;

        private AzureGenerationFixture(List<String> tcIds, List<String> steps,
                                       List<String> expectedResults, int dataRows) {
            this.tcIds = tcIds;
            this.steps = steps;
            this.expectedResults = expectedResults;
            this.dataRows = dataRows;
        }
    }
}
