package com.test.automation.sdk.reporting;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;

/**
 * Maps neutral execution events into Allure steps and attachments.
 */
public final class AllureExecutionReporter implements ExecutionReporter {

    private final ThreadLocal<Deque<String>> activeStepIds =
            ThreadLocal.withInitial(ArrayDeque<String>::new);

    @Override
    public void report(ExecutionEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case STEP_STARTED:
                startStep(event);
                break;
            case STEP_PASSED:
                finishStep(event, Status.PASSED);
                break;
            case STEP_FAILED:
                finishStep(event, Status.FAILED);
                break;
            case SCREENSHOT_CAPTURED:
            case DOM_CAPTURED:
            case PAGE_SOURCE_CAPTURED:
                attachEvidence(event);
                break;
            case EXCEPTION:
                attachException(event);
                break;
            case TEST_PASSED:
            case TEST_FAILED:
            case TEST_SKIPPED:
                activeStepIds.remove();
                break;
            default:
                break;
        }
    }

    private void startStep(ExecutionEvent event) {
        String uuid = event.getStepId().isEmpty() ? UUID.randomUUID().toString() : event.getStepId();
        StepResult step = new StepResult()
                .setName(buildStepName(event))
                .setStatus(Status.PASSED);
        Allure.getLifecycle().startStep(uuid, step);
        activeStepIds.get().push(uuid);
    }

    private void finishStep(ExecutionEvent event, Status status) {
        Deque<String> stack = activeStepIds.get();
        String uuid = stack.isEmpty() ? event.getStepId() : stack.pop();
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        Allure.getLifecycle().updateStep(uuid, step -> {
            step.setStatus(status);
            if (event.getDurationMillis() != null) {
                step.setStop(event.getTimestampMillis());
            }
            if (event.getThrowable() != null || !event.getMessage().isEmpty()) {
                StatusDetails details = new StatusDetails();
                details.setMessage(SecretRedactor.redactMessage(
                        event.getThrowable() != null ? event.getThrowable().getMessage() : event.getMessage()));
                if (event.getThrowable() != null) {
                    details.setTrace(stackTrace(event.getThrowable()));
                }
                step.setStatusDetails(details);
            }
        });
        Allure.getLifecycle().stopStep(uuid);
    }

    private void attachEvidence(ExecutionEvent event) {
        for (ExecutionEvidence evidence : event.getEvidence()) {
            if (evidence == null || evidence.getPath() == null) {
                continue;
            }
            Path path = evidence.getPath();
            try (InputStream in = Files.newInputStream(path)) {
                Allure.addAttachment(
                        evidence.getName().isEmpty() ? event.getType().name() : evidence.getName(),
                        evidence.getContentType(),
                        in,
                        extension(path));
            } catch (Exception ignored) {
            }
        }
    }

    private void attachException(ExecutionEvent event) {
        if (event.getThrowable() == null) {
            return;
        }
        Allure.addAttachment("Exception", "text/plain", stackTrace(event.getThrowable()));
    }

    private String buildStepName(ExecutionEvent event) {
        if (event.getStepNumber() == null) {
            return SecretRedactor.redactMessage(event.getStepName());
        }
        return "Step " + event.getStepNumber() + ": " + SecretRedactor.redactMessage(event.getStepName());
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private static String stackTrace(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        java.io.PrintWriter printWriter = new java.io.PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }
}
