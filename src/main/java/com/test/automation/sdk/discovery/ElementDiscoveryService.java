package com.test.automation.sdk.discovery;

/**
 * Common discovery contract implemented by both the desktop web crawler
 * ({@link WebElementDiscoveryAdapter}) and the mobile crawler
 * ({@code mobile.crawler.MobileElementDiscoveryAdapter}) -- see Structure
 * Cleanup Phase 6 (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md).
 * Adapts each crawler's existing, mature, platform-specific algorithm into one
 * normalized result contract without rewriting either algorithm (Guardrails
 * #15/#16: keep mature crawler algorithms platform-specific, normalize outputs
 * not algorithms).
 */
public interface ElementDiscoveryService {

    /** Discovers elements on the current page/screen (whatever the driver is currently showing). */
    DiscoveryResult discoverCurrent();
}
