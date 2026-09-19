package com.test.automation.sdk.evidence.network;

import java.util.List;

import org.json.JSONObject;

/**
 * SDK v1.5.1 -- an active, adapter-owned Chrome DevTools Protocol network
 * capture session for a single {@link org.openqa.selenium.WebDriver} instance.
 *
 * <p>This interface intentionally exposes nothing CDP-version-specific: it is
 * the seam that lets {@code NetworkTraceRecorder} (the orchestrator) remain
 * completely unaware of which concrete CDP domain-binding package
 * (e.g. {@code org.openqa.selenium.devtools.v146}) produced the buffered
 * entries. Replacing the pinned CDP version in a future release only
 * requires a new {@link CdpNetworkAdapter}/{@link CdpNetworkSession}
 * implementation -- the rest of the evidence/reporting pipeline does not
 * change.
 */
public interface CdpNetworkSession {

    /**
     * Returns an immutable snapshot of the network entries buffered so far.
     * Each entry is a {@code {request: {...}, response: {...}}} shaped
     * {@link JSONObject}; sensitive header/parameter values must already be
     * redacted by the adapter before they are added to the buffer, so callers
     * never need to redact again before persisting the snapshot.
     */
    List<JSONObject> snapshotEntries();

    /** Releases the underlying CDP session/resources. Must never throw. */
    void close();
}
