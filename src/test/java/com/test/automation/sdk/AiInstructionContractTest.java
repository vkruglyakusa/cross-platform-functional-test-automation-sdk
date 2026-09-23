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

    @Test
    @DisplayName("create-test prompt offers an optional, repository-aware detailed implementation plan review gate with a real approval loop")
    void createTestPromptOffersOptionalPlanReviewGate() throws Exception {
        String content = readUtf8(AUTHORITATIVE_CREATE);

        assertTrue(content.contains("Would you like to review the detailed implementation plan before I create or modify the test script?"),
                "create-test prompt must ask the exact optional plan-review question");
        assertTrue(content.contains("**If No:** continue directly to Step 1"),
                "create-test prompt must permit the operator to skip plan review and proceed normally");
        assertTrue(content.contains("Detailed Test Implementation Plan"),
                "create-test prompt must define a Detailed Test Implementation Plan format");
        assertTrue(content.contains("not invent class names, methods, directories, endpoints, or locators"),
                "create-test prompt's plan must be repository-aware and never invent existing implementation");
        assertTrue(content.contains("must be explicitly labeled `NEW`"),
                "create-test prompt must require non-existent components to be explicitly labeled NEW");
        assertTrue(content.contains("### Files to Create / Modify") && content.contains("### Class / Method Changes"),
                "create-test prompt's plan must show exact files and class/method changes");
        assertTrue(content.contains("### Existing Components to Reuse") && content.contains("### New Components"),
                "create-test prompt's plan must separate reused components from new components");
        assertTrue(content.contains("### Test Suite Impact") && content.contains("### Configuration Impact") && content.contains("### Dependency Impact"),
                "create-test prompt's plan must cover suite, configuration, and dependency impact");
        assertTrue(content.contains("### Implementation Summary"),
                "create-test prompt's plan must include an Implementation Summary section");
        assertTrue(content.contains("Do you approve this implementation plan?"),
                "create-test prompt must ask for explicit plan approval");
        assertTrue(content.contains("**Approve**") && content.contains("**Request Changes**") && content.contains("**Cancel**"),
                "create-test prompt must offer Approve / Request Changes / Cancel outcomes");
        assertTrue(content.contains("do not implement anything while waiting for this decision"),
                "create-test prompt must not implement while awaiting the approval decision");
        assertTrue(content.contains("Request Changes** -- do not start implementation"),
                "create-test prompt's Request Changes path must re-present a revised plan and ask again, never implicitly approve");
        assertTrue(content.contains("Cancel** -- do not create or modify the test"),
                "create-test prompt's Cancel path must prevent test creation/modification");
        assertTrue(content.contains("Do **not** treat ambiguous responses"),
                "create-test prompt must reject ambiguous responses as approval");
        assertTrue(content.contains("Material deviations after approval"),
                "create-test prompt must require renewed approval after a material deviation from the approved plan");
        assertTrue(content.contains("Approved Plan vs Implemented Result") || content.contains("Approved Plan vs. Implemented Result"),
                "create-test prompt must report an approved-plan-vs-implemented-result comparison after implementation");
    }

    @Test
    @DisplayName("modify-test prompt offers the same detailed plan review gate for significant changes")
    void modifyTestPromptOffersOptionalPlanReviewGate() throws Exception {
        Path authoritativeModify = Paths.get("src", "main", "resources", "sdk-prompts", "modify-test.prompt.md");
        String content = readUtf8(authoritativeModify);

        assertTrue(content.contains("Would you like to review the detailed implementation plan before I modify the test script?"),
                "modify-test prompt must ask the exact optional plan-review question");
        assertTrue(content.contains("Do not create unnecessary approval friction for trivial, mechanical fixes"),
                "modify-test prompt must keep trivial/mechanical fixes lightweight, without forced approval");
        assertTrue(content.contains("Detailed Test Implementation Plan"),
                "modify-test prompt must reuse the repository-aware Detailed Test Implementation Plan format");
        assertTrue(content.contains("Do you approve this"),
                "modify-test prompt must ask for explicit plan approval");
        assertTrue(content.contains("Request Changes"), "modify-test prompt must support Request Changes");
        assertTrue(content.contains("Cancel"), "modify-test prompt must support Cancel");
        assertTrue(content.contains("Approved Plan vs Implemented Result"),
                "modify-test prompt must report an approved-plan-vs-implemented-result comparison after implementation");
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
