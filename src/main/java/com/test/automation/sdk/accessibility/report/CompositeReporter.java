package com.test.automation.sdk.accessibility.report;

import java.util.Arrays;
import java.util.List;

/**
 * Fan-out {@link A11yReporter} that forwards every message to each delegate.
 * Handy to log via SLF4J <em>and</em> push to a rich report at the same time:
 *
 * <pre>{@code
 * AccessibilityChecker.setReporter(
 *     new CompositeReporter(new Slf4jReporter(), new ExtentA11yReporter()));
 * }</pre>
 */
public class CompositeReporter implements A11yReporter {

    private final List<A11yReporter> delegates;

    public CompositeReporter(A11yReporter... delegates) {
        this.delegates = Arrays.asList(delegates);
    }

    @Override
    public void info(String message) {
        for (A11yReporter d : delegates) {
            d.info(message);
        }
    }

    @Override
    public void warn(String message) {
        for (A11yReporter d : delegates) {
            d.warn(message);
        }
    }

    @Override
    public void fail(String message) {
        for (A11yReporter d : delegates) {
            d.fail(message);
        }
    }
}

