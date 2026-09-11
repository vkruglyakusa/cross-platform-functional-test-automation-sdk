package com.test.automation.sdk.reporting;

import com.test.automation.sdk.testbase.TestBase;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.TestResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Execution reporting artifact compatibility")
class ExecutionReportingArtifactCompatibilityTest {

    @Test
    @DisplayName("Passing story creates one Allure test with multiple business steps plus Extent and log output")
    void passingStoryCreatesAlignedArtifacts() throws Exception {
        Path workDir = Paths.get("target", "test-work", "reporting-pass");
        recreateDirectory(workDir);
        writeReportingConfig(workDir);

        runProbe(workDir, PassingExecutionReportingProbe.class.getName());

        String allureJson = Files.readString(findSingleFile(workDir.resolve("allure-results"), "-result.json"), StandardCharsets.UTF_8);
        assertTrue(allureJson.contains("\"name\":\"sdk-reporting-pass\""));
        assertTrue(allureJson.contains("\"name\":\"Step 1: Initialization\""), allureJson);
        assertTrue(allureJson.contains("\"name\":\"Step 2: Verify announcements section\""), allureJson);
        assertTrue(allureJson.contains("\"status\":\"passed\""), allureJson);

        String extentHtml = Files.readString(workDir.resolve("extent").resolve("Test-Automaton-Report.html"), StandardCharsets.UTF_8);
        assertTrue(extentHtml.contains("ADO-900 Pass Story"), "Extent report missing test case name");
        assertTrue(extentHtml.contains("Initialization"), "Extent report missing first step");
        assertTrue(extentHtml.contains("Verify announcements section"), "Extent report missing second step");

        String logOutput = Files.readString(workDir.resolve("logs").resolve("sdk-reporting.log"), StandardCharsets.UTF_8);
        assertTrue(logOutput.contains("TEST_STARTED"), logOutput);
        assertTrue(logOutput.contains("STEP_PASSED"), logOutput);
        assertTrue(logOutput.contains("Initialization"), logOutput);
        assertFalse(logOutput.contains("beforeFindElement"), "Low-level Selenium noise should not dominate INFO log");
    }

    @Test
    @DisplayName("Failing story preserves failing step and reuses evidence across Allure Extent and logs")
    void failingStoryCreatesAlignedFailureArtifacts() throws Exception {
        Path workDir = Paths.get("target", "test-work", "reporting-fail");
        recreateDirectory(workDir);
        writeReportingConfig(workDir);

        runProbe(workDir, FailingExecutionReportingProbe.class.getName());

        String allureJson = Files.readString(findSingleFile(workDir.resolve("allure-results"), "-result.json"), StandardCharsets.UTF_8);
        assertTrue(allureJson.contains("\"status\":\"failed\""), allureJson);
        assertTrue(allureJson.contains("\"name\":\"Step 2: Verify links\""), allureJson);
        assertTrue(allureJson.contains("\"message\":\"Intentional reporting failure\""), allureJson);
        assertTrue(allureJson.contains("\"attachments\""), allureJson);

        List<String> attachmentBodies = listAttachmentBodies(workDir.resolve("allure-results"));
        assertTrue(attachmentBodies.stream().anyMatch(body -> body.contains("fake image body")),
                "Expected screenshot attachment content");
        assertTrue(attachmentBodies.stream().anyMatch(body -> body.contains("<html>failure dom</html>")),
                "Expected DOM attachment content");

        String extentHtml = Files.readString(workDir.resolve("extent").resolve("Test-Automaton-Report.html"), StandardCharsets.UTF_8);
        assertTrue(extentHtml.contains("ADO-901 Fail Story"), "Extent report missing failing test case name");
        assertTrue(extentHtml.contains("Verify links"), "Extent report missing failing step");
        assertTrue(extentHtml.contains("failure.png"), "Extent report missing screenshot evidence");

        String logOutput = Files.readString(workDir.resolve("logs").resolve("sdk-reporting.log"), StandardCharsets.UTF_8);
        assertTrue(logOutput.contains("TEST_FAILED"), logOutput);
        assertTrue(logOutput.contains("Verify links"), logOutput);
        assertTrue(logOutput.contains("Intentional reporting failure"), logOutput);
    }

    private void writeReportingConfig(Path workDir) throws IOException {
        Path configDir = workDir.resolve("configuration");
        Files.createDirectories(configDir);
        Files.createDirectories(workDir.resolve("logs"));

        Files.writeString(configDir.resolve("config.properties"),
                "extReportDir=/extent/\n",
                StandardCharsets.UTF_8);

        Files.writeString(configDir.resolve("log4j.properties"),
                "status = error\n"
                        + "name = ReportingTestConfig\n"
                        + "appender.file.type = File\n"
                        + "appender.file.name = FileAppender\n"
                        + "appender.file.fileName = logs/sdk-reporting.log\n"
                        + "appender.file.layout.type = PatternLayout\n"
                        + "appender.file.layout.pattern = %level %msg%n\n"
                        + "rootLogger.level = info\n"
                        + "rootLogger.appenderRefs = file\n"
                        + "rootLogger.appenderRef.file.ref = FileAppender\n"
                        + "logger.reporting.name = com.test.automation.sdk.reporting.ExecutionLogReporter\n"
                        + "logger.reporting.level = debug\n"
                        + "logger.reporting.additivity = false\n"
                        + "logger.reporting.appenderRefs = file\n"
                        + "logger.reporting.appenderRef.file.ref = FileAppender\n",
                StandardCharsets.UTF_8);

        Files.writeString(configDir.resolve("sdk-config.yaml"),
                "reporting:\n"
                        + "  screenshotsDir: \"test-output/screenshots\"\n",
                StandardCharsets.UTF_8);
    }

