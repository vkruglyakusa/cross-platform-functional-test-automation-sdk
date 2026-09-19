package com.test.automation.sdk.flaky;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FlakyTestRegistry")
class FlakyTestRegistryTest {

    @TempDir
    File tempDir;

    private void writeRunFile(String name, String... lines) throws Exception {
        Path file = tempDir.toPath().resolve(name);
        Files.write(file, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a test with mixed pass/fail history, enough runs, and a low failure rate is known flaky")
    void mixedHistoryWithEnoughRunsIsFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");

        FlakyTestRegistry registry = new FlakyTestRegistry(tempDir.toPath(), 5, 80.0);

        assertTrue(registry.isKnownFlaky("CheckoutTest.testCheckout"));
    }

    @Test
    @DisplayName("a test with too few runs is not quarantined even if mixed pass/fail")
    void tooFewRunsIsNotFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");

        FlakyTestRegistry registry = new FlakyTestRegistry(tempDir.toPath(), 5, 80.0);

        assertFalse(registry.isKnownFlaky("CheckoutTest.testCheckout"));
    }

    @Test
    @DisplayName("a test that fails almost every run is broken, not flaky, and is not quarantined")
    void consistentlyFailingTestIsNotFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"BrokenTest\",\"methodName\":\"testBroken\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"BrokenTest\",\"methodName\":\"testBroken\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"BrokenTest\",\"methodName\":\"testBroken\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"BrokenTest\",\"methodName\":\"testBroken\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"BrokenTest\",\"methodName\":\"testBroken\"}");

        FlakyTestRegistry registry = new FlakyTestRegistry(tempDir.toPath(), 5, 50.0);

        assertFalse(registry.isKnownFlaky("BrokenTest.testBroken"));
    }

    @Test
    @DisplayName("a test that only ever passes is not flaky")
    void onlyPassingTestIsNotFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"StableTest\",\"methodName\":\"testStable\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"StableTest\",\"methodName\":\"testStable\"}");

        FlakyTestRegistry registry = new FlakyTestRegistry(tempDir.toPath(), 1, 80.0);

        assertFalse(registry.isKnownFlaky("StableTest.testStable"));
    }

    @Test
    @DisplayName("an unknown test key (no history at all) is not flaky")
    void unknownTestKeyIsNotFlaky() throws Exception {
        FlakyTestRegistry registry = new FlakyTestRegistry(tempDir.toPath(), 1, 80.0);

        assertFalse(registry.isKnownFlaky("NeverSeenTest.testNeverSeen"));
        assertFalse(registry.isKnownFlaky(null));
        assertFalse(registry.isKnownFlaky(""));
    }

    @Test
    @DisplayName("a non-existent analytics directory yields no flaky tests, never throws")
    void nonExistentDirectoryYieldsNoFlakyTests() {
        Path missing = tempDir.toPath().resolve("does-not-exist");
        FlakyTestRegistry registry = new FlakyTestRegistry(missing, 1, 80.0);

        assertFalse(registry.isKnownFlaky("Anything.anything"));
        assertTrue(registry.getHistory().isEmpty());
    }

    @Test
    @DisplayName("buildTestKey mirrors AnalyticsTrendReport's className.methodName[testCaseName] format")
    void buildTestKeyMirrorsAnalyticsFormat() {
        assertEquals("LoginTest.testLogin", FlakyTestRegistry.buildTestKey("LoginTest", "testLogin", null));
        assertEquals("LoginTest.testLogin", FlakyTestRegistry.buildTestKey("LoginTest", "testLogin", ""));
        assertEquals("SearchTest.testSearch[ADO-1]", FlakyTestRegistry.buildTestKey("SearchTest", "testSearch", "ADO-1"));
        assertEquals("", FlakyTestRegistry.buildTestKey("", "", null));
    }
}
