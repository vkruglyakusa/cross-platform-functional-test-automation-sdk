package com.test.automation.sdk.accessibility.report;

/**
 * {@link A11yReporter} that discards all messages. Use when you only want the
 * file artifacts (JSON / Excel / HTML) and no inline/log output.
 */
public class NoOpReporter implements A11yReporter {

    @Override
    public void info(String message) {
        // no-op
    }

    @Override
    public void warn(String message) {
        // no-op
    }

    @Override
    public void fail(String message) {
        // no-op
    }
}

