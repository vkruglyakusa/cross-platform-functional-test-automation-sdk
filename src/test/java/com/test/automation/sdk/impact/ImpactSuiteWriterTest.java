package com.test.automation.sdk.impact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactSuiteWriterTest {

    @Test
    void writesSuiteXmlWithGivenClasses(@TempDir Path tempDir) throws IOException {
        Set<String> classes = new TreeSet<>();
        classes.add("com.example.tests.Test_Alpha");
        classes.add("com.example.tests.Test_Beta");

        Path output = tempDir.resolve("nested/impact_suite.xml");
        ImpactSuiteWriter.write(classes, output);

        assertTrue(Files.exists(output));
        String content = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertTrue(content.contains("<class name=\"com.example.tests.Test_Alpha\"/>"));
        assertTrue(content.contains("<class name=\"com.example.tests.Test_Beta\"/>"));
        assertTrue(content.contains("<suite name=\"Impacted Tests\""));
    }

    @Test
    void createsParentDirectoriesAsNeeded(@TempDir Path tempDir) {
        Path output = tempDir.resolve("a/b/c/impact_suite.xml");
        ImpactSuiteWriter.write(Set.of("com.example.tests.Test_Only"), output);
        assertTrue(Files.exists(output));
    }
}
