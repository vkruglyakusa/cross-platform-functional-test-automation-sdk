package com.test.automation.sdk.reporting;

import java.util.ArrayList;
import java.util.List;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.TestResult;

/**
 * Re-applies the SDK's neutral parentSuite/suite/subSuite labels directly inside Allure's own
 * lifecycle callbacks (auto-discovered via the {@code ServiceLoader}-based
 * {@code META-INF/services/io.qameta.allure.listener.TestLifecycleListener} mechanism).
 *
 * <p>TestNG does not guarantee that suite/service-loaded {@code ITestListener}s (such as
 * {@code io.qameta.allure.testng.AllureTestNg}) run before or after this SDK's own
 * {@code com.test.automation.sdk.listener.Listener} for a given test -- that relative order can
 * legitimately vary across TestNG versions and execution modes (suite-XML-file execution vs.
 * programmatic {@code XmlSuite} execution). Relying on {@code Allure.getLifecycle()
 * .getCurrentTestCase()} being visible from within {@code Listener}'s callbacks is therefore
 * fragile. This listener instead hooks directly into Allure's own lifecycle notifications, which
 * are always invoked synchronously, on the correct thread, at the exact moment Allure processes
 * the test case -- independent of TestNG's listener ordering.</p>
 */
public final class AllureLabelLifecycleListener implements TestLifecycleListener {

    @Override
    public void beforeTestStart(TestResult result) {
        applyLabels(result);
    }

    @Override
    public void beforeTestWrite(TestResult result) {
        applyLabels(result);
    }

    private void applyLabels(TestResult result) {
        if (result == null) {
            return;
        }
        ExecutionReporting.CurrentTestMetadata metadata = ExecutionReporting.peekCurrentTestMetadata();
        if (metadata.suiteName.isEmpty() && metadata.testNgTestName.isEmpty() && metadata.className.isEmpty()) {
            return;
        }
        List<Label> labels = new ArrayList<Label>();
        if (result.getLabels() != null) {
            labels.addAll(result.getLabels());
        }
        if (!metadata.suiteName.isEmpty()) {
            labels = AllureExecutionReporter.replaceLabel(labels, "parentSuite", metadata.suiteName);
        }
        if (!metadata.testNgTestName.isEmpty()) {
            labels = AllureExecutionReporter.replaceLabel(labels, "suite", metadata.testNgTestName);
        }
        if (!metadata.className.isEmpty()) {
            labels = AllureExecutionReporter.replaceLabel(labels, "subSuite", metadata.className);
        }
        result.setLabels(labels);
    }
}
