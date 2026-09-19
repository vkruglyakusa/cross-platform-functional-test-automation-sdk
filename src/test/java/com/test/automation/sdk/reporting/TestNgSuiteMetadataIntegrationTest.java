package com.test.automation.sdk.reporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OBS-10 TestNG suite metadata integration")
class TestNgSuiteMetadataIntegrationTest {

    @Test
    @DisplayName("XML suite name comes from <suite name> instead of the XML filename or Surefire synthetic names")
    void xmlSuiteNameComesFromSuiteElement() throws Exception {
        Path workDir = Paths.get("target", "test-work", "obs10-xml-suite");
        recreateDirectory(workDir);

        String output = runProbe(workDir, "xml");
        String resultJson = Files.readString(findSingleFile(workDir.resolve("allure-results"), "-result.json"), StandardCharsets.UTF_8);
        List<String> containerBodies = listFileBodies(workDir.resolve("allure-results"), "-container.json");

        assertTrue(output.contains("Example XML Suite"), output);
        assertTrue(resultJson.contains("\"name\":\"parentSuite\",\"value\":\"Example XML Suite\""), resultJson);
        assertTrue(resultJson.contains("\"name\":\"suite\",\"value\":\"Example XML Test Group\""), resultJson);
        assertTrue(resultJson.contains("\"name\":\"subSuite\",\"value\":\"ExampleXmlSuiteTest\""), resultJson);
        assertFalse(resultJson.contains("regression_suite"), resultJson);
        assertFalse(resultJson.contains("Surefire suite"), resultJson);
        assertTrue(containerBodies.stream().anyMatch(body -> body.contains("\"name\":\"Example XML Suite\"")));
    }

    @Test
    @DisplayName("Programmatic TestNG execution uses runtime suite and test names without requiring a physical XML file")
    void programmaticSuiteNameUsesRuntimeMetadata() throws Exception {
        Path workDir = Paths.get("target", "test-work", "obs10-programmatic-suite");
        recreateDirectory(workDir);

        runProbe(workDir, "programmatic");
        String resultJson = Files.readString(findSingleFile(workDir.resolve("allure-results"), "-result.json"), StandardCharsets.UTF_8);

        assertTrue(resultJson.contains("\"name\":\"parentSuite\",\"value\":\"Programmatic Example Suite\""), resultJson);
        assertTrue(resultJson.contains("\"name\":\"suite\",\"value\":\"Programmatic Example Test Group\""), resultJson);
        assertTrue(resultJson.contains("\"name\":\"subSuite\",\"value\":\"ProgrammaticSuiteTest\""), resultJson);
    }

    @Test
    @DisplayName("Multiple suites in one TestNG run retain independent suite names")
    void multipleSuitesRetainIndependentSuiteNames() throws Exception {
        Path workDir = Paths.get("target", "test-work", "obs10-multi-suite");
        recreateDirectory(workDir);

        runProbe(workDir, "multi");
        List<String> resultBodies = listFileBodies(workDir.resolve("allure-results"), "-result.json");
        List<String> containerBodies = listFileBodies(workDir.resolve("allure-results"), "-container.json");

        assertEquals(2, resultBodies.size(), "Expected one result per synthetic suite");
        assertTrue(resultBodies.stream().anyMatch(body ->
                body.contains("\"name\":\"parentSuite\",\"value\":\"Example Multi Suite A\"")
                        && body.contains("\"name\":\"suite\",\"value\":\"Example Multi Test A\"")));
        assertTrue(resultBodies.stream().anyMatch(body ->
                body.contains("\"name\":\"parentSuite\",\"value\":\"Example Multi Suite B\"")
                        && body.contains("\"name\":\"suite\",\"value\":\"Example Multi Test B\"")));
        assertTrue(containerBodies.stream().anyMatch(body -> body.contains("\"name\":\"Example Multi Suite A\"")));
        assertTrue(containerBodies.stream().anyMatch(body -> body.contains("\"name\":\"Example Multi Suite B\"")));
    }

    private String runProbe(Path workDir, String mode) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                resolveJavaExecutable().toString(),
                "-Duser.dir=" + workDir.toAbsolutePath(),
                "-cp",
                System.getProperty("java.class.path"),
                RuntimeSuiteMetadataProbe.class.getName(),
                mode,
                workDir.toAbsolutePath().toString());
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readAll(process.getInputStream());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Probe failed. Output:\n" + output);
        return output;
    }

    private Path resolveJavaExecutable() {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path javaPath = Paths.get(System.getProperty("java.home"), "bin", executableName);
        assertTrue(Files.exists(javaPath), "Java executable not found: " + javaPath);
        return javaPath;
    }

    private String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
        Files.createDirectories(dir);
    }

    private Path findSingleFile(Path dir, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> matches = files
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .collect(Collectors.toList());
            assertEquals(1, matches.size(),
                    "Expected exactly one " + suffix + " file under " + dir + " but found " + matches);
            return matches.get(0);
        }
    }

    private List<String> listFileBodies(Path dir, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }
}

