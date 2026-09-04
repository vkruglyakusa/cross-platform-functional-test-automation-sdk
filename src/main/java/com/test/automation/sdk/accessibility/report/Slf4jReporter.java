package com.test.automation.sdk.accessibility.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link A11yReporter} that writes findings to SLF4J.
 *
 * <p>This is the out-of-the-box reporter so the library is useful immediately in any
 * project, with no reporting framework required. HTML tags in messages are stripped
 * for clean log output.</p>
 */
public class Slf4jReporter implements A11yReporter {

    private static final Logger log = LoggerFactory.getLogger("accessibility");

    @Override
    public void info(String message) {
        log.info(strip(message));
    }

    @Override
    public void warn(String message) {
        log.warn(strip(message));
    }

    @Override
    public void fail(String message) {
        log.error(strip(message));
    }

    /** Removes simple HTML so the log line is readable. */
    private static String strip(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