    private void runProbe(Path workDir, String mainClass) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                resolveJavaExecutable().toString(),
                "-Duser.dir=" + workDir.toAbsolutePath(),
                "-Dsdk.config.dir=" + workDir.resolve("configuration").toAbsolutePath(),
                "-Dallure.results.directory=" + workDir.resolve("allure-results").toAbsolutePath(),
                "-cp",
                System.getProperty("java.class.path"),
                mainClass);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readAll(process.getInputStream());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Probe failed. Output:\n" + output);
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

    private List<String> listAttachmentBodies(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".json"))
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

class PassingExecutionReportingProbe {

    public static void main(String[] args) throws Exception {
        StepEnabledProbeTestBase base = new StepEnabledProbeTestBase();
        TestResult allureResult = startAllureTest("sdk-reporting-pass");
        org.testng.ITestResult testNgResult = ProbeResults.mockResult("sdkReportingPass");

        TestBase.setCurrentTestCaseName("ADO-900 Pass Story");
        ExecutionReporting.setReporterForTests(new CompositeExecutionReporter(
                new ExecutionLogReporter(),
                new AllureExecutionReporter(),
                new ExtentExecutionReporter()));

        ExecutionReporting.onTestStarted(testNgResult);
        base.runStep("Initialization", new TestBase.StepAction() {
            @Override
            public void run() {
            }
        });
        base.runStep("Verify announcements section", new TestBase.StepAction() {
            @Override
            public void run() {
            }
        });
        ExecutionReporting.onTestPassed(testNgResult);

        finishAllureTest(allureResult, Status.PASSED);
        com.test.automation.sdk.utility.reports.ExtentTestManager.endTest();
        ExecutionReporting.resetReporterForTests();
        ExecutionReporting.clear();
    }

    static TestResult startAllureTest(String name) {
        String uuid = UUID.randomUUID().toString();
        TestResult result = new TestResult().setUuid(uuid).setName(name);
        io.qameta.allure.Allure.getLifecycle().scheduleTestCase(result);
        io.qameta.allure.Allure.getLifecycle().startTestCase(uuid);
        return result;
    }

    static void finishAllureTest(TestResult result, Status status) {
        io.qameta.allure.Allure.getLifecycle().updateTestCase(result.getUuid(), testResult -> testResult.setStatus(status));
        io.qameta.allure.Allure.getLifecycle().stopTestCase(result.getUuid());
        io.qameta.allure.Allure.getLifecycle().writeTestCase(result.getUuid());
    }
}

class FailingExecutionReportingProbe {

    public static void main(String[] args) throws Exception {
        StepEnabledProbeTestBase base = new StepEnabledProbeTestBase();
        TestResult allureResult = PassingExecutionReportingProbe.startAllureTest("sdk-reporting-fail");
        org.testng.ITestResult testNgResult = ProbeResults.mockResult("sdkReportingFail");

        TestBase.setCurrentTestCaseName("ADO-901 Fail Story");
        ExecutionReporting.setReporterForTests(new CompositeExecutionReporter(
                new ExecutionLogReporter(),
                new AllureExecutionReporter(),
                new ExtentExecutionReporter()));

        ExecutionReporting.onTestStarted(testNgResult);
        base.runStep("Initialization", new TestBase.StepAction() {
            @Override
            public void run() {
            }
        });

        IllegalStateException failure = new IllegalStateException("Intentional reporting failure");
        try {
            base.runStep("Verify links", new TestBase.StepAction() {
                @Override
                public void run() {
                    throw failure;
                }
            });
        } catch (IllegalStateException expected) {
            Path evidenceDir = Paths.get(System.getProperty("user.dir"), "evidence");
            Files.createDirectories(evidenceDir);
            Path screenshot = evidenceDir.resolve("failure.png");
            Path dom = evidenceDir.resolve("failure_DOM.html");
            FileUtils.writeStringToFile(screenshot.toFile(), "fake image body", StandardCharsets.UTF_8.name());
            FileUtils.writeStringToFile(dom.toFile(), "<html>failure dom</html>", StandardCharsets.UTF_8.name());
            ExecutionReporting.onTestFailed(testNgResult, expected, java.util.Arrays.asList(
                    ExecutionEvidence.screenshot("Failure Screenshot", screenshot),
                    ExecutionEvidence.domDump("Failure DOM", dom)));
        }

        PassingExecutionReportingProbe.finishAllureTest(allureResult, Status.FAILED);
        com.test.automation.sdk.utility.reports.ExtentTestManager.endTest();
        ExecutionReporting.resetReporterForTests();
        ExecutionReporting.clear();
    }
}

class ProbeResults {

    static org.testng.ITestResult mockResult(String methodName) {
        org.testng.ITestResult result = org.mockito.Mockito.mock(org.testng.ITestResult.class);
        org.testng.ITestNGMethod testNgMethod = org.mockito.Mockito.mock(org.testng.ITestNGMethod.class);
        org.testng.ITestClass testClass = org.mockito.Mockito.mock(org.testng.ITestClass.class);
        org.mockito.Mockito.when(testClass.getRealClass()).thenReturn((Class) StepEnabledProbeTestBase.class);
        org.mockito.Mockito.when(testNgMethod.getMethodName()).thenReturn(methodName);
        org.mockito.Mockito.when(result.getTestClass()).thenReturn(testClass);
        org.mockito.Mockito.when(result.getMethod()).thenReturn(testNgMethod);
        org.mockito.Mockito.when(result.getName()).thenReturn(methodName);
        return result;
    }
}

class StepEnabledProbeTestBase extends com.test.automation.sdk.testbase.TestBase {
    void runStep(String name, StepAction action) throws Exception {
        step(name, action);
    }
}
