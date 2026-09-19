package com.test.automation.sdk.impact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestImpactAnalyzerTest {

    private static final Pattern TEST_PATTERN = Pattern.compile("Test_.*");

    @Test
    void changingMainClassAffectsTestThatReferencesIt(@TempDir Path root) throws IOException {
        Path mainDir = root.resolve("src/main/java");
        Path testDir = root.resolve("src/test/java");

        Path pageObjectA = writeJava(mainDir, "com/example/pages", "PageA.java",
            "package com.example.pages;\n" +
            "public class PageA {\n" +
            "    public void click() {}\n" +
            "}\n");
        writeJava(mainDir, "com/example/pages", "PageB.java",
            "package com.example.pages;\n" +
            "public class PageB {\n" +
            "    public void submit() {}\n" +
            "}\n");
        writeJava(testDir, "com/example/tests", "Test_PageA.java",
            "package com.example.tests;\n" +
            "import com.example.pages.PageA;\n" +
            "public class Test_PageA {\n" +
            "    void testIt() { new PageA().click(); }\n" +
            "}\n");
        writeJava(testDir, "com/example/tests", "Test_PageB.java",
            "package com.example.tests;\n" +
            "import com.example.pages.PageB;\n" +
            "public class Test_PageB {\n" +
            "    void testIt() { new PageB().submit(); }\n" +
            "}\n");

        ImpactResult result = TestImpactCli.analyze(
            List.of(pageObjectA), mainDir, testDir, root.resolve("impact_suite.xml"), TEST_PATTERN);

        assertTrue(result.getAffectedTestClasses().contains("com.example.tests.Test_PageA"));
        assertFalse(result.getAffectedTestClasses().contains("com.example.tests.Test_PageB"));
        assertFalse(result.isFullSuiteRecommended());
    }

    @Test
    void changingTestClassDirectlyIncludesItself(@TempDir Path root) throws IOException {
        Path mainDir = root.resolve("src/main/java");
        Path testDir = root.resolve("src/test/java");
        Files.createDirectories(mainDir);

        Path testFile = writeJava(testDir, "com/example/tests", "Test_Standalone.java",
            "package com.example.tests;\n" +
            "public class Test_Standalone {\n" +
            "    void testIt() {}\n" +
            "}\n");

        ImpactResult result = TestImpactCli.analyze(
            List.of(testFile), mainDir, testDir, root.resolve("impact_suite.xml"), TEST_PATTERN);

        assertEquals(1, result.getAffectedTestClasses().size());
        assertTrue(result.getAffectedTestClasses().contains("com.example.tests.Test_Standalone"));
    }

    @Test
    void unresolvedNonJavaChangeRecommendsFullSuite(@TempDir Path root) throws IOException {
        Path mainDir = root.resolve("src/main/java");
        Path testDir = root.resolve("src/test/java");
        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);

        Path pomFile = root.resolve("pom.xml");
        Files.write(pomFile, "<project/>".getBytes(StandardCharsets.UTF_8));

        ImpactResult result = TestImpactCli.analyze(
            List.of(pomFile), mainDir, testDir, root.resolve("impact_suite.xml"), TEST_PATTERN);

        assertTrue(result.isFullSuiteRecommended());
        assertEquals(1, result.getUnresolvedFiles().size());
    }

    @Test
    void unmappedJavaFileOutsideIndexAlsoRecommendsFullSuite(@TempDir Path root) throws IOException {
        Path mainDir = root.resolve("src/main/java");
        Path testDir = root.resolve("src/test/java");
        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);

        Path outsideFile = root.resolve("Scratch.java");
        Files.write(outsideFile, "public class Scratch {}".getBytes(StandardCharsets.UTF_8));

        ImpactResult result = TestImpactCli.analyze(
            List.of(outsideFile), mainDir, testDir, root.resolve("impact_suite.xml"), TEST_PATTERN);

        assertTrue(result.isFullSuiteRecommended());
        assertTrue(result.getAffectedTestClasses().isEmpty());
    }

    @Test
    void transitiveReferenceIsFollowed(@TempDir Path root) throws IOException {
        Path mainDir = root.resolve("src/main/java");
        Path testDir = root.resolve("src/test/java");

        Path helper = writeJava(mainDir, "com/example/util", "Helper.java",
            "package com.example.util;\n" +
            "public class Helper {\n" +
            "    public static void help() {}\n" +
            "}\n");
        writeJava(mainDir, "com/example/pages", "PageUsingHelper.java",
            "package com.example.pages;\n" +
            "import com.example.util.Helper;\n" +
            "public class PageUsingHelper {\n" +
            "    void go() { Helper.help(); }\n" +
            "}\n");
        writeJava(testDir, "com/example/tests", "Test_Transitive.java",
            "package com.example.tests;\n" +
            "import com.example.pages.PageUsingHelper;\n" +
            "public class Test_Transitive {\n" +
            "    void testIt() { new PageUsingHelper().go(); }\n" +
            "}\n");

        ImpactResult result = TestImpactCli.analyze(
            List.of(helper), mainDir, testDir, root.resolve("impact_suite.xml"), TEST_PATTERN);

        assertTrue(result.getAffectedTestClasses().contains("com.example.tests.Test_Transitive"));
    }

    private static Path writeJava(Path root, String packagePath, String fileName, String content) throws IOException {
        Path dir = root.resolve(packagePath);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
