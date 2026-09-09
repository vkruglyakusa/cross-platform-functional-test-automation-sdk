package com.test.automation.sdk.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One discovered UI element, normalized from either crawler's own richer element
 * representation -- see Structure Cleanup Phase 6
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md). Keeps a
 * reference to the original platform-specific object via {@link #unwrap(Class)}
 * for full-fidelity access (e.g. the desktop crawler's shadow-DOM/map-widget/
 * table-header flags, or the mobile crawler's suggested field name) -- mirrors
 * the {@code session.AutomationSession#unwrap(Class)} pattern from Structure
 * Cleanup Phase 3.
 */
public final class DiscoveredElement {

    private final String platform;
    private final String tagOrType;
    private final String text;
    private final List<LocatorCandidate> candidates;
    private final Object source;

    public DiscoveredElement(String platform, String tagOrType, String text,
            List<LocatorCandidate> candidates, Object source) {
        this.platform = platform;
        this.tagOrType = tagOrType;
        this.text = text == null ? "" : text;
        this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        this.source = source;
    }

    public String getPlatform() {
        return platform;
    }

    public String getTagOrType() {
        return tagOrType;
    }

    public String getText() {
        return text;
    }

    public List<LocatorCandidate> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    /** First candidate marked {@link LocatorCandidate.Marker#UNIQUE}, or {@code null} if none resolved. */
    public LocatorCandidate getBestUniqueCandidate() {
        for (LocatorCandidate candidate : candidates) {
            if (candidate.isUnique()) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isResolved() {
        return getBestUniqueCandidate() != null;
    }

    /**
     * Returns the original platform-specific object this element was normalized
     * from (e.g. {@code utility.ElementCrawler.ElementInfo} or
     * {@code mobile.crawler.MobileElementInfo}), cast to {@code type}. Use this
     * for any platform-specific detail not carried by the normalized fields above.
     *
     * @throws ClassCastException if the underlying source is not an instance of {@code type}
     */
    public <T> T unwrap(Class<T> type) {
        return type.cast(source);
    }
}
