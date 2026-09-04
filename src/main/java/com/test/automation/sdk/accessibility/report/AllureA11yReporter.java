package com.test.automation.sdk.accessibility.report;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StepResult;

import java.util.UUID;

/**
 * {@link A11yReporter} adapter that routes accessibility findings into the
 * active Allure report.
 *
 * <h3>Usage</h3>
 * <pre>
 * // In TestBase.initAccessibility() or @BeforeSuite:
 * AccessibilityChecker.setReporter(
 *     new CompositeReporter(new Slf4jReporter(), new AllureA11yReporter()));
 * </pre>
 *
 * <h3>What each level produces in Allure</h3>
 * <ul>
 *   <li>{@link #info} — a PASSED step: "Accessibility: &lt;message&gt;"</li>
 *   <li>{@link #warn} — a BROKEN step with the message as body</li>
 *   <li>{@link #fail} — a FAILED step; also attaches the raw violation HTML as a
 *       text attachment so the full element detail is visible in the Allure UI.</li>
 * </ul>
 *
 * <p>HTML tags in messages are stripped for step names (Allure renders step names
 * as plain text) but preserved in attachments.</p>
 */
public class AllureA11yReporter implements A11yReporter {

    private static final String STEP_PREFIX = "[A11Y] ";

    @Override
    public void info(String message) {
        String title = STEP_PREFIX + stripHtml(message);
        StepResult step = new StepResult();
        step.setName(truncate(title, 200));
        step.setStatus(Status.PASSED);
        Allure.getLifecycle().startStep(UUID.randomUUID().toString(), step);
        Allure.getLifecycle().stopStep();
    }

    @Override
    public void warn(String message) {
        String title = STEP_PREFIX + "WARNING — " + stripHtml(message);
        StepResult step = new StepResult();
        step.setName(truncate(title, 200));
        step.setStatus(Status.BROKEN);
        String uuid = UUID.randomUUID().toString();
        Allure.getLifecycle().startStep(uuid, step);
        Allure.addAttachment("Accessibility Warning Detail", "text/html",
                message != null ? message : "", ".html");
        Allure.getLifecycle().stopStep();
    }

    @Override
    public void fail(String message) {
        String title = STEP_PREFIX + "VIOLATION — " + stripHtml(message);
        StepResult step = new StepResult();
        step.setName(truncate(title, 200));
        step.setStatus(Status.FAILED);
        String uuid = UUID.randomUUID().toString();
        Allure.getLifecycle().startStep(uuid, step);
        Allure.addAttachment("Accessibility Violation Detail", "text/html",
                message != null ? message : "", ".html");
        Allure.getLifecycle().stopStep();
    }

    /** Removes HTML tags so step names render cleanly in Allure. */
    static String stripHtml(String message) {
        if (message == null) return "";
        return message
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&lt;",  "<")
                .replaceAll("&gt;",  ">")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+",  " ")
                .trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
