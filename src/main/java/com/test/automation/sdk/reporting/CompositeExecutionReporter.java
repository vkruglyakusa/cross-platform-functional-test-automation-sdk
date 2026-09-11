package com.test.automation.sdk.reporting;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fan-out reporter that forwards the same logical event to many adapters.
 */
public final class CompositeExecutionReporter implements ExecutionReporter {

    private static final Logger log = LogManager.getLogger(CompositeExecutionReporter.class);

    private final List<ExecutionReporter> delegates;

    public CompositeExecutionReporter(ExecutionReporter... delegates) {
        this.delegates = Arrays.asList(delegates);
    }

    @Override
    public void report(ExecutionEvent event) {
        for (ExecutionReporter delegate : delegates) {
            if (delegate == null) {
                continue;
            }
            try {
                delegate.report(event);
            } catch (Exception e) {
                log.warn("Execution reporter [{}] failed for event [{}]: {}",
                        delegate.getClass().getSimpleName(),
                        event != null ? event.getType() : "unknown",
                        e.getMessage());
            }
        }
    }
}
