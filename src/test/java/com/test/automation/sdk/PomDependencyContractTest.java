package com.test.automation.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("pom.xml dependency and Java contract")
class PomDependencyContractTest {

    private final XPath xpath = XPathFactory.newInstance().newXPath();

    @Test
    @DisplayName("compiler and enforcer implement Java 20 or newer as a minimum contract")
    void javaMinimumContractIsTwentyOrNewer() throws Exception {
        Document pom = readPom();

        assertEquals("20", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='java.version.minimum']"));
        assertEquals("${java.version.minimum}", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='maven.compiler.source']"));
        assertEquals("${java.version.minimum}", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='maven.compiler.target']"));
        assertEquals("${java.version.minimum}", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='maven.compiler.release']"));
        assertEquals("${maven.compiler.release}",
                value(pom, "/*[local-name()='project']/*[local-name()='build']/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId']='maven-compiler-plugin']/*[local-name()='configuration']/*[local-name()='release']"));
        assertEquals("[${java.version.minimum},)",
                value(pom, "/*[local-name()='project']/*[local-name()='build']/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId']='maven-enforcer-plugin']/*[local-name()='executions']/*[local-name()='execution'][*[local-name()='id']='enforce-java-minimum']/*[local-name()='configuration']/*[local-name()='rules']/*[local-name()='requireJavaVersion']/*[local-name()='version']"));
        assertTrue(
                value(pom, "/*[local-name()='project']/*[local-name()='build']/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId']='maven-enforcer-plugin']/*[local-name()='executions']/*[local-name()='execution'][*[local-name()='id']='enforce-java-minimum']/*[local-name()='configuration']/*[local-name()='rules']/*[local-name()='requireJavaVersion']/*[local-name()='message']")
                        .contains("Java ${java.version.minimum} or newer"),
                "Enforcer message must describe a minimum, not an exact JDK");
    }

    @Test
    @DisplayName("Selenium version is defined once through the shared property and BOM")
    void seleniumVersionIsManagedAuthoritatively() throws Exception {
        Document pom = readPom();

        assertEquals("4.44.0", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='selenium.version']"));
        assertEquals("${selenium.version}",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencyManagement']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='selenium-bom']/*[local-name()='version']"));
        assertEquals("",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='selenium-api']/*[local-name()='version']"),
                "selenium-api should inherit its version from the BOM");
        assertEquals("",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='selenium-remote-driver']/*[local-name()='version']"),
                "selenium-remote-driver should inherit its version from the BOM");
        assertEquals("",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='selenium-support']/*[local-name()='version']"),
                "selenium-support should inherit its version from the BOM");
        assertEquals("",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='selenium-java']/*[local-name()='version']"),
                "selenium-java should inherit its version from the BOM rather than duplicating a literal");
    }

    @Test
    @DisplayName("Appium version is pinned to the approved 10.1.1 baseline")
    void appiumVersionMatchesApprovedBaseline() throws Exception {
        Document pom = readPom();

        assertEquals("10.1.1", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='appium.version']"));
        assertEquals("${appium.version}",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='java-client']/*[local-name()='version']"));
    }

    @Test
    @DisplayName("AspectJ weaver remains pinned to 1.9.25")
    void aspectjVersionMatchesApprovedBaseline() throws Exception {
        Document pom = readPom();

        assertEquals("1.9.25", value(pom, "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='aspectj.version']"));
        assertEquals("${aspectj.version}",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='aspectjweaver']/*[local-name()='version']"));
    }

    @Test
    @DisplayName("BrowserStack remains optional for consumers")
    void browserStackDependencyIsOptional() throws Exception {
        Document pom = readPom();

        assertEquals("true",
                value(pom, "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='browserstack-java-sdk']/*[local-name()='optional']"));
    }

    private Document readPom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File("pom.xml"));
    }

    private String value(Document document, String expression) throws Exception {
        String result = (String) xpath.evaluate(expression, document, XPathConstants.STRING);
        return result == null ? "" : result.trim();
    }
}
