package com.test.automation.sdk.evidence.network;

import java.util.Collections;
import java.util.List;

/**
 * SDK v1.5.1 -- factory exposing the registered {@link CdpNetworkAdapter}s to
 * {@code NetworkTraceRecorder}, without that orchestrator ever needing to
 * reference a concrete CDP-version-specific adapter class directly.
 *
 * <p>Exactly one adapter is registered today ({@code cdp-v146}). Adding
 * support for a newer CDP version in a future release means adding a new
 * adapter implementation and registering it here -- {@code NetworkTraceRecorder}
 * does not change.
 */
public final class CdpNetworkAdapters {

    private static final List<CdpNetworkAdapter> ADAPTERS =
            Collections.singletonList(new CdpV146NetworkAdapter());

    private CdpNetworkAdapters() {
    }

    /** All registered adapters, in preference order. */
    public static List<CdpNetworkAdapter> all() {
        return ADAPTERS;
    }
}
