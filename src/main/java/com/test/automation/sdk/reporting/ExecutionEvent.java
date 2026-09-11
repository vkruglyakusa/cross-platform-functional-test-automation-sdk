package com.test.automation.sdk.reporting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable execution event emitted once by the SDK core and then fanned out to
 * multiple reporter adapters.
 */
public final class ExecutionEvent {

    private final ExecutionEventType type;
    private final ExecutionStatus status;
    private final String executionId;
    private final String testName;
    private final String testCaseName;
    private final String stepId;
    private final Integer stepNumber;
    private final String stepName;
    private final String action;
    private final String message;
    private final String expectedResult;
    private final String actualResult;
    private final String locator;
    private final String url;
    private final String platform;
    private final String browser;
    private final String device;
    private final long timestampMillis;
    private final Long durationMillis;
    private final Throwable throwable;
    private final List<ExecutionEvidence> evidence;

    private ExecutionEvent(Builder builder) {
        this.type = builder.type;
        this.status = builder.status;
        this.executionId = builder.executionId;
        this.testName = builder.testName;
        this.testCaseName = builder.testCaseName;
        this.stepId = builder.stepId;
        this.stepNumber = builder.stepNumber;
        this.stepName = builder.stepName;
        this.action = builder.action;
        this.message = builder.message;
        this.expectedResult = builder.expectedResult;
        this.actualResult = builder.actualResult;
        this.locator = builder.locator;
        this.url = builder.url;
        this.platform = builder.platform;
        this.browser = builder.browser;
        this.device = builder.device;
        this.timestampMillis = builder.timestampMillis;
        this.durationMillis = builder.durationMillis;
        this.throwable = builder.throwable;
        this.evidence = Collections.unmodifiableList(new ArrayList<ExecutionEvidence>(builder.evidence));
    }

    public static Builder builder(ExecutionEventType type) {
        return new Builder(type);
    }

    public ExecutionEventType getType() {
        return type;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getTestName() {
        return testName;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public String getStepId() {
        return stepId;
    }

    public Integer getStepNumber() {
        return stepNumber;
    }

    public String getStepName() {
        return stepName;
    }

    public String getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getActualResult() {
        return actualResult;
    }

    public String getLocator() {
        return locator;
    }

    public String getUrl() {
        return url;
    }

    public String getPlatform() {
        return platform;
    }

    public String getBrowser() {
        return browser;
    }

    public String getDevice() {
        return device;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public List<ExecutionEvidence> getEvidence() {
        return evidence;
    }

    public static final class Builder {
        private final ExecutionEventType type;
        private ExecutionStatus status = ExecutionStatus.INFO;
        private String executionId = "";
        private String testName = "";
        private String testCaseName = "";
        private String stepId = "";
        private Integer stepNumber;
        private String stepName = "";
        private String action = "";
        private String message = "";
        private String expectedResult = "";
        private String actualResult = "";
        private String locator = "";
        private String url = "";
        private String platform = "";
        private String browser = "";
        private String device = "";
        private long timestampMillis = System.currentTimeMillis();
        private Long durationMillis;
        private Throwable throwable;
        private List<ExecutionEvidence> evidence = new ArrayList<ExecutionEvidence>();

        private Builder(ExecutionEventType type) {
            this.type = type;
        }

        public Builder status(ExecutionStatus status) {
            this.status = status == null ? ExecutionStatus.INFO : status;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = nullToEmpty(executionId);
            return this;
        }

        public Builder testName(String testName) {
            this.testName = nullToEmpty(testName);
            return this;
        }

        public Builder testCaseName(String testCaseName) {
            this.testCaseName = nullToEmpty(testCaseName);
            return this;
        }

        public Builder stepId(String stepId) {
            this.stepId = nullToEmpty(stepId);
            return this;
        }

        public Builder stepNumber(Integer stepNumber) {
            this.stepNumber = stepNumber;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = nullToEmpty(stepName);
            return this;
        }

        public Builder action(String action) {
            this.action = nullToEmpty(action);
            return this;
        }

        public Builder message(String message) {
            this.message = nullToEmpty(message);
            return this;
        }

        public Builder expectedResult(String expectedResult) {
            this.expectedResult = nullToEmpty(expectedResult);
            return this;
        }

        public Builder actualResult(String actualResult) {
            this.actualResult = nullToEmpty(actualResult);
            return this;
        }

        public Builder locator(String locator) {
            this.locator = nullToEmpty(locator);
            return this;
        }

        public Builder url(String url) {
            this.url = nullToEmpty(url);
            return this;
        }

        public Builder platform(String platform) {
            this.platform = nullToEmpty(platform);
            return this;
        }

        public Builder browser(String browser) {
            this.browser = nullToEmpty(browser);
            return this;
        }

        public Builder device(String device) {
            this.device = nullToEmpty(device);
            return this;
        }

        public Builder timestampMillis(long timestampMillis) {
            this.timestampMillis = timestampMillis;
            return this;
        }

        public Builder durationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder evidence(List<ExecutionEvidence> evidence) {
            this.evidence = evidence == null ? new ArrayList<ExecutionEvidence>() : new ArrayList<ExecutionEvidence>(evidence);
            return this;
        }

        public Builder addEvidence(ExecutionEvidence evidence) {
            if (evidence != null) {
                this.evidence.add(evidence);
            }
            return this;
        }

        public ExecutionEvent build() {
            return new ExecutionEvent(this);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
