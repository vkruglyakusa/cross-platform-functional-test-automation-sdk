package com.test.automation.sdk;

import com.test.automation.sdk.execution.RunMode;
import io.qameta.allure.Attachment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
