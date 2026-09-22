package com.test.automation.sdk.accessibility.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fan-out {@link A11yReporter} that forwards every message to each delegate.
 * Handy to log via SLF4J <em>and</em> push to a rich report at the same time:
 *
 * <pre>{@code
 * AccessibilityChecker.setReporter(
 *     new CompositeReporter(new Slf4jReporter(), new ExtentA11yReporter()));
 * }</pre>
 *
 * <p><b>Failure isolation:</b> each delegate is invoked in its own try/catch. A broken
 * sink (e.g. Allure lifecycle not started, an Extent I/O error) is logged and skipped —
 * it never prevents the remaining sinks from receiving the finding, and it never
 * propagates out of {@code AccessibilityChecker} to replace the real functional or
 * accessibility-enforcement result with a reporter-internal exception.</p>
 */
public class CompositeReporter implements A11yReporter {

    private static final Logger logger = LoggerFactory.getLogger(CompositeReporter.class);

    private final List<A11yReporter> delegates;

    public CompositeReporter(A11yReporter... delegates) {
        this.delegates = Arrays.asList(delegates);
    }

    @Override
    public void info(String message) {
        dispatch(d -> d.info(message));
    }

    @Override
    public void warn(String message) {
        dispatch(d -> d.warn(message));
    }

    @Override
    public void fail(String message) {
        dispatch(d -> d.fail(message));
    }

    private void dispatch(Consumer<A11yReporter> action) {
        for (A11yReporter d : delegates) {
            try {
                action.accept(d);
            } catch (Exception e) {
                logger.warn("[A11Y REPORTER] {} failed to record an accessibility finding — "
                                + "continuing with remaining reporters: {}",
                        d.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}

