package com.test.automation.sdk.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Normalized result of one discovery pass over a page/screen, from either the
 * desktop web crawler or the mobile crawler -- see Structure Cleanup Phase 6
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md).
 */
public final class DiscoveryResult {

    private final String platform;
    private final String sourceLabel;
    private final List<DiscoveredElement> elements;

    public DiscoveryResult(String platform, String sourceLabel, List<DiscoveredElement> elements) {
        this.platform = platform;
        this.sourceLabel = sourceLabel;
        this.elements = elements == null ? new ArrayList<>() : new ArrayList<>(elements);
    }

    public String getPlatform() {
        return platform;
    }

    /** URL (web) or screen/context identifier (mobile) this result was discovered from. */
    public String getSourceLabel() {
        return sourceLabel;
    }

    public List<DiscoveredElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /** Count of elements with at least one {@code UNIQUE} candidate. */
    public int resolvedCount() {
        int count = 0;
        for (DiscoveredElement element : elements) {
            if (element.isResolved()) {
                count++;
            }
        }
        return count;
    }
}
