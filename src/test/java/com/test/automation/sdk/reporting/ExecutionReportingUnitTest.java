package com.test.automation.sdk.reporting;

import com.test.automation.sdk.testbase.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testng.ITestClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Execution reporting unit tests")
class ExecutionReportingUnitTest {

    @AfterEach
    void cleanup() {
        ExecutionReporting.resetReporterForTests();
        ExecutionReporting.clear();
    }

    @Test
    @DisplayName("SDK step emits started and passed events with duration")
    void stepEmitsStartedAndPassedEvents() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        ExecutionReporting.setReporterForTests(reporter);
        StepEnabledTestBase base = new StepEnabledTestBase();
        TestBase.setCurrentTestCaseName("ADO-101 Home page");

        base.runStep("Verify page loads", new TestBase.StepAction() {
            @Override
            public void run() {
            }
        });

        assertEquals(2, reporter.events.size(), "Expected start + pass events");
        assertEquals(ExecutionEventType.STEP_STARTED, reporter.events.get(0).getType());
        assertEquals(ExecutionEventType.STEP_PASSED, reporter.events.get(1).getType());
        assertEquals("Verify page loads", reporter.events.get(0).getStepName());
        assertEquals(Integer.valueOf(1), reporter.events.get(0).getStepNumber());
        assertTrue(reporter.events.get(1).getDurationMillis() != null
                && reporter.events.get(1).getDurationMillis() >= 0L,
                "Step duration should be populated");
        assertEquals("ADO-101 Home page", reporter.events.get(1).getTestCaseName());
    }

    @Test
    @DisplayName("SDK step failure is reported and original exception propagates")
    void stepFailureRethrowsOriginalException() {
        RecordingReporter reporter = new RecordingReporter();
        ExecutionReporting.setReporterForTests(reporter);
        StepEnabledTestBase base = new StepEnabledTestBase();
        IllegalStateException failure = new IllegalStateException("step boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                base.runStep("Verify announcements section", new TestBase.StepAction() {
                    @Override
                    public void run() {
                        throw failure;
                    }
                }));

        assertSame(failure, thrown, "Original exception instance must propagate");
        assertEquals(2, reporter.events.size(), "Expected start + failure events");
        assertEquals(ExecutionEventType.STEP_FAILED, reporter.events.get(1).getType());
        assertSame(failure, reporter.events.get(1).getThrowable());
    }

    @Test
    @DisplayName("Composite reporter forwards the same logical event to multiple adapters")
    void compositeReporterForwardsSameEvent() {
        RecordingReporter left = new RecordingReporter();
        RecordingReporter right = new RecordingReporter();
        CompositeExecutionReporter composite = new CompositeExecutionReporter(left, right);
        ExecutionEvent event = ExecutionEvent.builder(ExecutionEventType.INFO)
                .message("Shared logical event")
                .build();

        composite.report(event);

        assertEquals(1, left.events.size());
        assertEquals(1, right.events.size());
        assertSame(event, left.events.get(0));
        assertSame(event, right.events.get(0));
    }

    @Test
    @DisplayName("Secret redactor masks passwords tokens and auth headers centrally")
    void secretRedactorMasksSecrets() {
        String message = "password=myPass123 token=abc123 Authorization=Bearer xyz apiKey=qwerty";
        String masked = SecretRedactor.redactMessage(message);

        assertFalse(masked.contains("myPass123"));
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("xyz"));
        assertFalse(masked.contains("qwerty"));
        assertTrue(masked.contains("[REDACTED]"));
        assertEquals("[REDACTED]", SecretRedactor.redactTypedValue("password field", "superSecret"));
    }

    @Test
    @DisplayName("Failure evidence remains associated with published logical events")
    void failureEvidenceIsIncludedInPublishedEvents() {
        RecordingReporter reporter = new RecordingReporter();
        ExecutionReporting.setReporterForTests(reporter);
        List<ExecutionEvidence> evidence = new ArrayList<ExecutionEvidence>();
        evidence.add(ExecutionEvidence.screenshot("Failure Screenshot", Paths.get("target", "fake.png")));
        evidence.add(ExecutionEvidence.domDump("Failure DOM", Paths.get("target", "fake.html")));

        ExecutionReporting.onTestFailed(mockResult("reportingFailure"), new IllegalStateException("boom"), evidence);

        assertTrue(reporter.events.size() >= 3, "Expected exception + evidence + test failure events");
        long evidenceEvents = reporter.events.stream().filter(event ->
                event.getType() == ExecutionEventType.SCREENSHOT_CAPTURED
                        || event.getType() == ExecutionEventType.DOM_CAPTURED).count();
        assertEquals(2L, evidenceEvents);
        ExecutionEvent failed = reporter.events.get(reporter.events.size() - 1);
        assertEquals(ExecutionEventType.TEST_FAILED, failed.getType());
        assertEquals(2, failed.getEvidence().size());
    }

    @Test
    @DisplayName("Lifecycle events publish started and passed states")
    void lifecycleEventsPublishStartedAndPassed() {
        RecordingReporter reporter = new RecordingReporter();
        ExecutionReporting.setReporterForTests(reporter);
        TestBase.setCurrentTestCaseName("ADO-202 Verify links");

        ITestResult result = mockResult("lifecycleStory");
        ExecutionReporting.onTestStarted(result);
        ExecutionReporting.onTestPassed(result);

        assertEquals(2, reporter.events.size());
        assertEquals(ExecutionEventType.TEST_STARTED, reporter.events.get(0).getType());
        assertEquals(ExecutionEventType.TEST_PASSED, reporter.events.get(1).getType());
        assertEquals("ADO-202 Verify links", reporter.events.get(0).getTestCaseName());
    }

    @Test
    @DisplayName("Parallel reporting state stays isolated per thread")
    void parallelIsolationIsMaintained() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        ExecutionReporting.setReporterForTests(reporter);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Runnable work = new Runnable() {
            @Override
            public void run() {
                try {
                    StepEnabledTestBase base = new StepEnabledTestBase();
                    TestBase.setCurrentTestCaseName(Thread.currentThread().getName());
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    base.runStep("Parallel step", new TestBase.StepAction() {
                        @Override
                        public void run() {
                        }
                    });
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    ExecutionReporting.clear();
                }
            }
        };

        Thread left = new Thread(work, "TC-A");
        Thread right = new Thread(work, "TC-B");
        left.start();
        right.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS), "Threads did not get ready");
        start.countDown();
        left.join();
        right.join();

        if (failure.get() != null) {
            throw new AssertionError("Parallel worker failed", failure.get());
        }

        List<String> testCaseNames = new ArrayList<String>();
        for (ExecutionEvent event : reporter.events) {
            if (event.getType() == ExecutionEventType.STEP_PASSED) {
                testCaseNames.add(event.getTestCaseName());
            }
        }
        Collections.sort(testCaseNames);
        assertEquals(2, testCaseNames.size());
        assertEquals("TC-A", testCaseNames.get(0));
        assertEquals("TC-B", testCaseNames.get(1));
    }

    private ITestResult mockResult(String methodName) {
        ITestResult result = Mockito.mock(ITestResult.class);
        ITestNGMethod testNgMethod = Mockito.mock(ITestNGMethod.class);
        ITestClass testClass = Mockito.mock(ITestClass.class);
        Mockito.when(testClass.getRealClass()).thenReturn((Class) StepEnabledTestBase.class);
        Mockito.when(testNgMethod.getMethodName()).thenReturn(methodName);
        Mockito.when(result.getTestClass()).thenReturn(testClass);
        Mockito.when(result.getMethod()).thenReturn(testNgMethod);
        Mockito.when(result.getName()).thenReturn(methodName);
        return result;
    }

    private static final class StepEnabledTestBase extends TestBase {
        void runStep(String name, StepAction action) throws Exception {
            step(name, action);
        }
    }

    private static final class RecordingReporter implements ExecutionReporter {
        private final List<ExecutionEvent> events = new CopyOnWriteArrayList<ExecutionEvent>();

        @Override
        public void report(ExecutionEvent event) {
            assertNotNull(event);
            events.add(event);
        }
    }
}
