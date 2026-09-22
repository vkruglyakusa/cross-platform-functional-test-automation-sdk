package com.test.automation.sdk.accessibility.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link CompositeReporter} isolates a broken delegate (e.g. an
 * Allure/Extent/Excel-style sink throwing) so that:
 * <ul>
 *   <li>the other configured reporters still receive the finding, and</li>
 *   <li>the failure never propagates out of {@code AccessibilityChecker} to
 *       replace the real functional/accessibility-enforcement result.</li>
 * </ul>
 */
@DisplayName("CompositeReporter failure isolation")
class CompositeReporterFailureSafetyTest {

    /** Reporter that always throws — simulates an Allure/Extent/Excel-style sink failure. */
    private static final class ExplodingReporter implements A11yReporter {
        @Override public void info(String message) { throw new RuntimeException("simulated info failure"); }
        @Override public void warn(String message) { throw new RuntimeException("simulated warn failure"); }
        @Override public void fail(String message) { throw new RuntimeException("simulated fail failure"); }
    }

    /** Reporter that records every call it receives, for assertion. */
    private static final class RecordingReporter implements A11yReporter {
        final List<String> received = new ArrayList<>();
        @Override public void info(String message) { received.add("info:" + message); }
        @Override public void warn(String message) { received.add("warn:" + message); }
        @Override public void fail(String message) { received.add("fail:" + message); }
    }

    @Test
    @DisplayName("a throwing delegate does not prevent other delegates from receiving the finding")
    void throwingDelegateDoesNotBlockOthers() {
        RecordingReporter recorder = new RecordingReporter();
        CompositeReporter composite = new CompositeReporter(new ExplodingReporter(), recorder);

        assertDoesNotThrow(() -> composite.info("scan started"));
        assertDoesNotThrow(() -> composite.warn("moderate finding"));
        assertDoesNotThrow(() -> composite.fail("critical finding"));

        assertEquals(List.of("info:scan started", "warn:moderate finding", "fail:critical finding"), recorder.received);
    }

    @Test
    @DisplayName("delegate order does not matter — a healthy reporter before a broken one still isolates the failure")
    void healthyDelegateBeforeBrokenOneStillIsolatesFailure() {
        RecordingReporter recorder = new RecordingReporter();
        CompositeReporter composite = new CompositeReporter(recorder, new ExplodingReporter());

        assertDoesNotThrow(() -> composite.fail("critical finding"));
        assertEquals(List.of("fail:critical finding"), recorder.received);
    }

    @Test
    @DisplayName("all delegates broken: composite still never throws (findings are lost, not the test result)")
    void allDelegatesBrokenNeverThrows() {
        CompositeReporter composite = new CompositeReporter(new ExplodingReporter(), new ExplodingReporter());
        assertDoesNotThrow(() -> composite.info("x"));
        assertDoesNotThrow(() -> composite.warn("y"));
        assertDoesNotThrow(() -> composite.fail("z"));
    }
}
