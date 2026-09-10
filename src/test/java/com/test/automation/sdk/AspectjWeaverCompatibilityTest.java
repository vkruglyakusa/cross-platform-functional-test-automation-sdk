package com.test.automation.sdk;

import com.test.automation.sdk.execution.RunMode;
import com.test.automation.sdk.listener.Listener;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import io.qameta.allure.model.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
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

@DisplayName("AspectJ weaver compatibility")
class AspectjWeaverCompatibilityTest {

    @Test
    @DisplayName("AspectJ javaagent can weave the SDK's Java 20+ bytecode")
    void aspectjJavaagentCanWeaveCurrentBytecodeLevel() throws Exception {
        Path javaExecutable = resolveJavaExecutable();
        Path agentJar = resolveAspectjWeaverJar();

        ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable.toString(),
                "-javaagent:" + agentJar.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                AspectjWeaverCompatibilityProbe.class.getName());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readAll(process.getInputStream());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode,
                "AspectJ javaagent smoke probe failed. Output:\n" + output);
        assertTrue(output.contains("PROBE_OK"),
                "Probe did not report success. Output:\n" + output);
        assertFalse(output.contains("AspectJ Internal Error"),
                "Old AspectJ stackmap error resurfaced. Output:\n" + output);
        assertFalse(output.contains("Unsupported class file major version"),
                "AspectJ still cannot parse the SDK bytecode level. Output:\n" + output);
    }

    @Test
    @DisplayName("AspectJ weaver version supports Java 20+ class files")
    void aspectjWeaverVersionSupportsJava20Plus() throws Exception {
        String fileName = resolveAspectjWeaverJar().getFileName().toString();
        assertTrue(fileName.startsWith("aspectjweaver-") && fileName.endsWith(".jar"),
                "Unexpected AspectJ jar name: " + fileName);
        String version = fileName.substring("aspectjweaver-".length(), fileName.length() - ".jar".length());
        assertTrue(compareVersions(version, "1.9.20.1") >= 0,
                "AspectJ " + version + " is too old for Java 20+ class files; expected at least 1.9.20.1");
    }

    @Test
    @DisplayName("@Attachment and explicit Allure attachments still work without BrowserStack on the child runtime classpath")
    void attachmentsStillWorkWithoutBrowserStackOnChildRuntimeClasspath() throws Exception {
        Path workDir = Paths.get("target", "test-work", "aspectj-no-browserstack");
        recreateDirectory(workDir);
        Path resultsDir = workDir.resolve("allure-results");
        Files.createDirectories(resultsDir);

        String output = runChildJvm(
                filterBrowserStackFromClasspath(System.getProperty("java.class.path")),
                resultsDir,
                AllureAttachmentLifecycleProbe.class.getName(),
                null);

        assertTrue(output.contains("ALLURE_ATTACHMENTS_OK"),
                "Child probe did not report attachment success. Output:\n" + output);
        assertFalse(output.contains("MeasureAspect"),
                "BrowserStack MeasureAspect warning leaked into a BrowserStack-free child classpath. Output:\n" + output);
        assertFalse(output.contains("Cannot read debug info for @Aspect"),
                "AspectJ warning should be absent when BrowserStack is removed from the child classpath. Output:\n" + output);

        Path resultJson = findSingleFile(resultsDir, "-result.json");
        String json = Files.readString(resultJson, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"explicit\""), "Missing explicit attachment in result json:\n" + json);
        assertTrue(json.contains("\"name\":\"annotated\""), "Missing annotated attachment in result json:\n" + json);
        assertTrue(json.contains("\"name\":\"listener-body\""), "Missing Listener.saveTextLog attachment in result json:\n" + json);

        List<String> attachmentBodies = listAttachmentBodies(resultsDir);
        assertTrue(attachmentBodies.contains("explicit-body"), "Explicit attachment body missing: " + attachmentBodies);
        assertTrue(attachmentBodies.contains("annotated-body"), "Annotated attachment body missing: " + attachmentBodies);
        assertTrue(attachmentBodies.contains("listener-body"), "Listener.saveTextLog attachment body missing: " + attachmentBodies);
    }

    @Test
    @DisplayName("BrowserStack MeasureAspect warning only appears when the BrowserStack jar is on the child runtime classpath")
    void browserStackMeasureAspectWarningRequiresBrowserStackJarOnClasspath() throws Exception {
        Path workDir = Paths.get("target", "test-work", "measure-aspect-warning");
        recreateDirectory(workDir);
        Path metaInf = workDir.resolve("META-INF");
        Files.createDirectories(metaInf);
        Files.writeString(metaInf.resolve("aop.xml"),
                "<aspectj>\n"
                        + "  <aspects>\n"
                        + "    <aspect name=\"com.browserstack.monitoring.MeasureAspect\"/>\n"
                        + "  </aspects>\n"
                        + "  <weaver options=\"-verbose -showWeaveInfo\">\n"
                        + "    <include within=\"*\"/>\n"
                        + "  </weaver>\n"
                        + "</aspectj>\n",
                StandardCharsets.UTF_8);

        String currentClasspath = System.getProperty("java.class.path");
        String withBrowserStack = workDir.toString() + File.pathSeparator + currentClasspath;
        String withoutBrowserStack = workDir.toString() + File.pathSeparator + filterBrowserStackFromClasspath(currentClasspath);

        String withOutput = runChildJvm(withBrowserStack, null, MeasureAspectWarningProbe.class.getName(), null);
        String withoutOutput = runChildJvm(withoutBrowserStack, null, MeasureAspectWarningProbe.class.getName(), null);

        assertTrue(withOutput.contains("MEASURE_PROBE_OK"),
                "BrowserStack-present child probe did not finish cleanly. Output:\n" + withOutput);
        assertTrue(withOutput.contains("Cannot read debug info for @Aspect"),
                "Expected BrowserStack MeasureAspect warning was not reproduced. Output:\n" + withOutput);
        assertTrue(withOutput.contains("com.browserstack.monitoring.MeasureAspect"),
                "Expected BrowserStack aspect name missing from warning output. Output:\n" + withOutput);

        assertTrue(withoutOutput.contains("MEASURE_PROBE_OK"),
                "BrowserStack-free child probe did not finish cleanly. Output:\n" + withoutOutput);
        assertFalse(withoutOutput.contains("Cannot read debug info for @Aspect"),
                "MeasureAspect warning should disappear when BrowserStack is removed from the child classpath. Output:\n" + withoutOutput);
    }

    private Path resolveJavaExecutable() {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path javaPath = Paths.get(System.getProperty("java.home"), "bin", executableName);
        assertTrue(Files.exists(javaPath), "Java executable not found: " + javaPath);
        return javaPath;
    }

    private Path resolveAspectjWeaverJar() throws URISyntaxException {
        Path jarPath = Paths.get(org.aspectj.weaver.loadtime.Agent.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        assertTrue(Files.exists(jarPath), "AspectJ weaver jar not found: " + jarPath);
        return jarPath;
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

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return leftValue - rightValue;
            }
        }
        return 0;
    }

    private String filterBrowserStackFromClasspath(String classpath) {
        return Stream.of(classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(entry -> !entry.contains("browserstack-java-sdk"))
                .collect(Collectors.joining(File.pathSeparator));
    }

    private String runChildJvm(String classpath, Path resultsDir, String mainClass, String extraJvmArg) throws Exception {
        ProcessBuilder processBuilder;
        if (extraJvmArg == null) {
            processBuilder = new ProcessBuilder(
                    resolveJavaExecutable().toString(),
                    "-javaagent:" + resolveAspectjWeaverJar(),
                    resultsDir == null ? "" : "-Dallure.results.directory=" + resultsDir,
                    "-cp",
                    classpath,
                    mainClass);
        } else {
            processBuilder = new ProcessBuilder(
                    resolveJavaExecutable().toString(),
                    extraJvmArg,
                    "-javaagent:" + resolveAspectjWeaverJar(),
                    resultsDir == null ? "" : "-Dallure.results.directory=" + resultsDir,
                    "-cp",
                    classpath,
                    mainClass);
        }
        processBuilder.command().removeIf(String::isEmpty);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readAll(process.getInputStream());
        int exitCode = process.waitFor();
        assertEquals(0, exitCode,
                "Child JVM probe failed for " + mainClass + ". Output:\n" + output);
        return output;
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

class AspectjWeaverCompatibilityProbe {

    @Attachment(value = "aspectj-probe", type = "text/plain")
    static String attachment() {
        return "attachment-body";
    }

    public static void main(String[] args) {
        if (RunMode.resolve() != RunMode.LOCAL) {
            throw new IllegalStateException("RunMode default changed unexpectedly: " + RunMode.resolve());
        }
        if (!"attachment-body".equals(attachment())) {
            throw new IllegalStateException("Allure attachment probe returned an unexpected value");
        }
        System.out.println("PROBE_OK");
    }
}

class AllureAttachmentLifecycleProbe {

    @Attachment(value = "annotated", type = "text/plain")
    static String annotatedAttachment() {
        return "annotated-body";
    }

    public static void main(String[] args) {
        String resultsDir = System.getProperty("allure.results.directory");
        if (resultsDir == null || resultsDir.isEmpty()) {
            throw new IllegalStateException("allure.results.directory not set");
        }
        String uuid = UUID.randomUUID().toString();
        TestResult result = new TestResult().setUuid(uuid).setName("allure-attachment-probe");
        Allure.getLifecycle().scheduleTestCase(result);
        Allure.getLifecycle().startTestCase(uuid);
        Allure.addAttachment("explicit", "text/plain", "explicit-body");
        if (!"annotated-body".equals(annotatedAttachment())) {
            throw new IllegalStateException("Unexpected annotated attachment return value");
        }
        Listener.saveTextLog("listener-body");
        Allure.getLifecycle().stopTestCase(uuid);
        Allure.getLifecycle().writeTestCase(uuid);
        System.out.println("ALLURE_ATTACHMENTS_OK");
    }
}

class MeasureAspectWarningProbe {
    public static void main(String[] args) {
        System.out.println("MEASURE_PROBE_OK");
    }
}
