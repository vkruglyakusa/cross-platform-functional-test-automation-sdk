package com.test.automation.sdk.impact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes a minimal TestNG suite XML file containing only the given set of
 * (fully qualified) test class names, so it can be executed directly via:
 * {@code mvn test -Dsurefire.suiteXmlFiles=<output path>}
 */
public final class ImpactSuiteWriter {

    private ImpactSuiteWriter() {
    }

    public static void write(Set<String> testClassNames, Path outputFile) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n");
        xml.append("<suite name=\"Impacted Tests\" verbose=\"1\">\n");
        xml.append("    <test name=\"Impacted\">\n");
        xml.append("        <classes>\n");
        for (String className : new TreeSet<>(testClassNames)) {
            xml.append("            <class name=\"").append(className).append("\"/>\n");
        }
        xml.append("        </classes>\n");
        xml.append("    </test>\n");
        xml.append("</suite>\n");

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outputFile, xml.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write impact suite XML to " + outputFile, e);
        }
    }
}
