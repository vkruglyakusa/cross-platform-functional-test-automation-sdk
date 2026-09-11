package com.test.automation.sdk.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Human-readable log adapter. Business lifecycle and steps are logged at INFO;
 * lower-level actions are logged at DEBUG.
 */
public final class ExecutionLogReporter implements ExecutionReporter {

    private static final Logger log = LogManager.getLogger(ExecutionLogReporter.class);

    @Override
    public void report(ExecutionEvent event) {
        if (event == null) {
            return;
        }
        String message = format(event);
        switch (event.getType()) {
            case ACTION_STARTED:
            case ACTION_COMPLETED:
            case ACTION_FAILED:
                if (event.getThrowable() != null) {
                    log.debug(message, event.getThrowable());
                } else {
                    log.debug(message);
                }
                break;
            case WARNING:
                log.warn(message);
                break;
            case EXCEPTION:
                if (event.getThrowable() != null) {
                    log.error(message, event.getThrowable());
                } else {
                    log.error(message);
                }
                break;
            case TEST_FAILED:
            case STEP_FAILED:
                if (event.getThrowable() != null) {
                    log.error(message, event.getThrowable());
                } else {
                    log.error(message);
                }
                break;
            default:
                log.info(message);
                break;
        }
    }

    private String format(ExecutionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.getType());
        if (!event.getExecutionId().isEmpty()) {
            sb.append(" | executionId=").append(event.getExecutionId());
        }
        if (!event.getTestName().isEmpty()) {
            sb.append(" | test=").append(SecretRedactor.redactMessage(event.getTestName()));
        }
        if (!event.getTestCaseName().isEmpty()) {
            sb.append(" | case=").append(SecretRedactor.redactMessage(event.getTestCaseName()));
        }
        if (event.getStepNumber() != null) {
            sb.append(" | step=").append(event.getStepNumber());
        }
        if (!event.getStepName().isEmpty()) {
            sb.append(" | stepName=").append(SecretRedactor.redactMessage(event.getStepName()));
        }
        if (!event.getAction().isEmpty()) {
            sb.append(" | action=").append(SecretRedactor.redactMessage(event.getAction()));
        }
        if (!event.getExpectedResult().isEmpty()) {
            sb.append(" | expected=").append(SecretRedactor.redactMessage(event.getExpectedResult()));
        }
        if (!event.getActualResult().isEmpty()) {
            sb.append(" | actual=").append(SecretRedactor.redactMessage(event.getActualResult()));
        }
        if (!event.getLocator().isEmpty()) {
            sb.append(" | locator=").append(SecretRedactor.redactMessage(event.getLocator()));
        }
        if (!event.getUrl().isEmpty()) {
            sb.append(" | url=").append(SecretRedactor.redactMessage(event.getUrl()));
        }
        if (!event.getBrowser().isEmpty()) {
            sb.append(" | browser=").append(event.getBrowser());
        }
        if (!event.getPlatform().isEmpty()) {
            sb.append(" | platform=").append(event.getPlatform());
        }
        if (!event.getDevice().isEmpty()) {
            sb.append(" | device=").append(SecretRedactor.redactMessage(event.getDevice()));
        }
        if (event.getDurationMillis() != null) {
            sb.append(" | durationMs=").append(event.getDurationMillis());
        }
        if (!event.getMessage().isEmpty()) {
            sb.append(" | ").append(SecretRedactor.redactMessage(event.getMessage()));
        }
        if (!event.getEvidence().isEmpty()) {
            sb.append(" | evidence=").append(event.getEvidence().size());
        }
        return sb.toString();
    }
}