class RuntimeSuiteMetadataProbe {

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path workDir = Paths.get(args[1]);
        configureEnvironment(workDir);

        org.testng.TestNG testng = new org.testng.TestNG();
        testng.setUseDefaultListeners(false);
        testng.setOutputDirectory(workDir.resolve("testng-output").toString());

        if ("xml".equals(mode)) {
            testng.setTestSuites(Collections.singletonList(writeXmlSuite(
                    workDir.resolve("regression_suite.xml"),
                    "Example XML Suite",
                    "Example XML Test Group",
                    ExampleXmlSuiteTest.class.getName())));
        } else if ("programmatic".equals(mode)) {
            testng.setXmlSuites(Collections.singletonList(programmaticSuite(
                    "Programmatic Example Suite",
                    "Programmatic Example Test Group",
                    ProgrammaticSuiteTest.class.getName())));
            testng.addListener(new io.qameta.allure.testng.AllureTestNg());
            testng.addListener(new com.test.automation.sdk.listener.Listener());
        } else if ("multi".equals(mode)) {
            testng.setTestSuites(Arrays.asList(
                    writeXmlSuite(workDir.resolve("suite-a.xml"),
                            "Example Multi Suite A",
                            "Example Multi Test A",
                            ExampleMultiSuiteATest.class.getName()),
                    writeXmlSuite(workDir.resolve("suite-b.xml"),
                            "Example Multi Suite B",
                            "Example Multi Test B",
                            ExampleMultiSuiteBTest.class.getName())));
        } else {
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }

        testng.run();
    }

    private static void configureEnvironment(Path workDir) throws IOException {
        Files.createDirectories(workDir);
        Path configDir = workDir.resolve("configuration");
        Files.createDirectories(configDir);
        Files.write(configDir.resolve("config.properties"),
                Collections.singletonList("extReportDir=/extent/"),
                StandardCharsets.UTF_8);
        Files.write(configDir.resolve("sdk-config.yaml"),
                Collections.singletonList("reporting:\n  allure:\n    generateAfterExecution: false\n"),
                StandardCharsets.UTF_8);
        System.setProperty("sdk.config.dir", configDir.toAbsolutePath().toString());
        System.setProperty("allure.results.directory", workDir.resolve("allure-results").toAbsolutePath().toString());
        System.setProperty("reporting.allure.generateAfterExecution", "false");
    }

    private static String writeXmlSuite(Path xmlPath, String suiteName, String testName, String className) throws IOException {
        String xml = "<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n"
                + "<suite name=\"" + suiteName + "\" verbose=\"1\">\n"
                + "  <listeners>\n"
                + "    <listener class-name=\"io.qameta.allure.testng.AllureTestNg\"/>\n"
                + "    <listener class-name=\"com.test.automation.sdk.listener.Listener\"/>\n"
                + "  </listeners>\n"
                + "  <test name=\"" + testName + "\">\n"
                + "    <classes>\n"
                + "      <class name=\"" + className + "\"/>\n"
                + "    </classes>\n"
                + "  </test>\n"
                + "</suite>\n";
        Files.write(xmlPath, Collections.singletonList(xml), StandardCharsets.UTF_8);
        return xmlPath.toAbsolutePath().toString();
    }

    private static org.testng.xml.XmlSuite programmaticSuite(String suiteName, String testName, String className) {
        org.testng.xml.XmlSuite suite = new org.testng.xml.XmlSuite();
        suite.setName(suiteName);
        org.testng.xml.XmlTest test = new org.testng.xml.XmlTest(suite);
        test.setName(testName);
        test.setXmlClasses(Collections.singletonList(new org.testng.xml.XmlClass(className)));
        return suite;
    }
}

class ExampleXmlSuiteTest {
    @org.testng.annotations.Test
    public void xmlSuiteMethod() {
    }
}

class ProgrammaticSuiteTest {
    @org.testng.annotations.Test
    public void programmaticSuiteMethod() {
    }
}

class ExampleMultiSuiteATest {
    @org.testng.annotations.Test
    public void suiteAMethod() {
    }
}

class ExampleMultiSuiteBTest {
    @org.testng.annotations.Test
    public void suiteBMethod() {
    }
}
