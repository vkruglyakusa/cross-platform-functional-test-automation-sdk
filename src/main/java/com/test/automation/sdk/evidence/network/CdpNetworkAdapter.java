package com.test.automation.sdk.evidence.network;

import org.openqa.selenium.WebDriver;

/**
 * SDK v1.5.1 -- isolation seam for CDP-version-specific network capture code.
 *
 * <p>{@code NetworkTraceRecorder} (the public evidence-capture entry point)
 * never imports {@code org.openqa.selenium.devtools.v146.*} directly; it only
 * depends on this interface plus {@link CdpNetworkSession}. Today exactly one
 * adapter is registered ({@code CdpV146NetworkAdapter}), but a future SDK
 * release can add a newer-CDP-version adapter (or replace this one) purely by
 * implementing this interface -- no changes are required anywhere else in the
 * evidence/reporting pipeline.
 *
 * <p>This is deliberately a small, single-adapter seam rather than a general
 * multi-version CDP negotiation framework: building true dynamic CDP-version
 * negotiation is out of scope for this hardening pass and is not attempted
 * here.
 */
public interface CdpNetworkAdapter {

    /** Short identifier for logging, e.g. {@code "cdp-v146"}. */
    String name();

    /**
     * Returns {@code true} if this adapter is able to attach to {@code driver}.
     * Implementations should check both the CDP capability
     * ({@link org.openqa.selenium.devtools.HasDevTools}) and, where practical,
     * the runtime-detected browser name/version reported by the driver's
     * {@code Capabilities} -- so an incompatible browser/CDP-version
     * combination is recognized and skipped rather than attempted and failed.
     * Must never throw; return {@code false} on any detection error.
     */
    boolean supports(WebDriver driver);

    /**
     * Attaches network capture to {@code driver} and returns the active
     * session. May throw -- callers are responsible for treating any failure
     * as "network capture unavailable" and continuing without it (network
     * evidence capture must never fail the test).
     *
     * @param maxEntries bounded in-memory buffer size (oldest entries dropped)
     * @param redactSensitiveData whether sensitive header/param values must be masked before buffering
     */
    CdpNetworkSession attach(WebDriver driver, int maxEntries, boolean redactSensitiveData) throws Exception;
}
