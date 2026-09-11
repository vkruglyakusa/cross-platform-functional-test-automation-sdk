package com.test.automation.sdk.reporting;

import java.nio.file.Path;

/**
 * Reference to an already-captured execution artifact so the SDK can capture
 * once and publish many times.
 */
public final class ExecutionEvidence {

    private final String name;
    private final String type;
    private final String contentType;
    private final Path path;

    private ExecutionEvidence(String name, String type, String contentType, Path path) {
        this.name = name == null ? "" : name;
        this.type = type == null ? "" : type;
        this.contentType = contentType == null ? "application/octet-stream" : contentType;
        this.path = path;
    }

    public static ExecutionEvidence screenshot(String name, Path path) {
        return new ExecutionEvidence(name, "screenshot", "image/png", path);
    }

    public static ExecutionEvidence domDump(String name, Path path) {
        return new ExecutionEvidence(name, "dom", "text/html", path);
    }

    public static ExecutionEvidence pageSource(String name, Path path) {
        return new ExecutionEvidence(name, "pageSource", "text/html", path);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getContentType() {
        return contentType;
    }

    public Path getPath() {
        return path;
    }
}
